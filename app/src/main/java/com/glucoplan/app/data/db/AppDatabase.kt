package com.glucoplan.app.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glucoplan.app.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Миграция объявлена на верхнем уровне файла — это конкретный class, а не анонимный object,
// поэтому компилятор точно знает тип и не путается с порядком инициализации companion object.
private class Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS insulin_injections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                injectedAt TEXT NOT NULL,
                insulinType TEXT NOT NULL,
                dose REAL NOT NULL,
                injectionSite TEXT,
                isBasal INTEGER NOT NULL DEFAULT 0,
                mealId INTEGER,
                glucoseAtInjection REAL,
                carbsCovered REAL,
                notes TEXT,
                source TEXT NOT NULL DEFAULT 'manual',
                nsId TEXT
            )
        """)
    }
}

@Database(
    entities = [
        Product::class,
        Pan::class,
        Dish::class,
        DishComposition::class,
        Meal::class,
        MealComponent::class,
        AppSettingEntry::class,
        NsConfigEntry::class,
        NsSyncLog::class,
        InsulinInjection::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun panDao(): PanDao
    abstract fun dishDao(): DishDao
    abstract fun mealDao(): MealDao
    abstract fun settingsDao(): SettingsDao
    abstract fun injectionDao(): InsulinInjectionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "glucoplan.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("PRAGMA foreign_keys = ON")
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON")
                        }
                    })
                    .addMigrations(Migration1To2())
                    .build()
                INSTANCE = instance
                // Seed after INSTANCE is assigned so the callback cannot race
                CoroutineScope(Dispatchers.IO).launch {
                    seedDatabase(context, instance)
                }
                instance
            }
        }

        private suspend fun seedDatabase(context: Context, db: AppDatabase) {
            try {
                val json = context.assets.open("initial_products.json")
                    .bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<ProductSeed>>() {}.type
                val seeds: List<ProductSeed> = Gson().fromJson(json, type)
                val products = seeds.map {
                    Product(
                        name = it.name,
                        calories = it.calories,
                        proteins = it.proteins,
                        fats = it.fats,
                        carbs = it.carbs,
                        glycemicIndex = it.glycemic_index
                    )
                }
                db.productDao().insertAll(products)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val settingsDao = db.settingsDao()
            settingsDao.upsertAll(listOf(
                AppSettingEntry("carbs_per_xe", 10.0),
                AppSettingEntry("carb_coefficient", 0.5),
                AppSettingEntry("sensitivity", 8.0),
                AppSettingEntry("target_glucose", 6.0),
                AppSettingEntry("target_glucose_min", 3.9),
                AppSettingEntry("target_glucose_max", 10.0),
                AppSettingEntry("insulin_step", 1.0),
                AppSettingEntry("basal_dose", 4.0)
            ))
            settingsDao.upsertNsAll(listOf(
                NsConfigEntry("enabled", "1"),
                NsConfigEntry("url", "https://j89028552.nightscout-jino.ru"),
                NsConfigEntry("api_secret", "xj5kJyVv9n8F"),
                NsConfigEntry("insulin_type", "fiasp"),
                NsConfigEntry("basal_type", "toujeo"),
                NsConfigEntry("basal_time", "9:00")
            ))
        }
    }
}

private data class ProductSeed(
    val name: String,
    val calories: Double,
    val proteins: Double,
    val fats: Double,
    val carbs: Double,
    val glycemic_index: Double
)
