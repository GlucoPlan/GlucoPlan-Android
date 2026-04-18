package com.glucoplan.app.domain.model

import timber.log.Timber

/**
 * Bolus recommendation with timing and dose advice
 */
data class BolusRecommendation(
    val dose: Double,                    // Recommended dose in units
    val timing: BolusTiming,             // When to inject
    val confidence: Confidence,          // How confident is the recommendation
    val adjustments: List<DoseAdjustment>,
    val warnings: List<String>,
    val reasoning: String,
    val iobConsidered: Double,           // IOB that was considered
    val components: RecommendationComponents
)

enum class BolusTiming {
    NOW("Сразу перед едой"),
    IN_5_MIN("За 5 минут до еды"),
    IN_10_MIN("За 10 минут до еды"),
    IN_15_MIN("За 15 минут до еды"),
    IN_20_MIN("За 20 минут до еды"),
    AFTER_MEAL("После еды"),
    WAIT_AND_RECHECK("Подождать и проверить сахар");

    val description: String

    constructor(description: String) {
        this.description = description
    }
}

enum class Confidence {
    HIGH,       // Standard situation, reliable calculation
    MEDIUM,     // Some uncertainty (e.g., unusual GI, high fat)
    LOW,        // Significant uncertainty, recommend checking
    DO_NOT_INJECT  // Dangerous situation, do not inject
}

data class DoseAdjustment(
    val reason: String,
    val amount: Double,  // Positive = add, Negative = subtract
    val category: AdjustmentCategory
)

enum class AdjustmentCategory {
    FOOD,           // Adjustment for carbs
    CORRECTION,     // Adjustment for current glucose
    IOB,            // Adjustment for insulin on board
    TREND,          // Adjustment for CGM trend
    PROTEIN,        // Adjustment for protein
    FAT,            // Adjustment for fat
    SAFETY          // Safety margin
}

data class RecommendationComponents(
    val foodDose: Double,
    val correctionDose: Double,
    val trendAdjustment: Double,
    val iobReduction: Double,
    val proteinDose: Double,
    val fatDelay: Int  // Minutes to delay injection due to fat
)

/**
 * Bolus recommendation calculator
 */
object BolusRecommender {

    /**
     * Calculate bolus recommendation
     */
    fun calculate(
        components: List<CalcComponent>,
        currentGlucose: Double,
        cgmReading: CgmReading?,
        iobState: IobState,
        settings: AppSettings,
        previousMeals: List<Meal> = emptyList()
    ): BolusRecommendation {
        val warnings = mutableListOf<String>()
        val adjustments = mutableListOf<DoseAdjustment>()
        var reasoning = ""

        // ─── 1. Calculate carb curves ───
        val aggregatedCurves = CarbCurveCalculator.aggregateFromComponents(components)

        val totalCarbs = aggregatedCurves.totalCarbs
        val totalProtein = components.sumOf { it.proteinsInPortion }
        val totalFat = components.sumOf { it.fatsInPortion }

        Timber.d("Recommending: carbs=$totalCarbs, protein=$totalProtein, fat=$totalFat")

        // ─── 2. Calculate food dose ───
        val foodDose = if (settings.carbsPerXe > 0) {
            (totalCarbs / settings.carbsPerXe) * settings.carbCoefficient
        } else 0.0

        adjustments.add(DoseAdjustment(
            reason = "Углеводы: ${"%.0f".format(totalCarbs)} г (${"%.1f".format(totalCarbs / settings.carbsPerXe)} ХЕ)",
            amount = foodDose,
            category = AdjustmentCategory.FOOD
        ))

        // ─── 3. Calculate correction dose ───
        val correctionDose = if (currentGlucose > settings.targetGlucose && settings.sensitivity > 0) {
            (currentGlucose - settings.targetGlucose) / settings.sensitivity
        } else if (currentGlucose < settings.targetGlucoseMin) {
            // Low glucose - reduce dose
            warnings.add("⚠️ Сахар ниже нормы (${"%.1f".format(currentGlucose)} ммоль/л)")
            0.0
        } else 0.0

        if (correctionDose > 0) {
            adjustments.add(DoseAdjustment(
                reason = "Коррекция: сахар ${"%.1f".format(currentGlucose)} → цель ${"%.1f".format(settings.targetGlucose)}",
                amount = correctionDose,
                category = AdjustmentCategory.CORRECTION
            ))
        }

        // ─── 4. CGM Trend adjustment ───
        var trendAdjustment = 0.0
        cgmReading?.let { reading ->
            trendAdjustment = reading.trendDelta * settings.carbCoefficient * 0.3
            if (trendAdjustment != 0.0) {
                val direction = if (trendAdjustment > 0) "растёт" else "падает"
                adjustments.add(DoseAdjustment(
                    reason = "Тренд CGM: сахар $direction",
                    amount = trendAdjustment,
                    category = AdjustmentCategory.TREND
                ))
            }
        }

        // ─── 5. IOB reduction ───
        val iobReduction = iobState.bolusIob
        if (iobReduction > 0.1) {
            adjustments.add(DoseAdjustment(
                reason = "Активный инсулин: ${"%.1f".format(iobReduction)} ед",
                amount = -iobReduction,
                category = AdjustmentCategory.IOB
            ))
            if (iobReduction > foodDose + correctionDose) {
                warnings.add("⚠️ Много активного инсулина! Риск гипогликемии.")
            }
        }

        // ─── 6. Protein consideration ───
        var proteinDose = 0.0
        if (totalProtein > 20) {
            // Significant protein - may need extended bolus or additional dose
            proteinDose = totalProtein * 0.01 * settings.carbCoefficient / settings.carbsPerXe * 0.5
            adjustments.add(DoseAdjustment(
                reason = "Белки: ${"%.0f".format(totalProtein)} г (поздний эффект)",
                amount = proteinDose,
                category = AdjustmentCategory.PROTEIN
            ))
            warnings.add("ℹ️ Много белка - возможен поздний подъём сахара через 3-4 часа")
        }

        // ─── 7. Fat delay ───
        var fatDelay = 0
        if (totalFat > 15) {
            fatDelay = ((totalFat - 15) * 0.5).toInt().coerceIn(0, 30)
            if (fatDelay > 0) {
                adjustments.add(DoseAdjustment(
                    reason = "Жиры: ${"%.0f".format(totalFat)} г (замедление всасывания)",
                    amount = 0.0,
                    category = AdjustmentCategory.FAT
                ))
                warnings.add("ℹ️ Много жира - всасывание замедлено, возможен поздний подъём")
            }
        }

        // ─── 8. Calculate total dose ───
        var totalDose = foodDose + correctionDose + trendAdjustment - iobReduction + proteinDose
        totalDose = totalDose.coerceAtLeast(0.0)

        // Round to insulin step
        val roundedDose = if (totalDose > 0) {
            kotlin.math.floor(totalDose / settings.insulinStep) * settings.insulinStep
        } else 0.0

        // ─── 9. Determine timing ───
        val timing = determineTiming(
            currentGlucose = currentGlucose,
            targetGlucose = settings.targetGlucose,
            targetGlucoseMin = settings.targetGlucoseMin,
            aggregatedCurves = aggregatedCurves,
            insulinType = settings.insulinType,
            iob = iobState.totalIob,
            fatDelay = fatDelay
        )

        // ─── 10. Determine confidence ───
        val confidence = determineConfidence(
            currentGlucose = currentGlucose,
            iob = iobState.totalIob,
            hasCgm = cgmReading != null,
            isComplexMeal = totalFat > 20 || totalProtein > 30,
            timing = timing
        )

        // ─── 11. Generate reasoning ───
        reasoning = buildReasoning(
            foodDose = foodDose,
            correctionDose = correctionDose,
            iobReduction = iobReduction,
            totalDose = roundedDose,
            timing = timing,
            fastCarbs = aggregatedCurves.fast.sumOf { it.carbsGrams },
            mediumCarbs = aggregatedCurves.medium.sumOf { it.carbsGrams },
            slowCarbs = aggregatedCurves.slow.sumOf { it.carbsGrams }
        )

        // ─── 12. Safety checks ───
        if (currentGlucose < 3.5) {
            warnings.add(0, "🚨 ГИПОГЛИКЕМИЯ! Сначала съешьте быстрые углеводы!")
            return BolusRecommendation(
                dose = 0.0,
                timing = BolusTiming.WAIT_AND_RECHECK,
                confidence = Confidence.DO_NOT_INJECT,
                adjustments = adjustments,
                warnings = warnings,
                reasoning = "Сначала нормализуйте сахар, затем введите болюс",
                iobConsidered = iobReduction,
                components = RecommendationComponents(foodDose, correctionDose, trendAdjustment, iobReduction, proteinDose, fatDelay)
            )
        }

        return BolusRecommendation(
            dose = roundedDose,
            timing = timing,
            confidence = confidence,
            adjustments = adjustments,
            warnings = warnings,
            reasoning = reasoning,
            iobConsidered = iobReduction,
            components = RecommendationComponents(foodDose, correctionDose, trendAdjustment, iobReduction, proteinDose, fatDelay)
        )
    }

    private fun determineTiming(
        currentGlucose: Double,
        targetGlucose: Double,
        targetGlucoseMin: Double,
        aggregatedCurves: AggregatedCarbCurves,
        insulinType: String,
        iob: Double,
        fatDelay: Int
    ): BolusTiming {
        val profile = InsulinProfiles.get(insulinType)
        val hasFastCarbs = aggregatedCurves.fast.isNotEmpty()
        val hasSlowCarbs = aggregatedCurves.slow.isNotEmpty()

        // High glucose - inject earlier
        if (currentGlucose > targetGlucose + 2) {
            return BolusTiming.IN_20_MIN
        }
        if (currentGlucose > targetGlucose + 1) {
            return BolusTiming.IN_15_MIN
        }

        // Low glucose - inject later or after
        if (currentGlucose < targetGlucoseMin + 0.5) {
            return if (hasFastCarbs) BolusTiming.AFTER_MEAL else BolusTiming.NOW
        }

        // High IOB - be cautious
        if (iob > 2) {
            return BolusTiming.IN_5_MIN
        }

        // Fast-acting insulin with fast carbs - inject closer to meal
        if (profile?.type == InsulinType.RAPID && hasFastCarbs) {
            return BolusTiming.IN_5_MIN
        }

        // Fast carbs with normal insulin - inject early
        if (hasFastCarbs && !hasSlowCarbs) {
            return BolusTiming.IN_15_MIN
        }

        // Mixed meal - moderate timing
        if (hasFastCarbs && hasSlowCarbs) {
            return BolusTiming.IN_10_MIN
        }

        // Slow carbs only - can inject closer to meal
        if (hasSlowCarbs && !hasFastCarbs) {
            return BolusTiming.IN_5_MIN
        }

        // Fat delay adjustment
        if (fatDelay > 15) {
            return BolusTiming.AFTER_MEAL
        }

        return BolusTiming.NOW
    }

    private fun determineConfidence(
        currentGlucose: Double,
        iob: Double,
        hasCgm: Boolean,
        isComplexMeal: Boolean,
        timing: BolusTiming
    ): Confidence {
        // Low confidence situations
        if (iob > 3) return Confidence.LOW
        if (isComplexMeal && !hasCgm) return Confidence.MEDIUM
        if (timing == BolusTiming.AFTER_MEAL) return Confidence.MEDIUM
        if (currentGlucose < 4.0 || currentGlucose > 15.0) return Confidence.LOW

        // Medium confidence
        if (isComplexMeal) return Confidence.MEDIUM
        if (!hasCgm) return Confidence.MEDIUM

        return Confidence.HIGH
    }

    private fun buildReasoning(
        foodDose: Double,
        correctionDose: Double,
        iobReduction: Double,
        totalDose: Double,
        timing: BolusTiming,
        fastCarbs: Double,
        mediumCarbs: Double,
        slowCarbs: Double
    ): String {
        val parts = mutableListOf<String>()

        // Carb breakdown
        if (fastCarbs > 0 || mediumCarbs > 0 || slowCarbs > 0) {
            val carbBreakdown = mutableListOf<String>()
            if (fastCarbs > 0) carbBreakdown.add("быстрые ${"%.0f".format(fastCarbs)}г")
            if (mediumCarbs > 0) carbBreakdown.add("средние ${"%.0f".format(mediumCarbs)}г")
            if (slowCarbs > 0) carbBreakdown.add("медленные ${"%.0f".format(slowCarbs)}г")
            parts.add("Углеводы: ${carbBreakdown.joinToString(", ")}")
        }

        // Dose breakdown
        parts.add("Доза: ${"%.1f".format(totalDose)} ед")
        if (iobReduction > 0.1) {
            parts.add("(с учётом IOB: -${"%.1f".format(iobReduction)})")
        }

        // Timing reasoning
        parts.add("Тайминг: ${timing.description}")

        return parts.joinToString("\n")
    }
}


