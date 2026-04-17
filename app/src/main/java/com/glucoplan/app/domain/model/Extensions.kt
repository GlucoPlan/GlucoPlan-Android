package com.glucoplan.app.domain.model


/**
 * Extension to get glucose color based on range.
 *
 * Баги 3 и 4 исправлены:
 *  - Удалены дублирующие объявления CarbCurveCalculator, AggregatedCarbCurves, IobCalculator
 *    (они объявлены в CarbCurve.kt и Iob.kt соответственно).
 *  - Удалён рекурсивный extension val InsulinProfile.displayName — InsulinProfile уже имеет
 *    свойство displayName в data class, extension вызывал StackOverflowError.
 */
fun glucoseColor(glucose: Double, min: Double, max: Double): androidx.compose.ui.graphics.Color {
    return when {
        glucose < min -> androidx.compose.ui.graphics.Color(0xFFFF5722)  // Orange/red for low
        glucose > max -> androidx.compose.ui.graphics.Color(0xFFE53935)  // Red for high
        else          -> androidx.compose.ui.graphics.Color(0xFF43A047)  // Green for in range
    }
}

// GlucoRepository.getRecentInjections(hours) is available directly — no extension needed
