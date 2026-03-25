package com.glucoplan.app.ui.calculator

import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import java.time.Instant

/**
 * Unit tests for CalculatorViewModel.
 * Uses MockK for mocking and Turbine for Flow testing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {

    private lateinit var repository: GlucoRepository
    private lateinit var viewModel: CalculatorViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)

        // Default settings — nsEnabled=false so no real network calls in tests
        coEvery { repository.getSettings() } returns AppSettings(
            carbsPerXe = 12.0,
            carbCoefficient = 1.5,
            sensitivity = 2.5,
            targetGlucose = 6.0,
            insulinStep = 0.5,
            nsEnabled = false,
            nsUrl = ""
        )

        viewModel = CalculatorViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ─── Initialization Tests ─────────────────────────────────────────────────────

    @Test
    fun initial_state_has_empty_components() = runTest {
        val state = viewModel.state.value

        assertThat(state.components).isEmpty()
        assertThat(state.totalCarbs).isEqualTo(0.0)
    }

    @Test
    fun initial_state_loads_settings() = runTest {
        advanceUntilIdle()

        coVerify { repository.getSettings() }
        assertThat(viewModel.state.value.settings.carbsPerXe).isEqualTo(12.0)
    }

    // ─── Component Management Tests ───────────────────────────────────────────────

    @Test
    fun addProduct_adds_component_to_state() = runTest {
        val product = Product(
            id = 1,
            name = "Bread",
            carbs = 50.0,
            glycemicIndex = 70.0
        )

        viewModel.addProduct(product, 100.0)

        val state = viewModel.state.value
        assertThat(state.components).hasSize(1)
        assertThat(state.components.first().name).isEqualTo("Bread")
        assertThat(state.components.first().carbsInPortion).isEqualTo(50.0)
    }

    @Test
    fun addDish_adds_component_to_state() = runTest {
        val dish = DishWithIngredients(
            dish = Dish(id = 1, name = "Sandwich"),
            ingredients = listOf(
                DishIngredient(
                    productId = 1,
                    productName = "Bread",
                    weight = 60.0,
                    carbs = 48.0,
                    calories = 240.0,
                    proteins = 8.0,
                    fats = 2.0,
                    glycemicIndex = 70.0
                )
            )
        )

        viewModel.addDish(dish, 100.0)

        val state = viewModel.state.value
        assertThat(state.components).hasSize(1)
        assertThat(state.components.first().name).isEqualTo("Sandwich")
    }

    @Test
    fun removeComponent_removes_from_state() = runTest {
        val product = Product(id = 1, name = "Test", carbs = 20.0)
        viewModel.addProduct(product, 100.0)

        val key = viewModel.state.value.components.first().key
        viewModel.removeComponent(key)

        assertThat(viewModel.state.value.components).isEmpty()
    }

    @Test
    fun updateWeight_changes_serving_weight() = runTest {
        val product = Product(id = 1, name = "Test", carbs = 20.0)
        viewModel.addProduct(product, 100.0)

        val key = viewModel.state.value.components.first().key
        viewModel.updateWeight(key, 200.0)

        val component = viewModel.state.value.components.first()
        assertThat(component.servingWeight).isEqualTo(200.0)
        assertThat(component.carbsInPortion).isEqualTo(40.0) // 20g/100g * 200g
    }

    @Test
    fun toggleAdjustment_changes_includedInAdjustment() = runTest {
        val product = Product(id = 1, name = "Test", carbs = 20.0)
        viewModel.addProduct(product, 100.0)

        val key = viewModel.state.value.components.first().key
        val before = viewModel.state.value.components.first().includedInAdjustment

        viewModel.toggleAdjustment(key)

        val after = viewModel.state.value.components.first().includedInAdjustment
        assertThat(after).isEqualTo(!before)
    }

    @Test
    fun clearAll_removes_all_components() = runTest {
        viewModel.addProduct(Product(id = 1, name = "A"), 100.0)
        viewModel.addProduct(Product(id = 2, name = "B"), 100.0)

        viewModel.clearAll()

        assertThat(viewModel.state.value.components).isEmpty()
    }

    // ─── Glucose Management Tests ─────────────────────────────────────────────────

    @Test
    fun updateGlucose_changes_current_glucose() = runTest {
        viewModel.updateGlucose(7.5)

        assertThat(viewModel.state.value.currentGlucose).isEqualTo(7.5)
    }

    @Test
    fun updateCgmReading_sets_glucose_and_reading() = runTest {
        val reading = CgmReading(
            glucose = 8.2,
            direction = "SingleUp",
            time = Instant.now()
        )

        viewModel.updateCgmReading(reading)

        val state = viewModel.state.value
        assertThat(state.cgmReading).isEqualTo(reading)
        assertThat(state.currentGlucose).isEqualTo(8.2)
    }

    @Test
    fun `updateCgmReading null clears reading`() = runTest {
        viewModel.updateGlucose(7.0)
        viewModel.updateCgmReading(null)

        assertThat(viewModel.state.value.cgmReading).isNull()
    }

    // ─── Dose Calculation Tests ───────────────────────────────────────────────────

    @Test
    fun `foodDose calculated correctly`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 36.0), 100.0)

        // 36g / 12g per XE * 1.5 coefficient = 4.5 units
        val state = viewModel.state.value
        assertThat(state.foodDose).isEqualTo(4.5)
    }

    @Test
    fun `correction calculated for high glucose`() = runTest {
        viewModel.updateGlucose(11.0)

        // (11 - 6) / 2.5 = 2 units
        val state = viewModel.state.value
        assertThat(state.correction).isEqualTo(2.0)
    }

    @Test
    fun `correction is zero for normal glucose`() = runTest {
        viewModel.updateGlucose(5.5)

        assertThat(viewModel.state.value.correction).isEqualTo(0.0)
    }

    @Test
    fun `totalDose sums food and correction`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 24.0), 100.0) // 2 XE = 3 units
        viewModel.updateGlucose(8.5) // Correction: (8.5 - 6) / 2.5 = 1 unit

        val state = viewModel.state.value
        assertThat(state.totalDose).isEqualTo(4.0)
    }

    @Test
    fun `trendDelta from CGM affects dose`() = runTest {
        val reading = CgmReading(
            glucose = 6.0,
            direction = "SingleUp", // +1.0
            time = Instant.now()
        )
        viewModel.updateCgmReading(reading)

        val state = viewModel.state.value
        assertThat(state.trendDelta).isEqualTo(1.0)
    }

    @Test
    fun `rounding works correctly`() = runTest {
        // Create situation that results in non-round number
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 25.0), 100.0)
        // 25/12 * 1.5 = 3.125

        val state = viewModel.state.value
        assertThat(state.roundedDown).isEqualTo(3.0) // floor to 0.5
        assertThat(state.roundedUp).isEqualTo(3.5)
    }

    // ─── Computed Properties Tests ────────────────────────────────────────────────

    @Test
    fun `totalCarbs sums all components`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "A", carbs = 20.0), 100.0)
        viewModel.addProduct(Product(id = 2, name = "B", carbs = 30.0), 100.0)

        assertThat(viewModel.state.value.totalCarbs).isEqualTo(50.0)
    }

    @Test
    fun `breadUnits calculated correctly`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 36.0), 100.0)

        // 36 / 12 = 3 XE
        assertThat(viewModel.state.value.breadUnits).isEqualTo(3.0)
    }

    @Test
    fun `glycemicLoad calculated correctly`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 50.0, glycemicIndex = 60.0), 100.0)

        // GL = GI * carbs / 100 = 60 * 50 / 100 = 30
        assertThat(viewModel.state.value.glycemicLoad).isEqualTo(30.0)
    }

    // ─── Save Meal Tests ───────────────────────────────────────────────────────────

    @Test
    fun `saveMeal calls repository`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 20.0), 100.0)

        viewModel.saveMeal("Test notes")

        advanceUntilIdle()

        coVerify { repository.saveMeal(any(), any()) }
    }

    @Test
    fun `saveMeal clears components after success`() = runTest {
        viewModel.addProduct(Product(id = 1, name = "Test", carbs = 20.0), 100.0)

        viewModel.saveMeal("Test")
        // Only advance past the repo call, not past the 2s delay
        advanceTimeBy(100)
        runCurrent()

        // Components are cleared immediately after save
        assertThat(viewModel.state.value.components).isEmpty()
        // savedSuccess is true before the 2-second reset delay elapses
        assertThat(viewModel.state.value.savedSuccess).isTrue()
    }

    @Test
    fun `saveMeal does nothing with empty components`() = runTest {
        viewModel.saveMeal("Test")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.saveMeal(any(), any()) }
    }

    // ─── History Loading Tests ─────────────────────────────────────────────────────

    @Test
    fun `loadFromHistory sets components`() = runTest {
        val components = listOf(
            CalcComponent(
                type = ComponentType.PRODUCT,
                sourceId = 1,
                name = "Historical",
                servingWeight = 150.0,
                carbsPer100g = 30.0,
                caloriesPer100g = 150.0,
                proteinsPer100g = 5.0,
                fatsPer100g = 2.0
            )
        )

        viewModel.loadFromHistory(components)

        assertThat(viewModel.state.value.components).isEqualTo(components)
    }

    // ─── Settings Update Tests ────────────────────────────────────────────────────

    @Test
    fun `updateSettings changes settings in state`() = runTest {
        val newSettings = AppSettings(
            carbsPerXe = 15.0,
            carbCoefficient = 2.0,
            sensitivity = 3.0
        )

        viewModel.updateSettings(newSettings)

        assertThat(viewModel.state.value.settings.carbsPerXe).isEqualTo(15.0)
    }
}
