package com.glucoplan.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.abs

@RunWith(JUnit4::class)
class CarbCurveTest {

    // ─── CarbCurve базовые тесты ─────────────────────────────────────────────────

    @Test
    fun carbCurveWithZeroCarbs_returnsZeroRise() {
        val curve = CarbCurve(carbsGrams = 0.0, glycemicIndex = 70.0)
        assertThat(curve.getGlucoseRiseAt(30.0)).isEqualTo(0.0)
    }

    @Test
    fun carbCurve_returnsZeroForNegativeTime() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        assertThat(curve.getGlucoseRiseAt(-10.0)).isEqualTo(0.0)
    }

    @Test
    fun carbCurve_returnsZeroAfterDurationEnds() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        assertThat(curve.getGlucoseRiseAt(curve.durationMinutes + 10)).isEqualTo(0.0)
    }

    @Test
    fun carbCurve_hasPositiveRiseAtPeakTime() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        assertThat(curve.getGlucoseRiseAt(curve.peakMinutes)).isGreaterThan(0.0)
    }

    // ─── Гликемический индекс ─────────────────────────────────────────────────────

    @Test
    fun highGI_peaksEarlierThanLowGI() {
        val highGi = CarbCurve(carbsGrams = 30.0, glycemicIndex = 85.0)
        val lowGi  = CarbCurve(carbsGrams = 30.0, glycemicIndex = 35.0)
        assertThat(highGi.peakMinutes).isLessThan(lowGi.peakMinutes)
    }

    @Test
    fun speedCategory_isFastWhenGiAtLeast70() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 75.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.FAST)
    }

    @Test
    fun speedCategory_isMediumWhenGiBetween50And69() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 55.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.MEDIUM)
    }

    @Test
    fun speedCategory_isSlowWhenGiBelow50() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 40.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.SLOW)
    }

    @Test
    fun highGI_givesHigherRiseAt30MinThanLowGI() {
        val highGi = CarbCurve(carbsGrams = 50.0, glycemicIndex = 90.0)
        val lowGi  = CarbCurve(carbsGrams = 50.0, glycemicIndex = 40.0)
        assertThat(highGi.getGlucoseRiseAt(30.0)).isGreaterThan(lowGi.getGlucoseRiseAt(30.0))
    }

    // ─── Белки и жиры ────────────────────────────────────────────────────────────

    @Test
    fun protein_delaysPeakTime() {
        val noProtein   = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, proteinsGrams = 0.0)
        val withProtein = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, proteinsGrams = 30.0)
        assertThat(withProtein.peakMinutes).isGreaterThan(noProtein.peakMinutes)
    }

    @Test
    fun fat_delaysPeakTime() {
        val noFat   = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 0.0)
        val withFat = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 20.0)
        assertThat(withFat.peakMinutes).isGreaterThan(noFat.peakMinutes)
    }

    @Test
    fun fat_extendsDuration() {
        val noFat   = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 0.0)
        val withFat = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0, fatsGrams = 30.0)
        assertThat(withFat.durationMinutes).isGreaterThan(noFat.durationMinutes)
    }

    @Test
    fun highFatMeal_hasExtendedAbsorption() {
        val pizza = CarbCurve(
            carbsGrams = 60.0, glycemicIndex = 60.0,
            proteinsGrams = 25.0, fatsGrams = 25.0
        )
        assertThat(pizza.durationMinutes).isGreaterThan(180.0)
        assertThat(pizza.peakMinutes).isGreaterThan(60.0)
    }

    // ─── Форма кривой ────────────────────────────────────────────────────────────

    @Test
    fun curve_risesThenFalls() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        val atPeak      = curve.getGlucoseRiseAt(curve.peakMinutes)
        val afterPeak   = curve.getGlucoseRiseAt(curve.peakMinutes + 30)
        assertThat(afterPeak).isLessThan(atPeak)
    }

    @Test
    fun curve_isHigherAtPeakThanAtStart() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        assertThat(curve.getGlucoseRiseAt(curve.peakMinutes))
            .isGreaterThan(curve.getGlucoseRiseAt(10.0))
    }

    @Test
    fun generatePoints_createsValidList() {
        val curve  = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        val points = curve.generatePoints(stepMinutes = 10)
        assertThat(points).isNotEmpty()
        assertThat(points.first().minutes).isEqualTo(0.0)
        assertThat(points.last().minutes).isEqualTo(curve.durationMinutes)
    }

    @Test
    fun moreCarbs_produceHigherRise() {
        val small = CarbCurve(carbsGrams = 15.0, glycemicIndex = 70.0)
        val large = CarbCurve(carbsGrams = 60.0, glycemicIndex = 70.0)
        assertThat(large.getGlucoseRiseAt(large.peakMinutes))
            .isGreaterThan(small.getGlucoseRiseAt(small.peakMinutes))
    }

    // ─── ProteinCurve ─────────────────────────────────────────────────────────────

    @Test
    fun proteinCurveWithZeroProtein_returnsZeroRise() {
        val curve = ProteinCurve(proteinsGrams = 0.0)
        assertThat(curve.getGlucoseRiseAt(180.0)).isEqualTo(0.0)
    }

    @Test
    fun proteinCurve_peaksAround3to4Hours() {
        val curve = ProteinCurve(proteinsGrams = 50.0)
        assertThat(curve.peakMinutes).isGreaterThan(180.0)
        assertThat(curve.peakMinutes).isLessThan(270.0)
    }

    @Test
    fun proteinCurve_hasLongerDurationThanFastCarbs() {
        val fastCarb = CarbCurve(carbsGrams = 30.0, glycemicIndex = 80.0)
        val protein  = ProteinCurve(proteinsGrams = 30.0)
        assertThat(protein.durationMinutes).isGreaterThan(fastCarb.durationMinutes)
    }

    @Test
    fun proteinCurve_hasDelayedRiseComparedToCarbs() {
        val carbCurve    = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        val proteinCurve = ProteinCurve(proteinsGrams = 30.0)
        // В 30 минут углеводы дают больший подъём
        assertThat(carbCurve.getGlucoseRiseAt(30.0))
            .isGreaterThan(proteinCurve.getGlucoseRiseAt(30.0))
        // В 3 часа белки дают больший подъём
        assertThat(proteinCurve.getGlucoseRiseAt(180.0))
            .isGreaterThan(carbCurve.getGlucoseRiseAt(180.0))
    }

    @Test
    fun proteinCurve_glucoseEquivalentIsAbout55Percent() {
        val curve = ProteinCurve(proteinsGrams = 100.0)
        assertThat(curve.glucoseEquivalent).isGreaterThan(50.0)
        assertThat(curve.glucoseEquivalent).isLessThan(60.0)
    }

    // ─── AggregatedCarbCurves ────────────────────────────────────────────────────

    @Test
    fun aggregatedCurves_groupsBySpeedCorrectly() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0), // FAST
            CarbCurve(carbsGrams = 15.0, glycemicIndex = 55.0), // MEDIUM
            CarbCurve(carbsGrams = 10.0, glycemicIndex = 35.0)  // SLOW
        )
        val agg = AggregatedCarbCurves.from(curves)
        assertThat(agg.fast).hasSize(1)
        assertThat(agg.medium).hasSize(1)
        assertThat(agg.slow).hasSize(1)
    }

    @Test
    fun aggregatedCurves_totalCarbsIsSumOfAll() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0),
            CarbCurve(carbsGrams = 15.0, glycemicIndex = 55.0),
            CarbCurve(carbsGrams = 10.0, glycemicIndex = 35.0)
        )
        assertThat(AggregatedCarbCurves.from(curves).totalCarbs).isEqualTo(45.0)
    }

    @Test
    fun aggregatedCurves_twoCurvesGiveDoubleRise() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0),
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0)
        )
        val agg    = AggregatedCarbCurves.from(curves)
        val single = CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0)
        assertThat(agg.getFastRiseAt(30.0)).isGreaterThan(single.getGlucoseRiseAt(30.0) * 1.8)
    }

    @Test
    fun getTotalRiseAt_sumsAllCategories() {
        val curves = listOf(
            CarbCurve(carbsGrams = 20.0, glycemicIndex = 80.0),
            CarbCurve(carbsGrams = 15.0, glycemicIndex = 55.0),
            CarbCurve(carbsGrams = 10.0, glycemicIndex = 35.0)
        )
        val agg   = AggregatedCarbCurves.from(curves)
        val total = agg.getTotalRiseAt(30.0)
        val sum   = agg.getFastRiseAt(30.0) + agg.getMediumRiseAt(30.0) + agg.getSlowRiseAt(30.0)
        assertThat(total).isEqualTo(sum)
    }

    // ─── CarbCurveCalculator ─────────────────────────────────────────────────────

    @Test
    fun fromComponent_createsCorrectCurve() {
        val component = CalcComponent(
            type = ComponentType.PRODUCT, sourceId = 1, name = "Bread",
            servingWeight = 100.0, carbsPer100g = 50.0, caloriesPer100g = 250.0,
            proteinsPer100g = 8.0, fatsPer100g = 2.0, glycemicIndex = 70.0
        )
        val curve = CarbCurveCalculator.fromComponent(component)
        assertThat(curve.carbsGrams).isEqualTo(50.0)
        assertThat(curve.glycemicIndex).isEqualTo(70.0)
        assertThat(curve.proteinsGrams).isEqualTo(8.0)
        assertThat(curve.fatsGrams).isEqualTo(2.0)
        assertThat(curve.name).isEqualTo("Bread")
    }

    @Test
    fun fromComponents_createsListOfCurves() {
        val components = listOf(
            CalcComponent(type = ComponentType.PRODUCT, sourceId = 1, name = "Bread",
                servingWeight = 50.0, carbsPer100g = 50.0, caloriesPer100g = 250.0,
                proteinsPer100g = 8.0, fatsPer100g = 2.0, glycemicIndex = 70.0),
            CalcComponent(type = ComponentType.PRODUCT, sourceId = 2, name = "Apple",
                servingWeight = 150.0, carbsPer100g = 14.0, caloriesPer100g = 52.0,
                proteinsPer100g = 0.3, fatsPer100g = 0.2, glycemicIndex = 38.0)
        )
        val curves = CarbCurveCalculator.fromComponents(components)
        assertThat(curves).hasSize(2)
        assertThat(curves[0].speedCategory).isEqualTo(CarbSpeed.FAST)
        assertThat(curves[1].speedCategory).isEqualTo(CarbSpeed.SLOW)
    }

    @Test
    fun aggregateFromComponents_groupsCorrectly() {
        val components = listOf(
            CalcComponent(type = ComponentType.PRODUCT, sourceId = 1, name = "Rice",
                servingWeight = 100.0, carbsPer100g = 28.0, caloriesPer100g = 130.0,
                proteinsPer100g = 2.7, fatsPer100g = 0.3, glycemicIndex = 73.0),
            CalcComponent(type = ComponentType.PRODUCT, sourceId = 2, name = "Beans",
                servingWeight = 100.0, carbsPer100g = 22.0, caloriesPer100g = 110.0,
                proteinsPer100g = 8.0, fatsPer100g = 0.5, glycemicIndex = 40.0)
        )
        val agg = CarbCurveCalculator.aggregateFromComponents(components)
        assertThat(agg.fast).hasSize(1)
        assertThat(agg.slow).hasSize(1)
        assertThat(agg.totalCarbs).isEqualTo(50.0)
    }

    @Test
    fun proteinFromComponents_sumsAllProteins() {
        val components = listOf(
            CalcComponent(type = ComponentType.PRODUCT, sourceId = 1, name = "Chicken",
                servingWeight = 150.0, carbsPer100g = 0.0, caloriesPer100g = 165.0,
                proteinsPer100g = 31.0, fatsPer100g = 3.6, glycemicIndex = 0.0),
            CalcComponent(type = ComponentType.PRODUCT, sourceId = 2, name = "Eggs",
                servingWeight = 100.0, carbsPer100g = 1.1, caloriesPer100g = 155.0,
                proteinsPer100g = 13.0, fatsPer100g = 11.0, glycemicIndex = 0.0)
        )
        // 150 * 31/100 + 100 * 13/100 = 46.5 + 13 = 59.5г
        assertThat(CarbCurveCalculator.proteinFromComponents(components).proteinsGrams)
            .isWithin(1.0).of(59.5)
    }

    // ─── Числовая стабильность ───────────────────────────────────────────────────

    @Test
    fun curveValues_areNeverNegative() {
        val curve = CarbCurve(carbsGrams = 50.0, glycemicIndex = 65.0)
        for (t in 0..curve.durationMinutes.toInt() step 5) {
            assertThat(curve.getGlucoseRiseAt(t.toDouble())).isAtLeast(0.0)
        }
    }

    @Test
    fun curve_isReasonablySmooth() {
        val curve = CarbCurve(carbsGrams = 30.0, glycemicIndex = 70.0)
        var prev = 0.0
        for (t in 1..curve.durationMinutes.toInt()) {
            val rise   = curve.getGlucoseRiseAt(t.toDouble())
            val change = abs(rise - prev)
            assertThat(change).isLessThan(1.0)
            prev = rise
        }
    }

    @Test
    fun veryLargeCarbs_producesValidCurve() {
        val curve    = CarbCurve(carbsGrams = 200.0, glycemicIndex = 70.0)
        val peakRise = curve.getGlucoseRiseAt(curve.peakMinutes)
        assertThat(peakRise).isGreaterThan(0.0)
        assertThat(peakRise).isLessThan(100.0)
    }

    // ─── Реальные сценарии ───────────────────────────────────────────────────────

    @Test
    fun breakfastScenario_toastWithJam() {
        val toast = CarbCurve(carbsGrams = 30.0, glycemicIndex = 75.0,
            proteinsGrams = 4.0, fatsGrams = 2.0)
        val jam   = CarbCurve(carbsGrams = 15.0, glycemicIndex = 85.0)
        val agg   = AggregatedCarbCurves.from(listOf(toast, jam))
        assertThat(agg.totalCarbs).isEqualTo(45.0)
        assertThat(agg.fast.sumOf { it.carbsGrams }).isGreaterThan(30.0)
    }

    @Test
    fun pizzaScenario_hasDelayedAndExtendedAbsorption() {
        val pizza = CarbCurve(carbsGrams = 80.0, glycemicIndex = 60.0,
            proteinsGrams = 30.0, fatsGrams = 25.0)
        assertThat(pizza.peakMinutes).isGreaterThan(60.0)
        assertThat(pizza.durationMinutes).isGreaterThan(180.0)
        // В 30 минут кривая ещё растёт
        assertThat(pizza.getGlucoseRiseAt(90.0)).isGreaterThan(pizza.getGlucoseRiseAt(30.0))
    }

    @Test
    fun lowCarbHighProteinMeal_isSlowCategory() {
        val curve = CarbCurve(carbsGrams = 10.0, glycemicIndex = 40.0,
            proteinsGrams = 50.0, fatsGrams = 20.0)
        assertThat(curve.speedCategory).isEqualTo(CarbSpeed.SLOW)
        assertThat(curve.peakMinutes).isGreaterThan(60.0)
    }
}
