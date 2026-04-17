package com.glucoplan.app.data.db

import androidx.room.*
import com.glucoplan.app.domain.model.*
import kotlinx.coroutines.flow.Flow

// ─── Products ─────────────────────────────────────────────────────────────────

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllFlow(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :q || '%' ORDER BY name ASC")
    suspend fun search(q: String): List<Product>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(products: List<Product>)

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM products WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Product?

    // Удаляет дубликаты: оставляет запись с минимальным id
    @Query("DELETE FROM products WHERE id NOT IN (SELECT MIN(id) FROM products GROUP BY name)")
    suspend fun deleteDuplicates(): Int

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}

// ─── Pans ────────────────────────────────────────────────────────────────────

@Dao
interface PanDao {
    @Query("SELECT * FROM pans ORDER BY name ASC")
    fun getAllFlow(): Flow<List<Pan>>

    @Query("SELECT * FROM pans ORDER BY name ASC")
    suspend fun getAll(): List<Pan>

    @Query("SELECT * FROM pans WHERE id = :id")
    suspend fun getById(id: Long): Pan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pan: Pan): Long

    @Update
    suspend fun update(pan: Pan)

    @Query("DELETE FROM pans WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ─── Dishes ──────────────────────────────────────────────────────────────────

@Dao
interface DishDao {
    @Query("SELECT * FROM dishes ORDER BY name ASC")
    fun getAllFlow(): Flow<List<Dish>>

    @Query("SELECT * FROM dishes WHERE name LIKE '%' || :q || '%' ORDER BY name ASC")
    suspend fun search(q: String): List<Dish>

    @Query("SELECT * FROM dishes ORDER BY name ASC")
    suspend fun getAll(): List<Dish>

    @Query("SELECT * FROM dishes WHERE id = :id")
    suspend fun getById(id: Long): Dish?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dish: Dish): Long

    @Update
    suspend fun update(dish: Dish)

    @Query("DELETE FROM dishes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT dc.id, dc.product_id as productId, p.name as productName,
               dc.weight, p.carbs, p.calories, p.proteins, p.fats, p.glycemic_index as glycemicIndex
        FROM dish_composition dc
        JOIN products p ON dc.product_id = p.id
        WHERE dc.dish_id = :dishId
    """)
    suspend fun getIngredients(dishId: Long): List<DishIngredientRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComposition(comp: DishComposition): Long

    @Query("DELETE FROM dish_composition WHERE dish_id = :dishId")
    suspend fun deleteComposition(dishId: Long)
}

data class DishIngredientRow(
    val id: Long,
    val productId: Long,
    val productName: String,
    val weight: Double,
    val carbs: Double,
    val calories: Double,
    val proteins: Double,
    val fats: Double,
    val glycemicIndex: Double
) {
    fun toDomainModel() = DishIngredient(
        id = id, productId = productId, productName = productName,
        weight = weight, carbs = carbs, calories = calories,
        proteins = proteins, fats = fats, glycemicIndex = glycemicIndex
    )
}

// ─── Meals ───────────────────────────────────────────────────────────────────

@Dao
interface MealDao {
    @Query("SELECT * FROM meals ORDER BY datetime DESC")
    fun getAllFlow(): Flow<List<Meal>>

    @Query("SELECT * FROM meals WHERE datetime LIKE :datePrefix || '%' ORDER BY datetime DESC")
    suspend fun getByDate(datePrefix: String): List<Meal>

    @Query("SELECT * FROM meals ORDER BY datetime DESC")
    suspend fun getAll(): List<Meal>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getById(id: Long): Meal?

    @Insert
    suspend fun insert(meal: Meal): Long

    @Insert
    suspend fun insertComponents(components: List<MealComponent>)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT mc.*, 
               p.name as product_name, p.carbs as product_carbs,
               d.name as dish_name
        FROM meal_components mc
        LEFT JOIN products p ON mc.product_id = p.id
        LEFT JOIN dishes d ON mc.dish_id = d.id
        WHERE mc.meal_id = :mealId
    """)
    suspend fun getComponents(mealId: Long): List<MealComponentRow>
}

data class MealComponentRow(
    val id: Long,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    @ColumnInfo(name = "component_type") val componentType: String,
    @ColumnInfo(name = "product_id") val productId: Long?,
    @ColumnInfo(name = "dish_id") val dishId: Long?,
    @ColumnInfo(name = "serving_weight") val servingWeight: Double,
    @ColumnInfo(name = "product_name") val productName: String?,
    @ColumnInfo(name = "product_carbs") val productCarbs: Double?,
    @ColumnInfo(name = "dish_name") val dishName: String?
) {
    val displayName: String get() = productName ?: dishName ?: "?"
    val carbsInPortion: Double get() = (productCarbs ?: 0.0) * servingWeight / 100.0
}

// ─── Settings ────────────────────────────────────────────────────────────────

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettingEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AppSettingEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AppSettingEntry>)

    @Query("SELECT * FROM ns_config")
    suspend fun getNsConfig(): List<NsConfigEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNs(entry: NsConfigEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNsAll(entries: List<NsConfigEntry>)

    @Insert
    suspend fun insertSyncLog(log: NsSyncLog)
}

// ─── Insulin Injections ──────────────────────────────────────────────────────

@Dao
interface InsulinInjectionDao {
    @Query("SELECT * FROM insulin_injections ORDER BY injectedAt DESC")
    suspend fun getAll(): List<InsulinInjection>

    @Query("SELECT * FROM insulin_injections ORDER BY injectedAt DESC")
    fun getAllFlow(): Flow<List<InsulinInjection>>

    @Query("SELECT * FROM insulin_injections WHERE injectedAt >= :since ORDER BY injectedAt DESC")
    suspend fun getSince(since: String): List<InsulinInjection>

    @Query("SELECT * FROM insulin_injections WHERE injectedAt >= :since AND injectedAt <= :until ORDER BY injectedAt DESC")
    suspend fun getBetween(since: String, until: String): List<InsulinInjection>

    @Query("SELECT * FROM insulin_injections WHERE isBasal = 0 AND injectedAt >= :since ORDER BY injectedAt DESC")
    suspend fun getBolusSince(since: String): List<InsulinInjection>

    @Query("SELECT * FROM insulin_injections WHERE isBasal = 1 AND injectedAt >= :since ORDER BY injectedAt DESC")
    suspend fun getBasalSince(since: String): List<InsulinInjection>

    @Query("SELECT * FROM insulin_injections ORDER BY injectedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<InsulinInjection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(injection: InsulinInjection): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(injections: List<InsulinInjection>)

    @Query("DELETE FROM insulin_injections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM insulin_injections WHERE injectedAt < :before")
    suspend fun deleteOlderThan(before: String): Int

    @Query("SELECT COUNT(*) FROM insulin_injections")
    suspend fun count(): Int
}
