package es.sebas1705.haltera.data.local.foods

import es.sebas1705.haltera.data.local.inMemoryDatabase
import es.sebas1705.haltera.data.models.FoodImportResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledFoodsTest {
    // Unit tests run with the module directory as working directory.
    private val bundled = File("../../composeApp/src/commonMain/composeResources/files/foods.json")

    @Test
    fun `the bundled foods import and the counts match the file`() {
        val db = inMemoryDatabase()
        val json = bundled.readText()
        val file = FoodsJson.decodeFromString(FoodsDto.serializer(), json)

        val result = FoodImporter(db, now = { 0L }).importIfNewer(json)

        val imported = assertIs<FoodImportResult.Imported>(result)
        assertTrue(imported.foods >= 600, "expected the full foods database, got ${imported.foods}")
        assertEquals(file.foods.size, imported.foods)
        assertEquals(imported.foods.toLong(), db.foodEntityQueries.countActiveGeneric().executeAsOne())
        // Every food has both names and plausible values.
        file.foods.forEach { food ->
            assertTrue(food.name.en.isNotBlank() && food.name.es.isNotBlank(), food.id)
            assertTrue(food.kcal in 0.0..902.0, food.id)
        }
        // Accent-insensitive search finds Spanish names typed without accents.
        val hits = db.foodEntityQueries.search(word = "platano", limit = 10).executeAsList()
        assertTrue(hits.any { it.nameEs == "Plátano" }, hits.map { it.nameEs }.toString())
    }
}
