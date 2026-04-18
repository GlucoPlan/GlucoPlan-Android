package com.glucoplan.app.data.repository

import com.glucoplan.app.data.db.*
import com.glucoplan.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlucoRepository @Inject constructor(
    private val productDao: ProductDao,
    private val panDao: PanDao,
    private val dishDao: DishDao,
    private val mealDao: MealDao,
    private val settingsDao: SettingsDao,
    private val injectionDao: InsulinInjectionDao
) {
    // ─── Products ────────────────────────────────────────────────────────────
    fun getProductsFlow(): Flow<List<Product>> = productDao.getAllFlow()
    suspend fun searchProducts(q: String) = if (q.isBlank()) productDao.getAll() else productDao.search(q)
    suspend fun getProduct(id: Long) = productDao.getById(id)
    suspend fun saveProduct(p: Product): Long {
        if (p.id == 0L) {
            // Проверяем дубликат по имени перед вставкой
            val existing = productDao.getByName(p.name.trim())
            if (existing != null) return existing.id  // возвращаем id существующего
            return productDao.insert(p.copy(name = p.name.trim()))
        } else {
            productDao.update(p)
            return p.id
        }
    }
    suspend fun deleteProduct(id: Long) = productDao.deleteById(id)

    // ─── Pans ────────────────────────────────────────────────────────────────
    fun getPansFlow(): Flow<List<Pan>> = panDao.getAllFlow()
    suspend fun getPans() = panDao.getAll()
    suspend fun getPan(id: Long) = panDao.getById(id)
    suspend fun savePan(p: Pan) = if (p.id == 0L) panDao.insert(p) else { panDao.update(p); p.id }
    suspend fun deletePan(id: Long) = panDao.deleteById(id)

    // ─── Dishes ──────────────────────────────────────────────────────────────
    fun getDishesFlow(): Flow<List<Dish>> = dishDao.getAllFlow()
    suspend fun searchDishes(q: String) = if (q.isBlank()) dishDao.getAll() else dishDao.search(q)
    suspend fun getDish(id: Long) = dishDao.getById(id)

    suspend fun getDishWithIngredients(dishId: Long): DishWithIngredients? {
        val dish = dishDao.getById(dishId) ?: return null
        val ingredients = dishDao.getIngredients(dishId).map { it.toDomainModel() }
        val pan = dish.defaultPanId?.let { panDao.getById(it) }
        return DishWithIngredients(dish, ingredients, pan)
    }

    suspend fun searchDishesWithIngredients(q: String): List<DishWithIngredients> {
        val dishes = if (q.isBlank()) dishDao.getAll() else dishDao.search(q)
        return dishes.map { dish ->
            val ingredients = dishDao.getIngredients(dish.id).map { it.toDomainModel() }
            val pan = dish.defaultPanId?.let { panDao.getById(it) }
            DishWithIngredients(dish, ingredients, pan)
        }
    }

    suspend fun saveDish(dish: Dish, ingredients: List<DishIngredient>): Long {
        val id = if (dish.id == 0L) dishDao.insert(dish) else {
            dishDao.update(dish)
            dish.id
        }
        dishDao.deleteComposition(id)
        ingredients.forEach { ing ->
            dishDao.insertComposition(DishComposition(dishId = id, productId = ing.productId, weight = ing.weight))
        }
        return id
    }

    suspend fun deleteDish(id: Long) = dishDao.deleteById(id)


    suspend fun getMealsByDate(date: LocalDate?): List<Meal> {
        return if (date != null) {
            val prefix = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            mealDao.getByDate(prefix)
        } else {
            mealDao.getAll()
        }
    }

    suspend fun saveMeal(meal: Meal, components: List<CalcComponent>): Long {
        val mealId = mealDao.insert(meal)
        val compEntities = components.map { c ->
            MealComponent(
                mealId = mealId,
                componentType = if (c.type == ComponentType.PRODUCT) "product" else "dish",
                productId = if (c.type == ComponentType.PRODUCT) c.sourceId else null,
                dishId = if (c.type == ComponentType.DISH) c.sourceId else null,
                servingWeight = c.servingWeight
            )
        }
        mealDao.insertComponents(compEntities)
        return mealId
    }

    suspend fun getMealComponents(mealId: Long): List<MealComponentRow> =
        mealDao.getComponents(mealId)

    suspend fun deleteMeal(id: Long) = mealDao.deleteById(id)

    // ─── Settings ────────────────────────────────────────────────────────────
    suspend fun getSettings(): AppSettings {
        val map = settingsDao.getAll().associate { it.key to it.value }
        val ns = settingsDao.getNsConfig().associate { it.key to it.value }
        return AppSettings(
            carbsPerXe = map["carbs_per_xe"] ?: 12.0,
            carbCoefficient = map["carb_coefficient"] ?: 1.5,
            sensitivity = map["sensitivity"] ?: 2.5,
            targetGlucose = map["target_glucose"] ?: 6.0,
            targetGlucoseMin = map["target_glucose_min"] ?: 3.9,
            targetGlucoseMax = map["target_glucose_max"] ?: 10.0,
            insulinStep = map["insulin_step"] ?: 0.5,
            basalDose = map["basal_dose"] ?: 0.0,
            insulinType = ns["insulin_type"] ?: "novorapid",
            basalType = ns["basal_type"] ?: "none",
            basalTime = ns["basal_time"] ?: "22:00",
            nsEnabled = ns["enabled"] == "1",
            nsUrl = ns["url"] ?: "",
            nsApiSecret = ns["api_secret"] ?: ""
        )
    }

    suspend fun saveSettings(s: AppSettings) {
        settingsDao.upsertAll(listOf(
            AppSettingEntry("carbs_per_xe", s.carbsPerXe),
            AppSettingEntry("carb_coefficient", s.carbCoefficient),
            AppSettingEntry("sensitivity", s.sensitivity),
            AppSettingEntry("target_glucose", s.targetGlucose),
            AppSettingEntry("target_glucose_min", s.targetGlucoseMin),
            AppSettingEntry("target_glucose_max", s.targetGlucoseMax),
            AppSettingEntry("insulin_step", s.insulinStep),
            AppSettingEntry("basal_dose", s.basalDose)
        ))
        settingsDao.upsertNsAll(listOf(
            NsConfigEntry("insulin_type", s.insulinType),
            NsConfigEntry("basal_type", s.basalType),
            NsConfigEntry("basal_time", s.basalTime),
            NsConfigEntry("enabled", if (s.nsEnabled) "1" else "0"),
            NsConfigEntry("url", s.nsUrl),
            NsConfigEntry("api_secret", s.nsApiSecret)
        ))
    }

    suspend fun logNsSync(mealId: Long?, status: String, message: String) {
        settingsDao.insertSyncLog(NsSyncLog(
            mealId = mealId,
            syncedAt = java.time.Instant.now().toString(),
            status = status,
            message = message
        ))
    }

    // ─── Insulin Injections (IOB) ─────────────────────────────────────────────

    /**
     * Get injections from the last N hours
     */
    suspend fun getRecentInjections(hours: Int = 6): List<InsulinInjection> {
        val since = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS)
        return injectionDao.getSince(since.toString())
    }

    /**
     * Get bolus injections from the last N hours
     */
    suspend fun getRecentBolusInjections(hours: Int = 6): List<InsulinInjection> {
        val since = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS)
        return injectionDao.getBolusSince(since.toString())
    }

    /**
     * Get basal injections from the last N hours
     */
    suspend fun getRecentBasalInjections(hours: Int = 24): List<InsulinInjection> {
        val since = Instant.now().minus(hours.toLong(), ChronoUnit.HOURS)
        return injectionDao.getBasalSince(since.toString())
    }

    /**
     * Save an injection
     */
    suspend fun saveInjection(injection: InsulinInjection): Long {
        Timber.d("Saving injection: ${injection.insulinType} ${injection.dose}U at ${injection.injectedAt}")
        return injectionDao.insert(injection)
    }

    /**
     * Save multiple injections (e.g., from Nightscout sync)
     */
    suspend fun saveInjections(injections: List<InsulinInjection>) {
        Timber.d("Saving ${injections.size} injections")
        injectionDao.insertAll(injections)
    }

    /**
     * Delete old injections (cleanup)
     */
    suspend fun deleteOldInjections(daysOld: Int = 7): Int {
        val before = Instant.now().minus(daysOld.toLong(), ChronoUnit.DAYS)
        val count = injectionDao.deleteOlderThan(before.toString())
        Timber.d("Deleted $count old injections")
        return count
    }

    /**
     * Calculate current IOB
     */
    suspend fun calculateIob(hours: Int = 6): IobState {
        val injections = getRecentInjections(hours)
        return IobCalculator.calculate(injections)
    }

    /**
     * Calculate IOB for bolus insulins only
     */
    suspend fun calculateBolusIob(hours: Int = 6): IobState {
        val injections = getRecentBolusInjections(hours)
        return IobCalculator.calculate(injections)
    }
}
