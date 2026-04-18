package com.glucoplan.app.ui.history

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
import java.time.LocalDate

/**
 * Unit-тесты для HistoryViewModel.
 * Проверяет загрузку, фильтрацию по дате и удаление приёмов пищи.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var репозиторий: GlucoRepository
    private lateinit var viewModel: HistoryViewModel
    private val диспетчер = UnconfinedTestDispatcher()

    private val тестовыеПриёмы = listOf(
        Meal(id = 1, datetime = "2024-03-20T08:00:00", totalCarbs = 30.0, insulinDose = 3.0),
        Meal(id = 2, datetime = "2024-03-20T12:00:00", totalCarbs = 50.0, insulinDose = 4.5),
        Meal(id = 3, datetime = "2024-03-21T08:00:00", totalCarbs = 40.0, insulinDose = 3.5)
    )

    @Before
    fun подготовка() {
        Dispatchers.setMain(диспетчер)
        репозиторий = mockk(relaxed = true)
        coEvery { репозиторий.getMealsByDate(null) } returns тестовыеПриёмы
        viewModel = HistoryViewModel(репозиторий)
    }

    @After
    fun очистка() {
        Dispatchers.resetMain()
    }

    // ─── Начальное состояние ──────────────────────────────────────────────────────

    @Test
    fun `начальное состояние не имеет фильтра даты`() {
        // ViewModel вызывает load() в init{} — с UnconfinedTestDispatcher
        // данные загружаются сразу, список будет заполнен.
        // Проверяем только filterDate — он точно null до setFilter()
        assertThat(viewModel.state.value.filterDate).isNull()
    }

    // ─── Загрузка данных ──────────────────────────────────────────────────────────

    @Test
    fun `load загружает все приёмы пищи без фильтра`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.state.value.meals).hasSize(3)
    }

    @Test
    fun `load вызывает репозиторий`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        coVerify { репозиторий.getMealsByDate(null) }
    }

    @Test
    fun `load с фильтром вызывает репозиторий с датой`() = runTest {
        val дата = LocalDate.of(2024, 3, 20)
        coEvery { репозиторий.getMealsByDate(дата) } returns тестовыеПриёмы.take(2)

        viewModel.setFilter(дата)
        viewModel.load()
        advanceUntilIdle()

        coVerify { репозиторий.getMealsByDate(дата) }
        assertThat(viewModel.state.value.meals).hasSize(2)
    }

    // ─── Фильтрация по дате ───────────────────────────────────────────────────────

    @Test
    fun `setFilter устанавливает дату фильтра в состоянии`() {
        val дата = LocalDate.of(2024, 3, 20)
        viewModel.setFilter(дата)
        assertThat(viewModel.state.value.filterDate).isEqualTo(дата)
    }

    @Test
    fun `setFilter с null сбрасывает фильтр`() {
        viewModel.setFilter(LocalDate.now())
        viewModel.setFilter(null)
        assertThat(viewModel.state.value.filterDate).isNull()
    }

    @Test
    fun `setFilter запускает перезагрузку`() = runTest {
        val дата = LocalDate.of(2024, 3, 21)
        coEvery { репозиторий.getMealsByDate(дата) } returns listOf(тестовыеПриёмы[2])

        viewModel.setFilter(дата)
        advanceUntilIdle()

        assertThat(viewModel.state.value.meals).hasSize(1)
    }

    // ─── Удаление ─────────────────────────────────────────────────────────────────

    @Test
    fun `delete вызывает репозиторий с правильным id`() = runTest {
        val приём = тестовыеПриёмы.first()
        viewModel.delete(приём)
        advanceUntilIdle()
        coVerify { репозиторий.deleteMeal(приём.id) }
    }

    @Test
    fun `delete перезагружает список после удаления`() = runTest {
        coEvery { репозиторий.getMealsByDate(null) } returnsMany listOf(
            тестовыеПриёмы,
            тестовыеПриёмы.drop(1)
        )
        viewModel.load()
        advanceUntilIdle()
        assertThat(viewModel.state.value.meals).hasSize(3)

        viewModel.delete(тестовыеПриёмы.first())
        advanceUntilIdle()
        assertThat(viewModel.state.value.meals).hasSize(2)
    }

    // ─── Загрузка компонентов ─────────────────────────────────────────────────────

    @Test
    fun `loadComponents вызывает репозиторий с правильным id приёма`() = runTest {
        coEvery { репозиторий.getMealComponents(1) } returns emptyList()
        viewModel.loadComponents(1)
        advanceUntilIdle()
        coVerify { репозиторий.getMealComponents(1) }
    }

    @Test
    fun `список приёмов сохраняется в состоянии после загрузки`() = runTest {
        viewModel.load()
        advanceUntilIdle()
        val приёмы = viewModel.state.value.meals
        assertThat(приёмы.any { it.id == 1L }).isTrue()
        assertThat(приёмы.any { it.id == 2L }).isTrue()
    }
}
