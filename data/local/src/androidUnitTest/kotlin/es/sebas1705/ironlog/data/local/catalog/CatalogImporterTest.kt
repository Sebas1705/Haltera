package es.sebas1705.ironlog.data.local.catalog

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CatalogImporterTest {
    private val db = AppDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) })
    private var clock = 1_000L
    private val importer = CatalogImporter(db, now = { clock })

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
        val squat = """,{"id":"squat","name":{"en":"Squat","es":"Sentadilla"},"category":"STRENGTH",
            "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":[],
            "instructions":{"en":["Squat."],"es":["Agáchate."]},
            "variants":[{"id":"squat.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]}]}"""

        importer.importIfNewer(
            catalog(2, benchEs = "Press banca", deprecated = "\"bench-press.dumbbell\"", extra = squat),
        )

        val bench = db.exerciseEntityQueries.selectById("bench-press").executeAsOne()
        assertEquals("Press banca", bench.nameEs)
        assertEquals(2_000L, bench.updatedAt)
        assertEquals(1L, db.exerciseVariantEntityQueries.selectById("bench-press.dumbbell").executeAsOne().deprecated)
        assertEquals(2L, db.exerciseEntityQueries.countActive().executeAsOne())
        assertEquals(2, importer.installedVersion())
    }

    @Test
    fun `user exercises are never touched`() {
        db.exerciseEntityQueries.insertUser(
            id = "user-1", nameEn = "My lift", nameEs = "Mi ejercicio", category = "STRENGTH",
            trackingType = "WEIGHT_REPS", level = "BEGINNER", primaryMuscles = "CHEST", secondaryMuscles = "",
            instructionsEn = "[]", instructionsEs = "[]", updatedAt = 1L,
        )

        importer.importIfNewer(catalog(1, deprecated = "\"user-1\""))

        val mine = db.exerciseEntityQueries.selectById("user-1").executeAsOne()
        assertEquals(0L, mine.deprecated)
        assertEquals("USER", mine.source)
    }

    @Test
    fun `malformed catalogue leaves the installed one untouched`() {
        importer.importIfNewer(catalog(1))

        assertFailsWith<Exception> { importer.importIfNewer("{\"catalogVersion\":2,\"exercises\":[{\"id\":") }

        assertEquals(1, importer.installedVersion())
        assertEquals(1L, db.exerciseEntityQueries.countActive().executeAsOne())
    }
}
