package com.glucoplan.app.ui.chart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.core.NightscoutClient
import com.glucoplan.app.core.NsResult
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// ─── Domain models for chart ──────────────────────────────────────────────────

/** Одна точка CGM */
data class GlucosePoint(
    val time: Instant,
    val glucose: Double,   // ммоль/л
    val isManual: Boolean = false  // true = глюкометр, false = сенсор
)

/** Событие на графике — укол, еда или прочее из Nightscout */
sealed class ChartEvent {
    abstract val time: Instant

    data class Injection(
        override val time: Instant,
        val dose: Double,          // ед
        val insulinType: String,
        val isBasal: Boolean
    ) : ChartEvent()

    data class Meal(
        override val time: Instant,
        val carbs: Double,          // г
        val insulin: Double,        // ед
        val proteins: Double = 0.0, // г
        val fats: Double = 0.0,     // г
        val giCategory: String = "", // "low" | "medium" | "high" | ""
        val notes: String
    ) : ChartEvent()

    data class Other(
        override val time: Instant,
        val eventType: String,
        val label: String,
        val notes: String = ""
    ) : ChartEvent()
}

/** Что показывать в тултипе при касании */
data class ChartTooltip(
    val time: Instant,
    val glucose: Double?,
    val nearestEvent: ChartEvent?
)

data class GlucoseChartUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val points: List<GlucosePoint> = emptyList(),
    val events: List<ChartEvent> = emptyList(),
    val settings: AppSettings = AppSettings(),

    // Видимое окно: сколько часов показываем (6, 12, 24, 48)
    val windowHours: Int = 24,

    // Правый край окна (Instant.now() при инициализации, меняется при скролле)
    val windowEnd: Instant = Instant.now(),

    // Что сейчас под пальцем
    val tooltip: ChartTooltip? = null
) {
    val windowStart: Instant get() = windowEnd.minus(windowHours.toLong(), ChronoUnit.HOURS)

    /** Точки в текущем окне */
    val visiblePoints: List<GlucosePoint>
        get() = points.filter { it.time >= windowStart && it.time <= windowEnd }

    /** События в текущем окне */
    val visibleEvents: List<ChartEvent>
        get() = events.filter { it.time >= windowStart && it.time <= windowEnd }

    /** Ось Y по видимым точкам: минимум снизу, максимум сверху, без пустых полей */
    val yMin: Double get() = yRange.first
    val yMax: Double get() = yRange.second

    private val yRange: Pair<Double, Double>
        get() {
            val gs = visiblePoints.map { it.glucose }
            if (gs.isEmpty()) return 3.0 to 10.0
            val lo = gs.minOrNull() ?: return 3.0 to 10.0
            val hi = gs.maxOrNull() ?: return 3.0 to 10.0
            val span = (hi - lo).coerceAtLeast(0.8)
            val pad = span * 0.04
            return (lo - pad) to (hi + pad)
        }
}

@HiltViewModel
class GlucoseChartViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GlucoseChartUiState())
    val state: StateFlow<GlucoseChartUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val settings = repo.getSettings()
            _state.update { it.copy(settings = settings) }

            if (!settings.nsEnabled) {
                _state.update { it.copy(loading = false,
                    error = "Включите Nightscout в Настройках") }
                return@launch
            }
            if (settings.nsUrl.isBlank()) {
                _state.update { it.copy(loading = false,
                    error = "Укажите URL Nightscout в Настройках") }
                return@launch
            }

            loadFromNightscout(settings)
        }
    }

    private suspend fun loadFromNightscout(settings: AppSettings) {
        val client = NightscoutClient(settings.nsUrl, settings.nsApiSecret)
        val since = Instant.now().minus(48, ChronoUnit.HOURS)

        // Загружаем CGM и treatments параллельно
        val cgmResult = withContext(Dispatchers.IO) { client.getEntries(since, count = 600) }
        val treatResult = withContext(Dispatchers.IO) { client.getTreatments(since) }

        when (cgmResult) {
            is NsResult.Error -> {
                Timber.w("Chart: CGM load failed: ${cgmResult.message}")
                _state.update { it.copy(loading = false, error = "Ошибка загрузки: ${cgmResult.message}") }
                return
            }
            is NsResult.Success -> Unit
        }

        val cgmPoints = (cgmResult as NsResult.Success).data.map { reading ->
            GlucosePoint(
                time = reading.time,
                glucose = reading.glucose,
                isManual = false
            )
        }.sortedBy { it.time }

        val events = mutableListOf<ChartEvent>()
        val manualReadings = mutableListOf<GlucosePoint>()

        if (treatResult is NsResult.Success) {
            treatResult.data.forEach { t ->
                Timber.d("Chart: treatment eventType=${t.eventType} glucoseType=${t.glucoseType} carbs=${t.carbs} insulin=${t.insulin} glucose=${t.glucose}")
                if (t.glucoseType.equals("Finger", true) ||
                    t.glucoseType.equals("Manual", true) ||
                    t.eventType.equals("BG Check", true)
                ) {
                    t.glucose?.let { g ->
                        manualReadings.add(GlucosePoint(
                            time = t.createdAt,
                            glucose = g,
                            isManual = true
                        ))
                    }
                }

                val carbs = t.carbs ?: 0.0
                val insulin = t.insulin ?: 0.0
                when {
                    insulin > 0 && carbs <= 0 -> {
                        events.add(ChartEvent.Injection(
                            time = t.createdAt,
                            dose = insulin,
                            insulinType = t.eventType.ifBlank { "bolus" },
                            isBasal = t.eventType.contains("basal", ignoreCase = true)
                        ))
                    }
                    carbs > 0 -> {
                        events.add(ChartEvent.Meal(
                            time = t.createdAt,
                            carbs = carbs,
                            insulin = insulin,
                            proteins = t.proteins ?: 0.0,
                            fats = t.fats ?: 0.0,
                            giCategory = t.glycemicIndex ?: "",
                            notes = t.notes ?: ""
                        ))
                    }
                    t.eventType.equals("BG Check", true) -> Unit
                    t.eventType.equals("Unknown", true) && t.notes.isNullOrBlank() -> Unit
                    else -> {
                        events.add(ChartEvent.Other(
                            time = t.createdAt,
                            eventType = t.eventType,
                            label = nsEventLabel(t),
                            notes = t.notes ?: ""
                        ))
                    }
                }
            }
        }

        // Также добавляем инъекции из локальной БД (GlucoPen записи)
        val localInjections = repo.getRecentInjections(48)
        localInjections.forEach { inj ->
            if (inj.dose <= 0.0) return@forEach
            try {
                val injTime = Instant.parse(inj.injectedAt)
                if (injTime >= since) {
                    // Не дублируем болюс уже загруженный из NS treatments
                    val alreadyHave = events.filterIsInstance<ChartEvent.Injection>()
                        .any { kotlin.math.abs(it.time.epochSecond - injTime.epochSecond) < 60
                               && kotlin.math.abs(it.dose - inj.dose) < 0.1 }
                    if (!alreadyHave) {
                        events.add(ChartEvent.Injection(
                            time = injTime,
                            dose = inj.dose,
                            insulinType = inj.insulinType,
                            isBasal = inj.isBasal
                        ))
                    }
                }
            } catch (e: Exception) {
                Timber.w("Chart: failed to parse injection time: ${inj.injectedAt}")
            }
        }

        // Приёмы пищи из локальной БД
        // Формат datetime: "yyyy-MM-dd'T'HH:mm:ss" в локальной таймзоне
        val localMealFormatter = java.time.format.DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
        val localMeals = repo.getMealsByDate(null)
        localMeals.forEach { meal ->
            try {
                val mealTime = Instant.from(localMealFormatter.parse(meal.datetime))
                if (mealTime >= since) {
                    // Не дублируем если уже есть из NS (±5 минут)
                    val alreadyHave = events.filterIsInstance<ChartEvent.Meal>()
                        .any { kotlin.math.abs(it.time.epochSecond - mealTime.epochSecond) < 300 }
                    if (!alreadyHave) {
                        events.add(ChartEvent.Meal(
                            time = mealTime,
                            carbs = meal.totalCarbs,
                            insulin = meal.insulinDose,
                            proteins = meal.totalProteins,
                            fats = meal.totalFats,
                            notes = meal.notes
                        ))
                    }
                }
            } catch (e: Exception) {
                Timber.w("Chart: failed to parse meal datetime: ${meal.datetime}")
            }
        }

        // Авто-масштаб оси Y считается из visiblePoints в UiState

        // Добавляем ручные замеры к CGM точкам (с флагом isManual=true)
        // Дедупликация: не добавляем если уже есть CGM точка в ±2 минуты
        val mergedPoints = (cgmPoints + manualReadings
            .filter { manual ->
                cgmPoints.none { cgm ->
                    kotlin.math.abs(cgm.time.epochSecond - manual.time.epochSecond) < 120
                }
            })
            .sortedBy { it.time }

        _state.update {
            it.copy(
                loading = false,
                points = mergedPoints,
                events = events.sortedBy { e -> e.time },
                windowEnd = Instant.now()
            )
        }
        Timber.i("Chart: loaded ${cgmPoints.size} CGM points, ${events.size} events")
    }

    // ── Управление окном просмотра ────────────────────────────────────────────

    /** Сдвиг окна влево/вправо. delta в часах, отрицательное = назад */
    fun shiftWindow(deltaHours: Double) {
        _state.update { s ->
            val newEnd = s.windowEnd.plus((deltaHours * 3600).toLong(), ChronoUnit.SECONDS)
            // Не пускаем вперёд времени
            val clampedEnd = minOf(newEnd, Instant.now())
            // Не пускаем дальше 48ч назад
            val earliest = Instant.now().minus(48, ChronoUnit.HOURS)
                .plus(s.windowHours.toLong(), ChronoUnit.HOURS)
            s.copy(windowEnd = clampedEnd.coerceAtLeast(earliest))
        }
    }

    fun setWindowHours(hours: Int) {
        _state.update { it.copy(windowHours = hours, windowEnd = Instant.now()) }
    }

    /** Pinch zoom: scale > 1 = zoom in (меньше окно), scale < 1 = zoom out */
    fun onPinchZoom(scale: Float) {
        _state.update { s ->
            val currentHours = s.windowHours.toDouble()
            // Инвертируем: сводим пальцы (scale > 1) = уменьшаем окно
            val newHours = (currentHours / scale).coerceIn(2.0, 48.0)
            // Снэппим к ближайшему из доступных значений
            val snapped = listOf(2, 4, 6, 12, 24, 48)
                .minByOrNull { kotlin.math.abs(it - newHours) }!!
            if (snapped != s.windowHours) s.copy(windowHours = snapped) else s
        }
    }

    fun goToNow() {
        _state.update { it.copy(windowEnd = Instant.now()) }
    }

    // ── Тултип при касании ────────────────────────────────────────────────────

    /** Вызывается из Canvas когда палец на позиции [fraction] по оси X (0.0–1.0) */
    fun onChartTouch(fraction: Float) {
        val s = _state.value
        if (s.visiblePoints.isEmpty()) {
            _state.update { it.copy(tooltip = null) }
            return
        }

        val windowStartMs = s.windowStart.toEpochMilli()
        val windowEndMs   = s.windowEnd.toEpochMilli()
        val touchTimeMs   = windowStartMs + (fraction * (windowEndMs - windowStartMs)).toLong()
        val touchTime     = Instant.ofEpochMilli(touchTimeMs)

        // Ближайшая точка CGM
        val nearest = s.visiblePoints.minByOrNull {
            kotlin.math.abs(it.time.epochSecond - touchTime.epochSecond)
        }

        // Ближайшее событие в радиусе 15 минут
        val nearestEvent = s.visibleEvents.minByOrNull {
            kotlin.math.abs(it.time.epochSecond - touchTime.epochSecond)
        }?.let { ev ->
            if (kotlin.math.abs(ev.time.epochSecond - touchTime.epochSecond) < 900) ev else null
        }

        _state.update {
            it.copy(tooltip = ChartTooltip(
                time = nearest?.time ?: touchTime,
                glucose = nearest?.glucose,
                nearestEvent = nearestEvent
            ))
        }
    }

    fun clearTooltip() {
        _state.update { it.copy(tooltip = null) }
    }
}

private fun nsEventLabel(t: com.glucoplan.app.core.NsTreatment): String {
    val type = t.eventType
    val lower = type.lowercase()
    return when {
        "temp basal" in lower -> buildString {
            append("База")
            t.absolute?.let { append(" ${"%.2f".format(it)} ед/ч") }
            t.percent?.let { append(" $it%") }
            t.duration?.let { if (it > 0) append(" ${it.toInt()} мин") }
        }
        "site change" in lower || "cannula" in lower -> "Смена места"
        "sensor start" in lower -> "Новый сенсор"
        "sensor stop" in lower || "sensor change" in lower -> "Сенсор"
        "cartridge" in lower -> "Смена картриджа"
        "battery" in lower -> "Батарея"
        "exercise" in lower -> "Нагрузка"
        "note" in lower || "announcement" in lower -> t.notes?.takeIf { it.isNotBlank() } ?: "Заметка"
        "profile" in lower -> "Профиль"
        "target" in lower -> "Цель"
        else -> t.notes?.takeIf { it.isNotBlank() } ?: type.ifBlank { "Событие" }
    }
}
