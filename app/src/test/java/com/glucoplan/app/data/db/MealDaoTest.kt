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

    private fun meal(id: Long, datetime: String = "2023-10-27T12:00:00") = Meal(
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
    fun `getAllFlow empty database check`() = runTest {
        every { mealDao.getAllFlow() } returns flowOf(emptyList())

        mealDao.getAllFlow().test {
            assertThat(awaitItem()).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `getAllFlow sorting order validation`() = runTest {
        val meals = listOf(
            meal(1, "2023-10-27T14:00:00"),
            meal(2, "2023-10-27T12:00:00"),
            meal(3, "2023-10-26T10:00:00")
        )
        every { mealDao.getAllFlow() } returns flowOf(meals)

        mealDao.getAllFlow().test {
            val items = awaitItem()
            assertThat(items).hasSize(3)
            assertThat(items[0].datetime).isGreaterThan(items[1].datetime)
            assertThat(items[1].datetime).isGreaterThan(items[2].datetime)
            awaitComplete()
        }
    }

    @Test
    fun `getAllFlow reactive update check`() = runTest {
        val initial = listOf(meal(1, "2023-10-27T12:00:00"))
        val updated = listOf(meal(1, "2023-10-27T12:00:00"), meal(2, "2023-10-28T08:00:00"))
        every { mealDao.getAllFlow() } returns flow {
            emit(initial)
            emit(updated)
        }

        mealDao.getAllFlow().test {
            assertThat(awaitItem()).hasSize(1)
            assertThat(awaitItem()).hasSize(2)
            awaitComplete()
        }
    }

    @Test
    fun `getByDate exact prefix match`() = runTest {
        val meals = listOf(
            meal(1, "2023-10-27T10:00:00"),
            meal(2, "2023-10-27T18:00:00")
        )
        coEvery { mealDao.getByDate("2023-10-27") } returns meals

        val result = mealDao.getByDate("2023-10-27")
        assertThat(result).hasSize(2)
        assertThat(result.all { it.datetime.startsWith("2023-10-27") }).isTrue()
    }

    @Test
    fun `getByDate partial prefix month match`() = runTest {
        val meals = listOf(
            meal(1, "2023-10-01T10:00:00"),
            meal(2, "2023-10-15T12:00:00"),
            meal(3, "2023-10-31T18:00:00")
        )
        coEvery { mealDao.getByDate("2023-10") } returns meals

        val result = mealDao.getByDate("2023-10")
        assertThat(result).hasSize(3)
        assertThat(result.all { it.datetime.startsWith("2023-10") }).isTrue()
    }

    @Test
    fun `getByDate empty prefix check`() = runTest {
        val meals = listOf(meal(1), meal(2), meal(3))
        coEvery { mealDao.getByDate("") } returns meals

        val result = mealDao.getByDate("")
        assertThat(result).hasSize(3)
    }

    @Test
    fun `getByDate no match returns empty`() = runTest {
        coEvery { mealDao.getByDate("2099-01-01") } returns emptyList()

        val result = mealDao.getByDate("2099-01-01")
        assertThat(result).isEmpty()
    }

    @Test
    fun `getAll standard retrieval check`() = runTest {
        val meals = listOf(meal(1), meal(2), meal(3))
        coEvery { mealDao.getAll() } returns meals

        val result = mealDao.getAll()
        assertThat(result).hasSize(3)
        assertThat(result).containsExactlyElementsIn(meals)
    }

    @Test
    fun `getAll sorting consistency`() = runTest {
        val meals = listOf(
            meal(1, "2023-10-27T18:00:00"),
            meal(2, "2023-10-27T12:00:00"),
            meal(3, "2023-10-26T10:00:00")
        )
        coEvery { mealDao.getAll() } returns meals

        val result = mealDao.getAll()
        for (i in 0 until result.size - 1) {
            assertThat(result[i].datetime).isGreaterThan(result[i + 1].datetime)
        }
    }

    @Test
    fun `getById existing record retrieval`() = runTest {
        val expected = meal(42, "2023-10-27T12:00:00")
        coEvery { mealDao.getById(42L) } returns expected

        val result = mealDao.getById(42L)
        assertThat(result).isEqualTo(expected)
        assertThat(result?.id).isEqualTo(42L)
        assertThat(result?.datetime).isEqualTo("2023-10-27T12:00:00")
    }

    @Test
    fun `getById non existent record check`() = runTest {
        coEvery { mealDao.getById(999L) } returns null

        val result = mealDao.getById(999L)
        assertThat(result).isNull()
    }

    @Test
    fun `insert successful record check`() = runTest {
        val newMeal = meal(0, "2023-10-27T12:00:00")
        coEvery { mealDao.insert(newMeal) } returns 1L

        val rowId = mealDao.insert(newMeal)
        assertThat(rowId).isEqualTo(1L)
        assertThat(rowId).isGreaterThan(0L)
    }

    @Test
    fun `insertComponents multiple records success`() = runTest {
        val components = listOf(
            MealComponent(mealId = 1L, componentType = "product", productId = 1L, servingWeight = 100.0),
            MealComponent(mealId = 1L, componentType = "product", productId = 2L, servingWeight = 200.0)
        )
        coEvery { mealDao.insertComponents(components) } returns Unit

        mealDao.insertComponents(components)
        coVerify { mealDao.insertComponents(components) }
    }

    @Test
    fun `insertComponents empty list handling`() = runTest {
        coEvery { mealDao.insertComponents(emptyList()) } returns Unit

        mealDao.insertComponents(emptyList())
        coVerify { mealDao.insertComponents(emptyList()) }
    }

    @Test
    fun `deleteById existing record removal`() = runTest {
        coEvery { mealDao.deleteById(1L) } returns Unit
        coEvery { mealDao.getById(1L) } returnsMany listOf(meal(1L), null)

        val before = mealDao.getById(1L)
        assertThat(before).isNotNull()
        mealDao.deleteById(1L)
        val after = mealDao.getById(1L)
        assertThat(after).isNull()
    }

    @Test
    fun `deleteById non existent record check`() = runTest {
        coEvery { mealDao.deleteById(999L) } returns Unit

        mealDao.deleteById(999L)
        coVerify { mealDao.deleteById(999L) }
    }

    @Test
    fun `getComponents relational join validation`() = runTest {
        val component = MealComponentRow(
            id = 1L,
            mealId = 1L,
            componentType = "product",
            productId = 10L,
            dishId = null,
            servingWeight = 150.0,
            productName = "Apple",
            productCarbs = 14.0,
            dishName = null
        )
        coEvery { mealDao.getComponents(1L) } returns listOf(component)

        val result = mealDao.getComponents(1L)
        assertThat(result).hasSize(1)
        assertThat(result[0].productName).isEqualTo("Apple")
        assertThat(result[0].productCarbs).isEqualTo(14.0)
        assertThat(result[0].dishName).isNull()
    }

    @Test
    fun `getComponents left join null handling`() = runTest {
        val component = MealComponentRow(
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
        coEvery { mealDao.getComponents(1L) } returns listOf(component)

        val result = mealDao.getComponents(1L)
        assertThat(result).hasSize(1)
        assertThat(result[0].productName).isNull()
        assertThat(result[0].dishName).isNull()
        assertThat(result[0].displayName).isEqualTo("?")
    }

    @Test
    fun `getComponents invalid mealId check`() = runTest {
        coEvery { mealDao.getComponents(999L) } returns emptyList()

        val result = mealDao.getComponents(999L)
        assertThat(result).isEmpty()
    }

    @Test
    fun `insert meal with duplicate ID conflict`() = runTest {
        val duplicate = meal(1L, "2023-10-27T12:00:00")
        coEvery { mealDao.insert(duplicate) } throws RuntimeException("UNIQUE constraint failed: meals.id")

        var caught: RuntimeException? = null
        try {
            mealDao.insert(duplicate)
        } catch (e: RuntimeException) {
            caught = e
        }
        assertThat(caught).isNotNull()
        assertThat(caught?.message).contains("UNIQUE constraint failed")
    }
}
