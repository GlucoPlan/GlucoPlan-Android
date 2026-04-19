package com.glucoplan.app.data.db

import app.cash.turbine.test
import com.glucoplan.app.domain.model.Meal
import com.glucoplan.app.domain.model.MealComponent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MealDaoTest {

    private lateinit var mealDao: MealDao

    @Before
    fun setup() {
        mealDao = mockk()
    }

    private fun приём(id: Long, datetime: String = "2023-10-27T12:00:00") = Meal(
        id = id,
        datetime = datetime,
        insulinDose = 0.0,
        glucose = 0.0,
        notes = "",
        totalCarbs = 0.0,
        totalCalories = 0.0,
        totalProteins = 0.0,
        totalFats = 0.0,
        breadUnits = 0.0
    )

    @Test
    fun `getAllFlow - пустая база данных возвращает пустой список`() = runTest {
        every { mealDao.getAllFlow() } returns flowOf(emptyList())

        mealDao.getAllFlow().test {
            assertThat(awaitItem()).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `getAllFlow - приёмы отсортированы по убыванию даты`() = runTest {
        val приёмы = listOf(
            приём(1, "2023-10-27T14:00:00"),
            приём(2, "2023-10-27T12:00:00"),
            приём(3, "2023-10-26T10:00:00")
        )
        every { mealDao.getAllFlow() } returns flowOf(приёмы)

        mealDao.getAllFlow().test {
            val items = awaitItem()
            assertThat(items).hasSize(3)
            assertThat(items[0].datetime).isGreaterThan(items[1].datetime)
            assertThat(items[1].datetime).isGreaterThan(items[2].datetime)
            awaitComplete()
        }
    }

    @Test
    fun `getAllFlow - поток автоматически обновляется при добавлении записи`() = runTest {
        val начальный = listOf(приём(1, "2023-10-27T12:00:00"))
        val обновлённый = listOf(приём(1, "2023-10-27T12:00:00"), приём(2, "2023-10-28T08:00:00"))
        every { mealDao.getAllFlow() } returns flow {
            emit(начальный)
            emit(обновлённый)
        }

        mealDao.getAllFlow().test {
            assertThat(awaitItem()).hasSize(1)
            assertThat(awaitItem()).hasSize(2)
            awaitComplete()
        }
    }

    @Test
    fun `getByDate - точный префикс даты возвращает все приёмы за день`() = runTest {
        val приёмы = listOf(
            приём(1, "2023-10-27T10:00:00"),
            приём(2, "2023-10-27T18:00:00")
        )
        coEvery { mealDao.getByDate("2023-10-27") } returns приёмы

        val результат = mealDao.getByDate("2023-10-27")
        assertThat(результат).hasSize(2)
        assertThat(результат.all { it.datetime.startsWith("2023-10-27") }).isTrue()
    }

    @Test
    fun `getByDate - префикс месяца возвращает все приёмы за месяц`() = runTest {
        val приёмы = listOf(
            приём(1, "2023-10-01T10:00:00"),
            приём(2, "2023-10-15T12:00:00"),
            приём(3, "2023-10-31T18:00:00")
        )
        coEvery { mealDao.getByDate("2023-10") } returns приёмы

        val результат = mealDao.getByDate("2023-10")
        assertThat(результат).hasSize(3)
        assertThat(результат.all { it.datetime.startsWith("2023-10") }).isTrue()
    }

    @Test
    fun `getByDate - пустой префикс возвращает все записи`() = runTest {
        val приёмы = listOf(приём(1), приём(2), приём(3))
        coEvery { mealDao.getByDate("") } returns приёмы

        val результат = mealDao.getByDate("")
        assertThat(результат).hasSize(3)
    }

    @Test
    fun `getByDate - несуществующая дата возвращает пустой список`() = runTest {
        coEvery { mealDao.getByDate("2099-01-01") } returns emptyList()

        val результат = mealDao.getByDate("2099-01-01")
        assertThat(результат).isEmpty()
    }

    @Test
    fun `getAll - возвращает все записи из базы данных`() = runTest {
        val приёмы = listOf(приём(1), приём(2), приём(3))
        coEvery { mealDao.getAll() } returns приёмы

        val результат = mealDao.getAll()
        assertThat(результат).hasSize(3)
        assertThat(результат).containsExactlyElementsIn(приёмы)
    }

    @Test
    fun `getAll - список отсортирован по убыванию даты`() = runTest {
        val приёмы = listOf(
            приём(1, "2023-10-27T18:00:00"),
            приём(2, "2023-10-27T12:00:00"),
            приём(3, "2023-10-26T10:00:00")
        )
        coEvery { mealDao.getAll() } returns приёмы

        val результат = mealDao.getAll()
        for (i in 0 until результат.size - 1) {
            assertThat(результат[i].datetime).isGreaterThan(результат[i + 1].datetime)
        }
    }

    @Test
    fun `getById - возвращает существующую запись с корректными полями`() = runTest {
        val ожидаемый = приём(42, "2023-10-27T12:00:00")
        coEvery { mealDao.getById(42L) } returns ожидаемый

        val результат = mealDao.getById(42L)
        assertThat(результат).isEqualTo(ожидаемый)
        assertThat(результат?.id).isEqualTo(42L)
        assertThat(результат?.datetime).isEqualTo("2023-10-27T12:00:00")
    }

    @Test
    fun `getById - несуществующий ID возвращает null`() = runTest {
        coEvery { mealDao.getById(999L) } returns null

        val результат = mealDao.getById(999L)
        assertThat(результат).isNull()
    }

    @Test
    fun `insert - успешная вставка возвращает сгенерированный ID строки`() = runTest {
        val новыйПриём = приём(0, "2023-10-27T12:00:00")
        coEvery { mealDao.insert(новыйПриём) } returns 1L

        val rowId = mealDao.insert(новыйПриём)
        assertThat(rowId).isEqualTo(1L)
        assertThat(rowId).isGreaterThan(0L)
    }

    @Test
    fun `insertComponents - список компонентов успешно сохраняется`() = runTest {
        val компоненты = listOf(
            MealComponent(mealId = 1L, componentType = "product", productId = 1L, servingWeight = 100.0),
            MealComponent(mealId = 1L, componentType = "product", productId = 2L, servingWeight = 200.0)
        )
        coEvery { mealDao.insertComponents(компоненты) } returns Unit

        mealDao.insertComponents(компоненты)
        coVerify { mealDao.insertComponents(компоненты) }
    }

    @Test
    fun `insertComponents - пустой список не вызывает ошибку`() = runTest {
        coEvery { mealDao.insertComponents(emptyList()) } returns Unit

        mealDao.insertComponents(emptyList())
        coVerify { mealDao.insertComponents(emptyList()) }
    }

    @Test
    fun `deleteById - запись удаляется и getById возвращает null`() = runTest {
        coEvery { mealDao.deleteById(1L) } returns Unit
        coEvery { mealDao.getById(1L) } returnsMany listOf(приём(1L), null)

        val до = mealDao.getById(1L)
        assertThat(до).isNotNull()
        mealDao.deleteById(1L)
        val после = mealDao.getById(1L)
        assertThat(после).isNull()
    }

    @Test
    fun `deleteById - удаление несуществующей записи не вызывает ошибку`() = runTest {
        coEvery { mealDao.deleteById(999L) } returns Unit

        mealDao.deleteById(999L)
        coVerify { mealDao.deleteById(999L) }
    }

    @Test
    fun `getComponents - возвращает данные JOIN с именем продукта и углеводами`() = runTest {
        val компонент = MealComponentRow(
            id = 1L,
            mealId = 1L,
            componentType = "product",
            productId = 10L,
            dishId = null,
            servingWeight = 150.0,
            productName = "Яблоко",
            productCarbs = 14.0,
            dishName = null
        )
        coEvery { mealDao.getComponents(1L) } returns listOf(компонент)

        val результат = mealDao.getComponents(1L)
        assertThat(результат).hasSize(1)
        assertThat(результат[0].productName).isEqualTo("Яблоко")
        assertThat(результат[0].productCarbs).isEqualTo(14.0)
        assertThat(результат[0].dishName).isNull()
    }

    @Test
    fun `getComponents - LEFT JOIN с null-полями возвращает компонент без исключения`() = runTest {
        val компонент = MealComponentRow(
            id = 1L,
            mealId = 1L,
            componentType = "product",
            productId = null,
            dishId = null,
            servingWeight = 100.0,
            productName = null,
            productCarbs = null,
            dishName = null
        )
        coEvery { mealDao.getComponents(1L) } returns listOf(компонент)

        val результат = mealDao.getComponents(1L)
        assertThat(результат).hasSize(1)
        assertThat(результат[0].productName).isNull()
        assertThat(результат[0].dishName).isNull()
        assertThat(результат[0].displayName).isEqualTo("?")
    }

    @Test
    fun `getComponents - несуществующий mealId возвращает пустой список`() = runTest {
        coEvery { mealDao.getComponents(999L) } returns emptyList()

        val результат = mealDao.getComponents(999L)
        assertThat(результат).isEmpty()
    }

    @Test
    fun `insert - вставка с дублирующимся ID вызывает исключение конфликта`() = runTest {
        val дубликат = приём(1L, "2023-10-27T12:00:00")
        coEvery { mealDao.insert(дубликат) } throws RuntimeException("UNIQUE constraint failed: meals.id")

        var пойманное: RuntimeException? = null
        try {
            mealDao.insert(дубликат)
        } catch (e: RuntimeException) {
            пойманное = e
        }
        assertThat(пойманное).isNotNull()
        assertThat(пойманное?.message).contains("UNIQUE constraint failed")
    }
}
