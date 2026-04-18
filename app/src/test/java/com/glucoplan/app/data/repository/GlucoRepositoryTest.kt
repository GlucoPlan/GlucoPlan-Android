package com.glucoplan.app.data.repository

import com.glucoplan.app.data.db.*
import com.glucoplan.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit-тесты для GlucoRepository с мокированными DAO.
 * Проверяет бизнес-логику репозитория: дедупликацию продуктов,
 * сборку настроек, управление инъекциями и т.д.
 */
@RunWith(JUnit4::class)
class GlucoRepositoryTest {

    private lateinit var репозиторий: GlucoRepository
    private lateinit var productDao: ProductDao
    private lateinit var panDao: PanDao
    private lateinit var dishDao: DishDao
    private lateinit var mealDao: MealDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var injectionDao: InsulinInjectionDao

    @Before
    fun подготовка() {
        productDao = mockk(relaxed = true)
        panDao = mockk(relaxed = true)
        dishDao = mockk(relaxed = true)
        mealDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        injectionDao = mockk(relaxed = true)

        coEvery { productDao.getAllFlow() } returns flowOf(emptyList())
        coEvery { panDao.getAllFlow() } returns flowOf(emptyList())
        coEvery { injectionDao.getAllFlow() } returns flowOf(emptyList())

        репозиторий = GlucoRepository(
            productDao, panDao, dishDao, mealDao, settingsDao, injectionDao
        )
    }

    // ─── Продукты ─────────────────────────────────────────────────────────────────

    @Test
    fun `saveProduct вставляет новый продукт если нет дубликата`() = runTest {
        val продукт = Product(name = "Яблоко", carbs = 14.0)
        coEvery { productDao.getByName("Яблоко") } returns null
        coEvery { productDao.insert(продукт) } returns 1L

        val id = репозиторий.saveProduct(продукт)

        assertThat(id).isEqualTo(1L)
        coVerify { productDao.insert(продукт) }
    }

    @Test
    fun `saveProduct возвращает id существующего при совпадении имени без вставки`() = runTest {
        // Репозиторий при id==0 ищет дубликат по имени.
        // Если нашёл — возвращает id существующего, insert и update не вызываются.
        val существующий = Product(id = 5, name = "Яблоко", carbs = 12.0)
        val новый = Product(name = "Яблоко", carbs = 14.0) // id == 0
        coEvery { productDao.getByName("Яблоко") } returns существующий

        val id = репозиторий.saveProduct(новый)

        assertThat(id).isEqualTo(5L)
        coVerify(exactly = 0) { productDao.insert(any()) }
        coVerify(exactly = 0) { productDao.update(any()) }
    }

    @Test
    fun `saveProduct вызывает update когда id не равен нулю`() = runTest {
        // При id != 0 репозиторий сразу вызывает update, без проверки дубликата
        val продукт = Product(id = 3, name = "Яблоко", carbs = 14.0)

        val id = репозиторий.saveProduct(продукт)

        assertThat(id).isEqualTo(3L)
        coVerify { productDao.update(продукт) }
        coVerify(exactly = 0) { productDao.insert(any()) }
    }

    @Test
    fun `searchProducts возвращает все при пустом запросе`() = runTest {
        val все = listOf(Product(name = "A"), Product(name = "B"))
        coEvery { productDao.getAll() } returns все

        val результат = репозиторий.searchProducts("")

        assertThat(результат).hasSize(2)
        coVerify { productDao.getAll() }
    }

    @Test
    fun `searchProducts ищет по запросу при непустой строке`() = runTest {
        coEvery { productDao.search("яблок") } returns listOf(Product(name = "Яблоко"))

        val результат = репозиторий.searchProducts("яблок")

        assertThat(результат).hasSize(1)
        coVerify { productDao.search("яблок") }
    }

    @Test
    fun `deleteProduct вызывает DAO с правильным id`() = runTest {
        репозиторий.deleteProduct(42)
        coVerify { productDao.deleteById(42) }
    }

    // ─── Кастрюли ─────────────────────────────────────────────────────────────────

    @Test
    fun `savePan вставляет новую кастрюлю если id равен 0`() = runTest {
        val кастрюля = Pan(id = 0, name = "Большая", weight = 500.0)
        coEvery { panDao.insert(кастрюля) } returns 1L

        val id = репозиторий.savePan(кастрюля)

        assertThat(id).isEqualTo(1L)
        coVerify { panDao.insert(кастрюля) }
    }

    @Test
    fun `savePan обновляет кастрюлю если id не равен 0`() = runTest {
        val кастрюля = Pan(id = 3, name = "Средняя", weight = 300.0)

        val id = репозиторий.savePan(кастрюля)

        assertThat(id).isEqualTo(3L)
        coVerify { panDao.update(кастрюля) }
    }

    @Test
    fun `getPans возвращает список из DAO`() = runTest {
        val кастрюли = listOf(Pan(name = "К1", weight = 300.0), Pan(name = "К2", weight = 500.0))
        coEvery { panDao.getAll() } returns кастрюли

        val результат = репозиторий.getPans()

        assertThat(результат).hasSize(2)
    }

    // ─── Блюда ────────────────────────────────────────────────────────────────────

    @Test
    fun `getDishWithIngredients возвращает null если блюдо не найдено`() = runTest {
        coEvery { dishDao.getById(99) } returns null

        val результат = репозиторий.getDishWithIngredients(99)

        assertThat(результат).isNull()
    }

    @Test
    fun `getDishWithIngredients возвращает блюдо с ингредиентами`() = runTest {
        val блюдо = Dish(id = 1, name = "Борщ")
        val ингредиенты = listOf(
            com.glucoplan.app.data.db.DishIngredientRow(
                id = 1, productId = 1, productName = "Свёкла", weight = 100.0,
                carbs = 10.0, calories = 50.0, proteins = 1.5, fats = 0.1, glycemicIndex = 30.0)
        )
        coEvery { dishDao.getById(1) } returns блюдо
        coEvery { dishDao.getIngredients(1) } returns ингредиенты

        val результат = репозиторий.getDishWithIngredients(1)

        assertThat(результат).isNotNull()
        assertThat(результат!!.dish.name).isEqualTo("Борщ")
        assertThat(результат.ingredients).hasSize(1)
    }

    @Test
    fun `deleteDish вызывает DAO с правильным id`() = runTest {
        репозиторий.deleteDish(7)
        coVerify { dishDao.deleteById(7) }
    }

    // ─── Приёмы пищи ─────────────────────────────────────────────────────────────

    @Test
    fun `getMealsByDate без даты возвращает все приёмы`() = runTest {
        val приёмы = listOf(Meal(datetime = "2024-03-20T12:00:00"))
        coEvery { mealDao.getAll() } returns приёмы

        val результат = репозиторий.getMealsByDate(null)

        assertThat(результат).hasSize(1)
        coVerify { mealDao.getAll() }
    }

    @Test
    fun `deleteMeal вызывает DAO с правильным id`() = runTest {
        репозиторий.deleteMeal(15)
        coVerify { mealDao.deleteById(15) }
    }

    // ─── Инъекции ─────────────────────────────────────────────────────────────────

    @Test
    fun `saveInjection вставляет новую инъекцию`() = runTest {
        val инъекция = InsulinInjection(
            injectedAt = "2024-03-20T12:00:00Z",
            insulinType = "fiasp",
            dose = 4.0,
            isBasal = false
        )
        coEvery { injectionDao.insert(инъекция) } returns 1L

        val id = репозиторий.saveInjection(инъекция)

        assertThat(id).isEqualTo(1L)
        coVerify { injectionDao.insert(инъекция) }
    }

    @Test
    fun `deleteOldInjections вызывает DAO с правильным порогом`() = runTest {
        coEvery { injectionDao.deleteOlderThan(any()) } returns 3

        val удалено = репозиторий.deleteOldInjections(7)

        assertThat(удалено).isEqualTo(3)
        coVerify { injectionDao.deleteOlderThan(any()) }
    }

    // ─── Настройки ────────────────────────────────────────────────────────────────

    @Test
    fun `getSettings возвращает настройки по умолчанию при пустой БД`() = runTest {
        coEvery { settingsDao.getAll() } returns emptyList()
        coEvery { settingsDao.getNsConfig() } returns emptyList()

        val настройки = репозиторий.getSettings()

        // При пустой БД должны вернуться разумные дефолты
        assertThat(настройки.carbsPerXe).isGreaterThan(0.0)
        assertThat(настройки.sensitivity).isGreaterThan(0.0)
    }

    @Test
    fun `saveSettings вызывает DAO для сохранения`() = runTest {
        val настройки = AppSettings(carbsPerXe = 12.0, sensitivity = 2.5)

        репозиторий.saveSettings(настройки)

        coVerify { settingsDao.upsertAll(any()) }
    }
}
