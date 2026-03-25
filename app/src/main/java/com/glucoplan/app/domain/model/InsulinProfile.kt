package com.glucoplan.app.domain.model

import kotlin.math.*

/**
 * Insulin profile with time-activity curve.
 * Data digitized from official prescribing information.
 */
data class InsulinProfile(
    val name: String,
    val displayName: String,
    val type: InsulinType,
    val onsetMinutes: Double,      // When insulin starts working
    val peakMinutes: Double,       // Time of maximum activity
    val durationMinutes: Double,   // Total duration
    val curvePoints: List<CurvePoint>  // Digitized curve points (time, activity%)
) {
    data class CurvePoint(
        val minutes: Double,
        val activityPercent: Double  // 0-100% of peak activity
    )

    /**
     * Get activity at a specific time using linear interpolation
     */
    fun getActivityAt(minutes: Double): Double {
        if (minutes <= 0) return 0.0
        if (minutes >= durationMinutes) return 0.0

        // Find bracketing points
        val points = curvePoints.sortedBy { it.minutes }
        
        // Before first point
        if (minutes <= points.first().minutes) {
            return interpolateLinear(0.0, 0.0, points.first().minutes, points.first().activityPercent, minutes)
        }
        
        // After last point - exponential decay
        if (minutes >= points.last().minutes) {
            return points.last().activityPercent * exp(-(minutes - points.last().minutes) / (durationMinutes / 10))
        }

        // Find bracketing points for interpolation
        for (i in 0 until points.size - 1) {
            if (minutes >= points[i].minutes && minutes <= points[i + 1].minutes) {
                return interpolateLinear(
                    points[i].minutes, points[i].activityPercent,
                    points[i + 1].minutes, points[i + 1].activityPercent,
                    minutes
                )
            }
        }

        return 0.0
    }

    /**
     * Get the fraction of insulin that has been used by a given time
     */
    fun getUsedFractionAt(minutes: Double): Double {
        if (minutes <= 0) return 0.0
        if (minutes >= durationMinutes) return 1.0

        var totalArea = 0.0
        var usedArea = 0.0
        val step = 5.0

        var t = 0.0
        while (t < durationMinutes) {
            val activity = getActivityAt(t)
            totalArea += activity
            if (t < minutes) {
                usedArea += activity
            }
            t += step
        }

        return if (totalArea > 0) (usedArea / totalArea).coerceIn(0.0, 1.0) else 0.0
    }

    private fun interpolateLinear(x1: Double, y1: Double, x2: Double, y2: Double, x: Double): Double {
        if (x2 == x1) return y1
        return y1 + (y2 - y1) * (x - x1) / (x2 - x1)
    }
}

enum class InsulinType {
    RAPID,      // Ultra-rapid (Fiasp, Lyumjev)
    FAST,       // Fast-acting (NovoRapid, Humalog, Apidra)
    INTERMEDIATE, // Intermediate (NPH)
    LONG,       // Long-acting basal (Lantus, Levemir)
    ULTRA_LONG  // Ultra-long basal (Tresiba, Toujeo)
}

/**
 * Pre-defined insulin profiles digitized from manufacturer data
 */
object InsulinProfiles {

    // ═══════════════════════════════════════════════════════════════════
    // RAPID-ACTING INSULINS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Fiasp (insulin aspart) - fastest rapid-acting
     * Onset: 2-3 min, Peak: 45-60 min, Duration: 3-5 h
     * Source: Novo Nordisk prescribing information
     */
    val FIASP = InsulinProfile(
        name = "fiasp",
        displayName = "Fiasp",
        type = InsulinType.RAPID,
        onsetMinutes = 2.0,
        peakMinutes = 50.0,
        durationMinutes = 300.0,
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(5.0, 8.0),
            InsulinProfile.CurvePoint(15.0, 35.0),
            InsulinProfile.CurvePoint(30.0, 70.0),
            InsulinProfile.CurvePoint(45.0, 95.0),
            InsulinProfile.CurvePoint(60.0, 100.0),
            InsulinProfile.CurvePoint(90.0, 85.0),
            InsulinProfile.CurvePoint(120.0, 60.0),
            InsulinProfile.CurvePoint(150.0, 40.0),
            InsulinProfile.CurvePoint(180.0, 25.0),
            InsulinProfile.CurvePoint(240.0, 10.0),
            InsulinProfile.CurvePoint(300.0, 3.0)
        )
    )

    /**
     * Lyumjev (insulin lispro-aabc) - ultra-rapid
     * Onset: 1-2 min, Peak: 30-50 min, Duration: 2-4 h
     * Source: Eli Lilly prescribing information
     */
    val LYUMJEV = InsulinProfile(
        name = "lyumjev",
        displayName = "Lyumjev",
        type = InsulinType.RAPID,
        onsetMinutes = 1.0,
        peakMinutes = 40.0,
        durationMinutes = 240.0,
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(3.0, 10.0),
            InsulinProfile.CurvePoint(10.0, 40.0),
            InsulinProfile.CurvePoint(20.0, 70.0),
            InsulinProfile.CurvePoint(30.0, 90.0),
            InsulinProfile.CurvePoint(45.0, 100.0),
            InsulinProfile.CurvePoint(60.0, 85.0),
            InsulinProfile.CurvePoint(90.0, 55.0),
            InsulinProfile.CurvePoint(120.0, 30.0),
            InsulinProfile.CurvePoint(180.0, 12.0),
            InsulinProfile.CurvePoint(240.0, 3.0)
        )
    )

    /**
     * NovoRapid / NovoLog (insulin aspart)
     * Onset: 10-20 min, Peak: 60-90 min, Duration: 4-6 h
     * Source: Novo Nordisk prescribing information
     */
    val NOVORAPID = InsulinProfile(
        name = "novorapid",
        displayName = "NovoRapid",
        type = InsulinType.FAST,
        onsetMinutes = 10.0,
        peakMinutes = 75.0,
        durationMinutes = 360.0,
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(10.0, 2.0),
            InsulinProfile.CurvePoint(20.0, 10.0),
            InsulinProfile.CurvePoint(30.0, 25.0),
            InsulinProfile.CurvePoint(45.0, 50.0),
            InsulinProfile.CurvePoint(60.0, 80.0),
            InsulinProfile.CurvePoint(75.0, 100.0),
            InsulinProfile.CurvePoint(90.0, 95.0),
            InsulinProfile.CurvePoint(120.0, 70.0),
            InsulinProfile.CurvePoint(150.0, 45.0),
            InsulinProfile.CurvePoint(180.0, 30.0),
            InsulinProfile.CurvePoint(240.0, 15.0),
            InsulinProfile.CurvePoint(300.0, 7.0),
            InsulinProfile.CurvePoint(360.0, 2.0)
        )
    )

    /**
     * Humalog (insulin lispro)
     * Onset: 10-15 min, Peak: 50-70 min, Duration: 3-5 h
     * Source: Eli Lilly prescribing information
     */
    val HUMALOG = InsulinProfile(
        name = "humalog",
        displayName = "Humalog",
        type = InsulinType.FAST,
        onsetMinutes = 10.0,
        peakMinutes = 60.0,
        durationMinutes = 300.0,
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(10.0, 3.0),
            InsulinProfile.CurvePoint(20.0, 15.0),
            InsulinProfile.CurvePoint(30.0, 40.0),
            InsulinProfile.CurvePoint(45.0, 75.0),
            InsulinProfile.CurvePoint(60.0, 100.0),
            InsulinProfile.CurvePoint(90.0, 80.0),
            InsulinProfile.CurvePoint(120.0, 50.0),
            InsulinProfile.CurvePoint(150.0, 30.0),
            InsulinProfile.CurvePoint(180.0, 18.0),
            InsulinProfile.CurvePoint(240.0, 8.0),
            InsulinProfile.CurvePoint(300.0, 2.0)
        )
    )

    /**
     * Apidra (insulin glulisine)
     * Onset: 10-15 min, Peak: 45-60 min, Duration: 3-4 h
     * Source: Sanofi prescribing information
     */
    val APIDRA = InsulinProfile(
        name = "apidra",
        displayName = "Apidra",
        type = InsulinType.FAST,
        onsetMinutes = 10.0,
        peakMinutes = 55.0,
        durationMinutes = 270.0,
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(10.0, 5.0),
            InsulinProfile.CurvePoint(20.0, 20.0),
            InsulinProfile.CurvePoint(30.0, 50.0),
            InsulinProfile.CurvePoint(45.0, 90.0),
            InsulinProfile.CurvePoint(60.0, 100.0),
            InsulinProfile.CurvePoint(90.0, 65.0),
            InsulinProfile.CurvePoint(120.0, 35.0),
            InsulinProfile.CurvePoint(180.0, 12.0),
            InsulinProfile.CurvePoint(240.0, 4.0),
            InsulinProfile.CurvePoint(270.0, 1.0)
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // LONG-ACTING (BASAL) INSULINS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Toujeo (insulin glargine 300 U/mL)
     * Onset: 1-2 h, Peak: flat, Duration: 24-30 h
     * Note: Flat profile with minimal peak
     * Source: Sanofi prescribing information
     */
    val TOUJEO = InsulinProfile(
        name = "toujeo",
        displayName = "Toujeo",
        type = InsulinType.ULTRA_LONG,
        onsetMinutes = 90.0,
        peakMinutes = 720.0,  // Broad plateau, not a sharp peak
        durationMinutes = 1440.0,  // 24 hours
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(60.0, 5.0),
            InsulinProfile.CurvePoint(120.0, 30.0),
            InsulinProfile.CurvePoint(180.0, 60.0),
            InsulinProfile.CurvePoint(360.0, 85.0),
            InsulinProfile.CurvePoint(480.0, 95.0),
            InsulinProfile.CurvePoint(720.0, 100.0),
            InsulinProfile.CurvePoint(960.0, 98.0),
            InsulinProfile.CurvePoint(1200.0, 90.0),
            InsulinProfile.CurvePoint(1320.0, 70.0),
            InsulinProfile.CurvePoint(1440.0, 45.0)
        )
    )

    /**
     * Tresiba (insulin degludec)
     * Onset: 30-60 min, Peak: flat, Duration: >42 h
     * Note: Ultra-long, almost perfectly flat
     * Source: Novo Nordisk prescribing information
     */
    val TRESIBA = InsulinProfile(
        name = "tresiba",
        displayName = "Tresiba",
        type = InsulinType.ULTRA_LONG,
        onsetMinutes = 45.0,
        peakMinutes = 720.0,
        durationMinutes = 2520.0,  // 42 hours
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(30.0, 3.0),
            InsulinProfile.CurvePoint(60.0, 15.0),
            InsulinProfile.CurvePoint(120.0, 50.0),
            InsulinProfile.CurvePoint(240.0, 85.0),
            InsulinProfile.CurvePoint(480.0, 98.0),
            InsulinProfile.CurvePoint(720.0, 100.0),
            InsulinProfile.CurvePoint(1440.0, 100.0),  // 24h
            InsulinProfile.CurvePoint(2160.0, 95.0),   // 36h
            InsulinProfile.CurvePoint(2520.0, 80.0)    // 42h
        )
    )

    /**
     * Lantus (insulin glargine 100 U/mL)
     * Onset: 1-2 h, Peak: minimal, Duration: 20-24 h
     * Source: Sanofi prescribing information
     */
    val LANTUS = InsulinProfile(
        name = "lantus",
        displayName = "Lantus",
        type = InsulinType.LONG,
        onsetMinutes = 90.0,
        peakMinutes = 600.0,
        durationMinutes = 1320.0,  // 22 hours
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(60.0, 5.0),
            InsulinProfile.CurvePoint(120.0, 35.0),
            InsulinProfile.CurvePoint(180.0, 65.0),
            InsulinProfile.CurvePoint(360.0, 95.0),
            InsulinProfile.CurvePoint(600.0, 100.0),
            InsulinProfile.CurvePoint(900.0, 95.0),
            InsulinProfile.CurvePoint(1200.0, 80.0),
            InsulinProfile.CurvePoint(1320.0, 50.0)
        )
    )

    /**
     * Levemir (insulin detemir)
     * Onset: 1-2 h, Peak: 6-8 h, Duration: 16-20 h
     * Note: Has a slight peak compared to Lantus
     * Source: Novo Nordisk prescribing information
     */
    val LEVEMIR = InsulinProfile(
        name = "levemir",
        displayName = "Levemir",
        type = InsulinType.LONG,
        onsetMinutes = 90.0,
        peakMinutes = 420.0,
        durationMinutes = 1200.0,  // 20 hours
        curvePoints = listOf(
            InsulinProfile.CurvePoint(0.0, 0.0),
            InsulinProfile.CurvePoint(60.0, 5.0),
            InsulinProfile.CurvePoint(120.0, 30.0),
            InsulinProfile.CurvePoint(180.0, 60.0),
            InsulinProfile.CurvePoint(300.0, 85.0),
            InsulinProfile.CurvePoint(420.0, 100.0),
            InsulinProfile.CurvePoint(600.0, 90.0),
            InsulinProfile.CurvePoint(900.0, 60.0),
            InsulinProfile.CurvePoint(1200.0, 25.0)
        )
    )

    // ═══════════════════════════════════════════════════════════════════

    /**
     * All available bolus insulin profiles
     */
    val BOLUS_PROFILES = listOf(FIASP, LYUMJEV, NOVORAPID, HUMALOG, APIDRA)
        .associateBy { it.name }

    /**
     * All available basal insulin profiles
     */
    val BASAL_PROFILES = listOf(TOUJEO, TRESIBA, LANTUS, LEVEMIR)
        .associateBy { it.name }

    /**
     * All insulin profiles
     */
    val ALL_PROFILES = BOLUS_PROFILES + BASAL_PROFILES

    /**
     * Get profile by name
     */
    fun get(name: String): InsulinProfile? = ALL_PROFILES[name]

    /**
     * Get display names for UI
     */
    fun getBolusDisplayNames(): List<Pair<String, String>> = BOLUS_PROFILES.values.map { it.name to it.displayName }
    fun getBasalDisplayNames(): List<Pair<String, String>> = BASAL_PROFILES.values.map { it.name to it.displayName }
}
