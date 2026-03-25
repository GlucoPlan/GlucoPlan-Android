package com.glucoplan.app.domain.model

import androidx.room.*
import java.time.Instant
import kotlin.math.max

/**
 * Insulin injection record for IOB calculation
 */
@Entity(tableName = "insulin_injections")
data class InsulinInjection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val injectedAt: String,           // ISO timestamp
    val insulinType: String,          // fiasp, novorapid, humalog, etc.
    val dose: Double,                 // units
    val injectionSite: String? = null, // arm, leg, abdomen, etc.
    val isBasal: Boolean = false,     // true for basal insulin
    val mealId: Long? = null,         // link to meal if meal bolus
    val glucoseAtInjection: Double? = null,
    val carbsCovered: Double? = null,
    val notes: String? = null,
    val source: String = "manual",    // manual, nightscout, imported
    val nsId: String? = null          // Nightscout treatment ID if synced
)

/**
 * IOB (Insulin On Board) calculation result
 */
data class IobState(
    val totalIob: Double,             // Total active insulin
    val bolusIob: Double,             // Active bolus insulin
    val basalIob: Double,             // Active basal insulin
    val peakMinutesRemaining: Int,    // Minutes until peak activity
    val details: List<IobDetail>
)

data class IobDetail(
    val injectionId: Long,
    val insulinType: String,
    val insulinDisplayName: String,
    val originalDose: Double,
    val remainingDose: Double,
    val minutesAgo: Int,
    val minutesRemaining: Int,
    val isBasal: Boolean,
    val mealId: Long? = null
)

/**
 * IOB Calculator
 */
object IobCalculator {

    /**
     * Calculate IOB from list of injections
     */
    fun calculate(
        injections: List<InsulinInjection>,
        currentTime: Instant = Instant.now()
    ): IobState {
        val details = mutableListOf<IobDetail>()
        var totalBolusIob = 0.0
        var totalBasalIob = 0.0
        var nearestPeak = Int.MAX_VALUE

        for (injection in injections) {
            val profile = InsulinProfiles.get(injection.insulinType) ?: continue

            val injectedTime = Instant.parse(injection.injectedAt)
            val minutesAgo = ((currentTime.toEpochMilli() - injectedTime.toEpochMilli()) / 60000.0).toInt()

            // Skip if outside duration window
            if (minutesAgo < 0 || minutesAgo > profile.durationMinutes) continue

            // Calculate remaining fraction
            val usedFraction = profile.getUsedFractionAt(minutesAgo.toDouble())
            val remainingFraction = 1.0 - usedFraction
            val remainingDose = injection.dose * remainingFraction

            if (remainingDose < 0.01) continue  // Ignore negligible amounts

            // Calculate time to peak
            val minutesToPeak = max(0, (profile.peakMinutes - minutesAgo).toInt())
            if (!injection.isBasal && minutesToPeak < nearestPeak && minutesToPeak > 0) {
                nearestPeak = minutesToPeak
            }

            val detail = IobDetail(
                injectionId = injection.id,
                insulinType = injection.insulinType,
                insulinDisplayName = profile.displayName,
                originalDose = injection.dose,
                remainingDose = remainingDose,
                minutesAgo = minutesAgo,
                minutesRemaining = max(0, (profile.durationMinutes - minutesAgo).toInt()),
                isBasal = injection.isBasal,
                mealId = injection.mealId
            )
            details.add(detail)

            if (injection.isBasal) {
                totalBasalIob += remainingDose
            } else {
                totalBolusIob += remainingDose
            }
        }

        return IobState(
            totalIob = totalBolusIob + totalBasalIob,
            bolusIob = totalBolusIob,
            basalIob = totalBasalIob,
            peakMinutesRemaining = if (nearestPeak == Int.MAX_VALUE) 0 else nearestPeak,
            details = details.sortedByDescending { it.remainingDose }
        )
    }

    /**
     * Get the glucose-lowering effect of IOB over time
     */
    fun getIobGlucoseEffect(
        injections: List<InsulinInjection>,
        sensitivity: Double,  // mmol/L per unit
        durationMinutes: Int = 240,
        stepMinutes: Int = 5,
        currentTime: Instant = Instant.now()
    ): List<Pair<Int, Double>> {
        val result = mutableListOf<Pair<Int, Double>>()

        for (t in 0..durationMinutes step stepMinutes) {
            val futureTime = currentTime.plusSeconds((t * 60).toLong())
            val futureIob = calculate(injections, futureTime)
            val currentIob = if (t == 0) {
                futureIob.totalIob
            } else {
                calculate(injections, currentTime.plusSeconds(((t - stepMinutes) * 60).toLong())).totalIob
            }

            // Glucose drop = (IOB before - IOB after) * sensitivity
            val drop = (currentIob - futureIob.totalIob) * sensitivity
            result.add(t to drop)
        }

        return result
    }

    /**
     * Calculate how much insulin will be active at a future time
     */
    fun getFutureIob(
        injections: List<InsulinInjection>,
        minutesFromNow: Int,
        currentTime: Instant = Instant.now()
    ): Double {
        val futureTime = currentTime.plusSeconds((minutesFromNow * 60).toLong())
        return calculate(injections, futureTime).totalIob
    }
}
