package com.glucoplan.app.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PumpBolusPlannerTest {

    private fun plan(
        carbs: Double,
        protein: Double,
        fat: Double,
        gi: Double,
        food: Double,
        extraNow: Double = 0.0,
        factor: Double = 0.4,
        step: Double = 0.5,
        xe: Double = 12.0,
        coeff: Double = 1.5
    ) = PumpBolusPlanner.plan(
        carbsG = carbs,
        proteinG = protein,
        fatG = fat,
        weightedGi = gi,
        foodDose = food,
        immediateExtra = extraNow,
        carbsPerXe = xe,
        carbCoefficient = coeff,
        insulinStep = step,
        fpuFactor = factor
    )

    @Test
    fun `быстрые углеводы без БЖ — всё сразу`() {
        val p = plan(carbs = 30.0, protein = 1.0, fat = 0.2, gi = 80.0, food = 3.75)
        assertThat(p.kind).isEqualTo(PumpBolusKind.NORMAL)
        assertThat(p.extendedUnits).isEqualTo(0.0)
        assertThat(p.nowUnits).isGreaterThan(0.0)
        assertThat(p.extraFpuUnits).isEqualTo(0.0)
    }

    @Test
    fun `гречка с мясом — комбо и надбавка БЖ`() {
        // 40г УВ, 25г белка, 20г жира, ГИ 50
        val food = 40.0 / 12.0 * 1.5 // 5.0
        val p = plan(carbs = 40.0, protein = 25.0, fat = 20.0, gi = 50.0, food = food)
        assertThat(p.kind).isEqualTo(PumpBolusKind.DUAL)
        assertThat(p.nowUnits).isGreaterThan(0.0)
        assertThat(p.extendedUnits).isGreaterThan(0.0)
        assertThat(p.durationMinutes).isAtLeast(180)
        assertThat(p.extraFpuUnits).isGreaterThan(0.0)
        assertThat(p.fpu).isWithin(0.05).of(2.8)
    }

    @Test
    fun `надбавка 0 — БЖ не добавляет единицы`() {
        val food = 40.0 / 12.0 * 1.5
        val p = plan(carbs = 40.0, protein = 25.0, fat = 20.0, gi = 70.0, food = food, factor = 0.0)
        assertThat(p.extraFpuUnits).isEqualTo(0.0)
        assertThat(p.kind).isEqualTo(PumpBolusKind.NORMAL)
    }

    @Test
    fun `мясо почти без углеводов — растянутый`() {
        val p = plan(carbs = 0.0, protein = 40.0, fat = 25.0, gi = 0.0, food = 0.0)
        assertThat(p.kind).isEqualTo(PumpBolusKind.SQUARE)
        assertThat(p.nowUnits).isEqualTo(0.0)
        assertThat(p.extendedUnits).isGreaterThan(0.0)
    }

    @Test
    fun `коррекция уходит в немедленную часть`() {
        val p = plan(carbs = 30.0, protein = 0.0, fat = 0.0, gi = 80.0, food = 3.0, extraNow = 1.0, step = 0.5)
        assertThat(p.kind).isEqualTo(PumpBolusKind.NORMAL)
        assertThat(p.nowUnits).isWithin(0.01).of(4.0)
    }
}
