package es.sebas1705.ironlog.data.local.catalog

import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.local.inMemoryDriver
import es.sebas1705.ironlog.data.models.CatalogImportResult
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class CatalogImporterTest {
    private val driver = inMemoryDriver()
    private val db = AppDatabase(driver)
    private var clock = 1_000L
    private val importer = CatalogImporter(db, now = { clock })

    private val squat = """,{"id":"squat","name":{"en":"Squat","es":"Sentadilla"},"category":"STRENGTH",
        "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":[],
        "instructions":{"en":["Squat."],"es":["Agáchate."]},
        "variants":[{"id":"squat.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]}]}"""

    private fun catalog(version: Int, benchEs: String = "Press de banca", deprecated: String = "", extra: String = "") = """
        {"catalogVersion":$version,"contentHash":"h$version","deprecatedIds":[$deprecated],"exercises":[
          {"id":"bench-press","name":{"en":"Bench Press","es":"$benchEs"},"category":"STRENGTH",
           "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["CHEST"],
           "secondaryMuscles":["SHOULDERS","TRICEPS"],"instructions":{"en":["Lie down."],"es":["Túmbate."]},
           "variants":[
             {"id":"bench-press.barbell","equipment":"BARBELL","loadType":"BARBELL","name":null,
              "instructions":{"en":["Lie down."],"es":["Túmbate."]},"frames":["B/0.jpg","B/1.jpg"]},
             {"id":"bench-press.dumbbell","equipment":"DUMBBELL","loadType":"DUMBBELL","name":null,
              "instructions":null,"frames":[]}]}
          $extra
        ]}
    """.trimIndent()

    private fun insertUserExercise(id: String) = db.exerciseEntityQueries.insertUser(
        id = id, nameEn = "My lift", nameEs = "Mi ejercicio", nameEnSearch = "my lift", nameEsSearch = "mi ejercicio",
        category = "STRENGTH",
        trackingType = "WEIGHT_REPS", level = "BEGINNER", primaryMuscles = "CHEST", secondaryMuscles = "",
        instructionsEn = "[]", instructionsEs = "[]", updatedAt = 1L,
    )

    @Test
    fun `first import inserts exercises and variants and stores the version`() {
        val result = importer.importIfNewer(catalog(1))

        assertEquals(CatalogImportResult.Imported(version = 1, exercises = 1, variants = 2), result)
        assertEquals(1, importer.installedVersion())
        val bench = db.exerciseEntityQueries.selectById("bench-press").executeAsOne()
        assertEquals("CATALOG", bench.source)
        assertEquals("CHEST", bench.primaryMuscles)
        assertEquals("SHOULDERS,TRICEPS", bench.secondaryMuscles)
        assertEquals("[\"Túmbate.\"]", bench.instructionsEs)
        assertEquals("bench press", bench.nameEnSearch)
        assertEquals("press de banca", bench.nameEsSearch)
        val variants = db.exerciseVariantEntityQueries.selectByExercise("bench-press").executeAsList()
        assertEquals(listOf("bench-press.barbell", "bench-press.dumbbell"), variants.map { it.id })
        assertEquals("[\"B/0.jpg\",\"B/1.jpg\"]", variants[0].frames)
        assertEquals(null, variants[1].instructionsEn)
    }

    @Test
    fun `same or older version is not imported again`() {
        importer.importIfNewer(catalog(2))
        clock = 2_000L

        assertIs<CatalogImportResult.UpToDate>(importer.importIfNewer(catalog(2, benchEs = "Cambiado")))
        assertIs<CatalogImportResult.UpToDate>(importer.importIfNewer(catalog(1)))
        assertEquals("Press de banca", db.exerciseEntityQueries.selectById("bench-press").executeAsOne().nameEs)
    }

    @Test
    fun `newer version updates rows, adds new ones and deprecates removed ids`() {
        importer.importIfNewer(catalog(1))
        clock = 2_000L

        val result = importer.importIfNewer(
            catalog(2, benchEs = "Press banca", deprecated = "\"bench-press.dumbbell\"", extra = squat),
        )

        assertEquals(CatalogImportResult.Imported(version = 2, exercises = 2, variants = 3), result)
        val bench = db.exerciseEntityQueries.selectById("bench-press").executeAsOne()
        assertEquals("Press banca", bench.nameEs)
        assertEquals(2_000L, bench.updatedAt)
        assertEquals(1L, db.exerciseVariantEntityQueries.selectById("bench-press.dumbbell").executeAsOne().deprecated)
        assertEquals(2L, db.exerciseEntityQueries.countActive().executeAsOne())
        assertEquals(2, importer.installedVersion())
    }

    @Test
    fun `user exercises are never touched`() {
        insertUserExercise("user-1")

        importer.importIfNewer(catalog(1, deprecated = "\"user-1\""))

        val mine = db.exerciseEntityQueries.selectById("user-1").executeAsOne()
        assertEquals(0L, mine.deprecated)
        assertEquals("USER", mine.source)
    }

    @Test
    fun `catalogue exercise whose id belongs to a user row is skipped with its variants`() {
        insertUserExercise("bench-press")

        val result = importer.importIfNewer(catalog(1, extra = squat))

        assertEquals(CatalogImportResult.Imported(version = 1, exercises = 1, variants = 1), result)
        val mine = db.exerciseEntityQueries.selectById("bench-press").executeAsOne()
        assertEquals("USER", mine.source)
        assertEquals("Mi ejercicio", mine.nameEs)
        assertEquals(1L, mine.updatedAt)
        assertEquals(emptyList(), db.exerciseVariantEntityQueries.selectByExercise("bench-press").executeAsList())
        assertEquals(1L, db.exerciseVariantEntityQueries.countActive().executeAsOne())
    }

    @Test
    fun `malformed catalogue leaves the installed one untouched`() {
        importer.importIfNewer(catalog(1))

        assertFailsWith<SerializationException> {
            importer.importIfNewer("{\"catalogVersion\":2,\"exercises\":[{\"id\":")
        }

        assertEquals(1, importer.installedVersion())
        assertEquals(1L, db.exerciseEntityQueries.countActive().executeAsOne())
    }

    @Test
    fun `a failure mid-import rolls back every write`() {
        importer.importIfNewer(catalog(1))
        driver.execute(
            identifier = null,
            sql = "CREATE TRIGGER boom BEFORE INSERT ON ExerciseVariantEntity " +
                "WHEN NEW.id = 'squat.barbell' BEGIN SELECT RAISE(ABORT, 'boom'); END",
            parameters = 0,
        )

        assertFailsWith<Exception> { importer.importIfNewer(catalog(2, benchEs = "Press banca", extra = squat)) }

        assertEquals(1, importer.installedVersion())
        assertEquals("Press de banca", db.exerciseEntityQueries.selectById("bench-press").executeAsOne().nameEs)
        assertNull(db.exerciseEntityQueries.selectById("squat").executeAsOneOrNull())
    }
}
