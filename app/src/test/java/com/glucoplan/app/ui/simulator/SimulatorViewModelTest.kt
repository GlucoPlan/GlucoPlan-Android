package com.glucoplan.app.ui.simulator

import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Unit tests for SimulatorViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorViewModelTest {

    private lateinit var repository: GlucoRepository
    private lateinit var viewModel: SimulatorViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)

        // Default mock returns
        coEvery { repository.getRecentInjections(any()) } returns emptyList()

        viewModel = SimulatorViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ─── Initialization Tests ─────────────────────────────────────────────────────

    @Test
    fun `initial state has default values`() = runTest {
        val state = viewModel.state.value

        assertThat(state.currentGlucose).isEqualTo(7.0)
        assertThat(state.insulinDose).isEqualTo(2.0)
        assertThat(state.loading).isTrue()
    }

    @Test
    fun `init loads components and runs simulation`() = runTest {
        val components = listOf(
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 1,
                name = "Bread",
                servingWeight = 100.0,
                carbsPer100g = 50.0,
                caloriesPer100g = 250.0,
                proteinsPer100g = 8.0,
                fatsPer100g = 2.0,
                glycemicIndex = 70.0
            )
        )

        viewModel.init(
            components = components,
            insulin = 4.0,
            glucose = 8.0,
            insulinType = "fiasp",
            settings = AppSettings(
                carbsPerXe = 12.0,
                carbCoefficient = 1.5,
                sensitivity = 2.5
            )
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.components).hasSize(1)
        assertThat(state.insulinDose).isEqualTo(4.0)
        assertThat(state.currentGlucose).isEqualTo(8.0)
        assertThat(state.loading).isFalse()
    }

    // ─── Simulation Tests ─────────────────────────────────────────────────────────

    @Test
    fun `simulation generates chart points`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Test",
                    servingWeight = 100.0,
                    carbsPer100g = 30.0,
                    caloriesPer100g = 150.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 60.0
                )
            ),
            insulin = 3.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val state = viewModel.state.value

        assertThat(state.fastCarbPoints).isNotEmpty()
        assertThat(state.mediumCarbPoints).isNotEmpty()
        assertThat(state.slowCarbPoints).isNotEmpty()
        assertThat(state.insulinPoints).isNotEmpty()
        assertThat(state.combinedPoints).isNotEmpty()
    }

    @Test
    fun `simulation calculates peak and final glucose`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Carbs",
                    servingWeight = 100.0,
                    carbsPer100g = 50.0,
                    caloriesPer100g = 200.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0,
                    glycemicIndex = 75.0
                )
            ),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings(sensitivity = 2.0)
        )

        advanceUntilIdle()

        val state = viewModel.state.value

        assertThat(state.peakGlucose).isGreaterThan(0.0)
        assertThat(state.finalGlucose).isGreaterThan(0.0)
        assertThat(state.minutesToPeak).isGreaterThan(0)
    }

    @Test
    fun `simulation calculates totals correctly`() = runTest {
        val components = listOf(
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 1,
                name = "Food",
                servingWeight = 100.0,
                carbsPer100g = 40.0,
                caloriesPer100g = 200.0,
                proteinsPer100g = 10.0,
                fatsPer100g = 5.0,
                glycemicIndex = 55.0
            )
        )

        viewModel.init(
            components = components,
            insulin = 3.0,
            glucose = 6.5,
            insulinType = "fiasp",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val state = viewModel.state.value

        assertThat(state.totalCarbs).isEqualTo(40.0)
        assertThat(state.totalProtein).isEqualTo(10.0)
        assertThat(state.totalFat).isEqualTo(5.0)
    }

    // ─── Update Tests ──────────────────────────────────────────────────────────────

    @Test
    fun `update changes insulin dose`() = runTest {
        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )
        advanceUntilIdle()

        viewModel.update(insulin = 5.0)

        assertThat(viewModel.state.value.insulinDose).isEqualTo(5.0)
    }

    @Test
    fun `update changes insulin type`() = runTest {
        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )
        advanceUntilIdle()

        viewModel.update(insulinType = "fiasp")

        assertThat(viewModel.state.value.insulinType).isEqualTo("fiasp")
    }

    @Test
    fun `update changes glucose`() = runTest {
        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )
        advanceUntilIdle()

        viewModel.update(glucose = 9.0)

        assertThat(viewModel.state.value.currentGlucose).isEqualTo(9.0)
    }

    // ─── Recommendation Tests ──────────────────────────────────────────────────────

    @Test
    fun `recommendation is calculated`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Bread",
                    servingWeight = 100.0,
                    carbsPer100g = 50.0,
                    caloriesPer100g = 250.0,
                    proteinsPer100g = 8.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 70.0
                )
            ),
            insulin = 4.0,
            glucose = 7.5,
            insulinType = "novorapid",
            settings = AppSettings(
                carbsPerXe = 12.0,
                carbCoefficient = 1.5,
                sensitivity = 2.5,
                targetGlucose = 6.0
            )
        )

        advanceUntilIdle()

        val recommendation = viewModel.state.value.recommendation

        assertThat(recommendation).isNotNull()
        assertThat(recommendation!!.dose).isGreaterThan(0.0)
        assertThat(recommendation.timing).isNotNull()
        assertThat(recommendation.confidence).isNotNull()
    }

    @Test
    fun `recommendation includes adjustments`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Food",
                    servingWeight = 100.0,
                    carbsPer100g = 36.0,
                    caloriesPer100g = 180.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 65.0
                )
            ),
            insulin = 4.0,
            glucose = 8.0,
            insulinType = "fiasp",
            settings = AppSettings(
                carbsPerXe = 12.0,
                carbCoefficient = 1.5,
                sensitivity = 2.5,
                targetGlucose = 6.0
            )
        )

        advanceUntilIdle()

        val recommendation = viewModel.state.value.recommendation
        assertThat(recommendation!!.adjustments).isNotEmpty()
    }

    // ─── IOB Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `IOB is calculated from previous injections`() = runTest {
        val previousInjections = listOf(
            InsulinInjection(
                id = 1,
                injectedAt = java.time.Instant.now().minus(30L, ChronoUnit.MINUTES).toString(),
                insulinType = "fiasp",
                dose = 3.0
            )
        )
        coEvery { repository.getRecentInjections(any()) } returns previousInjections

        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "fiasp",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val iob = viewModel.state.value.iobState
        assertThat(iob.totalIob).isGreaterThan(0.0)
    }

    // ─── Insulin Profile Tests ─────────────────────────────────────────────────────

    @Test
    fun `insulinProfile is resolved correctly`() = runTest {
        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "fiasp",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val profile = viewModel.state.value.insulinProfile
        assertThat(profile).isNotNull()
        assertThat(profile!!.name).isEqualTo("fiasp")
    }

    // ─── Summary Text Tests ────────────────────────────────────────────────────────

    @Test
    fun `getSummaryText returns formatted string`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Food",
                    servingWeight = 100.0,
                    carbsPer100g = 30.0,
                    caloriesPer100g = 150.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 55.0
                )
            ),
            insulin = 3.0,
            glucose = 7.5,
            insulinType = "novorapid",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val summary = viewModel.getSummaryText()

        assertThat(summary).contains("Симуляция")
        assertThat(summary).contains("Сахар")
        assertThat(summary).contains("Углеводы")
        assertThat(summary).contains("Инсулин")
    }

    // ─── Advanced Toggle Tests ─────────────────────────────────────────────────────

    @Test
    fun `toggleAdvanced changes showAdvanced`() = runTest {
        val before = viewModel.state.value.showAdvanced

        viewModel.toggleAdvanced()

        assertThat(viewModel.state.value.showAdvanced).isEqualTo(!before)
    }

    // ─── Edge Cases ────────────────────────────────────────────────────────────────

    @Test
    fun `empty components still runs simulation`() = runTest {
        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.loading).isFalse()
        assertThat(state.totalCarbs).isEqualTo(0.0)
    }

    @Test
    fun `zero insulin dose is handled`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Food",
                    servingWeight = 100.0,
                    carbsPer100g = 30.0,
                    caloriesPer100g = 150.0,
                    proteinsPer100g = 5.0,
                    fatsPer100g = 2.0,
                    glycemicIndex = 60.0
                )
            ),
            insulin = 0.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.insulinDose).isEqualTo(0.0)
        assertThat(state.peakGlucose).isGreaterThan(7.0) // Carbs should raise glucose
    }

    @Test
    fun `negative glucose is coerced to valid value`() = runTest {
        viewModel.init(
            components = emptyList(),
            insulin = 2.0,
            glucose = 0.0, // Invalid
            insulinType = "novorapid",
            settings = AppSettings()
        )

        advanceUntilIdle()

        // Should use default glucose
        assertThat(viewModel.state.value.currentGlucose).isEqualTo(7.0)
    }

    // ─── Carb Curve Categorization Tests ──────────────────────────────────────────

    @Test
    fun `fast carbs are categorized correctly`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Fast Carb",
                    servingWeight = 100.0,
                    carbsPer100g = 25.0,
                    caloriesPer100g = 100.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0,
                    glycemicIndex = 80.0 // FAST
                )
            ),
            insulin = 2.0,
            glucose = 7.0,
            insulinType = "fiasp",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.carbCurves.fast).hasSize(1)
        assertThat(state.carbCurves.medium).isEmpty()
        assertThat(state.carbCurves.slow).isEmpty()
    }

    @Test
    fun `mixed carbs are categorized correctly`() = runTest {
        viewModel.init(
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 1,
                    name = "Fast",
                    servingWeight = 100.0,
                    carbsPer100g = 20.0,
                    caloriesPer100g = 80.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0,
                    glycemicIndex = 80.0
                ),
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 2,
                    name = "Medium",
                    servingWeight = 100.0,
                    carbsPer100g = 20.0,
                    caloriesPer100g = 80.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0,
                    glycemicIndex = 55.0
                ),
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = 3,
                    name = "Slow",
                    servingWeight = 100.0,
                    carbsPer100g = 20.0,
                    caloriesPer100g = 80.0,
                    proteinsPer100g = 0.0,
                    fatsPer100g = 0.0,
                    glycemicIndex = 35.0
                )
            ),
            insulin = 5.0,
            glucose = 7.0,
            insulinType = "novorapid",
            settings = AppSettings()
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.carbCurves.fast).hasSize(1)
        assertThat(state.carbCurves.medium).hasSize(1)
        assertThat(state.carbCurves.slow).hasSize(1)
    }
}
