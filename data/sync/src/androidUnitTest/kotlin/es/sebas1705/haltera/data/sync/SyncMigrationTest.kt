package es.sebas1705.ironlog.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import es.sebas1705.ironlog.core.datastore.KeyValueSettingsRepository
import es.sebas1705.ironlog.data.local.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * An install on the shipped schema v1 (with data) upgrades through the migrations: the data survives,
 * the change-tracking triggers work afterwards, and the backfill uploads what was logged before sync.
 */
class SyncMigrationTest {
    private fun shippedV1(): JdbcSqliteDriver {
        val copy = File.createTempFile("ironlog-v1", ".db").apply { deleteOnExit() }
        File("../local/src/commonMain/sqldelight/databases/1.db").copyTo(copy, overwrite = true)
        return JdbcSqliteDriver("jdbc:sqlite:${copy.path}", Properties().apply { put("foreign_keys", "true") })
    }

    @Test
    fun `a v1 database with data migrates, keeps it, tracks changes and backfills`() = runTest {
        val driver = shippedV1()
        listOf(
            "INSERT INTO RoutineEntity(id, name, description, isActive, nextDayIndex, createdAt, updatedAt) " +
                "VALUES ('r1', 'Mine', '', 1, 0, 1, 1)",
            "INSERT INTO RoutineDayEntity(id, routineId, position, name, kind) VALUES ('d1', 'r1', 0, 'A', 'TRAINING')",
            "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, routineId, notes) VALUES ('w1', 10, 20, 'r1', '')",
            "INSERT INTO BodyWeightEntity(id, measuredAt, weightGrams, notes) VALUES ('bw1', 5, 80000, '')",
        ).forEach { driver.execute(null, it, 0) }

        AppDatabase.Schema.migrate(driver, 1, AppDatabase.Schema.version)
        val db = AppDatabase(driver)

        assertEquals("Mine", db.routineEntityQueries.selectById("r1").executeAsOne().name)
        assertEquals(1, db.routineDayEntityQueries.selectByRoutine("r1").executeAsList().size)
        assertNotNull(db.workoutSessionEntityQueries.selectById("w1").executeAsOneOrNull())
        assertTrue(db.syncTrackingQueries.selectDirty(100).executeAsList().isEmpty())

        // Triggers fire after the migration.
        db.bodyWeightEntityQueries.upsert("bw2", 6, 81_000, "")
        assertEquals(1L, db.syncTrackingQueries.selectState(SyncCollections.BODY_WEIGHT, "bw2").executeAsOne().dirty)

        // The first engine start backfills the data logged before the upgrade.
        val cloud = FakeCloud()
        val bridge = SettingsSyncBridge(KeyValueSettingsRepository(MapSettings()), db)
        SyncEngineImpl(db, cloud, FakeAccount(), bridge, Dispatchers.Unconfined, now = { 0L }).syncNow()
        listOf(
            SyncCollections.ROUTINES to "r1",
            SyncCollections.SESSIONS to "w1",
            SyncCollections.BODY_WEIGHT to "bw1",
            SyncCollections.BODY_WEIGHT to "bw2",
            SyncCollections.SETTINGS to SyncCollections.SETTINGS_ID, // the active routine
        ).forEach { (c, id) -> assertNotNull(cloud.doc("uid-1", c, id), "$c/$id") }
    }
}
