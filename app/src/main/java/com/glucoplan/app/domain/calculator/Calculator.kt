package com.glucoplan.app.domain.calculator

import com.glucoplan.app.domain.model.CalcComponent
import kotlin.math.*

object InsulinCalculator {

    fun calculateFoodDose(carbsG: Double, carbsPerXe: Double, carbCoefficient: Double): Double {
        if (carbsPerXe <= 0) return 0.0
        return (carbsG / carbsPerXe) * carbCoefficient
    }

    fun calculateCorrection(currentGlucose: Double, targetGlucose: Double, sensitivity: Double): Double {
        if (sensitivity <= 0) return 0.0
        return max(0.0, (currentGlucose - targetGlucose) / sensitivity)
    }

    fun totalDose(foodDose: Double, correction: Double, trendDelta: Double): Double =
        foodDose + correction + trendDelta

    fun roundDown(dose: Double, step: Double): Double {
        if (step <= 0) return dose
        return floor(dose / step) * step
    }

    fun roundUp(dose: Double, step: Double): Double {
        if (step <= 0) return dose
        val lower = roundDown(dose, step)
        return if (lower < dose - 1e-9) lower + step else lower
    }

    fun adjustPortion(
        components: List<CalcComponent>,
        targetDose: Double,
        carbsPerXe: Double,
        carbCoefficient: Double,
        currentGlucose: Double,
        targetGlucose: Double,
        sensitivity: Double,
        trendDelta: Double
    ): List<CalcComponent> {
        val adjustable = components.filter { it.includedInAdjustment }
        val fixed = components.filter { !it.includedInAdjustment }
        if (adjustable.isEmpty()) return components

        val fixedCarbs = fixed.sumOf { it.carbsInPortion }
        val correction = calculateCorrection(currentGlucose, targetGlucose, sensitivity)
        val doseForFood = targetDose - correction - trendDelta
        val targetCarbs = doseForFood * carbsPerXe / carbCoefficient - fixedCarbs

        if (targetCarbs <= 0) return components

        val currentAdjustableCarbs = adjustable.sumOf { it.carbsInPortion }
        if (currentAdjustableCarbs <= 0) return components

        val ratio = targetCarbs / currentAdjustableCarbs
        return components.map { c ->
            if (c.includedInAdjustment) c.withWeight(c.servingWeight * ratio) else c
        }
    }
}

// ─── Simulator ───────────────────────────────────────────────────────────────

data class SimPoint(
    val minuteFromStart: Int,
    val glucoseFromCarbs: Double,
    val glucoseFromInsulin: Double,
    val combined: Double
)

object SimulatorCalculator {

    val insulinProfiles = mapOf(
        "fiasp"     to mapOf("onset" to 3.0,  "tp" to 45.0, "td" to 270.0),
        "novorapid" to mapOf("onset" to 5.0,  "tp" to 65.0, "td" to 360.0),
        "humalog"   to mapOf("onset" to 5.0,  "tp" to 60.0, "td" to 300.0)
    )

    private fun insulinCurve(
        insulinType: String, dose: Double, sensitivity: Double,
        minutes: Int = 240, step: Int = 5
    ): List<Double> {
        val profile = insulinProfiles[insulinType] ?: insulinProfiles["novorapid"]!!
        val onset = profile["onset"]!!
        val tp = profile["tp"]!!
        val td = profile["td"]!!
        val tpEff = tp - onset
        val tdEff = td - onset
        val denom = 1.0 - 2.0 * tpEff / tdEff
        val tau = if (abs(denom) > 1e-9) tpEff * (1.0 - tpEff / tdEff) / denom else tpEff
        val s = 1.0 / (1.0 - 2.0 * tau / tdEff)

        var integral = 0.0
        val densities = mutableListOf<Double>()
        for (t in 0..(td + onset).toInt()) {
            val tActive = t - onset
            val density = if (tActive > 0) (s / (tau * tau)) * tActive * exp(-tActive / tau) else 0.0
            densities.add(density)
            integral += density
        }

        val result = mutableListOf<Double>()
        var used = 0.0
        var idx = 0
        val points = minutes / step + 1
        for (i in 0 until points) {
            val t = i * step
            while (idx <= t && idx < densities.size) {
                used += if (integral > 0) densities[idx] / integral else 0.0
                idx++
            }
            result.add(min(used, 1.0) * dose * sensitivity)
        }
        return result
    }

    private fun carbCurve(
        carbsG: Double, glycemicIndex: Double, carbsPerXe: Double,
        minutes: Int = 240, step: Int = 5
    ): List<Double> {
        val gi = glycemicIndex.coerceIn(30.0, 100.0)
        val tPeak = 90.0 - (gi - 30.0) * (45.0 / 70.0)
        val peakRise = (carbsG / carbsPerXe) * 2.2
        val points = minutes / step + 1
        return (0 until points).map { i ->
            val t = i * step.toDouble()
            val riseFrac = 1.0 / (1.0 + exp(-(t - tPeak) / (tPeak / 3.0)))
            val fallFrac = exp(-max(0.0, t - tPeak) / (tPeak * 2.0))
            min(peakRise * riseFrac * fallFrac * 2.0, peakRise)
        }
    }

    fun simulate(
        initialGlucose: Double,
        carbsG: Double,
        glycemicIndex: Double,
        insulinDose: Double,
        insulinType: String,
        sensitivity: Double,
        carbsPerXe: Double,
        minutes: Int = 240
    ): List<SimPoint> {
        val step = 5
        val points = minutes / step + 1
        val carbs = carbCurve(carbsG, glycemicIndex, carbsPerXe, minutes, step)
        val insulin = insulinCurve(insulinType, insulinDose, sensitivity, minutes, step)
        return (0 until points).map { i ->
            val t = i * step
            val carbEffect = carbs[i]
            val insulinEffect = if (i < insulin.size) insulin[i] else insulin.last()
            SimPoint(
                minuteFromStart = t,
                glucoseFromCarbs = initialGlucose + carbEffect,
                glucoseFromInsulin = initialGlucose - insulinEffect,
                combined = initialGlucose + carbEffect - insulinEffect
            )
        }
    }
}
