package es.sebas1705.ironlog.data.repository

import com.russhwolf.settings.MapSettings
import es.sebas1705.ironlog.core.datastore.KeyValueSettingsRepository
import es.sebas1705.ironlog.data.local.foods.FoodImporter
import es.sebas1705.ironlog.data.models.ActivityLevel
import es.sebas1705.ironlog.data.models.BarcodeLookup
import es.sebas1705.ironlog.data.models.FoodImportResult
import es.sebas1705.ironlog.data.models.FoodInput
import es.sebas1705.ironlog.data.models.FoodSource
import es.sebas1705.ironlog.data.models.MacroMode
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.data.models.NutritionGoals
import es.sebas1705.ironlog.data.models.NutritionProfile
import es.sebas1705.ironlog.data.models.Objective
import es.sebas1705.ironlog.data.models.RecipeItemInput
import es.sebas1705.ironlog.data.models.Sex
import es.sebas1705.ironlog.data.repository.off.OpenFoodFactsClient
import es.sebas1705.ironlog.data.repository.sql.SqlNutritionRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val FOODS = """
    {"foodsVersion":1,"contentHash":"h","deprecatedIds":[],"foods":[
    {"id":"usda-1","name":{"en":"Rolled oats","es":"Copos de avena"},"unit":"G","kcal":380,"protein":13,
     "carbs":68,"fat":6,"fiber":10,"sugar":1,"salt":0,"serving":null},
    {"id":"usda-2","name":{"en":"Whole milk","es":"Leche entera"},"unit":"ML","kcal":60,"protein":3,
     "carbs":5,"fat":3,"fiber":0,"sugar":5,"salt":0.1,"serving":{"name":{"en":"glass","es":"vaso"},"grams":250}},
    {"id":"usda-3","name":{"en":"Banana","es":"Plátano"},"unit":"G","kcal":89,"protein":1.1,
     "carbs":22.8,"fat":0.3,"fiber":2.6,"sugar":12.2,"salt":0,"serving":{"name":{"en":"unit","es":"unidad"},"grams":120}},
    {"id":"usda-4","name":{"en":"Chicken breast, raw","es":"Pechuga de pollo, cruda"},"unit":"G","kcal":120,
     "protein":22.5,"carbs":0,"fat":2.6,"fiber":null,"sugar":null,"salt":0.1,"serving":null}
    ]}
""".trimIndent()

class NutritionRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = TestClock()
    private val settings = KeyValueSettingsRepository(MapSettings())
    private val day = LocalDate(2026, 10, 15)
    private val nextDay = LocalDate(2026, 10, 16)
    private var offResponse: Pair<HttpStatusCode, String> = HttpStatusCode.NotFound to "{}"
    private var offCalls = 0
    private val http = HttpClient(
        MockEngine {
            offCalls++
            respond(offResponse.second, offResponse.first, headersOf("Content-Type", "application/json"))
        },
    )

    private fun TestScope.repository() = SqlNutritionRepository(
        db, FoodImporter(db, now = clock), OpenFoodFactsClient(http, "1.0", now = clock), settings,
        UnconfinedTestDispatcher(testScheduler), clock,
    ).also { FoodImporter(db, now = clock).importIfNewer(FOODS) }

    @Test
    fun `entries are logged per day and meal, with nutrients computed from the food`() = runTest {
        val repo = repository()
        repo.addEntry(day, Meal.BREAKFAST, "usda-1", 50.0)
        repo.addEntry(day, Meal.BREAKFAST, "usda-2", 250.0, servings = 1.0)
        repo.addEntry(day, Meal.DINNER, "usda-4", 200.0)

        val food = repo.observeDay(day).first()
        assertEquals(listOf("usda-1", "usda-2"), food.meal(Meal.BREAKFAST).map { it.foodId })
        assertEquals(listOf(0, 1), food.meal(Meal.BREAKFAST).map { it.position })
        assertEquals(1.0, food.meal(Meal.BREAKFAST)[1].servings)
        assertEquals(190.0 + 150.0 + 240.0, food.totals.kcal, 1e-9)
        assertEquals(45.0, food.mealTotals(Meal.DINNER).protein, 1e-9)
        assertTrue(repo.observeDay(nextDay).first().entries.isEmpty())
    }

    @Test
    fun `entries can be edited, moved to any day and deleted`() = runTest {
        val repo = repository()
        val id = repo.addEntry(day, Meal.LUNCH, "usda-3", 120.0)
        repo.updateEntry(id, 60.0, servings = 0.5)
        assertEquals(60.0, repo.observeDay(day).first().entries.single().grams)

        repo.moveEntry(id, nextDay, Meal.SNACK)
        assertTrue(repo.observeDay(day).first().entries.isEmpty())
        assertEquals(Meal.SNACK, repo.observeDay(nextDay).first().entries.single().meal)

        repo.deleteEntry(id)
        assertTrue(repo.observeDay(nextDay).first().entries.isEmpty())
    }

    @Test
    fun `a meal or a whole day can be copied and is appended`() = runTest {
        val repo = repository()
        repo.addEntry(day, Meal.BREAKFAST, "usda-1", 50.0)
        repo.addEntry(day, Meal.BREAKFAST, "usda-2", 250.0)
        repo.addEntry(day, Meal.DINNER, "usda-4", 200.0)
        repo.addEntry(nextDay, Meal.BREAKFAST, "usda-3", 120.0)

        assertEquals(2, repo.copyMeal(day, Meal.BREAKFAST, nextDay, Meal.SNACK))
        assertEquals(3, repo.copyDay(day, nextDay))

        val copied = repo.observeDay(nextDay).first()
        assertEquals(listOf("usda-3", "usda-1", "usda-2"), copied.meal(Meal.BREAKFAST).map { it.foodId })
        assertEquals(listOf("usda-1", "usda-2"), copied.meal(Meal.SNACK).map { it.foodId })
        assertEquals(listOf("usda-4"), copied.meal(Meal.DINNER).map { it.foodId })
        // The source day is unchanged.
        assertEquals(3, repo.observeDay(day).first().entries.size)
    }

    @Test
    fun `search is accent-insensitive, matches every word in both languages and ranks favourites`() = runTest {
        val repo = repository()
        assertEquals(listOf("usda-3"), repo.search("platano", "es").map { it.id })
        assertEquals(listOf("usda-3"), repo.search("BANANA", "en").map { it.id })
        assertEquals(listOf("usda-4"), repo.search("pollo cruda", "es").map { it.id })
        assertTrue(repo.search("pollo frito", "es").isEmpty())
        assertTrue(repo.search("   ", "es").isEmpty())

        val shake = repo.createFood(FoodInput(nameEn = "", nameEs = "Batido de leche", per100 = Nutrients(kcal = 70.0)))
        assertEquals(listOf("usda-2", shake.id).toSet(), repo.search("leche", "es").map { it.id }.toSet())
        repo.setFavourite(shake.id, true)
        assertEquals(shake.id, repo.search("leche", "es").first().id)
    }

    @Test
    fun `recents follow the log and favourites the flag`() = runTest {
        val repo = repository()
        repo.addEntry(day, Meal.BREAKFAST, "usda-1", 50.0)
        clock.millis += 10
        repo.addEntry(day, Meal.LUNCH, "usda-4", 150.0)
        clock.millis += 10
        repo.addEntry(nextDay, Meal.BREAKFAST, "usda-1", 40.0)
        assertEquals(listOf("usda-1", "usda-4"), repo.observeRecents().first().map { it.id })

        repo.setFavourite("usda-3", true)
        assertEquals(listOf("usda-3"), repo.observeFavourites().first().map { it.id })
        repo.setFavourite("usda-3", false)
        assertTrue(repo.observeFavourites().first().isEmpty())
    }

    @Test
    fun `a custom food uses one name for both languages and edits change logged days`() = runTest {
        val repo = repository()
        val food = repo.createFood(
            FoodInput(nameEn = "Protein bar", nameEs = "", per100 = Nutrients(kcal = 350.0, protein = 30.0), servingName = "bar", servingGrams = 60.0),
        )
        assertEquals(FoodSource.USER, food.source)
        assertEquals("Protein bar", food.nameEs)
        assertEquals("bar", food.servingName("es"))
        repo.addEntry(day, Meal.SNACK, food.id, 60.0, servings = 1.0)
        assertEquals(210.0, repo.observeDay(day).first().totals.kcal, 1e-9)

        repo.updateFood(food.id, FoodInput(nameEn = "Protein bar", nameEs = "Barrita", per100 = Nutrients(kcal = 400.0, protein = 30.0)))
        assertEquals(240.0, repo.observeDay(day).first().totals.kcal, 1e-9)
        assertEquals("Barrita", repo.getFood(food.id)?.nameEs)
    }

    @Test
    fun `a used food is hidden instead of deleted, an unused one is deleted`() = runTest {
        val repo = repository()
        val used = repo.createFood(FoodInput(nameEn = "Used", nameEs = "Usado", per100 = Nutrients(kcal = 100.0)))
        val unused = repo.createFood(FoodInput(nameEn = "Unused", nameEs = "Sin usar", per100 = Nutrients(kcal = 100.0)))
        repo.addEntry(day, Meal.LUNCH, used.id, 100.0)

        assertFalse(repo.deleteFood(used.id))
        assertTrue(repo.deleteFood(unused.id))
        assertEquals(true, repo.getFood(used.id)?.deprecated)
        assertNull(repo.getFood(unused.id))
        assertTrue(repo.search("usado", "es").isEmpty())
        assertEquals(100.0, repo.observeDay(day).first().totals.kcal, 1e-9)
        assertEquals(emptyList(), repo.observeOwnFoods().first())
    }

    @Test
    fun `a recipe is computed from its items and yield and follows ingredient edits`() = runTest {
        val repo = repository()
        val milkLike = repo.createFood(FoodInput(nameEn = "Oat drink", nameEs = "Bebida de avena", per100 = Nutrients(kcal = 40.0)))
        val id = repo.saveRecipe(
            null,
            FoodInput(nameEn = "Porridge", nameEs = "Gachas", servingName = "bowl", servingGrams = 250.0),
            yieldGrams = 400.0,
            items = listOf(RecipeItemInput("usda-1", 80.0), RecipeItemInput(milkLike.id, 250.0)),
        )
        val recipe = assertNotNull(repo.getRecipe(id))
        assertEquals(FoodSource.RECIPE, recipe.food.source)
        assertEquals(listOf("usda-1", milkLike.id), recipe.items.map { it.foodId })
        // (304 + 100) kcal over 400 g cooked
        assertEquals(101.0, recipe.food.per100.kcal, 1e-9)

        repo.updateFood(milkLike.id, FoodInput(nameEn = "Oat drink", nameEs = "Bebida de avena", per100 = Nutrients(kcal = 80.0)))
        assertEquals((304.0 + 200.0) / 4.0, repo.getFood(id)!!.per100.kcal, 1e-9)

        // Editing replaces the items.
        repo.saveRecipe(id, FoodInput(nameEn = "Porridge", nameEs = "Gachas"), null, listOf(RecipeItemInput("usda-1", 100.0)))
        assertEquals(1, repo.getRecipe(id)!!.items.size)
        assertEquals(380.0, repo.getFood(id)!!.per100.kcal, 1e-9)
        assertEquals(listOf(id, milkLike.id).toSet(), repo.observeOwnFoods().first().map { it.id }.toSet())
    }

    @Test
    fun `barcodes resolve locally first, then on Open Food Facts, cached as OFF foods`() = runTest {
        val repo = repository()
        offResponse = HttpStatusCode.OK to """
            {"code":"8410000000017","status":"success","product":{"product_name":"Yogur natural","brands":"Marca, Otra",
            "nutriments":{"energy-kcal_100g":61,"proteins_100g":3.5,"carbohydrates_100g":4.7,"fat_100g":3.1,"salt_100g":"0.13"},
            "serving_quantity":125,"serving_size":"125 g"}}
        """.trimIndent()

        val found = assertIs<BarcodeLookup.Found>(repo.lookupBarcode("8410000000017"))
        assertEquals(FoodSource.OFF, found.food.source)
        assertEquals("off-8410000000017", found.food.id)
        assertEquals("Marca", found.food.brand)
        assertEquals(0.13, found.food.per100.salt)
        assertEquals(125.0, found.food.servingGrams)
        assertEquals(1, offCalls)

        // Cached: no second request.
        assertIs<BarcodeLookup.Found>(repo.lookupBarcode("8410000000017"))
        assertEquals(1, offCalls)
        assertEquals(listOf("off-8410000000017"), repo.search("yogur marca", "es").map { it.id })

        offResponse = HttpStatusCode.NotFound to """{"status":"failure"}"""
        assertEquals(BarcodeLookup.NotFound, repo.lookupBarcode("8410000000024"))
        offResponse = HttpStatusCode.ServiceUnavailable to ""
        assertEquals(BarcodeLookup.Unavailable, repo.lookupBarcode("8410000000031"))
    }

    @Test
    fun `goals round-trip through the settings`() = runTest {
        val repo = repository()
        assertFalse(repo.observeGoals().first().isSet)
        val goals = NutritionGoals(
            kcal = 2_300, proteinG = 150, carbsG = 260, fatG = 70, macroMode = MacroMode.PERCENT,
            profile = NutritionProfile(Sex.FEMALE, 1990, 170, ActivityLevel.ACTIVE, Objective.GAIN),
        )
        repo.setGoals(goals)
        assertEquals(goals, repo.observeGoals().first())
    }

    @Test
    fun `the bundled foods import once per version`() = runTest {
        val repo = repository()
        assertEquals(FoodImportResult.UpToDate(1), repo.importBundledFoods(FOODS))
    }
}
