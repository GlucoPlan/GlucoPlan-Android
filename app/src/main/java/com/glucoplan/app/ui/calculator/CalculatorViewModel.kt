package com.glucoplan.app.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.core.NightscoutClient
import com.glucoplan.app.core.NsResult
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.calculator.InsulinCalculator
import com.glucoplan.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CalculatorUiState(
    val components: List<CalcComponent> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val currentGlucose: Double = 0.0,
    val cgmReading: CgmReading? = null,
    val cgmError: String? = null,
    val isSaving: Boolean = false,
    val savedSuccess: Boolean = false,
    val error: String? = null
) {
    val totalCarbs: Double get() = components.sumOf { it.carbsInPortion }
    val totalCalories: Double get() = components.sumOf { it.caloriesInPortion }
    val totalProteins: Double get() = components.sumOf { it.proteinsInPortion }
    val totalFats: Double get() = components.sumOf { it.fatsInPortion }
    val breadUnits: Double get() = if (settings.carbsPerXe > 0) totalCarbs / settings.carbsPerXe else 0.0
    val glycemicLoad: Double get() = components.sumOf { it.glycemicLoad }

    val foodDose: Double get() = InsulinCalculator.calculateFoodDose(totalCarbs, settings.carbsPerXe, settings.carbCoefficient)
    val correction: Double get() = InsulinCalculator.calculateCorrection(currentGlucose, settings.targetGlucose, settings.sensitivity)
    val trendDelta: Double get() = cgmReading?.trendDelta ?: 0.0
    val totalDose: Double get() = InsulinCalculator.totalDose(foodDose, correction, trendDelta)
    val roundedDown: Double get() = InsulinCalculator.roundDown(totalDose, settings.insulinStep)
    val roundedUp: Double get() = InsulinCalculator.roundUp(totalDose, settings.insulinStep)
}

private const val CGM_POLL_INTERVAL_MS = 60_000L  // 1 minute

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CalculatorUiState())
    val state: StateFlow<CalculatorUiState> = _state.asStateFlow()

    private var cgmPollJob: Job? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            val settings = repo.getSettings()
            _state.update { it.copy(settings = settings) }
            // Start or stop CGM polling depending on whether NS is enabled
            if (settings.nsEnabled && settings.nsUrl.isNotBlank()) {
                startCgmPolling(settings)
            } else {
                stopCgmPolling()
            }
        }
    }

    // ── CGM polling ────────────────────────────────────────────────────────────

    private fun startCgmPolling(settings: AppSettings) {
        if (cgmPollJob?.isActive == true) return
        Timber.d("Starting CGM polling (interval ${CGM_POLL_INTERVAL_MS}ms)")
        cgmPollJob = viewModelScope.launch {
            while (true) {
                fetchCgm(settings)
                delay(CGM_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopCgmPolling() {
        cgmPollJob?.cancel()
        cgmPollJob = null
    }

    private suspend fun fetchCgm(settings: AppSettings) {
        try {
            val client = NightscoutClient(settings.nsUrl, settings.nsApiSecret)
            val result = withContext(Dispatchers.IO) { client.getLatestReading() }
            when (result) {
                is NsResult.Success -> {
                    Timber.d("CGM reading: ${result.data.glucose} mmol/L")
                    updateCgmReading(result.data)
                    _state.update { it.copy(cgmError = null) }
                }
                is NsResult.Error -> {
                    Timber.w("CGM fetch error: ${result.message}")
                    _state.update { it.copy(cgmError = result.message) }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CGM fetch exception")
            _state.update { it.copy(cgmError = e.message) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCgmPolling()
    }

    // ── Component management ────────────────────────────────────────────────────

    fun addProduct(product: Product, weight: Double) {
        val comp = CalcComponent.fromProduct(product, weight)
        _state.update { it.copy(components = it.components + comp) }
    }

    fun addDish(dish: DishWithIngredients, weight: Double) {
        val comp = CalcComponent.fromDish(dish, weight)
        _state.update { it.copy(components = it.components + comp) }
    }

    fun removeComponent(key: String) {
        _state.update { it.copy(components = it.components.filter { c -> c.key != key }) }
    }

    fun updateWeight(key: String, weight: Double) {
        _state.update {
            it.copy(components = it.components.map { c ->
                if (c.key == key) c.withWeight(weight) else c
            })
        }
    }

    fun toggleAdjustment(key: String) {
        _state.update {
            it.copy(components = it.components.map { c ->
                if (c.key == key) c.withAdjustment(!c.includedInAdjustment) else c
            })
        }
    }

    fun updateGlucose(glucose: Double) {
        _state.update { it.copy(currentGlucose = glucose) }
    }

    fun updateSettings(settings: AppSettings) {
        _state.update { it.copy(settings = settings) }
    }

    fun updateCgmReading(reading: CgmReading?) {
        _state.update {
            if (reading != null) {
                it.copy(cgmReading = reading, currentGlucose = reading.glucose)
            } else {
                it.copy(cgmReading = null)
            }
        }
    }

    fun adjustPortion(targetDose: Double) {
        val s = _state.value
        val adjusted = InsulinCalculator.adjustPortion(
            components = s.components,
            targetDose = targetDose,
            carbsPerXe = s.settings.carbsPerXe,
            carbCoefficient = s.settings.carbCoefficient,
            currentGlucose = s.currentGlucose,
            targetGlucose = s.settings.targetGlucose,
            sensitivity = s.settings.sensitivity,
            trendDelta = s.trendDelta
        )
        _state.update { it.copy(components = adjusted) }
    }

    fun clearAll() {
        _state.update { it.copy(components = emptyList()) }
    }

    fun loadFromHistory(components: List<CalcComponent>) {
        _state.update { it.copy(components = components) }
    }

    fun saveMeal(notes: String, sendToNightscout: Boolean = false) {
        val s = _state.value
        if (s.components.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                val meal = Meal(
                    datetime = formatter.format(Instant.now()),
                    insulinDose = s.totalDose,
                    glucose = s.currentGlucose,
                    notes = notes,
                    totalCarbs = s.totalCarbs,
                    totalCalories = s.totalCalories,
                    totalProteins = s.totalProteins,
                    totalFats = s.totalFats,
                    breadUnits = s.breadUnits
                )
                val mealId = repo.saveMeal(meal, s.components)

                // Отправить в Nightscout если запрошено и NS включён
                if (sendToNightscout && s.settings.nsEnabled && s.settings.nsUrl.isNotBlank()) {
                    try {
                        val client = NightscoutClient(s.settings.nsUrl, s.settings.nsApiSecret)
                        client.postTreatment(
                            carbs = s.totalCarbs,
                            insulin = s.totalDose,
                            glucose = s.currentGlucose,
                            notes = notes
                        )
                        Timber.i("Meal posted to Nightscout: mealId=$mealId")
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to post meal to Nightscout")
                        // Не прерываем — сохранение в БД уже прошло
                    }
                }

                _state.update { it.copy(isSaving = false, savedSuccess = true, components = emptyList()) }
                delay(2000)
                _state.update { it.copy(savedSuccess = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
