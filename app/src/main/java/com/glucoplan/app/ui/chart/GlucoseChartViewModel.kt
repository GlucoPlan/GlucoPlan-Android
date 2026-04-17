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

/** Событие на графике — укол или приём пищи */
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
        val carbs: Double,         // г
        val insulin: Double,       // ед
        val notes: String
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
    val tooltip: ChartTooltip? = null,

    // Границы оси Y (авто, но с минимум 2..16)
    val yMin: Double = 2.0,
    val yMax: Double = 16.0
) {
    val windowStart: Instant get() = windowEnd.minus(windowHours.toLong(), ChronoUnit.HOURS)

    /** Точки в текущем окне */
    val visiblePoints: List<GlucosePoint>
        get() = points.filter { it.time >= windowStart && it.time <= windowEnd }

    /** События в текущем окне */
    val visibleEvents: List<ChartEvent>
        get() = events.filter { it.time >= windowStart && it.time <= windowEnd }
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
        if (treatResult is NsResult.Success) {
            treatResult.data.forEach { t ->
                when {
                    // Болюс / коррекция
                    t.insulin != null && t.insulin > 0 && (t.carbs == null || t.carbs == 0.0) -> {
                        events.add(ChartEvent.Injection(
                            time = t.createdAt,
                            dose = t.insulin,
                            insulinType = "bolus",
                            isBasal = false
                        ))
                    }
                    // Приём пищи (с инсулином или без)
                    t.carbs != null && t.carbs > 0 -> {
                        events.add(ChartEvent.Meal(
                            time = t.createdAt,
                            carbs = t.carbs,
                            insulin = t.insulin ?: 0.0,
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
                            notes = meal.notes
                        ))
                    }
                }
            } catch (e: Exception) {
                Timber.w("Chart: failed to parse meal datetime: ${meal.datetime}")
            }
        }

        // Авто-масштаб оси Y
        val allGlucose = cgmPoints.map { it.glucose }
        val yMin = (allGlucose.minOrNull() ?: 2.0).coerceAtMost(2.0)
        val yMax = (allGlucose.maxOrNull() ?: 16.0).coerceAtLeast(16.0)

        _state.update {
            it.copy(
                loading = false,
                points = cgmPoints,
                events = events.sortedBy { e -> e.time },
                yMin = yMin,
                yMax = yMax,
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
