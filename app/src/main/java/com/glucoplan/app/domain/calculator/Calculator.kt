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

