package es.sebas1705.ironlog.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.ironlog.data.local.db.AppDatabase
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Upgrading a database created with the shipped schema (1.db) keeps its data. */
class MigrationTest {
    private val file = File.createTempFile("ironlog-v1", ".db")

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    private fun driver() = JdbcSqliteDriver(
        "jdbc:sqlite:${file.absolutePath}",
        Properties().apply { put("foreign_keys", "true") },
    )

    @Test
    fun `migrating from version 1 keeps routines and sessions and adds the override timestamp`() {
        // The snapshot of the schema that shipped to testers.
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        driver().use { v1 ->
            v1.execute(
                null,
                "INSERT INTO RoutineEntity(id, name, description, isActive, nextDayIndex, createdAt, updatedAt) " +
                    "VALUES ('r1', 'GZCLP', '', 1, 2, 10, 20)",
                0,
            )
            v1.execute(
                null,
                "INSERT INTO RoutineDayEntity(id, routineId, position, name, kind) VALUES ('d1', 'r1', 0, 'Day 1', 'TRAINING')",
                0
            )
            v1.execute(
                null,
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, routineId, routineDayIndex, dayName) " +
                    "VALUES ('s1', 1000, 2000, 'r1', 0, 'Day 1')",
                0,
            )
            AppDatabase.Schema.migrate(v1, 1, AppDatabase.Schema.version)
        }

        val db = AppDatabase(driver())
        val routine = db.routineEntityQueries.selectById("r1").executeAsOne()
        assertEquals("GZCLP", routine.name)
        assertEquals(1L, routine.isActive)
        assertEquals(2L, routine.nextDayIndex)
        assertNull(routine.nextDayIndexSetAt)
        assertEquals("Day 1", db.routineDayEntityQueries.selectByRoutine("r1").executeAsList().single().name)
        val session = db.workoutSessionEntityQueries.selectById("s1").executeAsOne()
        assertEquals(1000L to 2000L, session.startedAt to session.endedAt)
        assertEquals("r1", session.routineId)

        // The new column is usable after the upgrade.
        db.routineEntityQueries.setNextDayIndex(nextDayIndex = 1, setAt = 3000, id = "r1")
        assertEquals(3000L, db.routineEntityQueries.selectById("r1").executeAsOne().nextDayIndexSetAt)
    }

    @Test
    fun `the schema version is the shipped one plus the migrations`() {
        // 1 -> 2: nextDayIndexSetAt; 2 -> 3: sync change tracking (SyncMigrationTest in data:sync covers 1 -> 3).
        assertEquals(3L, AppDatabase.Schema.version)
    }
}
