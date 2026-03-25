package com.glucoplan.app.domain.model

import kotlin.math.*

/**
 * Carbohydrate absorption curve model.
 * Takes into account glycemic index, proteins, and fats.
 */
data class CarbCurve(
    val carbsGrams: Double,
    val glycemicIndex: Double,
    val proteinsGrams: Double = 0.0,
    val fatsGrams: Double = 0.0,
    val name: String = ""
) {
    /**
     * Carb speed category based on GI
     */
    val speedCategory: CarbSpeed = when {
        glycemicIndex >= 70 -> CarbSpeed.FAST
        glycemicIndex >= 50 -> CarbSpeed.MEDIUM
        else -> CarbSpeed.SLOW
    }

    /**
     * Time to peak absorption (minutes)
     * Adjusted for GI, proteins, and fats
     */
    val peakMinutes: Double = calculatePeakTime()

    /**
     * Duration of absorption (minutes)
     */
    val durationMinutes: Double = calculateDuration()

    /**
     * Maximum glucose rise (mmol/L per gram of carbs)
     * Based on typical ~0.06 mmol/L per gram (varies by individual)
     */
    val peakRisePerGram: Double = 0.06

    private fun calculatePeakTime(): Double {
        // Base peak time from GI
        // GI 100 -> 20 min, GI 50 -> 60 min, GI 30 -> 90 min
        val basePeak = when {
            glycemicIndex >= 90 -> 20.0
            glycemicIndex >= 70 -> 30.0
            glycemicIndex >= 55 -> 45.0
            glycemicIndex >= 40 -> 60.0
            else -> 75.0
        }

        // Protein delays absorption (slower gastric emptying)
        // ~5 min delay per 10g protein
        val proteinDelay = proteinsGrams * 0.5

        // Fat delays absorption more significantly
        // ~10 min delay per 10g fat
        val fatDelay = fatsGrams * 1.0

        return (basePeak + proteinDelay + fatDelay).coerceIn(20.0, 180.0)
    }

    private fun calculateDuration(): Double {
        // Base duration ~3-4 hours, extended by fat/protein
        val baseDuration = when {
            glycemicIndex >= 70 -> 120.0
            glycemicIndex >= 50 -> 150.0
            else -> 180.0
        }

        // High-fat meals can extend absorption to 5+ hours
        val fatExtension = fatsGrams * 2.0
        val proteinExtension = proteinsGrams * 0.5

        return (baseDuration + fatExtension + proteinExtension).coerceIn(90.0, 360.0)
    }

    /**
     * Get glucose rise at a specific time
     * Returns mmol/L contribution to glucose
     */
    fun getGlucoseRiseAt(minutes: Double): Double {
        if (minutes <= 0 || minutes >= durationMinutes) return 0.0
        if (carbsGrams <= 0) return 0.0

        val t = minutes.toDouble()
        val normalizedTime = t / durationMinutes

        // Skewed distribution: faster rise, slower fall
        // Using a modified beta distribution shape

        val peakFraction = peakMinutes / durationMinutes

        // Rise phase (sigmoid-like)
        val rise = 1.0 / (1.0 + exp(-12.0 * (normalizedTime - peakFraction * 0.5)))

        // Fall phase (exponential decay)
        val fall = exp(-4.0 * max(0.0, normalizedTime - peakFraction))

        // Combined curve
        val curve = rise * fall * 2.0

        // Scale to total glucose rise
        val totalRise = carbsGrams * peakRisePerGram

        return totalRise * curve
    }

    /**
     * Generate curve points for plotting
     */
    fun generatePoints(stepMinutes: Int = 5): List<CurvePoint> {
        val points = mutableListOf<CurvePoint>()
        var t = 0.0
        while (t <= durationMinutes) {
            points.add(CurvePoint(t, getGlucoseRiseAt(t)))
            t += stepMinutes
        }
        return points
    }

    data class CurvePoint(val minutes: Double, val glucoseRise: Double)
}

enum class CarbSpeed {
    FAST,    // GI >= 70, peak 20-40 min
    MEDIUM,  // GI 50-69, peak 40-70 min
    SLOW     // GI < 50, peak 70-120 min
}

/**
 * Protein-to-glucose conversion curve.
 * Proteins are converted to glucose via gluconeogenesis,
 * with peak around 3-4 hours after eating.
 */
data class ProteinCurve(
    val proteinsGrams: Double
) {
    // ~50-60% of protein converts to glucose
    private val conversionRate: Double = 0.55

    // Equivalent "glucose" from protein
    val glucoseEquivalent: Double
        get() = proteinsGrams * conversionRate

    // Peak at 3-4 hours
    val peakMinutes: Double
        get() = 210.0

    // Duration ~6 hours
    val durationMinutes: Double
        get() = 360.0

    /**
     * Get glucose rise from protein at a specific time
     */
    fun getGlucoseRiseAt(minutes: Double): Double {
        if (minutes <= 0 || minutes >= durationMinutes) return 0.0
        if (proteinsGrams <= 0) return 0.0

        val t = minutes.toDouble()
        val normalizedTime = t / durationMinutes

        // Delayed rise, peaked distribution
        val rise = 1.0 / (1.0 + exp(-15.0 * (normalizedTime - 0.5)))
        val fall = exp(-5.0 * max(0.0, normalizedTime - 0.6))

        val curve = rise * fall * 1.5

        // Lower rise rate per gram compared to carbs
        val totalRise = glucoseEquivalent * 0.04

        return totalRise * curve
    }

    fun generatePoints(stepMinutes: Int = 5): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var t = 0.0
        while (t <= durationMinutes) {
            points.add(t to getGlucoseRiseAt(t))
            t += stepMinutes
        }
        return points
    }
}

/**
 * Aggregated carb curves by speed category.
 * Used for displaying separate curves on the chart.
 */
data class AggregatedCarbCurves(
    val fast: List<CarbCurve>,      // GI >= 70
    val medium: List<CarbCurve>,    // GI 50-69
    val slow: List<CarbCurve>       // GI < 50
) {
    val totalCarbs: Double
        get() = (fast + medium + slow).sumOf { it.carbsGrams }

    /**
     * Get combined glucose rise at a specific time for each category
     */
    fun getFastRiseAt(minutes: Double): Double = fast.sumOf { it.getGlucoseRiseAt(minutes) }
    fun getMediumRiseAt(minutes: Double): Double = medium.sumOf { it.getGlucoseRiseAt(minutes) }
    fun getSlowRiseAt(minutes: Double): Double = slow.sumOf { it.getGlucoseRiseAt(minutes) }

    /**
     * Get total glucose rise at a specific time
     */
    fun getTotalRiseAt(minutes: Double): Double =
        getFastRiseAt(minutes) + getMediumRiseAt(minutes) + getSlowRiseAt(minutes)

    companion object {
        /**
         * Create from list of carb curves
         */
        fun from(curves: List<CarbCurve>): AggregatedCarbCurves {
            return AggregatedCarbCurves(
                fast = curves.filter { it.speedCategory == CarbSpeed.FAST },
                medium = curves.filter { it.speedCategory == CarbSpeed.MEDIUM },
                slow = curves.filter { it.speedCategory == CarbSpeed.SLOW }
            )
        }
    }
}

/**
 * Calculator for carb curves from calculator components
 */
object CarbCurveCalculator {

    /**
     * Convert CalcComponent to CarbCurve
     */
    fun fromComponent(component: CalcComponent): CarbCurve {
        return CarbCurve(
            carbsGrams = component.carbsInPortion,
            glycemicIndex = component.glycemicIndex,
            proteinsGrams = component.proteinsInPortion,
            fatsGrams = component.fatsInPortion,
            name = component.name
        )
    }

    /**
     * Create curves from list of components
     */
    fun fromComponents(components: List<CalcComponent>): List<CarbCurve> {
        return components.map { fromComponent(it) }
    }

    /**
     * Create aggregated curves from components
     */
    fun aggregateFromComponents(components: List<CalcComponent>): AggregatedCarbCurves {
        return AggregatedCarbCurves.from(fromComponents(components))
    }

    /**
     * Create protein curve from components
     */
    fun proteinFromComponents(components: List<CalcComponent>): ProteinCurve {
        val totalProtein = components.sumOf { it.proteinsInPortion }
        return ProteinCurve(totalProtein)
    }
}
