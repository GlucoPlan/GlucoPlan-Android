package com.glucoplan.app.domain.model

import com.google.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for IOB (Insulin On Board) calculation.
 * CRITICAL for patient safety - incorrect IOB can lead to insulin stacking
 * and dangerous hypoglycemia.
 */
@RunWith(JUnit4::class)
class IobCalculatorTest {

    // ─── Basic IOB Calculation Tests ─────────────────────────────────────────────

    @Test
    fun `calculate returns empty IOB for empty injection list`() {
        val result = IobCalculator.calculate(emptyList())

        assertThat(result.totalIob).isEqualTo(0.0)
        assertThat(result.bolusIob).isEqualTo(0.0)
        assertThat(result.basalIob).isEqualTo(0.0)
        assertThat(result.details).isEmpty()
    }

    @Test
    fun `calculate returns correct IOB for single recent injection`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp",
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        // After 30 minutes, about 15-25% of Fiasp should be used
        assertThat(result.totalIob).isGreaterThan(3.0)
        assertThat(result.totalIob).isLessThan(5.0)
        assertThat(result.bolusIob).isEqualTo(result.totalIob)
        assertThat(result.basalIob).isEqualTo(0.0)
    }

    @Test
    fun `calculate returns zero IOB for old injection`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(10, ChronoUnit.HOURS).toString(),
            insulinType = "novorapid",
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        assertThat(result.totalIob).isEqualTo(0.0)
    }

    @Test
    fun `calculate handles future injection gracefully`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.plus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp",
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        // Future injections should be ignored
        assertThat(result.totalIob).isEqualTo(0.0)
    }

    // ─── Multiple Injections Tests ───────────────────────────────────────────────

    @Test
    fun `calculate sums IOB from multiple injections`() {
        val now = Instant.now()
        val injections = listOf(
            InsulinInjection(
                id = 1,
                injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 3.0,
                isBasal = false
            ),
            InsulinInjection(
                id = 2,
                injectedAt = now.minus(60, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 2.0,
                isBasal = false
            )
        )

        val result = IobCalculator.calculate(injections, now)

        assertThat(result.totalIob).isGreaterThan(0.0)
        assertThat(result.details).hasSize(2)
    }

    @Test
    fun `calculate separates bolus and basal IOB`() {
        val now = Instant.now()
        val injections = listOf(
            InsulinInjection(
                id = 1,
                injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 4.0,
                isBasal = false
            ),
            InsulinInjection(
                id = 2,
                injectedAt = now.minus(2, ChronoUnit.HOURS).toString(),
                insulinType = "tresiba",
                dose = 10.0,
                isBasal = true
            )
        )

        val result = IobCalculator.calculate(injections, now)

        assertThat(result.bolusIob).isGreaterThan(0.0)
        assertThat(result.basalIob).isGreaterThan(0.0)
        assertThat(result.totalIob).isEqualTo(result.bolusIob + result.basalIob)
    }

    // ─── Insulin Type Specific Tests ─────────────────────────────────────────────

    @Test
    fun `FIASP has faster IOB decay than NOVORAPID`() {
        val now = Instant.now()
        val fiaspInjection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(2, ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 5.0,
            isBasal = false
        )
        val novorapidInjection = InsulinInjection(
            id = 2,
            injectedAt = now.minus(2, ChronoUnit.HOURS).toString(),
            insulinType = "novorapid",
            dose = 5.0,
            isBasal = false
        )

        val fiaspResult = IobCalculator.calculate(listOf(fiaspInjection), now)
        val novorapidResult = IobCalculator.calculate(listOf(novorapidInjection), now)

        // Fiasp should have less IOB remaining at 2 hours (faster acting)
        assertThat(fiaspResult.totalIob).isLessThan(novorapidResult.totalIob)
    }

    @Test
    fun `basal insulin maintains IOB for longer duration`() {
        val now = Instant.now()
        val basalInjection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(12, ChronoUnit.HOURS).toString(),
            insulinType = "tresiba",
            dose = 15.0,
            isBasal = true
        )

        val result = IobCalculator.calculate(listOf(basalInjection), now)

        // Tresiba should still have significant IOB at 12 hours
        assertThat(result.basalIob).isGreaterThan(5.0)
    }

    @Test
    fun `unknown insulin type is ignored`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "unknown_insulin",
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        assertThat(result.totalIob).isEqualTo(0.0)
        assertThat(result.details).isEmpty()
    }

    // ─── IOB Detail Tests ────────────────────────────────────────────────────────

    @Test
    fun `IOB detail contains correct information`() {
        val now = Instant.now()
        val injectionTime = now.minus(45, ChronoUnit.MINUTES)
        val injection = InsulinInjection(
            id = 123,
            injectedAt = injectionTime.toString(),
            insulinType = "novorapid",
            dose = 4.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        assertThat(result.details).hasSize(1)
        val detail = result.details.first()
        assertThat(detail.injectionId).isEqualTo(123)
        assertThat(detail.insulinType).isEqualTo("novorapid")
        assertThat(detail.insulinDisplayName).isEqualTo("NovoRapid")
        assertThat(detail.originalDose).isEqualTo(4.0)
        assertThat(detail.remainingDose).isLessThan(4.0)
        assertThat(detail.minutesAgo).isEqualTo(45)
        assertThat(detail.isBasal).isFalse()
    }

    @Test
    fun `details are sorted by remaining dose descending`() {
        val now = Instant.now()
        val injections = listOf(
            InsulinInjection(
                id = 1,
                injectedAt = now.minus(60, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 2.0,
                isBasal = false
            ),
            InsulinInjection(
                id = 2,
                injectedAt = now.minus(15, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 3.0,
                isBasal = false
            )
        )

        val result = IobCalculator.calculate(injections, now)

        // More recent injection should have more remaining
        assertThat(result.details.first().injectionId).isEqualTo(2)
    }

    // ─── Peak Time Tests ─────────────────────────────────────────────────────────

    @Test
    fun `peakMinutesRemaining is correct for recent injection`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(15, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp", // Peak at ~50 minutes
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        // Should be about 35 minutes to peak (50 - 15)
        assertThat(result.peakMinutesRemaining).isGreaterThan(25)
        assertThat(result.peakMinutesRemaining).isLessThan(45)
    }

    @Test
    fun `peakMinutesRemaining is zero when past peak`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(2, ChronoUnit.HOURS).toString(),
            insulinType = "fiasp", // Peak at ~50 minutes
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        assertThat(result.peakMinutesRemaining).isEqualTo(0)
    }

    // ─── Glucose Effect Tests ────────────────────────────────────────────────────

    @Test
    fun `getIobGlucoseEffect returns expected drop`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp",
            dose = 2.0,
            isBasal = false
        )

        val effects = IobCalculator.getIobGlucoseEffect(
            injections = listOf(injection),
            sensitivity = 2.5, // 2.5 mmol/L per unit
            durationMinutes = 120,
            stepMinutes = 30,
            currentTime = now
        )

        assertThat(effects).isNotEmpty()
        // Total glucose drop should be approximately dose * sensitivity
        val totalDrop = effects.sumOf { it.second }
        assertThat(totalDrop).isGreaterThan(0.0)
    }

    @Test
    fun `getFutureIob decreases over time`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp",
            dose = 4.0,
            isBasal = false
        )

        val iobNow = IobCalculator.getFutureIob(listOf(injection), 0, now)
        val iob30min = IobCalculator.getFutureIob(listOf(injection), 30, now)
        val iob60min = IobCalculator.getFutureIob(listOf(injection), 60, now)

        assertThat(iobNow).isGreaterThan(iob30min)
        assertThat(iob30min).isGreaterThan(iob60min)
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────────────

    @Test
    fun `very small remaining dose is filtered out`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(5, ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 0.5, // Small dose
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        // Very small remaining should be filtered (< 0.01)
        assertThat(result.totalIob).isEqualTo(0.0)
    }

    @Test
    fun `zero dose injection produces zero IOB`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp",
            dose = 0.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        assertThat(result.totalIob).isEqualTo(0.0)
    }

    @Test
    fun `negative time injection is ignored`() {
        val now = Instant.now()
        val injection = InsulinInjection(
            id = 1,
            injectedAt = now.minus(1, ChronoUnit.DAYS).toString(), // 24 hours ago
            insulinType = "fiasp",
            dose = 5.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(injection), now)

        // Fiasp duration is 5 hours, so 24h should be zero
        assertThat(result.totalIob).isEqualTo(0.0)
    }

    // ─── Integration Tests ───────────────────────────────────────────────────────

    @Test
    fun `realistic meal scenario`() {
        val now = Instant.now()

        // Breakfast bolus 3 hours ago
        val breakfast = InsulinInjection(
            id = 1,
            injectedAt = now.minus(3, ChronoUnit.HOURS).toString(),
            insulinType = "novorapid",
            dose = 6.0,
            isBasal = false
        )

        // Correction bolus 1 hour ago
        val correction = InsulinInjection(
            id = 2,
            injectedAt = now.minus(1, ChronoUnit.HOURS).toString(),
            insulinType = "novorapid",
            dose = 2.0,
            isBasal = false
        )

        val result = IobCalculator.calculate(listOf(breakfast, correction), now)

        // Breakfast bolus should have very little remaining
        // Correction should still have significant IOB
        assertThat(result.bolusIob).isGreaterThan(0.5)
        assertThat(result.bolusIob).isLessThan(3.0)
        assertThat(result.details).hasSize(2)
    }

    @Test
    fun `multiple insulin types scenario`() {
        val now = Instant.now()

        val injections = listOf(
            // Recent Fiasp
            InsulinInjection(
                id = 1,
                injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 4.0,
                isBasal = false
            ),
            // Morning basal
            InsulinInjection(
                id = 2,
                injectedAt = now.minus(8, ChronoUnit.HOURS).toString(),
                insulinType = "toujeo",
                dose = 18.0,
                isBasal = true
            )
        )

        val result = IobCalculator.calculate(injections, now)

        assertThat(result.bolusIob).isGreaterThan(0.0)
        assertThat(result.basalIob).isGreaterThan(0.0)
        assertThat(result.totalIob).isEqualTo(result.bolusIob + result.basalIob)
    }
}
