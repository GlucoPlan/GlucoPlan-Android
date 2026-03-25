package com.glucoplan.app.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SimulatorUiState(
    // Input parameters
    val components: List<CalcComponent> = emptyList(),
    val currentGlucose: Double = 7.0,
    val insulinDose: Double = 2.0,
    val insulinType: String = "novorapid",
    val settings: AppSettings = AppSettings(),
    
    // Simulation results
    val carbCurves: AggregatedCarbCurves = AggregatedCarbCurves(emptyList(), emptyList(), emptyList()),
    val proteinCurve: ProteinCurve = ProteinCurve(0.0),
    val iobState: IobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
    
    // Chart data
    val fastCarbPoints: List<ChartPoint> = emptyList(),
    val mediumCarbPoints: List<ChartPoint> = emptyList(),
    val slowCarbPoints: List<ChartPoint> = emptyList(),
    val proteinPoints: List<ChartPoint> = emptyList(),
    val insulinPoints: List<ChartPoint> = emptyList(),
    val combinedPoints: List<ChartPoint> = emptyList(),
    
    // Stats
    val totalCarbs: Double = 0.0,
    val totalProtein: Double = 0.0,
    val totalFat: Double = 0.0,
    val peakGlucose: Double = 7.0,
    val finalGlucose: Double = 7.0,
    val minutesToPeak: Int = 0,
    
    // Recommendations
    val recommendation: BolusRecommendation? = null,
    
    // UI state
    val showAdvanced: Boolean = false,
    val loading: Boolean = true
) {
    val insulinProfile: InsulinProfile? get() = InsulinProfiles.get(insulinType)
    
    data class ChartPoint(
        val minutes: Int,
        val value: Double
    )
}

@HiltViewModel
class SimulatorViewModel @Inject constructor(
    private val repo: GlucoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SimulatorUiState())
    val state: StateFlow<SimulatorUiState> = _state.asStateFlow()

    /**
     * Initialize from calculator state
     */
    fun init(
        components: List<CalcComponent>,
        insulin: Double,
        glucose: Double,
        insulinType: String,
        settings: AppSettings
    ) {
        Timber.d("Simulator init: ${components.size} components, insulin=$insulin, glucose=$glucose")
        
        viewModelScope.launch {
            // Get recent injections for IOB
            val previousInjections = repo.getRecentInjections(6)
            val iobState = IobCalculator.calculate(previousInjections)
            Timber.d("IOB calculated: ${iobState.totalIob} units from ${previousInjections.size} injections")
            
            val totalCarbs = components.sumOf { it.carbsInPortion }
            val totalProtein = components.sumOf { it.proteinsInPortion }
            val totalFat = components.sumOf { it.fatsInPortion }
            
            _state.value = SimulatorUiState(
                components = components,
                currentGlucose = if (glucose > 0) glucose else 7.0,
                insulinDose = insulin.coerceAtLeast(0.0),
                insulinType = insulinType,
                settings = settings,
                totalCarbs = totalCarbs,
                totalProtein = totalProtein,
                totalFat = totalFat,
                iobState = iobState,
                loading = false
            )
            
            runSimulation()
            calculateRecommendation()
        }
    }

    /**
     * Update simulation parameters
     */
    fun update(
        insulin: Double? = null,
        insulinType: String? = null,
        glucose: Double? = null
    ) {
        _state.update { s ->
            s.copy(
                insulinDose = insulin ?: s.insulinDose,
                insulinType = insulinType ?: s.insulinType,
                currentGlucose = glucose ?: s.currentGlucose
            )
        }
        runSimulation()
        calculateRecommendation()
    }

    /**
     * Toggle advanced settings visibility
     */
    fun toggleAdvanced() {
        _state.update { it.copy(showAdvanced = !it.showAdvanced) }
    }

    /**
     * Run the simulation
     */
    private fun runSimulation() {
        val s = _state.value
        val profile = InsulinProfiles.get(s.insulinType) ?: return
        
        Timber.d("Running simulation with ${s.components.size} components")
        
        // 1. Calculate carb curves
        val carbCurves = CarbCurveCalculator.aggregateFromComponents(s.components)
        val proteinCurve = CarbCurveCalculator.proteinFromComponents(s.components)
        
        // 2. Generate chart points
        val durationMinutes = 360  // 6 hours
        val stepMinutes = 5
        
        val fastPoints = mutableListOf<SimulatorUiState.ChartPoint>()
        val mediumPoints = mutableListOf<SimulatorUiState.ChartPoint>()
        val slowPoints = mutableListOf<SimulatorUiState.ChartPoint>()
        val proteinPoints = mutableListOf<SimulatorUiState.ChartPoint>()
        val insulinPoints = mutableListOf<SimulatorUiState.ChartPoint>()
        val combinedPoints = mutableListOf<SimulatorUiState.ChartPoint>()
        
        var peakGlucose = s.currentGlucose
        var minutesToPeak = 0
        
        for (t in 0..durationMinutes step stepMinutes) {
            val tDouble = t.toDouble()
            
            // Carb contributions
            val fastRise = carbCurves.getFastRiseAt(tDouble)
            val mediumRise = carbCurves.getMediumRiseAt(tDouble)
            val slowRise = carbCurves.getSlowRiseAt(tDouble)
            val proteinRise = proteinCurve.getGlucoseRiseAt(tDouble)
            
            // Insulin effect
            val usedFraction = profile.getUsedFractionAt(tDouble)
            val insulinEffect = s.insulinDose * usedFraction * s.settings.sensitivity
            
            // Combined glucose
            val carbTotal = fastRise + mediumRise + slowRise + proteinRise
            val combined = s.currentGlucose + carbTotal - insulinEffect
            
            // Track peak
            if (combined > peakGlucose) {
                peakGlucose = combined
                minutesToPeak = t
            }
            
            // Add points (for carb-only and insulin-only curves, use partial contributions)
            fastPoints.add(SimulatorUiState.ChartPoint(t, s.currentGlucose + fastRise))
            mediumPoints.add(SimulatorUiState.ChartPoint(t, s.currentGlucose + mediumRise))
            slowPoints.add(SimulatorUiState.ChartPoint(t, s.currentGlucose + slowRise))
            proteinPoints.add(SimulatorUiState.ChartPoint(t, s.currentGlucose + proteinRise))
            insulinPoints.add(SimulatorUiState.ChartPoint(t, s.currentGlucose - insulinEffect))
            combinedPoints.add(SimulatorUiState.ChartPoint(t, combined))
        }
        
        val finalGlucose = combinedPoints.lastOrNull()?.value ?: s.currentGlucose
        
        // 3. Update state
        _state.update { it.copy(
            carbCurves = carbCurves,
            proteinCurve = proteinCurve,
            fastCarbPoints = fastPoints,
            mediumCarbPoints = mediumPoints,
            slowCarbPoints = slowPoints,
            proteinPoints = proteinPoints,
            insulinPoints = insulinPoints,
            combinedPoints = combinedPoints,
            peakGlucose = peakGlucose,
            finalGlucose = finalGlucose,
            minutesToPeak = minutesToPeak
        )}
        
        Timber.d("Simulation complete: peak=${"%.1f".format(peakGlucose)} at ${minutesToPeak}min, final=${"%.1f".format(finalGlucose)}")
    }

    /**
     * Calculate bolus recommendation
     */
    private fun calculateRecommendation() {
        val s = _state.value
        
        val recommendation = BolusRecommender.calculate(
            components = s.components,
            currentGlucose = s.currentGlucose,
            cgmReading = null,  // Will be passed from calculator
            iobState = s.iobState,
            settings = s.settings
        )
        
        _state.update { it.copy(recommendation = recommendation) }
        Timber.d("Recommendation: ${recommendation.dose} units, timing=${recommendation.timing}, confidence=${recommendation.confidence}")
    }

    /**
     * Get summary text for sharing/export
     */
    fun getSummaryText(): String {
        val s = _state.value
        return buildString {
            appendLine("=== Симуляция GlucoPlan ===")
            appendLine()
            appendLine("Параметры:")
            appendLine("• Текущий сахар: ${"%.1f".format(s.currentGlucose)} ммоль/л")
            appendLine("• Углеводы: ${"%.0f".format(s.totalCarbs)} г")
            appendLine("• Белки: ${"%.0f".format(s.totalProtein)} г")
            appendLine("• Жиры: ${"%.0f".format(s.totalFat)} г")
            appendLine("• Инсулин: ${"%.1f".format(s.insulinDose)} ед (${s.insulinProfile?.displayName ?: s.insulinType})")
            appendLine()
            appendLine("Прогноз:")
            appendLine("• Пик: ${"%.1f".format(s.peakGlucose)} ммоль/л через ${s.minutesToPeak} мин")
            appendLine("• Через 6ч: ${"%.1f".format(s.finalGlucose)} ммоль/л")
            appendLine()
            s.recommendation?.let { r ->
                appendLine("Рекомендация:")
                appendLine("• Доза: ${"%.1f".format(r.dose)} ед")
                appendLine("• Тайминг: ${r.timing.description}")
                if (r.warnings.isNotEmpty()) {
                    appendLine("• Предупреждения:")
                    r.warnings.forEach { appendLine("  - $it") }
                }
            }
        }
    }
}
