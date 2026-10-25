package es.sebas1705.haltera.data.local.foods

import es.sebas1705.haltera.data.local.inMemoryDatabase
import es.sebas1705.haltera.data.models.FoodImportResult
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class FoodImporterTest {
    private val db = inMemoryDatabase()
    private var clock = 1_000L
    private val importer = FoodImporter(db, now = { clock })

    private fun foods(version: Int, milkEs: String = "Leche entera", deprecated: String = "", extra: String = "") = """
        {"foodsVersion":$version,"contentHash":"h$version","deprecatedIds":[$deprecated],"foods":[
        {"id":"usda-1","name":{"en":"Whole milk","es":"$milkEs"},"unit":"ML","kcal":61,"protein":3.15,"carbs":4.8,
         "fat":3.25,"fiber":0,"sugar":5.05,"salt":0.11,"serving":{"name":{"en":"glass","es":"vaso"},"grams":250}},
        {"id":"usda-2","name":{"en":"Chicken breast, raw","es":"Pechuga de pollo, cruda"},"unit":"G","kcal":120,
         "protein":22.5,"carbs":0,"fat":2.62,"fiber":null,"sugar":null,"salt":0.11,"serving":null}
        $extra
        ]}
    """.trimIndent()

    @Test
    fun `first import inserts the generic foods and stores the version`() {
        val result = importer.importIfNewer(foods(1))

        assertEquals(FoodImportResult.Imported(version = 1, foods = 2), result)
        assertEquals(1, importer.installedVersion())
        val milk = db.foodEntityQueries.selectById("usda-1").executeAsOne()
        assertEquals("GENERIC", milk.source)
        assertEquals("ML", milk.unit)
        assertEquals(61.0, milk.kcal)
        assertEquals("vaso", milk.servingNameEs)
        assertEquals(250.0, milk.servingGrams)
        assertEquals("whole milk leche entera", milk.nameSearch)
        val chicken = db.foodEntityQueries.selectById("usda-2").executeAsOne()
        assertNull(chicken.fiber)
        assertNull(chicken.servingGrams)
        assertEquals(2L, db.foodEntityQueries.countActiveGeneric().executeAsOne())
    }

    @Test
    fun `the same or an older version is not imported again`() {
        importer.importIfNewer(foods(2))

        assertEquals(FoodImportResult.UpToDate(2), importer.importIfNewer(foods(2, milkEs = "Otra")))
        assertEquals(FoodImportResult.UpToDate(2), importer.importIfNewer(foods(1)))
        assertEquals("Leche entera", db.foodEntityQueries.selectById("usda-1").executeAsOne().nameEs)
    }

    @Test
    fun `a newer version updates foods, keeps favourites and deprecates removed ids`() {
        importer.importIfNewer(foods(1))
        db.foodEntityQueries.setFavourite(favourite = 1, id = "usda-1")
        clock = 2_000L

        importer.importIfNewer(foods(2, milkEs = "Leche de vaca entera", deprecated = "\"usda-2\""))

        val milk = db.foodEntityQueries.selectById("usda-1").executeAsOne()
        assertEquals("Leche de vaca entera", milk.nameEs)
        assertEquals(1L, milk.favourite)
        assertEquals(2_000L, milk.updatedAt)
        assertEquals(1L, db.foodEntityQueries.selectById("usda-2").executeAsOne().deprecated)
        assertEquals(1L, db.foodEntityQueries.countActiveGeneric().executeAsOne())
    }

    @Test
    fun `a food that comes back is live again`() {
        importer.importIfNewer(foods(1))
        importer.importIfNewer(foods(2, deprecated = "\"usda-2\""))
        importer.importIfNewer(foods(3))

        assertEquals(0L, db.foodEntityQueries.selectById("usda-2").executeAsOne().deprecated)
    }

    @Test
    fun `user foods are never touched by the import`() {
        db.foodEntityQueries.insert(
            id = "usda-3", source = "USER", nameEn = "Mine", nameEs = "Mío", nameSearch = "mine mio", brand = null,
            barcode = null, kcal = 1.0, protein = 0.0, carbs = 0.0, fat = 0.0, fiber = null, sugar = null, salt = null,
            unit = "G", servingNameEn = null, servingNameEs = null, servingGrams = null, yieldGrams = null,
            favourite = 0, updatedAt = 1,
        )
        val extra = """,{"id":"usda-3","name":{"en":"Generic","es":"Genérico"},"unit":"G","kcal":5,"protein":0,
            "carbs":0,"fat":0,"fiber":null,"sugar":null,"salt":null,"serving":null}"""

        val result = importer.importIfNewer(foods(1, extra = extra, deprecated = "\"usda-3\""))

        assertEquals(FoodImportResult.Imported(version = 1, foods = 2), result)
        val mine = db.foodEntityQueries.selectById("usda-3").executeAsOne()
        assertEquals("USER", mine.source)
        assertEquals("Mine", mine.nameEn)
        assertEquals(0L, mine.deprecated)
    }

    @Test
    fun `a malformed file throws and leaves the database as it was`() {
        importer.importIfNewer(foods(1))

        assertFailsWith<SerializationException> {
            importer.importIfNewer("""{"foodsVersion":2,"contentHash":"x","foods":[{"id":"usda-9"}]}""")
        }
        assertEquals(1, importer.installedVersion())
        assertNull(db.foodEntityQueries.selectById("usda-9").executeAsOneOrNull())
        assertIs<FoodImportResult.UpToDate>(importer.importIfNewer(foods(1)))
    }
}
