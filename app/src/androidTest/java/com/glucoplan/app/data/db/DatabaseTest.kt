package com.glucoplan.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucoplan.app.domain.model.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for Room Database and DAOs.
 * Tests run on an Android device/emulator with in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var productDao: ProductDao
    private lateinit var panDao: PanDao
    private lateinit var dishDao: DishDao
    private lateinit var mealDao: MealDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var injectionDao: InsulinInjectionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries()
         .fallbackToDestructiveMigration()
         .build()

        productDao = db.productDao()
        panDao = db.panDao()
        dishDao = db.dishDao()
        mealDao = db.mealDao()
        settingsDao = db.settingsDao()
        injectionDao = db.injectionDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ─── ProductDao Tests ────────────────────────────────────────────────────────

    @Test
    fun insertAndReadProduct() = runTest {
        val product = Product(
            name = "Test Bread",
            calories = 250.0,
            proteins = 8.0,
            fats = 2.0,
            carbs = 50.0,
            glycemicIndex = 70.0
        )

        val id = productDao.insert(product)
        val retrieved = productDao.getById(id)

        assertThat(retrieved).isNotNull()
        assertThat(retrieved!!.name).isEqualTo("Test Bread")
        assertThat(retrieved.carbs).isEqualTo(50.0)
        assertThat(retrieved.glycemicIndex).isEqualTo(70.0)
    }

    @Test
    fun updateProduct() = runTest {
        val product = Product(name = "Original", carbs = 30.0)
        val id = productDao.insert(product)

        val updated = product.copy(id = id, name = "Updated", carbs = 40.0)
        productDao.update(updated)

        val retrieved = productDao.getById(id)
        assertThat(retrieved!!.name).isEqualTo("Updated")
        assertThat(retrieved.carbs).isEqualTo(40.0)
    }

    @Test
    fun deleteProduct() = runTest {
        val product = Product(name = "To Delete")
        val id = productDao.insert(product)

        productDao.deleteById(id)

        val retrieved = productDao.getById(id)
        assertThat(retrieved).isNull()
    }

    @Test
    fun searchProducts() = runTest {
        // Use unique prefix "TSTXYZ" not present in any seed data
        productDao.insert(Product(name = "TSTXYZ_Apple"))
        productDao.insert(Product(name = "TSTXYZ_Banana"))
        productDao.insert(Product(name = "TSTXYZ_Apricot"))

        val results = productDao.search("TSTXYZ")
        assertThat(results).hasSize(3)
        assertThat(results.any { it.name == "TSTXYZ_Apple" }).isTrue()
    }

    @Test
    fun getAllProducts() = runTest {
        productDao.insert(Product(name = "A"))
        productDao.insert(Product(name = "B"))
        productDao.insert(Product(name = "C"))

        val all = productDao.getAll()
        assertThat(all).hasSize(3)
    }

    @Test
    fun productFlow() = runTest {
        productDao.insert(Product(name = "Flow Test"))

        val flow = productDao.getAllFlow().first()
        assertThat(flow).hasSize(1)
    }

    // ─── PanDao Tests ─────────────────────────────────────────────────────────────

    @Test
    fun insertAndReadPan() = runTest {
        val pan = Pan(
            name = "Frying Pan",
            weight = 500.0,
            photoPath = "/path/to/photo.jpg"
        )

        val id = panDao.insert(pan)
        val retrieved = panDao.getById(id)

        assertThat(retrieved!!.name).isEqualTo("Frying Pan")
        assertThat(retrieved.weight).isEqualTo(500.0)
        assertThat(retrieved.photoPath).isEqualTo("/path/to/photo.jpg")
    }

    @Test
    fun getAllPans() = runTest {
        panDao.insert(Pan(name = "Pan 1", weight = 300.0))
        panDao.insert(Pan(name = "Pan 2", weight = 400.0))

        val all = panDao.getAll()
        assertThat(all).hasSize(2)
    }

    // ─── DishDao Tests ────────────────────────────────────────────────────────────

    @Test
    fun insertDishWithComposition() = runTest {
        // Create products first
        val productId1 = productDao.insert(Product(name = "Ingredient 1", carbs = 10.0))
        val productId2 = productDao.insert(Product(name = "Ingredient 2", carbs = 20.0))

        // Create dish
        val dishId = dishDao.insert(Dish(name = "Test Dish"))

        // Add composition
        dishDao.insertComposition(DishComposition(dishId = dishId, productId = productId1, weight = 100.0))
        dishDao.insertComposition(DishComposition(dishId = dishId, productId = productId2, weight = 150.0))

        // Get ingredients
        val ingredients = dishDao.getIngredients(dishId)
        assertThat(ingredients).hasSize(2)
    }

    @Test
    fun getIngredientsWithNutrition() = runTest {
        val productId = productDao.insert(Product(
            name = "Test Ingredient",
            carbs = 25.0,
            proteins = 5.0,
            fats = 3.0
        ))

        val dishId = dishDao.insert(Dish(name = "Dish"))
        dishDao.insertComposition(DishComposition(dishId = dishId, productId = productId, weight = 200.0))

        val ingredients = dishDao.getIngredients(dishId)

        assertThat(ingredients).hasSize(1)
        val ingredient = ingredients.first()
        assertThat(ingredient.productName).isEqualTo("Test Ingredient")
        assertThat(ingredient.weight).isEqualTo(200.0)
        // Carbs: 25g/100g * 200g = 50g
        assertThat(ingredient.carbs).isEqualTo(25.0)
    }

    @Test
    fun deleteDishCascadesComposition() = runTest {
        val productId = productDao.insert(Product(name = "P"))
        val dishId = dishDao.insert(Dish(name = "D"))
        dishDao.insertComposition(DishComposition(dishId = dishId, productId = productId, weight = 100.0))

        dishDao.deleteById(dishId)

        val ingredients = dishDao.getIngredients(dishId)
        assertThat(ingredients).isEmpty()
    }

    // ─── MealDao Tests ────────────────────────────────────────────────────────────

    @Test
    fun insertAndReadMeal() = runTest {
        val meal = Meal(
            datetime = "2024-03-20T12:00:00",
            insulinDose = 4.5,
            glucose = 7.2,
            notes = "Test meal",
            totalCarbs = 45.0,
            totalCalories = 350.0,
            totalProteins = 15.0,
            totalFats = 10.0,
            breadUnits = 3.75
        )

        val id = mealDao.insert(meal)
        val retrieved = mealDao.getById(id)

        assertThat(retrieved!!.insulinDose).isEqualTo(4.5)
        assertThat(retrieved.totalCarbs).isEqualTo(45.0)
        assertThat(retrieved.notes).isEqualTo("Test meal")
    }

    @Test
    fun getMealsByDate() = runTest {
        mealDao.insert(Meal(datetime = "2024-03-20T08:00:00", totalCarbs = 30.0))
        mealDao.insert(Meal(datetime = "2024-03-20T12:00:00", totalCarbs = 50.0))
        mealDao.insert(Meal(datetime = "2024-03-21T08:00:00", totalCarbs = 40.0))

        val march20 = mealDao.getByDate("2024-03-20")

        assertThat(march20).hasSize(2)
    }

    @Test
    fun mealWithComponents() = runTest {
        val productId = productDao.insert(Product(name = "Bread", carbs = 50.0))
        val dishId = dishDao.insert(Dish(name = "Sandwich"))

        val mealId = mealDao.insert(Meal(datetime = "2024-03-20T12:00:00"))

        mealDao.insertComponents(listOf(
            MealComponent(
                mealId = mealId,
                componentType = "product",
                productId = productId,
                servingWeight = 60.0
            ),
            MealComponent(
                mealId = mealId,
                componentType = "dish",
                dishId = dishId,
                servingWeight = 150.0
            )
        ))

        val components = mealDao.getComponents(mealId)
        assertThat(components).hasSize(2)
    }

    // ─── SettingsDao Tests ────────────────────────────────────────────────────────

    @Test
    fun saveAndGetSettings() = runTest {
        settingsDao.upsertAll(listOf(
            AppSettingEntry("carbs_per_xe", 12.0),
            AppSettingEntry("carb_coefficient", 1.5),
            AppSettingEntry("sensitivity", 2.5),
            AppSettingEntry("target_glucose", 6.0)
        ))

        val settings = settingsDao.getAll()
        assertThat(settings).hasSize(4)

        val map = settings.associate { it.key to it.value }
        assertThat(map["carbs_per_xe"]).isEqualTo(12.0)
        assertThat(map["sensitivity"]).isEqualTo(2.5)
    }

    @Test
    fun updateSetting() = runTest {
        settingsDao.upsert(AppSettingEntry("test_key", 10.0))
        settingsDao.upsert(AppSettingEntry("test_key", 20.0))

        val settings = settingsDao.getAll()
        assertThat(settings).hasSize(1)
        assertThat(settings.first().value).isEqualTo(20.0)
    }

    @Test
    fun nsConfigCrud() = runTest {
        settingsDao.upsertNsAll(listOf(
            NsConfigEntry("url", "https://test.ns.com"),
            NsConfigEntry("enabled", "1")
        ))

        val config = settingsDao.getNsConfig()
        assertThat(config).hasSize(2)

        val map = config.associate { it.key to it.value }
        assertThat(map["url"]).isEqualTo("https://test.ns.com")
    }

    // ─── InsulinInjectionDao Tests ────────────────────────────────────────────────

    @Test
    fun insertAndReadInjection() = runTest {
        val injection = InsulinInjection(
            injectedAt = "2024-03-20T12:00:00Z",
            insulinType = "fiasp",
            dose = 4.5,
            isBasal = false
        )

        val id = injectionDao.insert(injection)
        val all = injectionDao.getAll()

        assertThat(all).hasSize(1)
        assertThat(all.first().insulinType).isEqualTo("fiasp")
        assertThat(all.first().dose).isEqualTo(4.5)
    }

    @Test
    fun getInjectionsSince() = runTest {
        val now = java.time.Instant.now()

        injectionDao.insert(InsulinInjection(
            injectedAt = now.minus(1, java.time.temporal.ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 3.0
        ))
        injectionDao.insert(InsulinInjection(
            injectedAt = now.minus(5, java.time.temporal.ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 4.0
        ))
        injectionDao.insert(InsulinInjection(
            injectedAt = now.minus(10, java.time.temporal.ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 5.0
        ))

        // getSince("3h ago") includes only injections from the last 3 hours
        // 1h ago: YES, 5h ago: NO (older than cutoff), 10h ago: NO
        val recent = injectionDao.getSince(now.minus(3, java.time.temporal.ChronoUnit.HOURS).toString())

        assertThat(recent).hasSize(1) // only 1h ago
    }

    @Test
    fun getBolusVsBasalInjections() = runTest {
        val now = java.time.Instant.now()
        val time = now.minus(1, java.time.temporal.ChronoUnit.HOURS).toString()

        injectionDao.insert(InsulinInjection(
            injectedAt = time,
            insulinType = "fiasp",
            dose = 4.0,
            isBasal = false
        ))
        injectionDao.insert(InsulinInjection(
            injectedAt = time,
            insulinType = "tresiba",
            dose = 15.0,
            isBasal = true
        ))

        val bolus = injectionDao.getBolusSince(now.minus(2, java.time.temporal.ChronoUnit.HOURS).toString())
        val basal = injectionDao.getBasalSince(now.minus(2, java.time.temporal.ChronoUnit.HOURS).toString())

        assertThat(bolus).hasSize(1)
        assertThat(basal).hasSize(1)
        assertThat(bolus.first().isBasal).isFalse()
        assertThat(basal.first().isBasal).isTrue()
    }

    @Test
    fun deleteOldInjections() = runTest {
        val now = java.time.Instant.now()

        injectionDao.insert(InsulinInjection(
            injectedAt = now.minus(2, java.time.temporal.ChronoUnit.DAYS).toString(),
            insulinType = "fiasp",
            dose = 3.0
        ))
        injectionDao.insert(InsulinInjection(
            injectedAt = now.minus(1, java.time.temporal.ChronoUnit.HOURS).toString(),
            insulinType = "fiasp",
            dose = 4.0
        ))

        val deleted = injectionDao.deleteOlderThan(now.minus(1, java.time.temporal.ChronoUnit.DAYS).toString())

        assertThat(deleted).isEqualTo(1)
        assertThat(injectionDao.getAll()).hasSize(1)
    }

    @Test
    fun injectionFlow() = runTest {
        injectionDao.insert(InsulinInjection(
            injectedAt = java.time.Instant.now().toString(),
            insulinType = "fiasp",
            dose = 2.0
        ))

        val flow = injectionDao.getAllFlow().first()
        assertThat(flow).hasSize(1)
    }

    // ─── Integration Tests ────────────────────────────────────────────────────────

    @Test
    fun fullMealWorkflow() = runTest {
        // 1. Create products
        val breadId = productDao.insert(Product(
            name = "Bread",
            carbs = 48.0,
            proteins = 8.0,
            glycemicIndex = 70.0
        ))
        val cheeseId = productDao.insert(Product(
            name = "Cheese",
            carbs = 1.0,
            proteins = 25.0,
            fats = 33.0
        ))

        // 2. Create dish
        val sandwichId = dishDao.insert(Dish(name = "Sandwich"))
        dishDao.insertComposition(DishComposition(dishId = sandwichId, productId = breadId, weight = 60.0))
        dishDao.insertComposition(DishComposition(dishId = sandwichId, productId = cheeseId, weight = 30.0))

        // 3. Get dish with ingredients
        val ingredients = dishDao.getIngredients(sandwichId)
        assertThat(ingredients).hasSize(2)

        // 4. Create meal
        val mealId = mealDao.insert(Meal(
            datetime = "2024-03-20T12:00:00",
            insulinDose = 3.0,
            totalCarbs = 28.8 // 48 * 0.6 + 1 * 0.3
        ))

        // 5. Add meal components
        mealDao.insertComponents(listOf(
            MealComponent(mealId = mealId, componentType = "dish", dishId = sandwichId, servingWeight = 90.0)
        ))

        // Verify
        val savedMeal = mealDao.getById(mealId)
        assertThat(savedMeal!!.insulinDose).isEqualTo(3.0)

        val mealComponents = mealDao.getComponents(mealId)
        assertThat(mealComponents).hasSize(1)
    }

    @Test
    fun settingsToRepositoryIntegration() = runTest {
        // Setup all settings
        settingsDao.upsertAll(listOf(
            AppSettingEntry("carbs_per_xe", 12.0),
            AppSettingEntry("carb_coefficient", 1.5),
            AppSettingEntry("sensitivity", 2.5),
            AppSettingEntry("target_glucose", 6.0),
            AppSettingEntry("target_glucose_min", 3.9),
            AppSettingEntry("target_glucose_max", 10.0),
            AppSettingEntry("insulin_step", 0.5),
            AppSettingEntry("basal_dose", 18.0)
        ))

        settingsDao.upsertNsAll(listOf(
            NsConfigEntry("insulin_type", "fiasp"),
            NsConfigEntry("basal_type", "tresiba"),
            NsConfigEntry("enabled", "1"),
            NsConfigEntry("url", "https://my.ns.site")
        ))

        // Verify all settings are retrievable
        val appSettings = settingsDao.getAll()
        val nsSettings = settingsDao.getNsConfig()

        assertThat(appSettings).hasSize(8)
        assertThat(nsSettings).hasSize(4)
    }
}
