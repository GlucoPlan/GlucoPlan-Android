package com.glucoplan.app.domain.calculator

import kotlin.math.max
import kotlin.math.roundToInt

enum class PumpBolusKind {
    NORMAL,   // всё сразу
    SQUARE,   // растянутый
    DUAL      // часть сразу, остаток равномерно
}

data class PumpBolusPlan(
    val kind: PumpBolusKind,
    val nowUnits: Double,
    val extendedUnits: Double,
    val durationMinutes: Int,
    val extraFpuUnits: Double,
    val fpu: Double,
    val weightedGi: Double,
    val reason: String
) {
    val totalUnits: Double get() = nowUnits + extendedUnits

    val kindLabel: String get() = when (kind) {
        PumpBolusKind.NORMAL -> "Всё сразу"
        PumpBolusKind.SQUARE -> "Растянутый"
        PumpBolusKind.DUAL -> "Комбо"
    }

    val durationLabel: String get() {
        if (durationMinutes <= 0) return ""
        return if (durationMinutes % 60 == 0) "${durationMinutes / 60} ч"
        else "${durationMinutes} мин"
    }

    /** Короткий текст на экран и в историю */
    val summary: String get() = when (kind) {
        PumpBolusKind.NORMAL ->
            "Всё сразу: ${fmt(nowUnits)} ед"
        PumpBolusKind.SQUARE ->
            "Растянутый: ${fmt(extendedUnits)} ед за $durationLabel"
        PumpBolusKind.DUAL ->
            "Комбо: ${fmt(nowUnits)} ед сразу + ${fmt(extendedUnits)} ед за $durationLabel"
    }

    val kindKey: String get() = kind.name.lowercase()

    companion object {
        fun fmt(v: Double): String = if (v == v.toLong().toDouble()) "%.0f".format(v) else "%.1f".format(v)

        val NONE = PumpBolusPlan(
            kind = PumpBolusKind.NORMAL,
            nowUnits = 0.0,
            extendedUnits = 0.0,
            durationMinutes = 0,
            extraFpuUnits = 0.0,
            fpu = 0.0,
            weightedGi = 0.0,
            reason = ""
        )
    }
}

/**
 * Рекомендация болюса 720G по Паньковской (ослабленной) + ГИ.
 * Инсулин на углеводы не меняет — только раскладывает его во времени
 * и предлагает консервативную надбавку на белок/жир.
 */
object PumpBolusPlanner {

    fun plan(
        carbsG: Double,
        proteinG: Double,
        fatG: Double,
        weightedGi: Double,
        foodDose: Double,
        immediateExtra: Double,
        carbsPerXe: Double,
        carbCoefficient: Double,
        insulinStep: Double,
        fpuFactor: Double
    ): PumpBolusPlan {
        val fpu = ((max(0.0, proteinG) * 4.0) + (max(0.0, fatG) * 9.0)) / 100.0
        val factor = fpuFactor.coerceIn(0.0, 1.0)
        val extraRaw = if (carbsPerXe > 0) {
            fpu * (10.0 / carbsPerXe) * carbCoefficient * factor
        } else 0.0

        val giNowFrac = when {
            carbsG <= 0.5 -> 0.0
            weightedGi >= 70 -> 1.0
            weightedGi >= 55 -> 0.70
            else -> 0.55
        }

        val nowRaw = max(0.0, foodDose) * giNowFrac + max(0.0, immediateExtra)
        val extendedRaw = max(0.0, foodDose) * (1.0 - giNowFrac) + extraRaw

        val extra = roundDown(extraRaw, insulinStep)
        var now = roundToStep(nowRaw, insulinStep)
        var extended = roundDown(extendedRaw, insulinStep)

        // Если растяжка меньше шага — сливаем в «сразу»
        if (extended < insulinStep / 2.0) {
            now = roundToStep(nowRaw + extendedRaw, insulinStep)
            extended = 0.0
        }
        if (now < insulinStep / 2.0 && extended > 0) {
            now = 0.0
        }

        val duration = durationMinutes(fpu, giNowFrac < 0.95 && foodDose > 0)
        val kind = when {
            now <= 0.0 && extended <= 0.0 -> PumpBolusKind.NORMAL
            extended <= 0.0 -> PumpBolusKind.NORMAL
            now <= 0.0 -> PumpBolusKind.SQUARE
            else -> PumpBolusKind.DUAL
        }
        val durationOut = if (kind == PumpBolusKind.NORMAL) 0 else duration

        val giWord = when {
            carbsG <= 0.5 -> "без углеводов"
            weightedGi >= 70 -> "ГИ высокий"
            weightedGi >= 55 -> "ГИ средний"
            else -> "ГИ низкий"
        }
        val reason = buildString {
            append(giWord)
            if (fpu >= 0.3) append(", FPU %.1f".format(fpu))
            if (extra > 0) append(", надбавка БЖ ${PumpBolusPlan.fmt(extra)} ед ×%.0f%%".format(factor * 100))
            else if (fpu >= 0.3 && factor == 0.0) append(", надбавка БЖ выключена")
        }

        return PumpBolusPlan(
            kind = kind,
            nowUnits = now,
            extendedUnits = extended,
            durationMinutes = durationOut,
            extraFpuUnits = extra,
            fpu = fpu,
            weightedGi = weightedGi,
            reason = reason
        )
    }

    /** 1 FPU ≈ 3 ч … 6 ч. Для ребёнка потолок 6 ч, не 8. */
    internal fun durationMinutes(fpu: Double, hasSlowCarbs: Boolean): Int {
        val fromFpu = when {
            fpu < 0.5 -> if (hasSlowCarbs) 120 else 0
            fpu < 1.0 -> 180
            fpu < 2.0 -> 240
            fpu < 3.0 -> 300
            else -> 360
        }
        return fromFpu
    }

    internal fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0) return value.coerceAtLeast(0.0)
        return ((value / step).roundToInt() * step).coerceAtLeast(0.0)
    }

    internal fun roundDown(value: Double, step: Double): Double {
        if (step <= 0) return value.coerceAtLeast(0.0)
        return (kotlin.math.floor((value + 1e-9) / step) * step).coerceAtLeast(0.0)
    }
}
