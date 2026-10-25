package es.sebas1705.haltera.data.local.catalog

import es.sebas1705.haltera.data.local.inMemoryDatabase
import es.sebas1705.haltera.data.models.CatalogImportResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BundledCatalogTest {
    // Unit tests run with the module directory as working directory.
    private val bundled = File("../../composeApp/src/commonMain/composeResources/files/catalog.json")

    @Test
    fun `the bundled catalogue imports and counts match the file`() {
        val db = inMemoryDatabase()
        val json = bundled.readText()
        val file = CatalogJson.decodeFromString(CatalogDto.serializer(), json)

        val result = CatalogImporter(db, now = { 0L }).importIfNewer(json)

        val imported = assertIs<CatalogImportResult.Imported>(result)
        assertTrue(imported.exercises > 700, "expected the full catalogue, got ${imported.exercises}")
        assertEquals(file.exercises.size, imported.exercises)
        assertEquals(file.exercises.sumOf { it.variants.size }, imported.variants)
        assertEquals(imported.exercises.toLong(), db.exerciseEntityQueries.countActive().executeAsOne())
        assertEquals(imported.variants.toLong(), db.exerciseVariantEntityQueries.countActive().executeAsOne())
        assertEquals(
            listOf("bench-press.barbell", "bench-press.dumbbell", "bench-press.smith", "bench-press.machine"),
            db.exerciseVariantEntityQueries.selectByExercise("bench-press").executeAsList().map { it.id },
        )
    }
}
