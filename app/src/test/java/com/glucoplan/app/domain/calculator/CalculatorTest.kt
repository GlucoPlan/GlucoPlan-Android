package com.glucoplan.app.domain.calculator

import com.glucoplan.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for InsulinCalculator and SimulatorCalculator.
 * These are the core calculation engines for insulin dosing.
 */
@RunWith(JUnit4::class)
class InsulinCalculatorTest {

    // ─── calculateFoodDose Tests ─────────────────────────────────────────────────

    @Test
    fun `calculateFoodDose returns zero for zero carbs`() {
        val dose = InsulinCalculator.calculateFoodDose(
            carbsG = 0.0,
            carbsPerXe = 12.0,
            carbCoefficient = 1.5
        )
        assertThat(dose).isEqualTo(0.0)
    }

    @Test
    fun `calculateFoodDose calculates correctly for 1 XE`() {
        // 12g carbs = 1 XE, 1.5 coefficient = 1.5 units
        val dose = InsulinCalculator.calculateFoodDose(
            carbsG = 12.0,
            carbsPerXe = 12.0,
            carbCoefficient = 1.5
        )
        assertThat(dose).isEqualTo(1.5)
    }

    @Test
    fun `calculateFoodDose calculates correctly for multiple XE`() {
        // 36g carbs = 3 XE, 1.5 coefficient = 4.5 units
        val dose = InsulinCalculator.calculateFoodDose(
            carbsG = 36.0,
            carbsPerXe = 12.0,
            carbCoefficient = 1.5
        )
        assertThat(dose).isEqualTo(4.5)
    }

    @Test
    fun `calculateFoodDose returns zero for invalid carbsPerXe`() {
        val dose = InsulinCalculator.calculateFoodDose(
            carbsG = 36.0,
            carbsPerXe = 0.0,
            carbCoefficient = 1.5
        )
        assertThat(dose).isEqualTo(0.0)
    }

    @Test
    fun `calculateFoodDose handles different XE sizes`() {
        // 15g carbs with 15g XE = 1 XE
        val dose = InsulinCalculator.calculateFoodDose(
            carbsG = 15.0,
            carbsPerXe = 15.0,
            carbCoefficient = 1.0
        )
        assertThat(dose).isEqualTo(1.0)
    }

    @Test
    fun `calculateFoodDose handles different coefficients`() {
        // Same carbs, different coefficient
        val dose1 = InsulinCalculator.calculateFoodDose(24.0, 12.0, 1.0)
        val dose2 = InsulinCalculator.calculateFoodDose(24.0, 12.0, 2.0)

        assertThat(dose2).isEqualTo(dose1 * 2.0)
    }

    // ─── calculateCorrection Tests ────────────────────────────────────────────────

    @Test
    fun `calculateCorrection returns zero for on-target glucose`() {
        val correction = InsulinCalculator.calculateCorrection(
            currentGlucose = 6.0,
            targetGlucose = 6.0,
            sensitivity = 2.5
        )
        assertThat(correction).isEqualTo(0.0)
    }

    @Test
    fun `calculateCorrection returns zero for below-target glucose`() {
        val correction = InsulinCalculator.calculateCorrection(
            currentGlucose = 4.0,
            targetGlucose = 6.0,
            sensitivity = 2.5
        )
        assertThat(correction).isEqualTo(0.0)
    }

    @Test
    fun `calculateCorrection calculates correctly for high glucose`() {
        // 5 mmol/L above target, 2.5 sensitivity = 2 units
        val correction = InsulinCalculator.calculateCorrection(
            currentGlucose = 11.0,
            targetGlucose = 6.0,
            sensitivity = 2.5
        )
        assertThat(correction).isEqualTo(2.0)
    }

    @Test
    fun `calculateCorrection returns zero for invalid sensitivity`() {
        val correction = InsulinCalculator.calculateCorrection(
            currentGlucose = 11.0,
            targetGlucose = 6.0,
            sensitivity = 0.0
        )
        assertThat(correction).isEqualTo(0.0)
    }

    @Test
    fun `calculateCorrection handles different sensitivities`() {
        val correction1 = InsulinCalculator.calculateCorrection(10.0, 6.0, 2.0)
        val correction2 = InsulinCalculator.calculateCorrection(10.0, 6.0, 4.0)

        // Higher sensitivity = lower correction
        assertThat(correction2).isEqualTo(correction1 / 2.0)
    }

    // ─── totalDose Tests ──────────────────────────────────────────────────────────

    @Test
    fun `totalDose sums all components`() {
        val total = InsulinCalculator.totalDose(
            foodDose = 4.0,
            correction = 2.0,
            trendDelta = 0.5
        )
        assertThat(total).isEqualTo(6.5)
    }

    @Test
    fun `totalDose handles negative trend delta`() {
        val total = InsulinCalculator.totalDose(
            foodDose = 4.0,
            correction = 0.0,
            trendDelta = -1.0
        )
        assertThat(total).isEqualTo(3.0)
    }

    @Test
    fun `totalDose handles all zeros`() {
        val total = InsulinCalculator.totalDose(0.0, 0.0, 0.0)
        assertThat(total).isEqualTo(0.0)
    }

    // ─── Rounding Tests ───────────────────────────────────────────────────────────

    @Test
    fun `roundDown rounds to step correctly`() {
        assertThat(InsulinCalculator.roundDown(3.7, 0.5)).isEqualTo(3.5)
        assertThat(InsulinCalculator.roundDown(3.2, 0.5)).isEqualTo(3.0)
        assertThat(InsulinCalculator.roundDown(4.0, 0.5)).isEqualTo(4.0)
    }

    @Test
    fun `roundUp rounds to step correctly`() {
        assertThat(InsulinCalculator.roundUp(3.2, 0.5)).isEqualTo(3.5)
        assertThat(InsulinCalculator.roundUp(3.0, 0.5)).isEqualTo(3.0)
        assertThat(InsulinCalculator.roundUp(3.5, 0.5)).isEqualTo(3.5)
    }

    @Test
    fun `roundDown handles zero step`() {
        assertThat(InsulinCalculator.roundDown(3.7, 0.0)).isEqualTo(3.7)
    }

    @Test
    fun `roundUp handles zero step`() {
        assertThat(InsulinCalculator.roundUp(3.7, 0.0)).isEqualTo(3.7)
    }

    @Test
    fun `rounding with 1 unit step`() {
        assertThat(InsulinCalculator.roundDown(3.7, 1.0)).isEqualTo(3.0)
        assertThat(InsulinCalculator.roundUp(3.2, 1.0)).isEqualTo(4.0)
    }

    // ─── adjustPortion Tests ───────────────────────────────────────────────────────

    private fun testComponent(
        id: Long = 1,
        name: String = "Test",
        carbs: Double = 20.0,
        adjustable: Boolean = true
    ) = CalcComponent(
        type = ComponentType.PRODUCT,
        sourceId = id,
        name = name,
        servingWeight = 100.0,
        carbsPer100g = carbs,
        caloriesPer100g = 100.0,
        proteinsPer100g = 0.0,
        fatsPer100g = 0.0,
        glycemicIndex = 50.0,
        includedInAdjustment = adjustable
    )

    @Test
    fun `adjustPortion returns same components when all non-adjustable`() {
        val components = listOf(
            testComponent(1, "A", 20.0, adjustable = false),
            testComponent(2, "B", 30.0, adjustable = false)
        )

        val adjusted = InsulinCalculator.adjustPortion(
            components = components,
            targetDose = 5.0,
            carbsPerXe = 12.0,
            carbCoefficient = 1.5,
            currentGlucose = 6.0,
            targetGlucose = 6.0,
            sensitivity = 2.5,
            trendDelta = 0.0
        )

        assertThat(adjusted).isEqualTo(components)
    }

    @Test
    fun `adjustPortion scales adjustable components`() {
        val components = listOf(
            testComponent(1, "Adjustable", carbs = 20.0, adjustable = true)
        )

        // Current: 20g carbs = 20/12 * 1.5 = 2.5 units
        // Target: 5 units food dose = 5/1.5 * 12 = 40g carbs

        val adjusted = InsulinCalculator.adjustPortion(
            components = components,
            targetDose = 5.0,
            carbsPerXe = 12.0,
            carbCoefficient = 1.5,
            currentGlucose = 6.0,
            targetGlucose = 6.0,
            sensitivity = 2.5,
            trendDelta = 0.0
        )

        // Should have scaled up
        val newCarbs = adjusted.sumOf { it.carbsInPortion }
        assertThat(newCarbs).isGreaterThan(20.0)
    }

    @Test
    fun `adjustPortion keeps fixed components unchanged`() {
        val components = listOf(
            testComponent(1, "Fixed", carbs = 10.0, adjustable = false),
            testComponent(2, "Adjustable", carbs = 20.0, adjustable = true)
        )

        val adjusted = InsulinCalculator.adjustPortion(
            components = components,
            targetDose = 3.75, // 30g / 12 * 1.5
            carbsPerXe = 12.0,
            carbCoefficient = 1.5,
            currentGlucose = 6.0,
            targetGlucose = 6.0,
            sensitivity = 2.5,
            trendDelta = 0.0
        )

        val fixed = adjusted.find { it.sourceId == 1L }
        assertThat(fixed!!.carbsInPortion).isEqualTo(10.0)
    }

    // ─── SimulatorCalculator Tests ────────────────────────────────────────────────

    @Test
    fun `simulate returns correct number of points`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 30.0,
            glycemicIndex = 70.0,
            insulinDose = 3.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        // 240 minutes / 5 step + 1 = 49 points
        assertThat(points).hasSize(49)
    }

    @Test
    fun `simulate starts at initial glucose`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 30.0,
            glycemicIndex = 70.0,
            insulinDose = 3.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        assertThat(points.first().combined).isWithin(1.0).of(7.0)
    }

    @Test
    fun `simulate carb curve rises then falls`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 50.0,
            glycemicIndex = 70.0,
            insulinDose = 0.0, // No insulin to see pure carb effect
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        // Find peak
        val peak = points.maxByOrNull { it.glucoseFromCarbs }
        val peakIndex = points.indexOf(peak)

        // Peak should be somewhere in the middle
        assertThat(peakIndex).isGreaterThan(0)
        assertThat(peakIndex).isLessThan(points.size - 5)

        // After peak should be lower
        assertThat(points.last().glucoseFromCarbs).isLessThan(peak!!.glucoseFromCarbs)
    }

    @Test
    fun `simulate insulin curve drops glucose`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 0.0,
            glycemicIndex = 50.0,
            insulinDose = 3.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        // With insulin only, glucose should drop
        val finalGlucose = points.last().glucoseFromInsulin
        assertThat(finalGlucose).isLessThan(7.0)
    }

    @Test
    fun `simulate high GI peaks earlier than low GI`() {
        val highGiPoints = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 30.0,
            glycemicIndex = 85.0,
            insulinDose = 0.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        val lowGiPoints = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 30.0,
            glycemicIndex = 35.0,
            insulinDose = 0.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        val highGiPeak = highGiPoints.maxByOrNull { it.glucoseFromCarbs }
        val lowGiPeak = lowGiPoints.maxByOrNull { it.glucoseFromCarbs }

        // High GI should peak earlier
        assertThat(highGiPeak!!.minuteFromStart).isLessThan(lowGiPeak!!.minuteFromStart)
    }

    @Test
    fun `simulate FIASP acts faster than NOVORAPID`() {
        val fiaspPoints = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 0.0,
            glycemicIndex = 50.0,
            insulinDose = 4.0,
            insulinType = "fiasp",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 120
        )

        val novorapidPoints = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 0.0,
            glycemicIndex = 50.0,
            insulinDose = 4.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 120
        )

        // At 30 minutes, Fiasp should have more effect
        val fiasp30 = fiaspPoints.find { it.minuteFromStart == 30 }
        val novorapid30 = novorapidPoints.find { it.minuteFromStart == 30 }

        assertThat(fiasp30!!.glucoseFromInsulin).isLessThan(novorapid30!!.glucoseFromInsulin)
    }

    @Test
    fun `simulate zero insulin and carbs stays at initial`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 0.0,
            glycemicIndex = 50.0,
            insulinDose = 0.0,
            insulinType = "novorapid",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 240
        )

        // Without inputs, glucose should stay constant
        for (point in points) {
            assertThat(point.combined).isWithin(0.01).of(7.0)
        }
    }

    @Test
    fun `simulate combined curve reflects both effects`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 48.0, // 4 XE
            glycemicIndex = 70.0,
            insulinDose = 6.0, // Match food
            insulinType = "novorapid",
            sensitivity = 2.0,
            carbsPerXe = 12.0,
            minutes = 360
        )

        // Combined should be between carb-only and insulin-only
        for (point in points) {
            assertThat(point.combined).isAtLeast(point.glucoseFromInsulin - 0.1)
            assertThat(point.combined).isAtMost(point.glucoseFromCarbs + 0.1)
        }
    }

    @Test
    fun `SimPoint contains valid data`() {
        val points = SimulatorCalculator.simulate(
            initialGlucose = 5.5,
            carbsG = 30.0,
            glycemicIndex = 60.0,
            insulinDose = 3.0,
            insulinType = "novorapid",
            sensitivity = 2.0,
            carbsPerXe = 12.0,
            minutes = 60
        )

        for (point in points) {
            assertThat(point.minuteFromStart).isAtLeast(0)
            // Glucose values should be positive
            assertThat(point.glucoseFromCarbs).isAtLeast(0.0)
            // Combined = initial + carbs - insulin
            assertThat(point.combined).isAtLeast(0.0)
        }
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────────────

    @Test
    fun `calculateFoodDose handles very large values`() {
        val dose = InsulinCalculator.calculateFoodDose(
            carbsG = 1000.0,
            carbsPerXe = 12.0,
            carbCoefficient = 1.5
        )
        assertThat(dose).isEqualTo(125.0)
    }

    @Test
    fun `calculateCorrection handles extreme glucose`() {
        val correction = InsulinCalculator.calculateCorrection(
            currentGlucose = 25.0,
            targetGlucose = 6.0,
            sensitivity = 2.0
        )
        assertThat(correction).isEqualTo(9.5)
    }

    @Test
    fun `simulate handles unknown insulin type gracefully`() {
        // Should use default (novorapid)
        val points = SimulatorCalculator.simulate(
            initialGlucose = 7.0,
            carbsG = 30.0,
            glycemicIndex = 70.0,
            insulinDose = 3.0,
            insulinType = "unknown",
            sensitivity = 2.5,
            carbsPerXe = 12.0,
            minutes = 60
        )

        assertThat(points).isNotEmpty()
    }

    @Test
    fun `roundDown handles negative values`() {
        val result = InsulinCalculator.roundDown(-0.3, 0.5)
        assertThat(result).isEqualTo(-0.5)
    }

    @Test
    fun `roundUp handles negative values`() {
        val result = InsulinCalculator.roundUp(-0.8, 0.5)
        assertThat(result).isEqualTo(-0.5)
    }
}
