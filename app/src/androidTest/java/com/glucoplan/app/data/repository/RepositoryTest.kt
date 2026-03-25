package com.glucoplan.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.app.data.db.*
import com.glucoplan.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Instrumented tests for GlucoRepository.
 * Tests the integration between repository and database.
 */
@RunWith(AndroidJUnit4::class)
class GlucoRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GlucoRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = GlucoRepository(
            productDao = db.productDao(),
            panDao = db.panDao(),
            dishDao = db.dishDao(),
            mealDao = db.mealDao(),
            settingsDao = db.settingsDao(),
            injectionDao = db.injectionDao()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    // ─── Product Operations ──────────────────────────────────────────────────────

    @Test
    fun saveAndRetrieveProduct() = runTest {
        val product = Product(
            name = "Test Product",
            carbs = 25.0,
            proteins = 5.0,
            glycemicIndex = 65.0
        )

        val id = repository.saveProduct(product)
        val retrieved = repository.getProduct(id)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.name).isEqualTo("Test Product")
    }

    @Test
    fun updateExistingProduct() = runTest {
        val product = Product(name = "Original", carbs = 10.0)
        val id = repository.saveProduct(product)

        val updated = product.copy(id = id, name = "Updated", carbs = 20.0)
        repository.saveProduct(updated)

        val retrieved = repository.getProduct(id)
        assertThat(retrieved!!.name).isEqualTo("Updated")
        assertThat(retrieved.carbs).isEqualTo(20.0)
    }

    @Test
    fun deleteProduct() = runTest {
        val product = Product(name = "To Delete")
        val id = repository.saveProduct(product)

        repository.deleteProduct(id)

        assertThat(repository.getProduct(id)).isNull()
    }

    @Test
    fun searchProductsReturnsMatching() = runTest {
        repository.saveProduct(Product(name = "Apple"))
        repository.saveProduct(Product(name = "Banana"))
        repository.saveProduct(Product(name = "Apricot"))

        val results = repository.searchProducts("App")

        assertThat(results).hasSize(2)
    }

    @Test
    fun searchProductsEmptyReturnsAll() = runTest {
        repository.saveProduct(Product(name = "A"))
        repository.saveProduct(Product(name = "B"))

        val results = repository.searchProducts("")

        assertThat(results).hasSize(2)
    }

    @Test
    fun productsFlow() = runTest {
        repository.saveProduct(Product(name = "Flow Test"))

        val flow = repository.getProductsFlow().first()
        assertThat(flow).hasSize(1)
    }

    // ─── Pan Operations ───────────────────────────────────────────────────────────

    @Test
    fun saveAndRetrievePan() = runTest {
        val pan = Pan(name = "Test Pan", weight = 350.0, photoPath = "/path")

        val id = repository.savePan(pan)
        val retrieved = repository.getPan(id)

        assertThat(retrieved!!.name).isEqualTo("Test Pan")
        assertThat(retrieved.weight).isEqualTo(350.0)
    }

    @Test
    fun getAllPans() = runTest {
        repository.savePan(Pan(name = "Pan 1", weight = 100.0))
        repository.savePan(Pan(name = "Pan 2", weight = 200.0))

        val pans = repository.getPans()
        assertThat(pans).hasSize(2)
    }

    // ─── Dish Operations ───────────────────────────────────────────────────────────

    @Test
    fun saveDishWithIngredients() = runTest {
        val productId = repository.saveProduct(Product(
            name = "Flour",
            carbs = 75.0,
            proteins = 10.0
        ))

        val dishId = repository.saveDish(
            dish = Dish(name = "Bread"),
            ingredients = listOf(
                DishIngredient(
                    productId = productId,
                    productName = "Flour",
                    weight = 200.0,
                    carbs = 75.0,
                    calories = 350.0,
                    proteins = 10.0,
                    fats = 1.0,
                    glycemicIndex = 70.0
                )
            )
        )

        val dishWithIngredients = repository.getDishWithIngredients(dishId)

        assertThat(dishWithIngredients).isNotNull()
        assertThat(dishWithIngredients!!.dish.name).isEqualTo("Bread")
        assertThat(dishWithIngredients.ingredients).hasSize(1)
    }

    @Test
    fun dishWithIngredientsCalculatesTotals() = runTest {
        val productId = repository.saveProduct(Product(name = "P", carbs = 50.0))

        val dishId = repository.saveDish(
            dish = Dish(name = "Test Dish"),
            ingredients = listOf(
                DishIngredient(
                    productId = productId,
                    productName = "P",
                    weight = 100.0,
                    carbs = 50.0,
                    calories = 200.0,
                    proteins = 0.0,
                    fats = 0.0,
                    glycemicIndex = 50.0
                )
            )
        )

        val dishWithIngredients = repository.getDishWithIngredients(dishId)

        assertThat(dishWithIngredients!!.totalCarbs).isEqualTo(50.0) // 50g/100g * 100g
        assertThat(dishWithIngredients.totalWeight).isEqualTo(100.0)
    }

    // ─── Meal Operations ───────────────────────────────────────────────────────────

    @Test
    fun saveMealWithComponents() = runTest {
        val productId = repository.saveProduct(Product(name = "Bread", carbs = 50.0))

        val mealId = repository.saveMeal(
            meal = Meal(
                datetime = "2024-03-20T12:00:00",
                insulinDose = 3.5,
                totalCarbs = 25.0
            ),
            components = listOf(
                CalcComponent(
                    type = ComponentType.PRODUCT,
                    sourceId = productId,
                    name = "Bread",
                    servingWeight = 50.0,
                    carbsPer100g = 50.0,
                    caloriesPer100g = 250.0,
                    proteinsPer100g = 8.0,
                    fatsPer100g = 2.0
                )
            )
        )

        val components = repository.getMealComponents(mealId)
        assertThat(components).hasSize(1)
    }

    @Test
    fun getMealsByDate() = runTest {
        repository.saveMeal(
            Meal(datetime = "2024-03-20T08:00:00"),
            emptyList()
        )
        repository.saveMeal(
            Meal(datetime = "2024-03-20T12:00:00"),
            emptyList()
        )
        repository.saveMeal(
            Meal(datetime = "2024-03-21T08:00:00"),
            emptyList()
        )

        val march20 = repository.getMealsByDate(
            java.time.LocalDate.of(2024, 3, 20)
        )

        assertThat(march20).hasSize(2)
    }

    @Test
    fun deleteMeal() = runTest {
        val mealId = repository.saveMeal(
            Meal(datetime = "2024-03-20T12:00:00"),
            emptyList()
        )

        repository.deleteMeal(mealId)

        val meals = repository.getMealsByDate(null)
        assertThat(meals).isEmpty()
    }

    // ─── Settings Operations ──────────────────────────────────────────────────────

    @Test
    fun getSettingsReturnsDefaultsWhenEmpty() = runTest {
        val settings = repository.getSettings()

        assertThat(settings.carbsPerXe).isEqualTo(12.0)
        assertThat(settings.carbCoefficient).isEqualTo(1.5)
        assertThat(settings.sensitivity).isEqualTo(2.5)
    }

    @Test
    fun saveAndRetrieveSettings() = runTest {
        val settings = AppSettings(
            carbsPerXe = 15.0,
            carbCoefficient = 2.0,
            sensitivity = 3.0,
            targetGlucose = 5.5,
            insulinType = "fiasp",
            basalType = "tresiba",
            nsEnabled = true,
            nsUrl = "https://test.ns.com"
        )

        repository.saveSettings(settings)
        val retrieved = repository.getSettings()

        assertThat(retrieved.carbsPerXe).isEqualTo(15.0)
        assertThat(retrieved.carbCoefficient).isEqualTo(2.0)
        assertThat(retrieved.sensitivity).isEqualTo(3.0)
        assertThat(retrieved.insulinType).isEqualTo("fiasp")
        assertThat(retrieved.nsEnabled).isTrue()
        assertThat(retrieved.nsUrl).isEqualTo("https://test.ns.com")
    }

    // ─── Insulin Injection Operations ─────────────────────────────────────────────

    @Test
    fun saveInjection() = runTest {
        val injection = InsulinInjection(
            injectedAt = Instant.now().toString(),
            insulinType = "fiasp",
            dose = 4.0,
            isBasal = false
        )

        val id = repository.saveInjection(injection)

        assertThat(id).isGreaterThan(0)
    }

    @Test
    fun getRecentInjections() = runTest {
        val now = Instant.now()

        repository.saveInjection(InsulinInjection(
            injectedAt = now.minus(1, ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 3.0
        ))
        repository.saveInjection(InsulinInjection(
            injectedAt = now.minus(5, ChronoUnit.HOURS).toString(),
            insulinType = "novorapid",
            dose = 4.0
        ))
        repository.saveInjection(InsulinInjection(
            injectedAt = now.minus(10, ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 5.0
        ))

        val recent = repository.getRecentInjections(6)

        assertThat(recent).hasSize(2) // 1h and 5h ago
    }

    @Test
    fun getBolusInjectionsOnly() = runTest {
        val time = Instant.now().minus(1, ChronoUnit.HOURS).toString()

        repository.saveInjection(InsulinInjection(
            injectedAt = time,
            insulinType = "fiasp",
            dose = 4.0,
            isBasal = false
        ))
        repository.saveInjection(InsulinInjection(
            injectedAt = time,
            insulinType = "tresiba",
            dose = 15.0,
            isBasal = true
        ))

        val bolus = repository.getRecentBolusInjections(6)

        assertThat(bolus).hasSize(1)
        assertThat(bolus.first().isBasal).isFalse()
    }

    @Test
    fun calculateIob() = runTest {
        val now = Instant.now()

        repository.saveInjection(InsulinInjection(
            injectedAt = now.minus(30, ChronoUnit.MINUTES).toString(),
            insulinType = "fiasp",
            dose = 4.0
        ))

        val iob = repository.calculateIob(2)

        assertThat(iob.totalIob).isGreaterThan(0.0)
        assertThat(iob.details).hasSize(1)
    }

    @Test
    fun deleteOldInjections() = runTest {
        val now = Instant.now()

        repository.saveInjection(InsulinInjection(
            injectedAt = now.minus(10, ChronoUnit.DAYS).toString(),
            insulinType = "fiasp",
            dose = 3.0
        ))
        repository.saveInjection(InsulinInjection(
            injectedAt = now.minus(1, ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 4.0
        ))

        val deleted = repository.deleteOldInjections(7)

        assertThat(deleted).isEqualTo(1)
        assertThat(repository.getRecentInjections(24)).hasSize(1)
    }

    // ─── Full Workflow Tests ───────────────────────────────────────────────────────

    @Test
    fun fullMealWorkflowWithIOB() = runTest {
        // 1. Setup settings
        repository.saveSettings(AppSettings(
            carbsPerXe = 12.0,
            carbCoefficient = 1.5,
            sensitivity = 2.5,
            insulinType = "fiasp"
        ))

        // 2. Create product
        val productId = repository.saveProduct(Product(
            name = "Bread",
            carbs = 48.0,
            glycemicIndex = 70.0
        ))

        // 3. Create component
        val component = CalcComponent(
            type = ComponentType.PRODUCT,
            sourceId = productId,
            name = "Bread",
            servingWeight = 75.0,
            carbsPer100g = 48.0,
            caloriesPer100g = 240.0,
            proteinsPer100g = 8.0,
            fatsPer100g = 2.0,
            glycemicIndex = 70.0
        )

        // 4. Calculate dose
        val dose = (component.carbsInPortion / 12.0) * 1.5

        // 5. Save meal
        val mealId = repository.saveMeal(
            meal = Meal(
                datetime = Instant.now().toString(),
                insulinDose = dose,
                totalCarbs = component.carbsInPortion
            ),
            components = listOf(component)
        )

        // 6. Record injection
        repository.saveInjection(InsulinInjection(
            injectedAt = Instant.now().toString(),
            insulinType = "fiasp",
            dose = dose,
            mealId = mealId
        ))

        // 7. Verify IOB
        val iob = repository.calculateIob(1)
        assertThat(iob.totalIob).isGreaterThan(0.0)
        assertThat(iob.details.first().mealId).isEqualTo(mealId)
    }

    @Test
    fun complexMealScenario() = runTest {
        // Setup multiple products
        val bread = repository.saveProduct(Product(name = "Bread", carbs = 48.0, glycemicIndex = 70.0))
        val cheese = repository.saveProduct(Product(name = "Cheese", carbs = 1.0, proteins = 25.0, fats = 33.0))
        val apple = repository.saveProduct(Product(name = "Apple", carbs = 14.0, glycemicIndex = 38.0))

        // Create dish
        val sandwichId = repository.saveDish(
            dish = Dish(name = "Sandwich"),
            ingredients = listOf(
                DishIngredient(bread,  60, "Bread",48.0, 240.0, 8.0, 2.0, 70.0, 33.0),
                DishIngredient(cheese, 30,"Cheese",  1.0, 350.0, 25.0, 33.0, 0.0, 33.0)
            )
        )

        // Get dish with ingredients
        val sandwich = repository.getDishWithIngredients(sandwichId)
        assertThat(sandwich!!.ingredients).hasSize(2)

        // Create meal with mixed components
        val components = listOf(
            CalcComponent.fromProduct(repository.getProduct(bread)!!, 60.0),
            CalcComponent.fromDish(sandwich, 90.0)
        )

        val mealId = repository.saveMeal(
            meal = Meal(
                datetime = Instant.now().toString(),
                insulinDose = 5.0,
                totalCarbs = components.sumOf { it.carbsInPortion }
            ),
            components = components
        )

        // Verify
        val savedComponents = repository.getMealComponents(mealId)
        assertThat(savedComponents).hasSize(2)
    }
}
