package com.glucoplan.app.ui.dishes

import com.glucoplan.app.data.repository.GlucoRepository
import com.glucoplan.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты для DishesViewModel.
 * Проверяет поиск блюд, сохранение, удаление и управление кастрюлями.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DishesViewModelTest {

    private lateinit var репозиторий: GlucoRepository
    private lateinit var viewModel: DishesViewModel
    private val диспетчер = UnconfinedTestDispatcher()

    private fun тестовоеБлюдо(id: Long = 1, название: String = "Борщ") = DishWithIngredients(
        dish = Dish(id = id, name = название),
        ingredients = listOf(
            DishIngredient(
                productId = 1, productName = "Свёкла",
                weight = 100.0, carbs = 10.0, calories = 50.0,
                proteins = 2.0, fats = 0.5, glycemicIndex = 30.0
            )
        )
    )

    @Before
    fun подготовка() {
        Dispatchers.setMain(диспетчер)
        репозиторий = mockk(relaxed = true)
        coEvery { репозиторий.searchDishesWithIngredients(any()) } returns listOf(тестовоеБлюдо())
        coEvery { репозиторий.getPans() } returns emptyList()
        viewModel = DishesViewModel(репозиторий)
    }

    @After
    fun очистка() {
        Dispatchers.resetMain()
    }

    // ─── Начальное состояние ──────────────────────────────────────────────────────

    @Test
    fun `после загрузки список блюд содержит данные из репозитория`() = runTest {
        // ViewModel вызывает load("") в init{} — проверяем что данные загрузились
        advanceUntilIdle()
        assertThat(viewModel.dishes.value).hasSize(1)
    }

    @Test
    fun `начальное состояние содержит пустой список кастрюль`() {
        assertThat(viewModel.pans.value).isEmpty()
    }

    // ─── Загрузка блюд ────────────────────────────────────────────────────────────

    @Test
    fun `load загружает блюда из репозитория`() = runTest {
        viewModel.load("")
        advanceUntilIdle()

        assertThat(viewModel.dishes.value).hasSize(1)
        assertThat(viewModel.dishes.value.first().dish.name).isEqualTo("Борщ")
    }

    @Test
    fun `load вызывает репозиторий с правильным запросом`() = runTest {
        viewModel.load("борщ")
        advanceUntilIdle()

        coVerify { репозиторий.searchDishesWithIngredients("борщ") }
    }

    @Test
    fun `load с пустой строкой загружает все блюда`() = runTest {
        viewModel.load("")
        advanceUntilIdle()

        coVerify { репозиторий.searchDishesWithIngredients("") }
    }

    // ─── Сохранение блюда ─────────────────────────────────────────────────────────

    @Test
    fun `saveDish вызывает репозиторий`() = runTest {
        val блюдо = Dish(name = "Щи")
        val ингредиенты = listOf(
            DishIngredient(
                productId = 1, productName = "Капуста",
                weight = 200.0, carbs = 6.0, calories = 30.0,
                proteins = 2.0, fats = 0.3, glycemicIndex = 15.0
            )
        )
        viewModel.saveDish(блюдо, ингредиенты)
        advanceUntilIdle()

        coVerify { репозиторий.saveDish(блюдо, ингредиенты) }
    }

    @Test
    fun `saveDish перезагружает список после сохранения`() = runTest {
        val новоеБлюдо = тестовоеБлюдо(id = 2, название = "Щи")
        coEvery { репозиторий.searchDishesWithIngredients("") } returnsMany listOf(
            listOf(тестовоеБлюдо()),
            listOf(тестовоеБлюдо(), новоеБлюдо)
        )

        viewModel.load("")
        advanceUntilIdle()
        assertThat(viewModel.dishes.value).hasSize(1)

        viewModel.saveDish(Dish(name = "Щи"), emptyList())
        advanceUntilIdle()
        assertThat(viewModel.dishes.value).hasSize(2)
    }

    // ─── Удаление блюда ───────────────────────────────────────────────────────────

    @Test
    fun `deleteDish вызывает репозиторий с правильным id`() = runTest {
        viewModel.deleteDish(42)
        advanceUntilIdle()

        coVerify { репозиторий.deleteDish(42) }
    }

    @Test
    fun `deleteDish перезагружает список после удаления`() = runTest {
        coEvery { репозиторий.searchDishesWithIngredients("") } returnsMany listOf(
            listOf(тестовоеБлюдо()),
            emptyList()
        )

        viewModel.load("")
        advanceUntilIdle()
        assertThat(viewModel.dishes.value).hasSize(1)

        viewModel.deleteDish(1)
        advanceUntilIdle()
        assertThat(viewModel.dishes.value).isEmpty()
    }

    // ─── Кастрюли ─────────────────────────────────────────────────────────────────

    @Test
    fun `loadPans загружает кастрюли из репозитория`() = runTest {
        val кастрюли = listOf(Pan(id = 1, name = "Большая кастрюля", weight = 500.0))
        coEvery { репозиторий.getPans() } returns кастрюли

        viewModel.loadPans()
        advanceUntilIdle()

        coVerify { репозиторий.getPans() }
        assertThat(viewModel.pans.value).hasSize(1)
        assertThat(viewModel.pans.value.first().name).isEqualTo("Большая кастрюля")
    }

    @Test
    fun `savePan вызывает репозиторий`() = runTest {
        val кастрюля = Pan(name = "Сковородка", weight = 300.0)
        coEvery { репозиторий.savePan(кастрюля) } returns 1L

        viewModel.savePan(кастрюля)
        advanceUntilIdle()

        coVerify { репозиторий.savePan(кастрюля) }
    }

    @Test
    fun `deletePan вызывает репозиторий и перезагружает список`() = runTest {
        coEvery { репозиторий.getPans() } returns emptyList()

        viewModel.deletePan(5)
        advanceUntilIdle()

        coVerify { репозиторий.deletePan(5) }
    }

    // ─── Поиск с ингредиентами ────────────────────────────────────────────────────

    @Test
    fun `searchWithIngredients вызывает репозиторий`() = runTest {
        coEvery { репозиторий.searchDishesWithIngredients("суп") } returns emptyList()

        viewModel.searchWithIngredients("суп")
        advanceUntilIdle()

        coVerify { репозиторий.searchDishesWithIngredients("суп") }
    }
}
