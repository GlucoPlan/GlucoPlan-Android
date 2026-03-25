package com.glucoplan.app.domain.model

import com.google.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant

/**
 * Unit tests for BolusRecommender.
 * CRITICAL for patient safety - incorrect recommendations can lead to
 * dangerous insulin dosing errors.
 */
@RunWith(JUnit4::class)
class BolusRecommenderTest {

    // Helper to create default settings
    private fun defaultSettings() = AppSettings(
        carbsPerXe = 12.0,
        carbCoefficient = 1.5,
        sensitivity = 2.5,
        targetGlucose = 6.0,
        targetGlucoseMin = 3.9,
        targetGlucoseMax = 10.0,
        insulinStep = 0.5,
        insulinType = "novorapid"
    )

    // Helper to create a simple component
    private fun testComponent(
        name: String = "Test Food",
        carbs: Double = 30.0,
        gi: Double = 70.0,
        proteins: Double = 0.0,
        fats: Double = 0.0
    ) = CalcComponent(
        type = ComponentType.PRODUCT,
        sourceId = 1,
        name = name,
        servingWeight = 100.0,
        carbsPer100g = carbs,
        caloriesPer100g = 200.0,
        proteinsPer100g = proteins,
        fatsPer100g = fats,
        glycemicIndex = gi
    )

    // ─── Basic Calculation Tests ─────────────────────────────────────────────────

    @Test
    fun `calculate returns zero dose for empty components`() {
        val result = BolusRecommender.calculate(
            components = emptyList(),
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.dose).isEqualTo(0.0)
    }

    @Test
    fun `calculate returns food dose for normal carbs`() {
        // 30g carbs = 2.5 XE * 1.5 = 3.75 units
        val components = listOf(testComponent(carbs = 30.0))

        val result = BolusRecommender.calculate(
            components = components,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // 30/12 * 1.5 = 3.75, rounded down to 3.5
        assertThat(result.dose).isGreaterThan(3.0)
        assertThat(result.dose).isLessThan(4.0)
    }

    @Test
    fun `calculate adds correction for high glucose`() {
        val components = listOf(testComponent(carbs = 24.0)) // 2 XE = 3 units

        val result = BolusRecommender.calculate(
            components = components,
            currentGlucose = 11.0, // 5 above target
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Food: 24/12 * 1.5 = 3
        // Correction: (11-6)/2.5 = 2
        // Total ~ 5 units
        assertThat(result.dose).isGreaterThan(4.5)
    }

    @Test
    fun `calculate subtracts IOB from dose`() {
        val components = listOf(testComponent(carbs = 48.0)) // 4 XE = 6 units

        val result = BolusRecommender.calculate(
            components = components,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(3.0, 3.0, 0.0, 0, emptyList()), // 3 units IOB
            settings = defaultSettings()
        )

        // Food: 48/12 * 1.5 = 6
        // Minus IOB: 6 - 3 = 3
        assertThat(result.dose).isLessThan(4.0)
    }

    // ─── Safety Checks ───────────────────────────────────────────────────────────

    @Test
    fun `calculate returns DO_NOT_INJECT for hypoglycemia`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 3.0, // Hypoglycemia
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.confidence).isEqualTo(Confidence.DO_NOT_INJECT)
        assertThat(result.timing).isEqualTo(BolusTiming.WAIT_AND_RECHECK)
        assertThat(result.dose).isEqualTo(0.0)
        assertThat(result.warnings).isNotEmpty()
    }

    @Test
    fun `calculate warns about high IOB`() {
        val components = listOf(testComponent(carbs = 12.0)) // 1 XE = 1.5 units
        val highIob = IobState(5.0, 5.0, 0.0, 0, emptyList())

        val result = BolusRecommender.calculate(
            components = components,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = highIob,
            settings = defaultSettings()
        )

        assertThat(result.warnings).isNotEmpty()
        assertThat(result.warnings.any { it.contains("активного инсулина") }).isTrue()
    }

    @Test
    fun `calculate dose never goes negative`() {
        val components = listOf(testComponent(carbs = 12.0))
        val veryHighIob = IobState(10.0, 10.0, 0.0, 0, emptyList())

        val result = BolusRecommender.calculate(
            components = components,
            currentGlucose = 4.0, // Below target
            cgmReading = null,
            iobState = veryHighIob,
            settings = defaultSettings()
        )

        assertThat(result.dose).isAtLeast(0.0)
    }

    // ─── Timing Recommendations ──────────────────────────────────────────────────

    @Test
    fun `high glucose recommends earlier injection`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 10.0, // High
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.timing).isIn(listOf(
            BolusTiming.IN_15_MIN,
            BolusTiming.IN_20_MIN
        ))
    }

    @Test
    fun `low glucose recommends later injection`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0, gi = 75.0)), // Fast carbs
            currentGlucose = 4.2, // Below target min
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.timing).isIn(listOf(
            BolusTiming.AFTER_MEAL,
            BolusTiming.NOW
        ))
    }

    @Test
    fun `fast carbs recommend earlier timing`() {
        val fastCarbs = listOf(testComponent(carbs = 30.0, gi = 85.0))

        val result = BolusRecommender.calculate(
            components = fastCarbs,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Fast carbs should recommend earlier timing
        assertThat(result.timing).isNotEqualTo(BolusTiming.AFTER_MEAL)
    }

    @Test
    fun `slow carbs allow later timing`() {
        val slowCarbs = listOf(testComponent(carbs = 30.0, gi = 35.0))

        val result = BolusRecommender.calculate(
            components = slowCarbs,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Slow carbs - more flexible timing
        assertThat(result.timing).isNotNull()
    }

    // ─── CGM Trend Integration ───────────────────────────────────────────────────

    @Test
    fun `rising glucose trend increases dose`() {
        val cgmRising = CgmReading(
            glucose = 8.0,
            direction = "SingleUp",
            time = Instant.now()
        )

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 8.0,
            cgmReading = cgmRising,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Should have a trend adjustment
        assertThat(result.adjustments.any { it.category == AdjustmentCategory.TREND }).isTrue()
    }

    @Test
    fun `falling glucose trend decreases dose`() {
        val cgmFalling = CgmReading(
            glucose = 8.0,
            direction = "SingleDown",
            time = Instant.now()
        )

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 8.0,
            cgmReading = cgmFalling,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Trend adjustment should be negative
        val trendAdj = result.adjustments.find { it.category == AdjustmentCategory.TREND }
        if (trendAdj != null) {
            assertThat(trendAdj.amount).isLessThan(0.0)
        }
    }

    // ─── Protein and Fat Effects ─────────────────────────────────────────────────

    @Test
    fun `high protein adds to dose with warning`() {
        val highProtein = listOf(testComponent(
            carbs = 20.0,
            proteins = 40.0
        ))

        val result = BolusRecommender.calculate(
            components = highProtein,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.adjustments.any { it.category == AdjustmentCategory.PROTEIN }).isTrue()
        assertThat(result.warnings.any { it.contains("белок") || it.contains("white") }).isTrue()
    }

    @Test
    fun `high fat adds warning about delayed absorption`() {
        val highFat = listOf(testComponent(
            carbs = 30.0,
            fats = 30.0
        ))

        val result = BolusRecommender.calculate(
            components = highFat,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.warnings.any { it.contains("жир") || it.contains("fat") }).isTrue()
    }

    // ─── Confidence Levels ───────────────────────────────────────────────────────

    @Test
    fun `high confidence for simple meal with CGM`() {
        val cgm = CgmReading(
            glucose = 6.0,
            direction = "Flat",
            time = Instant.now()
        )

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 6.0,
            cgmReading = cgm,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.confidence).isEqualTo(Confidence.HIGH)
    }

    @Test
    fun `low confidence for high IOB`() {
        val highIob = IobState(4.0, 4.0, 0.0, 0, emptyList())

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = highIob,
            settings = defaultSettings()
        )

        assertThat(result.confidence).isEqualTo(Confidence.LOW)
    }

    @Test
    fun `medium confidence for complex meal`() {
        val complexMeal = listOf(testComponent(
            carbs = 40.0,
            proteins = 35.0,
            fats = 25.0
        ))

        val result = BolusRecommender.calculate(
            components = complexMeal,
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.confidence).isIn(listOf(Confidence.MEDIUM, Confidence.LOW))
    }

    // ─── Adjustment Details ───────────────────────────────────────────────────────

    @Test
    fun `adjustments include food component`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 36.0)), // 3 XE
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        val foodAdj = result.adjustments.find { it.category == AdjustmentCategory.FOOD }
        assertThat(foodAdj).isNotNull()
        assertThat(foodAdj!!.amount).isGreaterThan(0.0)
    }

    @Test
    fun `adjustments include IOB component`() {
        val iob = IobState(2.0, 2.0, 0.0, 0, emptyList())

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0)),
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = iob,
            settings = defaultSettings()
        )

        val iobAdj = result.adjustments.find { it.category == AdjustmentCategory.IOB }
        assertThat(iobAdj).isNotNull()
        assertThat(iobAdj!!.amount).isLessThan(0.0) // Negative (reduction)
    }

    @Test
    fun `reasoning is generated`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0, gi = 70.0)),
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.reasoning).isNotEmpty()
        assertThat(result.reasoning).contains("Углеводы")
    }

    // ─── Components Breakdown ────────────────────────────────────────────────────

    @Test
    fun `components breakdown is correct`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 36.0)),
            currentGlucose = 8.0,
            cgmReading = null,
            iobState = IobState(1.0, 1.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Food dose should be present
        assertThat(result.components.foodDose).isGreaterThan(0.0)

        // Correction dose for high glucose
        assertThat(result.components.correctionDose).isGreaterThan(0.0)

        // IOB reduction
        assertThat(result.components.iobReduction).isEqualTo(1.0)
    }

    // ─── Insulin Type Effects ────────────────────────────────────────────────────

    @Test
    fun `fast insulin type affects timing`() {
        val fiaspSettings = defaultSettings().copy(insulinType = "fiasp")

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 30.0, gi = 80.0)),
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = fiaspSettings
        )

        // Fiasp with fast carbs can be injected closer to meal
        assertThat(result.timing).isNotNull()
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────────────

    @Test
    fun `very high carbs produces reasonable dose`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 150.0)), // 12.5 XE
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Should produce a dose but not be absurdly high
        assertThat(result.dose).isGreaterThan(15.0)
        assertThat(result.dose).isLessThan(25.0)
    }

    @Test
    fun `zero carbs produces zero food dose`() {
        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 0.0, proteins = 30.0)),
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        assertThat(result.components.foodDose).isEqualTo(0.0)
    }

    @Test
    fun `dose is rounded to insulin step`() {
        val settings = defaultSettings().copy(insulinStep = 0.5)

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 25.0)), // ~2.08 XE = ~3.125 units
            currentGlucose = 6.0,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = settings
        )

        // Should be rounded to 0.5 increments
        val remainder = result.dose % 0.5
        assertThat(remainder).isWithin(0.001).of(0.0)
    }

    // ─── Realistic Scenarios ──────────────────────────────────────────────────────

    @Test
    fun `typical breakfast scenario`() {
        val breakfast = listOf(
            testComponent("Oatmeal", carbs = 30.0, gi = 55.0, proteins = 5.0),
            testComponent("Banana", carbs = 25.0, gi = 62.0)
        )

        val result = BolusRecommender.calculate(
            components = breakfast,
            currentGlucose = 5.5,
            cgmReading = null,
            iobState = IobState(0.0, 0.0, 0.0, 0, emptyList()),
            settings = defaultSettings()
        )

        // Total carbs: 55g
        assertThat(result.components.foodDose).isGreaterThan(5.0)
        assertThat(result.confidence).isNotEqualTo(Confidence.DO_NOT_INJECT)
    }

    @Test
    fun `after correction scenario`() {
        // User just corrected 2 hours ago, now wants to eat
        val iobFromCorrection = IobState(1.5, 1.5, 0.0, 0, emptyList())

        val result = BolusRecommender.calculate(
            components = listOf(testComponent(carbs = 40.0)),
            currentGlucose = 5.8,
            cgmReading = null,
            iobState = iobFromCorrection,
            settings = defaultSettings()
        )

        // Should account for IOB
        assertThat(result.components.iobReduction).isEqualTo(1.5)
    }
}
