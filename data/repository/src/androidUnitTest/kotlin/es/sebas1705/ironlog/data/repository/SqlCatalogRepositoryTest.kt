package es.sebas1705.ironlog.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.local.db.AppDatabase
import es.sebas1705.ironlog.data.models.CatalogImportResult
import es.sebas1705.ironlog.data.models.CatalogSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SqlCatalogRepositoryTest {
    private val catalog = """
        {"catalogVersion":3,"contentHash":"h","deprecatedIds":[],"exercises":[
          {"id":"plank","name":{"en":"Plank","es":"Plancha"},"category":"STRENGTH","trackingType":"TIME",
           "level":"BEGINNER","primaryMuscles":["ABDOMINALS"],"secondaryMuscles":[],
           "instructions":{"en":["Hold."],"es":["Aguanta."]},
           "variants":[{"id":"plank.bodyweight","equipment":"BODYWEIGHT","loadType":"BODYWEIGHT","frames":[]}]}]}
    """.trimIndent()

    // Foreign keys on, as on the device.
    private fun inMemoryDatabase(): AppDatabase = AppDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
            .also { AppDatabase.Schema.create(it) },
    )

    @Test
    fun `summary reflects the import`() = runTest {
        val db = inMemoryDatabase()
        val repository = SqlCatalogRepository(
            db,
            CatalogImporter(db, now = { 0L }),
            UnconfinedTestDispatcher(testScheduler),
        )

        repository.observeSummary().test {
            assertEquals(CatalogSummary(version = null, exercises = 0, variants = 0), awaitItem())
            val result = repository.importBundled(catalog)
            assertEquals(CatalogImportResult.Imported(version = 3, exercises = 1, variants = 1), result)
            assertEquals(CatalogSummary(version = 3, exercises = 1, variants = 1), expectMostRecentItem())
        }
    }
}
