package com.glucoplan.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit-тесты для доменных моделей: CalcComponent, DishWithIngredients, Product, AppSettings.
 */
@RunWith(JUnit4::class)
class ModelsTest {

    // ─── Вспомогательные функции ──────────────────────────────────────────────────

    private fun продукт(
        id: Long = 1,
        название: String = "Хлеб",
        углеводы: Double = 50.0,
        белки: Double = 8.0,
        жиры: Double = 2.0,
        калории: Double = 250.0,
        ги: Double = 70.0
    ) = Product(id = id, name = название, carbs = углеводы, proteins = белки,
        fats = жиры, calories = калории, glycemicIndex = ги)

    private fun компонент(
        id: Long = 1,
        название: String = "Тест",
        вес: Double = 100.0,
        углеводыНа100г: Double = 20.0,
        калорийНа100г: Double = 100.0,
        белкиНа100г: Double = 5.0,
        жирыНа100г: Double = 3.0,
        ги: Double = 50.0,
        вАдаптацию: Boolean = true
    ) = CalcComponent(
        type = ComponentType.PRODUCT,
        sourceId = id,
        name = название,
        servingWeight = вес,
        carbsPer100g = углеводыНа100г,
        caloriesPer100g = калорийНа100г,
        proteinsPer100g = белкиНа100г,
        fatsPer100g = жирыНа100г,
        glycemicIndex = ги,
        includedInAdjustment = вАдаптацию
    )

    // ─── CalcComponent.fromProduct ────────────────────────────────────────────────

    @Test
    fun `fromProduct создаёт компонент с правильными углеводами в порции`() {
        val продукт = продукт(углеводы = 50.0)
        val компонент = CalcComponent.fromProduct(продукт, 200.0)

        assertThat(компонент.carbsInPortion).isEqualTo(100.0) // 50г/100г × 200г
    }

    @Test
    fun `fromProduct создаёт компонент с правильным типом`() {
        val компонент = CalcComponent.fromProduct(продукт(), 100.0)
        assertThat(компонент.type).isEqualTo(ComponentType.PRODUCT)
    }

    @Test
    fun `fromProduct копирует id продукта как sourceId`() {
        val компонент = CalcComponent.fromProduct(продукт(id = 42), 100.0)
        assertThat(компонент.sourceId).isEqualTo(42)
    }

    @Test
    fun `fromProduct сохраняет вес порции`() {
        val компонент = CalcComponent.fromProduct(продукт(), 150.0)
        assertThat(компонент.servingWeight).isEqualTo(150.0)
    }

    @Test
    fun `fromProduct вычисляет калории в порции правильно`() {
        val продукт = продукт(калории = 250.0)
        val компонент = CalcComponent.fromProduct(продукт, 200.0)
        assertThat(компонент.caloriesInPortion).isEqualTo(500.0) // 250×200/100
    }

    @Test
    fun `fromProduct вычисляет белки в порции правильно`() {
        val продукт = продукт(белки = 10.0)
        val компонент = CalcComponent.fromProduct(продукт, 150.0)
        assertThat(компонент.proteinsInPortion).isEqualTo(15.0) // 10×150/100
    }

    @Test
    fun `fromProduct вычисляет жиры в порции правильно`() {
        val продукт = продукт(жиры = 20.0)
        val компонент = CalcComponent.fromProduct(продукт, 50.0)
        assertThat(компонент.fatsInPortion).isEqualTo(10.0) // 20×50/100
    }

    @Test
    fun `fromProduct вычисляет гликемическую нагрузку правильно`() {
        val продукт = продукт(углеводы = 50.0, ги = 80.0)
        val компонент = CalcComponent.fromProduct(продукт, 100.0)
        // ГН = ГИ × углеводы / 100 = 80 × 50 / 100 = 40
        assertThat(компонент.glycemicLoad).isEqualTo(40.0)
    }

    // ─── CalcComponent.withWeight ─────────────────────────────────────────────────

    @Test
    fun `withWeight изменяет только вес порции`() {
        val исходный = компонент(вес = 100.0, углеводыНа100г = 30.0)
        val изменённый = исходный.withWeight(200.0)

        assertThat(изменённый.servingWeight).isEqualTo(200.0)
        assertThat(изменённый.carbsPer100g).isEqualTo(30.0) // не изменился
    }

    @Test
    fun `withWeight пересчитывает углеводы в порции`() {
        val исходный = компонент(вес = 100.0, углеводыНа100г = 30.0)
        val изменённый = исходный.withWeight(200.0)

        assertThat(изменённый.carbsInPortion).isEqualTo(60.0) // 30×200/100
    }

    @Test
    fun `withWeight сохраняет название и id`() {
        val исходный = компонент(id = 5, название = "Рис")
        val изменённый = исходный.withWeight(150.0)

        assertThat(изменённый.sourceId).isEqualTo(5)
        assertThat(изменённый.name).isEqualTo("Рис")
    }

    // ─── CalcComponent.withAdjustment ────────────────────────────────────────────

    @Test
    fun `withAdjustment включает компонент в адаптацию`() {
        val компонент = компонент(вАдаптацию = false)
        val включённый = компонент.withAdjustment(true)
        assertThat(включённый.includedInAdjustment).isTrue()
    }

    @Test
    fun `withAdjustment исключает компонент из адаптации`() {
        val компонент = компонент(вАдаптацию = true)
        val исключённый = компонент.withAdjustment(false)
        assertThat(исключённый.includedInAdjustment).isFalse()
    }

    @Test
    fun `withAdjustment не меняет остальные поля`() {
        val исходный = компонент(вес = 100.0, углеводыНа100г = 25.0)
        val изменённый = исходный.withAdjustment(false)

        assertThat(изменённый.servingWeight).isEqualTo(100.0)
        assertThat(изменённый.carbsPer100g).isEqualTo(25.0)
    }

    // ─── CalcComponent вычисляемые свойства ──────────────────────────────────────

    @Test
    fun `carbsInPortion равен нулю при нулевом весе порции`() {
        val компонент = компонент(вес = 0.0, углеводыНа100г = 50.0)
        assertThat(компонент.carbsInPortion).isEqualTo(0.0)
    }

    @Test
    fun `glycemicLoad равен нулю при нулевых углеводах`() {
        val компонент = компонент(углеводыНа100г = 0.0, ги = 80.0)
        assertThat(компонент.glycemicLoad).isEqualTo(0.0)
    }

    @Test
    fun `glycemicLoad равен нулю при нулевом ГИ`() {
        val компонент = компонент(углеводыНа100г = 30.0, ги = 0.0)
        assertThat(компонент.glycemicLoad).isEqualTo(0.0)
    }

    // ─── DishWithIngredients ──────────────────────────────────────────────────────

    private fun ингредиент(
        продуктId: Long = 1,
        вес: Double = 100.0,
        углеводы: Double = 20.0,
        калории: Double = 100.0,
        белки: Double = 5.0,
        жиры: Double = 2.0,
        ги: Double = 50.0
    ) = DishIngredient(
        productId = продуктId,
        productName = "Продукт $продуктId",
        weight = вес,
        carbs = углеводы,
        calories = калории,
        proteins = белки,
        fats = жиры,
        glycemicIndex = ги
    )

    @Test
    fun `totalCarbs суммирует углеводы из всех ингредиентов`() {
        val блюдо = DishWithIngredients(
            dish = Dish(name = "Суп"),
            ingredients = listOf(
                ингредиент(вес = 100.0, углеводы = 20.0), // 20г
                ингредиент(вес = 50.0, углеводы = 40.0)   // 20г
            )
        )
        assertThat(блюдо.totalCarbs).isEqualTo(40.0)
    }

    @Test
    fun `totalWeight суммирует вес всех ингредиентов`() {
        val блюдо = DishWithIngredients(
            dish = Dish(name = "Каша"),
            ingredients = listOf(
                ингредиент(вес = 100.0),
                ингредиент(вес = 50.0),
                ингредиент(вес = 30.0)
            )
        )
        assertThat(блюдо.totalWeight).isEqualTo(180.0)
    }

    @Test
    fun `carbsPer100g вычисляется правильно`() {
        val блюдо = DishWithIngredients(
            dish = Dish(name = "Каша"),
            ingredients = listOf(
                ингредиент(вес = 100.0, углеводы = 30.0) // 30г/100г
            )
        )
        // Общий вес 100г, углеводов 30г → 30г/100г
        assertThat(блюдо.carbsPer100g).isEqualTo(30.0)
    }

    @Test
    fun `carbsPer100g равен нулю для пустого блюда`() {
        val блюдо = DishWithIngredients(dish = Dish(name = "Пустое"), ingredients = emptyList())
        assertThat(блюдо.carbsPer100g).isEqualTo(0.0)
    }

    @Test
    fun `fromDish создаёт компонент с типом DISH`() {
        val блюдо = DishWithIngredients(
            dish = Dish(id = 10, name = "Борщ"),
            ingredients = listOf(ингредиент(вес = 200.0, углеводы = 10.0))
        )
        val компонент = CalcComponent.fromDish(блюдо, 300.0)

        assertThat(компонент.type).isEqualTo(ComponentType.DISH)
        assertThat(компонент.sourceId).isEqualTo(10)
        assertThat(компонент.name).isEqualTo("Борщ")
    }

    @Test
    fun `fromDish масштабирует углеводы по весу порции`() {
        val блюдо = DishWithIngredients(
            dish = Dish(name = "Каша"),
            ingredients = listOf(
                ингредиент(вес = 100.0, углеводы = 20.0) // carbsPer100g = 20
            )
        )
        val компонент = CalcComponent.fromDish(блюдо, 200.0) // порция 200г
        // 20г/100г × 200г = 40г
        assertThat(компонент.carbsInPortion).isEqualTo(40.0)
    }

    // ─── AppSettings значения по умолчанию ───────────────────────────────────────

    @Test
    fun `AppSettings имеет разумные значения по умолчанию`() {
        val настройки = AppSettings()

        assertThat(настройки.carbsPerXe).isGreaterThan(0.0)
        assertThat(настройки.carbCoefficient).isGreaterThan(0.0)
        assertThat(настройки.sensitivity).isGreaterThan(0.0)
        assertThat(настройки.targetGlucose).isGreaterThan(0.0)
        assertThat(настройки.insulinStep).isGreaterThan(0.0)
    }

    @Test
    fun `AppSettings nsEnabled по умолчанию выключен`() {
        assertThat(AppSettings().nsEnabled).isFalse()
    }

    @Test
    fun `AppSettings targetGlucoseMin меньше targetGlucose`() {
        val настройки = AppSettings()
        assertThat(настройки.targetGlucoseMin).isLessThan(настройки.targetGlucose)
    }

    @Test
    fun `AppSettings targetGlucose меньше targetGlucoseMax`() {
        val настройки = AppSettings()
        assertThat(настройки.targetGlucose).isLessThan(настройки.targetGlucoseMax)
    }

    // ─── DishIngredient.carbsInPortion ───────────────────────────────────────────

    @Test
    fun `DishIngredient carbsInPortion вычисляется от веса`() {
        // carbs — это углеводы на 100г в DishIngredient
        val ингр = ингредиент(вес = 150.0, углеводы = 30.0)
        // 30г/100г × 150г = 45г
        assertThat(ингр.carbsInPortion).isEqualTo(45.0)
    }

    @Test
    fun `DishIngredient carbsInPortion равен нулю при нулевом весе`() {
        val ингр = ингредиент(вес = 0.0, углеводы = 30.0)
        assertThat(ингр.carbsInPortion).isEqualTo(0.0)
    }
}
