package es.sebas1705.ironlog.data.models

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionModelsTest {
    private val oats = Food(
        id = "oats",
        source = FoodSource.GENERIC,
        nameEn = "Oats",
        nameEs = "Avena",
        per100 = Nutrients(kcal = 380.0, protein = 13.0, carbs = 68.0, fat = 6.0, fiber = 10.0),
    )
    private val milk = Food(
        id = "milk",
        source = FoodSource.GENERIC,
        nameEn = "Milk",
        nameEs = "Leche",
        unit = FoodUnit.ML,
        per100 = Nutrients(kcal = 60.0, protein = 3.0, carbs = 5.0, fat = 3.0, sugar = 5.0),
    )

    @Test
    fun `nutrients scale with the amount`() {
        val n = oats.nutrientsFor(50.0)
        assertEquals(190.0, n.kcal, 1e-9)
        assertEquals(6.5, n.protein, 1e-9)
        assertEquals(5.0, n.fiber!!, 1e-9)
        assertNull(n.sugar)
    }

    @Test
    fun `sums keep optional values unknown only when unknown on both sides`() {
        val sum = oats.nutrientsFor(100.0) + milk.nutrientsFor(200.0)
        assertEquals(500.0, sum.kcal, 1e-9)
        assertEquals(10.0, sum.fiber!!, 1e-9)
        assertEquals(10.0, sum.sugar!!, 1e-9)
        assertNull(sum.salt)
    }

    @Test
    fun `a recipe is the sum of its items over its weight or yield`() {
        val items = listOf(oats to 80.0, milk to 250.0)
        val raw = RecipeMath.per100(items, yieldGrams = null)
        // (304 + 150) kcal over 330 g
        assertEquals(454.0 / 330.0 * 100.0, raw.kcal, 1e-9)
        val cooked = RecipeMath.per100(items, yieldGrams = 400.0)
        assertEquals(454.0 / 4.0, cooked.kcal, 1e-9)
        assertEquals(Nutrients.ZERO, RecipeMath.per100(emptyList(), yieldGrams = null))
        // A missing ingredient weighs but adds nothing.
        assertEquals(304.0 / 160.0 * 100.0, RecipeMath.per100(listOf(oats to 80.0, null to 80.0), null).kcal, 1e-9)
    }

    @Test
    fun `a day totals its entries and meals`() {
        val date = LocalDate(2026, 10, 15)
        fun entry(id: String, meal: Meal, food: Food?, grams: Double, position: Int) =
            MealEntry(id, date, meal, food?.id ?: "gone", grams, null, position, 0, food)
        val day = FoodDay(
            date,
            listOf(
                entry("b2", Meal.BREAKFAST, milk, 250.0, 1),
                entry("b1", Meal.BREAKFAST, oats, 50.0, 0),
                entry("l1", Meal.LUNCH, null, 100.0, 0),
            ),
        )
        assertEquals(listOf("b1", "b2"), day.meal(Meal.BREAKFAST).map { it.id })
        assertEquals(190.0 + 150.0, day.totals.kcal, 1e-9)
        assertEquals(0.0, day.mealTotals(Meal.LUNCH).kcal, 1e-9)
    }

    @Test
    fun `names fall back to the other language and enums read leniently`() {
        assertEquals("Avena", oats.name("es"))
        assertEquals("Oats", oats.name("en"))
        assertEquals("Oats", oats.copy(nameEs = "").name("es"))
        assertEquals(Meal.SNACK, Meal.fromDb("brunch"))
        assertEquals(FoodSource.OFF, FoodSource.fromDb("off"))
        assertEquals(ActivityLevel.MODERATE, ActivityLevel.fromDb(null))
    }
}
