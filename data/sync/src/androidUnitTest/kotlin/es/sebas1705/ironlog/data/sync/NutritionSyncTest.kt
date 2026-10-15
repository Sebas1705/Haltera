package es.sebas1705.ironlog.data.sync

import es.sebas1705.ironlog.core.datastore.NutritionSettings
import es.sebas1705.ironlog.data.local.foods.FoodImporter
import es.sebas1705.ironlog.data.sync.SyncCollections.FOODS
import es.sebas1705.ironlog.data.sync.SyncCollections.MEAL_DAYS
import es.sebas1705.ironlog.data.sync.SyncCollections.SETTINGS
import es.sebas1705.ironlog.data.sync.SyncCollections.SETTINGS_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = "2026-10-15"

private val GENERIC_FOODS = """
    {"foodsVersion":1,"contentHash":"h","deprecatedIds":[],"foods":[
    {"id":"usda-1","name":{"en":"Rolled oats","es":"Copos de avena"},"unit":"G","kcal":379,"protein":13.2,
     "carbs":67.7,"fat":6.5,"fiber":10.1,"sugar":1,"salt":0.02,"serving":null}]}
""".trimIndent()

private fun Device.genericFoods() = FoodImporter(db, now = { 0L }).importIfNewer(GENERIC_FOODS)

private fun Device.food(id: String = "user-food-1", source: String = "USER", name: String = "My shake") {
    db.foodEntityQueries.insert(
        id = id, source = source, nameEn = name, nameEs = name, nameSearch = name.lowercase(), brand = null,
        barcode = if (source == "OFF") "8410000000000" else null, kcal = 120.0, protein = 20.0, carbs = 5.0,
        fat = 2.0, fiber = null, sugar = 3.0, salt = 0.1, unit = "G", servingNameEn = null, servingNameEs = null,
        servingGrams = null, yieldGrams = null, favourite = 0, updatedAt = 5,
    )
}

private fun Device.recipe(id: String = "recipe-1") {
    db.foodEntityQueries.insert(
        id = id, source = "RECIPE", nameEn = "Porridge", nameEs = "Gachas", nameSearch = "porridge gachas",
        brand = null, barcode = null, kcal = 90.0, protein = 4.0, carbs = 14.0, fat = 2.0, fiber = 1.5, sugar = 3.0,
        salt = 0.05, unit = "G", servingNameEn = null, servingNameEs = null, servingGrams = null, yieldGrams = 400.0,
        favourite = 1, updatedAt = 6,
    )
    db.recipeItemEntityQueries.insert("$id-i1", id, "usda-1", 80.0, 0)
    db.recipeItemEntityQueries.insert("$id-i2", id, "user-food-1", 30.0, 1)
}

private fun Device.entry(id: String, foodId: String, date: String = DAY, meal: String = "BREAKFAST", grams: Double = 100.0) {
    db.mealEntryEntityQueries.insert(id, date, meal, foodId, grams, null, 0, 10)
}

private fun Device.everythingNutrition() {
    genericFoods()
    food()
    recipe()
    entry("e1", "usda-1")
    entry("e2", "recipe-1", meal = "LUNCH", grams = 350.0)
}

class NutritionSyncTest {
    private val cloud = FakeCloud()
    private val d1 = Device(cloud, Dispatchers.Unconfined)
    private val d2 = Device(cloud, Dispatchers.Unconfined)

    private fun Device.roots() = dirty().map { "${it.collection}/${it.id}" }.toSet()

    private fun Device.clear() = db.transaction {
        dirty().forEach { db.syncTrackingQueries.clearDirty(it.collection, it.id, it.updatedAt) }
    }

    @Test
    fun `the generic foods import marks nothing`() {
        d1.genericFoods()
        assertTrue(d1.dirty().isEmpty())
    }

    @Test
    fun `foods, recipe items and entries map to their roots`() {
        d1.genericFoods()
        d1.food()
        assertEquals(setOf("foods/user-food-1"), d1.roots())
        d1.clear()
        d1.recipe()
        assertEquals(setOf("foods/recipe-1"), d1.roots())
        d1.clear()
        d1.entry("e1", "usda-1")
        assertEquals(setOf("mealDays/$DAY"), d1.roots())
        d1.clear()
        d1.db.mealEntryEntityQueries.moveTo(date = "2026-10-16", meal = "DINNER", position = 0, id = "e1")
        assertEquals(setOf("mealDays/$DAY", "mealDays/2026-10-16"), d1.roots())
        // The emptied day becomes a tombstone.
        assertEquals(1L, d1.state(MEAL_DAYS, DAY)?.deleted)
        assertEquals(0L, d1.state(MEAL_DAYS, "2026-10-16")?.deleted)
    }

    @Test
    fun `an Open Food Facts product is synced from its first use only`() {
        d1.food(id = "off-8410000000000", source = "OFF", name = "Yogurt")
        assertTrue(d1.dirty().isEmpty())
        d1.entry("e1", "off-8410000000000")
        assertEquals(setOf("foods/off-8410000000000", "mealDays/$DAY"), d1.roots())
        d1.clear()
        d1.db.foodEntityQueries.setFavourite(favourite = 1, id = "off-8410000000000")
        assertEquals(setOf("foods/off-8410000000000"), d1.roots())
    }

    @Test
    fun `foods and days round-trip between two devices`() = runTest {
        d1.everythingNutrition()
        d2.genericFoods()
        d1.engine.syncNow()
        assertNotNull(cloud.doc("uid-1", FOODS, "recipe-1"))
        assertNull(cloud.doc("uid-1", FOODS, "usda-1"), "generic foods are bundled, never synced")

        d2.engine.syncNow()
        listOf(FOODS to "user-food-1", FOODS to "recipe-1", MEAL_DAYS to DAY).forEach { (c, id) ->
            assertEquals(d1.read(c, id), d2.read(c, id), "$c/$id")
        }
        assertEquals(2, d2.db.recipeItemEntityQueries.selectByRecipe("recipe-1").executeAsList().size)
        assertEquals(2, d2.db.mealEntryEntityQueries.selectByDate(DAY).executeAsList().size)
        assertTrue(d2.dirty().isEmpty(), d2.dirty().toString())
    }

    @Test
    fun `last writer wins on a day edited on both devices`() = runTest {
        d1.everythingNutrition()
        d2.genericFoods()
        d1.engine.syncNow()
        d2.engine.syncNow()

        d1.db.mealEntryEntityQueries.updateAmount(grams = 50.0, servings = null, id = "e1")
        tick()
        d2.db.mealEntryEntityQueries.updateAmount(grams = 70.0, servings = null, id = "e1")
        d1.engine.syncNow()
        d2.engine.syncNow()
        d1.engine.syncNow()

        assertEquals(70.0, d1.db.mealEntryEntityQueries.selectById("e1").executeAsOne().grams)
        assertEquals(70.0, d2.db.mealEntryEntityQueries.selectById("e1").executeAsOne().grams)
        assertTrue(d1.dirty().isEmpty())
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `deleted days and foods propagate as tombstones`() = runTest {
        d1.everythingNutrition()
        d2.genericFoods()
        d1.engine.syncNow()
        d2.engine.syncNow()

        d1.db.mealEntryEntityQueries.delete("e1")
        d1.db.mealEntryEntityQueries.delete("e2")
        d1.db.foodEntityQueries.delete("recipe-1")
        d1.engine.syncNow()
        assertEquals(true, cloud.doc("uid-1", MEAL_DAYS, DAY)?.deleted)
        assertEquals(true, cloud.doc("uid-1", FOODS, "recipe-1")?.deleted)

        d2.engine.syncNow()
        assertTrue(d2.db.mealEntryEntityQueries.selectByDate(DAY).executeAsList().isEmpty())
        assertNull(d2.db.foodEntityQueries.selectById("recipe-1").executeAsOneOrNull())
        assertTrue(d2.db.recipeItemEntityQueries.selectByRecipe("recipe-1").executeAsList().isEmpty())
        assertNotNull(d2.db.foodEntityQueries.selectById("user-food-1").executeAsOneOrNull())
        assertTrue(d2.dirty().isEmpty())
    }

    @Test
    fun `a day can arrive before the food it uses`() = runTest {
        d1.everythingNutrition()
        d1.engine.syncNow()
        val day = cloud.doc("uid-1", MEAL_DAYS, DAY)!!
        val fresh = Device(FakeCloud(), Dispatchers.Unconfined)
        fresh.store.apply(day)
        assertEquals(2, fresh.db.mealEntryEntityQueries.selectByDate(DAY).executeAsList().size)
    }

    @Test
    fun `nutrition goals travel in the settings document and old documents keep local goals`() = runTest {
        val goals = NutritionSettings(kcal = 2_200, proteinG = 150, sex = "FEMALE", heightCm = 165)
        d1.settings.setNutrition(goals)
        d1.bridge.onLocal(SyncJson.encodeToString(SettingsPrefs.serializer(), d1.bridge.prefs()))
        d1.engine.syncNow()
        d2.engine.syncNow()
        assertEquals(goals, d2.settings.settings.value.nutrition)

        // A document written by an app version without nutrition does not erase the goals.
        val old = cloud.doc("uid-1", SETTINGS, SETTINGS_ID)!!
        val withoutGoals = SyncJson.decodeFromString(SettingsPayload.serializer(), old.payload)
            .let { it.copy(prefs = it.prefs.copy(nutrition = null)) }
        d2.store.apply(
            old.copy(
                updatedAt = old.updatedAt + 1,
                payload = SyncJson.encodeToString(SettingsPayload.serializer(), withoutGoals)
            )
        )
        assertEquals(goals, d2.settings.settings.value.nutrition)
    }
}
