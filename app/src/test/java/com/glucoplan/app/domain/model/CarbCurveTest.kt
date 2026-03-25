package com.glucoplan.app.domain.model

import com.google.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.abs

/**
 * Unit tests for CarbCurve, ProteinCurve, and CarbCurveCalculator.
 * These tests verify the accuracy of glucose prediction curves which
 * affect insulin dosing recommendations.
 */
@RunWith(JUnit4::class)
class CarbCurveTest {

    // ─── CarbCurve Basic Tests ───────────────────────────────────────────────────

    @Test
    fun `carbCurve with zero carbs returns zero rise`() {
        val curve = CarbCurve(
            carbsGrams = 0.0,
            glycemicIndex = 70.0
        )

        assertThat(curve.getGlucoseRiseAt(30.0)).isEqualTo(0.0)
    }

    @Test
    fun `carbCurve returns zero for negative time`() {
        val curve = CarbCurve(
            carbsGrams = 30.0,
            glycemicIndex = 70.0
        )

        assertThat(curve.getGlucoseRiseAt(-10.0)).isEqualTo(0.0)
    }

    @Test
    fun `carbCurve returns zero after duration`() {
        val curve = CarbCurve(
            carbsGrams = 30.0,
            glycemicIndex = 70.0
        )

        assertThat(curve.getGlucoseRiseAt(curve.durationMinutes + 10)).isEqualTo(0.0)
    }

    @Test
    fun `carbCurve has positive rise during active period`() {
        val curve = CarbCurve(
            carbsGrams = 30.0,
            glycemicIndex = 70.0
        )

        // At peak time, should have positive glucose rise
        val rise = curve.getGlucoseRiseAt(curve.peakMinutes)
        assertThat(rise).isGreaterThan(0.0)
    }

    // ─── Glycemic Index Tests ────────────────────────────────────────────────────

    @Test
    fun `high GI has faster peak than low GI`() {
        val highGi = CarbCurve(carbsGrams = 30.0, glycemicIndex = 85.0)
        val lowGi = CarbCurve(carbsGrams = 30.0, glycemicIndex = 35.0)

        assertThat(highGi.peakMinutes).isLessThan(lowGi.peakMinutes)
    }

    @Test
    fun `FAST speed category for GI >= 70`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 75.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.FAST)
    }

    @Test
    fun `MEDIUM speed category for GI 50-69`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 55.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.MEDIUM)
    }

    @Test
    fun `SLOW speed category for GI < 50`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 40.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.SLOW)
    }

    @Test
    fun `different GI values produce different curves`() {
        val highGi = CarbCurve(carbsGrams = 50.0, glycemicIndex = 90.0)
        val lowGi = CarbCurve(carbsGrams = 50.0, glycemicIndex = 40.0)

        // At 30 minutes, high GI should have higher rise
        val highGiRise = highGi.getGlucoseRiseAt(30.0)
        val lowGiRise = lowGi.getGlucoseRiseAt(30.0)

        assertThat(highGiRise).isGreaterThan(lowGiRise)
    }

    // ─── Protein and Fat Effects Tests ───────────────────────────────────────────

    @Test
    fun `protein delays peak`() {
        val noProtein = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, proteinsGrams = 0.0)
        val withProtein = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, proteinsGrams = 30.0)

        assertThat(withProtein.peakMinutes).isGreaterThan(noProtein.peakMinutes)
    }

    @Test
    fun `fat delays peak`() {
        val noFat = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 0.0)
        val withFat = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 20.0)

        assertThat(withFat.peakMinutes).isGreaterThan(noFat.peakMinutes)
    }

    @Test
    fun `fat extends duration`() {
        val noFat = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 0.0)
        val withFat = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 30.0)

        assertThat(withFat.durationMinutes).isGreaterThan(noFat.durationMinutes)
    }

    @Test
    fun `high fat meal has extended absorption`() {
        val pizza = CarbCurve(
            carbsGrams = 60.0,
            glycemicIndex = 60.0,
            proteinsGrams = 25.0,
            fatsGrams = 25.0
        )

        // Pizza should have longer duration
        assertThat(pizza.durationMinutes).isGreaterThan(180.0) // More than 3 hours
        assertThat(pizza.peakMinutes).isGreaterThan(60.0) // Delayed peak
    }

    // ─── Curve Shape Tests ───────────────────────────────────────────────────────

    @Test
    fun `curve rises then falls`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)

        val riseAtPeak = curve.getGlucoseRiseAt(curve.peakMinutes)
        val riseAfterPeak = curve.getGlucoseRiseAt(curve.peakMinutes + 30)

        assertThat(riseAfterPeak).isLessThan(riseAtPeak)
    }

    @Test
    fun `curve is higher near peak than at start`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)

        val riseAtStart = curve.getGlucoseRiseAt(10.0)
        val riseAtPeak = curve.getGlucoseRiseAt(curve.peakMinutes)

        assertThat(riseAtPeak).isGreaterThan(riseAtStart)
    }

    @Test
    fun `generatePoints creates valid list`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)

        val points = curve.generatePoints(stepMinutes = 10)

        assertThat(points).isNotEmpty()
        assertThat(points.first().minutes).isEqualTo(0.0)
        assertThat(points.last().minutes).isEqualTo(curve.durationMinutes)
    }

    @Test
    fun `more carbs produce higher rise`() {
        val smallCurve = CarbCurve(carbsGrams = 15.0, glycemicIndex = 70.0)
        val largeCurve = CarbCurve(carbsGrams = 60.0, glycemicIndex = 70.0)

        val smallRise = smallCurve.getGlucoseRiseAt(smallCurve.peakMinutes)
        val largeRise = largeCurve.getGlucoseRiseAt(largeCurve.peakMinutes)

        assertThat(largeRise).isGreaterThan(smallRise)
    }

    // ─── ProteinCurve Tests ───────────────────────────────────────────────────────

    @Test
    fun `proteinCurve with zero protein returns zero rise`() {
        val curve = ProteinCurve(proteinsGrams = 0.0)

        assertThat(curve.getGlucoseRiseAt(180.0)).isEqualTo(0.0)
    }

    @Test
    fun `proteinCurve peaks around 3-4 hours`() {
        val curve = ProteinCurve(proteinsGrams = 50.0)

        assertThat(curve.peakMinutes).isGreaterThan(180.0) // > 3 hours
        assertThat(curve.peakMinutes).isLessThan(270.0)    // < 4.5 hours
    }

    @Test
    fun `proteinCurve has longer duration than fast carbs`() {
        val fastCarb = CarbCurve(carbsGrams = 30.0, glycemicIndex = 80.0)
        val protein = ProteinCurve(proteinsGrams = 30.0)

        assertThat(protein.durationMinutes).isGreaterThan(fastCarb.durationMinutes)
    }

    @Test
    fun `proteinCurve has delayed rise compared to carbs`() {
        val carbCurve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        val proteinCurve = ProteinCurve(proteinsGrams = 30.0)

        val carbRiseAt30min = carbCurve.getGlucoseRiseAt(30.0)
        val proteinRiseAt30min = proteinCurve.getGlucoseRiseAt(30.0)

        // At 30 minutes, carbs should have higher rise
        assertThat(carbRiseAt30min).isGreaterThan(proteinRiseAt30min)

        val carbRiseAt3h = carbCurve.getGlucoseRiseAt(180.0)
        val proteinRiseAt3h = proteinCurve.getGlucoseRiseAt(180.0)

        // At 3 hours, protein may have higher rise
        assertThat(proteinRiseAt3h).isGreaterThan(carbRiseAt3h)
    }

    @Test
    fun `proteinCurve glucoseEquivalent is about 55 percent`() {
        val curve = ProteinCurve(proteinsGrams = 100.0)

        assertThat(curve.glucoseEquivalent).isGreaterThan(50.0)
        assertThat(curve.glucoseEquivalent).isLessThan(60.0)
    }

    // ─── AggregatedCarbCurves Tests ───────────────────────────────────────────────

    @Test
    fun `aggregatedCurves groups by speed correctly`() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0), // FAST
            CarbCurve(carbsGrams = 15.0, glycemicIndex = 55.0), // MEDIUM
            CarbCurve(carbsGrams = 10.0, glycemicIndex = 35.0)  // SLOW
        )

        val aggregated = AggregatedCarbCurves.from(curves)

        assertThat(aggregated.fast).hasSize(1)
        assertThat(aggregated.medium).hasSize(1)
        assertThat(aggregated.slow).hasSize(1)
    }

    @Test
    fun `aggregatedCurves totalCarbs is sum of all`() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0),
            CarbCurve(carbsGrams = 15.0, glycemicIndex = 55.0),
            CarbCurve(carbsGrams = 10.0, glycemicIndex = 35.0)
        )

        val aggregated = AggregatedCarbCurves.from(curves)

        assertThat(aggregated.totalCarbs).isEqualTo(45.0)
    }

    @Test
    fun `aggregatedCurves sums rises at each time`() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0),
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0)
        )

        val aggregated = AggregatedCarbCurves.from(curves)
        val singleCurve = CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0)

        val aggregatedRise = aggregated.getFastRiseAt(30.0)
        val singleRise = singleCurve.getGlucoseRiseAt(30.0)

        // Two curves should have approximately double the rise
        assertThat(aggregatedRise).isGreaterThan(singleRise * 1.8)
    }

    @Test
    fun `getTotalRiseAt sums all categories`() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0), // FAST
            CarbCurve(carbsGrams = 15.0, glycemicIndex = 55.0), // MEDIUM
            CarbCurve(carbsGrams = 10.0, glycemicIndex = 35.0)  // SLOW
        )

        val aggregated = AggregatedCarbCurves.from(curves)
        val totalRise = aggregated.getTotalRiseAt(30.0)
        val sumOfParts = aggregated.getFastRiseAt(30.0) +
                         aggregated.getMediumRiseAt(30.0) +
                         aggregated.getSlowRiseAt(30.0)

        assertThat(totalRise).isEqualTo(sumOfParts)
    }

    // ─── CarbCurveCalculator Tests ───────────────────────────────────────────────

    @Test
    fun `fromComponent creates correct curve`() {
        val component = CalcComponent(
            type = ComponentType.PRODUCT,
            sourceId = 1,
            name = "Bread",
            servingWeight = 100.0,
            carbsPer100g = 50.0,
            caloriesPer100g = 250.0,
            proteinsPer100g = 8.0,
            fatsPer100g = 2.0,
            glycemicIndex = 70.0
        )

        val curve = CarbCurveCalculator.fromComponent(component)

        assertThat(curve.carbsGrams).isEqualTo(50.0) // 100g * 50g/100g
        assertThat(curve.glycemicIndex).isEqualTo(70.0)
        assertThat(curve.proteinsGrams).isEqualTo(8.0)
        assertThat(curve.fatsGrams).isEqualTo(2.0)
        assertThat(curve.name).isEqualTo("Bread")
    }

    @Test
    fun `fromComponents creates list of curves`() {
        val components = listOf(
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 1,
                name = "Bread",
                servingWeight = 50.0,
                carbsPer100g = 50.0,
                caloriesPer100g = 250.0,
                proteinsPer100g = 8.0,
                fatsPer100g = 2.0,
                glycemicIndex = 70.0
            ),
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 2,
                name = "Apple",
                servingWeight = 150.0,
                carbsPer100g = 14.0,
                caloriesPer100g = 52.0,
                proteinsPer100g = 0.3,
                fatsPer100g = 0.2,
                glycemicIndex = 38.0
            )
        )

        val curves = CarbCurveCalculator.fromComponents(components)

        assertThat(curves).hasSize(2)
        assertThat(curves[0].speedCategory).isEqualTo(CarbSpeed.FAST)
        assertThat(curves[1].speedCategory).isEqualTo(CarbSpeed.SLOW)
    }

    @Test
    fun `aggregateFromComponents creates correct aggregation`() {
        val components = listOf(
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 1,
                name = "Rice",
                servingWeight = 100.0,
                carbsPer100g = 28.0,
                caloriesPer100g = 130.0,
                proteinsPer100g = 2.7,
                fatsPer100g = 0.3,
                glycemicIndex = 73.0
            ),
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 2,
                name = "Beans",
                servingWeight = 100.0,
                carbsPer100g = 22.0,
                caloriesPer100g = 110.0,
                proteinsPer100g = 8.0,
                fatsPer100g = 0.5,
                glycemicIndex = 40.0
            )
        )

        val aggregated = CarbCurveCalculator.aggregateFromComponents(components)

        assertThat(aggregated.fast).hasSize(1)  // Rice
        assertThat(aggregated.slow).hasSize(1)  // Beans
        assertThat(aggregated.totalCarbs).isEqualTo(50.0)
    }

    @Test
    fun `proteinFromComponents sums all proteins`() {
        val components = listOf(
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 1,
                name = "Chicken",
                servingWeight = 150.0,
                carbsPer100g = 0.0,
                caloriesPer100g = 165.0,
                proteinsPer100g = 31.0,
                fatsPer100g = 3.6,
                glycemicIndex = 0.0
            ),
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 2,
                name = "Eggs",
                servingWeight = 100.0,
                carbsPer100g = 1.1,
                caloriesPer100g = 155.0,
                proteinsPer100g = 13.0,
                fatsPer100g = 11.0,
                glycemicIndex = 0.0
            )
        )

        val proteinCurve = CarbCurveCalculator.proteinFromComponents(components)

        // 150g * 31g/100g + 100g * 13g/100g = 46.5 + 13 = 59.5g
        assertThat(proteinCurve.proteinsGrams).isWithin(1.0).of(59.5)
    }

    // ─── Numerical Stability Tests ───────────────────────────────────────────────

    @Test
    fun `curve values are never negative`() {
        val curve = CarbCurve(carbsGrams = 50.0, glycemicIndex = 65.0)

        for (t in 0..curve.durationMinutes.toInt() step 5) {
            val rise = curve.getGlucoseRiseAt(t.toDouble())
            assertThat(rise).isAtLeast(0.0)
        }
    }

    @Test
    fun `curve is reasonably smooth`() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)

        var prevRise = 0.0
        for (t in 1..curve.durationMinutes.toInt() step 1) {
            val rise = curve.getGlucoseRiseAt(t.toDouble())
            val change = abs(rise - prevRise)

            // No sudden jumps > 0.5 mmol/L per minute
            assertThat(change).isLessThan(0.5)
            prevRise = rise
        }
    }

    @Test
    fun `very large carb amount still produces valid curve`() {
        val curve = CarbCurve(carbsGrams = 200.0, glycemicIndex = 70.0)

        val peakRise = curve.getGlucoseRiseAt(curve.peakMinutes)

        // Should produce a positive but finite value
        assertThat(peakRise).isGreaterThan(0.0)
        assertThat(peakRise).isLessThan(100.0) // Reasonable upper bound
    }

    // ─── Realistic Meal Scenarios ────────────────────────────────────────────────

    @Test
    fun `typical breakfast scenario`() {
        // Breakfast: toast with jam
        val toast = CarbCurve(
            carbsGrams = 30.0,
            glycemicIndex = 75.0,
            proteinsGrams = 4.0,
            fatsGrams = 2.0
        )
        val jam = CarbCurve(
            carbsGrams = 15.0,
            glycemicIndex = 85.0
        )

        val aggregated = AggregatedCarbCurves.from(listOf(toast, jam))

        // Total carbs should be 45g
        assertThat(aggregated.totalCarbs).isEqualTo(45.0)

        // Most should be fast carbs
        assertThat(aggregated.fast.sumOf { it.carbsGrams }).isGreaterThan(30.0)
    }

    @Test
    fun `pizza scenario`() {
        // Pizza has high carbs, protein, and fat
        val pizza = CarbCurve(
            carbsGrams = 80.0,
            glycemicIndex = 60.0,
            proteinsGrams = 30.0,
            fatsGrams = 25.0
        )

        // Should have delayed and extended curve
        assertThat(pizza.peakMinutes).isGreaterThan(60.0)
        assertThat(pizza.durationMinutes).isGreaterThan(180.0)

        // At 30 minutes, should still be rising
        val rise30 = pizza.getGlucoseRiseAt(30.0)
        val rise90 = pizza.getGlucoseRiseAt(90.0)
        assertThat(rise90).isGreaterThan(rise30)
    }

    @Test
    fun `low carb high protein meal`() {
        val curve = CarbCurve(
            carbsGrams = 10.0,
            glycemicIndex = 40.0,
            proteinsGrams = 50.0,
            fatsGrams = 20.0
        )

        // Should be slow carbs
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.SLOW)

        // Peak should be delayed
        assertThat(curve.peakMinutes).isGreaterThan(60.0)
    }
}
