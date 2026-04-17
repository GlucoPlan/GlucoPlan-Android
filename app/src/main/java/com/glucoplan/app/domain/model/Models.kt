package com.glucoplan.app.domain.model

import androidx.room.*

// ─── Product ─────────────────────────────────────────────────────────────────

@Entity(tableName = "products", indices = [Index(value = ["name"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val calories: Double = 0.0,
    val proteins: Double = 0.0,
    val fats: Double = 0.0,
    val carbs: Double = 0.0,
    @ColumnInfo(name = "glycemic_index") val glycemicIndex: Double = 50.0
)

// ─── Pan ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "pans")
data class Pan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weight: Double,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null
)

// ─── Dish ────────────────────────────────────────────────────────────────────

@Entity(tableName = "dishes")
data class Dish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "default_pan_id") val defaultPanId: Long? = null,
    @ColumnInfo(name = "default_cooked_weight") val defaultCookedWeight: Double = 0.0
)

@Entity(
    tableName = "dish_composition",
    foreignKeys = [
        ForeignKey(entity = Dish::class, parentColumns = ["id"], childColumns = ["dish_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["product_id"])
    ],
    indices = [Index("dish_id"), Index("product_id")]
)
data class DishComposition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dish_id") val dishId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val weight: Double
)

data class DishIngredient(
    val id: Long = 0,
    val productId: Long,
    val productName: String,
    val weight: Double,
    val carbs: Double,
    val calories: Double,
    val proteins: Double,
    val fats: Double,
    val glycemicIndex: Double
) {
    val carbsInPortion: Double get() = carbs * weight / 100.0
    val caloriesInPortion: Double get() = calories * weight / 100.0
}

data class DishWithIngredients(
    val dish: Dish,
    val ingredients: List<DishIngredient> = emptyList(),
    val pan: Pan? = null
) {
    val totalCarbs: Double get() = ingredients.sumOf { it.carbsInPortion }
    val totalWeight: Double get() = ingredients.sumOf { it.weight }
    val carbsPer100g: Double get() = if (totalWeight > 0) totalCarbs / totalWeight * 100.0 else 0.0
    val caloriesPer100g: Double get() {
        val totalCal = ingredients.sumOf { it.caloriesInPortion }
        return if (totalWeight > 0) totalCal / totalWeight * 100.0 else 0.0
    }
}

// ─── Meal ────────────────────────────────────────────────────────────────────

@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val datetime: String,
    @ColumnInfo(name = "insulin_dose") val insulinDose: Double = 0.0,
    val glucose: Double = 0.0,
    val notes: String = "",
    @ColumnInfo(name = "total_carbs") val totalCarbs: Double = 0.0,
    @ColumnInfo(name = "total_calories") val totalCalories: Double = 0.0,
    @ColumnInfo(name = "total_proteins") val totalProteins: Double = 0.0,
    @ColumnInfo(name = "total_fats") val totalFats: Double = 0.0,
    @ColumnInfo(name = "bread_units") val breadUnits: Double = 0.0
)

@Entity(
    tableName = "meal_components",
    foreignKeys = [
        ForeignKey(entity = Meal::class, parentColumns = ["id"], childColumns = ["meal_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("meal_id")]
)
data class MealComponent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long,
    @ColumnInfo(name = "component_type") val componentType: String, // "product" or "dish"
    @ColumnInfo(name = "product_id") val productId: Long? = null,
    @ColumnInfo(name = "dish_id") val dishId: Long? = null,
    @ColumnInfo(name = "serving_weight") val servingWeight: Double
)

// ─── Settings ────────────────────────────────────────────────────────────────

@Entity(tableName = "app_settings")
data class AppSettingEntry(
    @PrimaryKey val key: String,
    val value: Double
)

@Entity(tableName = "ns_config")
data class NsConfigEntry(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "ns_sync_log")
data class NsSyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meal_id") val mealId: Long? = null,
    @ColumnInfo(name = "synced_at") val syncedAt: String,
    val status: String,
    val message: String
)

// ─── Calculator component (UI model, not in DB) ───────────────────────────────

enum class ComponentType { PRODUCT, DISH }

data class CalcComponent(
    val key: String = java.util.UUID.randomUUID().toString(),
    val type: ComponentType,
    val sourceId: Long,
    val name: String,
    var servingWeight: Double,
    val carbsPer100g: Double,
    val caloriesPer100g: Double,
    val proteinsPer100g: Double,
    val fatsPer100g: Double,
    val glycemicIndex: Double = 50.0,
    var includedInAdjustment: Boolean = true
) {
    val carbsInPortion: Double get() = carbsPer100g * servingWeight / 100.0
    val caloriesInPortion: Double get() = caloriesPer100g * servingWeight / 100.0
    val proteinsInPortion: Double get() = proteinsPer100g * servingWeight / 100.0
    val fatsInPortion: Double get() = fatsPer100g * servingWeight / 100.0
    val glycemicLoad: Double get() = glycemicIndex * carbsInPortion / 100.0

    fun withWeight(servingWeight: Double) = CalcComponent(
        key = key, type = type, sourceId = sourceId, name = name,
        servingWeight = servingWeight,
        carbsPer100g = carbsPer100g, caloriesPer100g = caloriesPer100g,
        proteinsPer100g = proteinsPer100g, fatsPer100g = fatsPer100g,
        glycemicIndex = glycemicIndex,
        includedInAdjustment = includedInAdjustment
    )

    fun withAdjustment(includedInAdjustment: Boolean) = CalcComponent(
        key = key, type = type, sourceId = sourceId, name = name,
        servingWeight = servingWeight,
        carbsPer100g = carbsPer100g, caloriesPer100g = caloriesPer100g,
        proteinsPer100g = proteinsPer100g, fatsPer100g = fatsPer100g,
        glycemicIndex = glycemicIndex,
        includedInAdjustment = includedInAdjustment
    )

    companion object {
        fun fromProduct(p: Product, weight: Double) = CalcComponent(
            type = ComponentType.PRODUCT, sourceId = p.id, name = p.name,
            servingWeight = weight, carbsPer100g = p.carbs,
            caloriesPer100g = p.calories, proteinsPer100g = p.proteins,
            fatsPer100g = p.fats, glycemicIndex = p.glycemicIndex
        )
        fun fromDish(d: DishWithIngredients, weight: Double) = CalcComponent(
            type = ComponentType.DISH, sourceId = d.dish.id, name = d.dish.name,
            servingWeight = weight, carbsPer100g = d.carbsPer100g,
            caloriesPer100g = d.caloriesPer100g, proteinsPer100g = 0.0,
            fatsPer100g = 0.0, glycemicIndex = 50.0
        )
    }
}

// ─── CGM ────────────────────────────────────────────────────────────────────

data class CgmReading(
    val glucose: Double,
    val direction: String,
    val time: java.time.Instant,
    val forecast20min: Double? = null
) {
    val isStale: Boolean get() =
        java.time.Instant.now().epochSecond - time.epochSecond > 600

    val directionArrow: String get() = when (direction) {
        "DoubleUp"      -> "⇈"
        "SingleUp"      -> "↑"
        "FortyFiveUp"   -> "↗"
        "Flat"          -> "→"
        "FortyFiveDown" -> "↘"
        "SingleDown"    -> "↓"
        "DoubleDown"    -> "⇊"
        else            -> "→"
    }

    val trendDelta: Double get() = when (direction) {
        "DoubleUp"      -> 2.0
        "SingleUp"      -> 1.0
        "FortyFiveUp"   -> 0.5
        "Flat"          -> 0.0
        "FortyFiveDown" -> -0.5
        "SingleDown"    -> -1.0
        "DoubleDown"    -> -2.0
        else            -> 0.0
    }
}

// ─── App Settings (domain object) ────────────────────────────────────────────

data class AppSettings(
    val carbsPerXe: Double = 10.0,
    val carbCoefficient: Double = 0.5,
    val sensitivity: Double = 8.0,
    val targetGlucose: Double = 6.0,
    val targetGlucoseMin: Double = 3.9,
    val targetGlucoseMax: Double = 10.0,
    val insulinStep: Double = 1.0,
    val basalDose: Double = 4.0,
    val insulinType: String = "fiasp",
    val basalType: String = "toujeo",
    val basalTime: String = "9:00",
    val nsEnabled: Boolean = false,
    val nsUrl: String = "",
    val nsApiSecret: String = ""
)
