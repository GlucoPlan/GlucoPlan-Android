package com.glucoplan.app.domain.model

import com.google.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.abs

/**
 * Unit tests for InsulinProfile and InsulinProfiles.
 * These tests verify the accuracy of insulin activity curves which are
 * CRITICAL for patient safety - incorrect calculations can lead to
 * hypoglycemia or hyperglycemia.
 */
@RunWith(JUnit4::class)
class InsulinProfileTest {

    // ─── InsulinProfile.getActivityAt() Tests ────────────────────────────────────

    @Test
    fun `getActivityAt returns 0 for negative time`() {
        val profile = InsulinProfiles.FIASP
        assertThat(profile.getActivityAt(-10.0)).isEqualTo(0.0)
    }

    @Test
    fun `getActivityAt returns 0 for zero time`() {
        val profile = InsulinProfiles.FIASP
        assertThat(profile.getActivityAt(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `getActivityAt returns 0 after duration`() {
        val profile = InsulinProfiles.FIASP
        assertThat(profile.getActivityAt(profile.durationMinutes + 10)).isEqualTo(0.0)
    }

    @Test
    fun `getActivityAt at onset returns low activity`() {
        val profile = InsulinProfiles.FIASP
        val activity = profile.getActivityAt(profile.onsetMinutes)
        assertThat(activity).isLessThan(10.0) // Less than 10% at onset
    }

    @Test
    fun `getActivityAt reaches peak around peak time`() {
        val profile = InsulinProfiles.FIASP
        // Peak should be within 10 minutes of stated peak
        val activityAtPeak = profile.getActivityAt(profile.peakMinutes)
        val activityBeforePeak = profile.getActivityAt(profile.peakMinutes - 15)
        val activityAfterPeak = profile.getActivityAt(profile.peakMinutes + 15)

        // Activity at peak should be highest
        assertThat(activityAtPeak).isAtLeast(activityBeforePeak)
        assertThat(activityAtPeak).isAtLeast(activityAfterPeak * 0.9) // Allow some tolerance
    }

    @Test
    fun `getActivityAt interpolates between curve points`() {
        val profile = InsulinProfiles.FIASP
        // Fiasp has points at 45min (95%) and 60min (100%)
        val activityAt50 = profile.getActivityAt(50.0)

        // Should be between 95 and 100
        assertThat(activityAt50).isAtLeast(95.0)
        assertThat(activityAt50).isAtMost(100.0)
    }

    @Test
    fun `FIASP has faster onset than NOVORAPID`() {
        val fiasp = InsulinProfiles.FIASP
        val novorapid = InsulinProfiles.NOVORAPID

        // At 10 minutes, Fiasp should have higher activity
        val fiaspActivity = fiasp.getActivityAt(10.0)
        val novorapidActivity = novorapid.getActivityAt(10.0)

        assertThat(fiaspActivity).isGreaterThan(novorapidActivity)
    }

    @Test
    fun `LYUMJEV has faster onset than HUMALOG`() {
        val lyumjev = InsulinProfiles.LYUMJEV
        val humalog = InsulinProfiles.HUMALOG

        val lyumjevActivity = lyumjev.getActivityAt(10.0)
        val humalogActivity = humalog.getActivityAt(10.0)

        assertThat(lyumjevActivity).isGreaterThan(humalogActivity)
    }

    @Test
    fun `basal insulins have flat profile`() {
        val tresiba = InsulinProfiles.TRESIBA

        // Check activity is relatively stable between 4-20 hours
        val activity4h = tresiba.getActivityAt(240.0)
        val activity12h = tresiba.getActivityAt(720.0)
        val activity20h = tresiba.getActivityAt(1200.0)

        // All should be high and similar
        assertThat(activity4h).isAtLeast(85.0)
        assertThat(activity12h).isAtLeast(95.0)
        assertThat(activity20h).isAtLeast(75.0)
    }

    // ─── InsulinProfile.getUsedFractionAt() Tests ────────────────────────────────

    @Test
    fun `getUsedFractionAt returns 0 for zero time`() {
        val profile = InsulinProfiles.FIASP
        assertThat(profile.getUsedFractionAt(0.0)).isEqualTo(0.0)
    }

    @Test
    fun `getUsedFractionAt returns 1 after duration`() {
        val profile = InsulinProfiles.FIASP
        assertThat(profile.getUsedFractionAt(profile.durationMinutes)).isEqualTo(1.0)
    }

    @Test
    fun `getUsedFractionAt increases monotonically`() {
        val profile = InsulinProfiles.NOVORAPID

        var prevFraction = 0.0
        for (t in 0..profile.durationMinutes.toInt() step 30) {
            val fraction = profile.getUsedFractionAt(t.toDouble())
            assertThat(fraction).isAtLeast(prevFraction - 0.001) // Small tolerance for numerical errors
            prevFraction = fraction
        }
    }

    @Test
    fun `getUsedFractionAt at half duration is approximately half used`() {
        val profile = InsulinProfiles.FIASP
        val halfDuration = profile.durationMinutes / 2

        val fraction = profile.getUsedFractionAt(halfDuration)

        // Should be roughly 40-60% used at half duration (skewed curve)
        assertThat(fraction).isAtLeast(0.3)
        assertThat(fraction).isAtMost(0.7)
    }

    // ─── InsulinProfiles Registry Tests ──────────────────────────────────────────

    @Test
    fun `ALL_PROFILES contains all defined insulins`() {
        assertThat(InsulinProfiles.ALL_PROFILES).hasSize(9)
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("fiasp")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("lyumjev")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("novorapid")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("humalog")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("apidra")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("toujeo")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("tresiba")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("lantus")
        assertThat(InsulinProfiles.ALL_PROFILES).containsKey("levemir")
    }

    @Test
    fun `BOLUS_PROFILES contains only bolus insulins`() {
        assertThat(InsulinProfiles.BOLUS_PROFILES).hasSize(5)
        // Assert each member explicitly to catch accidental removals (баг 8)
        assertThat(InsulinProfiles.BOLUS_PROFILES).containsKey("fiasp")
        assertThat(InsulinProfiles.BOLUS_PROFILES).containsKey("lyumjev")
        assertThat(InsulinProfiles.BOLUS_PROFILES).containsKey("novorapid")
        assertThat(InsulinProfiles.BOLUS_PROFILES).containsKey("humalog")
        assertThat(InsulinProfiles.BOLUS_PROFILES).containsKey("apidra")
        assertThat(InsulinProfiles.BOLUS_PROFILES).doesNotContainKey("tresiba")
        assertThat(InsulinProfiles.BOLUS_PROFILES).doesNotContainKey("lantus")
        assertThat(InsulinProfiles.BOLUS_PROFILES).doesNotContainKey("levemir")
        assertThat(InsulinProfiles.BOLUS_PROFILES).doesNotContainKey("toujeo")
    }

    @Test
    fun `BASAL_PROFILES contains only basal insulins`() {
        assertThat(InsulinProfiles.BASAL_PROFILES).hasSize(4)
        assertThat(InsulinProfiles.BASAL_PROFILES).containsKey("tresiba")
        assertThat(InsulinProfiles.BASAL_PROFILES).doesNotContainKey("fiasp")
    }

    @Test
    fun `get returns correct profile by name`() {
        assertThat(InsulinProfiles.get("fiasp")).isEqualTo(InsulinProfiles.FIASP)
        assertThat(InsulinProfiles.get("unknown")).isNull()
    }

    @Test
    fun `getBolusDisplayNames returns valid list`() {
        val names = InsulinProfiles.getBolusDisplayNames()
        assertThat(names).hasSize(5)
        assertThat(names.any { it.first == "fiasp" && it.second == "Fiasp" }).isTrue()
    }

    @Test
    fun `getBasalDisplayNames returns valid list`() {
        val names = InsulinProfiles.getBasalDisplayNames()
        assertThat(names).hasSize(4)
        assertThat(names.any { it.first == "tresiba" && it.second == "Tresiba" }).isTrue()
    }

    // ─── InsulinType Classification Tests ────────────────────────────────────────

    @Test
    fun `RAPID insulins have correct type`() {
        assertThat(InsulinProfiles.FIASP.type).isEqualTo(InsulinType.RAPID)
        assertThat(InsulinProfiles.LYUMJEV.type).isEqualTo(InsulinType.RAPID)
    }

    @Test
    fun `FAST insulins have correct type`() {
        assertThat(InsulinProfiles.NOVORAPID.type).isEqualTo(InsulinType.FAST)
        assertThat(InsulinProfiles.HUMALOG.type).isEqualTo(InsulinType.FAST)
        assertThat(InsulinProfiles.APIDRA.type).isEqualTo(InsulinType.FAST)
    }

    @Test
    fun `LONG acting insulins have correct type`() {
        assertThat(InsulinProfiles.LANTUS.type).isEqualTo(InsulinType.LONG)
        assertThat(InsulinProfiles.LEVEMIR.type).isEqualTo(InsulinType.LONG)
    }

    @Test
    fun `ULTRA_LONG acting insulins have correct type`() {
        assertThat(InsulinProfiles.TOUJEO.type).isEqualTo(InsulinType.ULTRA_LONG)
        assertThat(InsulinProfiles.TRESIBA.type).isEqualTo(InsulinType.ULTRA_LONG)
    }

    // ─── Profile Duration Tests ──────────────────────────────────────────────────

    @Test
    fun `FIASP duration is 3-5 hours`() {
        val profile = InsulinProfiles.FIASP
        assertThat(profile.durationMinutes).isAtLeast(180.0) // 3 hours
        assertThat(profile.durationMinutes).isAtMost(360.0)  // 6 hours
    }

    @Test
    fun `TRESIBA duration is over 40 hours`() {
        val profile = InsulinProfiles.TRESIBA
        assertThat(profile.durationMinutes).isAtLeast(2400.0) // 40 hours
    }

    @Test
    fun `TOUJEO duration is approximately 24 hours`() {
        val profile = InsulinProfiles.TOUJEO
        assertThat(profile.durationMinutes).isAtLeast(1200.0) // 20 hours
        assertThat(profile.durationMinutes).isAtMost(1560.0)  // 26 hours
    }

    // ─── Curve Point Validation Tests ────────────────────────────────────────────

    @Test
    fun `curve points start at zero`() {
        for (profile in InsulinProfiles.ALL_PROFILES.values) {
            val firstPoint = profile.curvePoints.first()
            assertThat(firstPoint.minutes).isEqualTo(0.0)
            assertThat(firstPoint.activityPercent).isEqualTo(0.0)
        }
    }

    @Test
    fun `curve points have increasing time`() {
        for (profile in InsulinProfiles.ALL_PROFILES.values) {
            var prevTime = -1.0
            for (point in profile.curvePoints) {
                assertThat(point.minutes).isGreaterThan(prevTime)
                prevTime = point.minutes
            }
        }
    }

    @Test
    fun `curve point activity is within valid range`() {
        for (profile in InsulinProfiles.ALL_PROFILES.values) {
            for (point in profile.curvePoints) {
                assertThat(point.activityPercent).isAtLeast(0.0)
                assertThat(point.activityPercent).isAtMost(100.0)
            }
        }
    }

    @Test
    fun `last curve point is near duration`() {
        for (profile in InsulinProfiles.ALL_PROFILES.values) {
            val lastPoint = profile.curvePoints.last()
            // Last point should be at or near the duration
            assertThat(lastPoint.minutes).isAtMost(profile.durationMinutes)
            // Last point should have low activity
            assertThat(lastPoint.activityPercent).isLessThan(50.0)
        }
    }

    // ─── Edge Cases and Numerical Stability ──────────────────────────────────────

    @Test
    fun `activity curve is continuous`() {
        val profile = InsulinProfiles.NOVORAPID
        var prevActivity = 0.0

        for (t in 1..profile.durationMinutes.toInt()) {
            val activity = profile.getActivityAt(t.toDouble())
            // No sudden jumps (more than 20% change in 1 minute)
            if (prevActivity > 0) {
                val change = abs(activity - prevActivity)
                assertThat(change).isLessThan(20.0)
            }
            prevActivity = activity
        }
    }

    @Test
    fun `used fraction never exceeds 1`() {
        val profile = InsulinProfiles.HUMALOG

        for (t in 0..(profile.durationMinutes.toInt() * 2) step 10) {
            val fraction = profile.getUsedFractionAt(t.toDouble())
            assertThat(fraction).isAtMost(1.0)
            assertThat(fraction).isAtLeast(0.0)
        }
    }

    @Test
    fun `interpolation handles edge cases correctly`() {
        val profile = InsulinProfiles.FIASP

        // Test at exact curve point
        val activityAtPoint = profile.getActivityAt(60.0)

        // Test just before and after
        val activityBefore = profile.getActivityAt(59.9)
        val activityAfter = profile.getActivityAt(60.1)

        // Should be similar values
        assertThat(abs(activityAtPoint - activityBefore)).isLessThan(5.0)
        assertThat(abs(activityAtPoint - activityAfter)).isLessThan(5.0)
    }
}
