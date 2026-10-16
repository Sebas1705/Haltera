package es.sebas1705.haltera.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.sebas1705.haltera.data.local.db.AppDatabase
import java.io.File
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Upgrading a database created with the shipped schema (1.db) keeps its data. */
class MigrationTest {
    private val file = File.createTempFile("haltera-v1", ".db")

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
    fun `migrating from version 1 to 4 keeps the data and adds the nutrition tables`() {
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        driver().use { v1 ->
            v1.execute(
                null,
                "INSERT INTO BodyWeightEntity(id, measuredAt, weightGrams, notes) VALUES ('bw1', 5, 80000, 'am')",
                0,
            )
            v1.execute(null, "INSERT INTO Meta(key, value) VALUES ('catalogVersion', '3')", 0)
            AppDatabase.Schema.migrate(v1, 1, AppDatabase.Schema.version)
        }

        val db = AppDatabase(driver())
        assertEquals(80_000L, db.bodyWeightEntityQueries.selectLatest().executeAsOne().weightGrams)
        assertEquals("3", db.metaQueries.get("catalogVersion").executeAsOne())

        // The new tables work, with the recipe items cascading from their recipe.
        db.foodEntityQueries.insert(
            id = "f1", source = "RECIPE", nameEn = "Porridge", nameEs = "Gachas", nameSearch = "porridge gachas",
            brand = null, barcode = null, kcal = 100.0, protein = 4.0, carbs = 15.0, fat = 2.0, fiber = null,
            sugar = null, salt = null, unit = "G", servingNameEn = null, servingNameEs = null, servingGrams = null,
            yieldGrams = 300.0, favourite = 0, updatedAt = 1,
        )
        db.recipeItemEntityQueries.insert("i1", "f1", "usda-1", 50.0, 0)
        db.mealEntryEntityQueries.insert("m1", "2026-10-15", "BREAKFAST", "f1", 250.0, null, 0, 10)
        assertEquals(250.0, db.mealEntryEntityQueries.selectByDate("2026-10-15").executeAsOne().grams)
        assertEquals(1L, db.syncTrackingQueries.selectState("mealDays", "2026-10-15").executeAsOne().dirty)
        assertEquals(1L, db.syncTrackingQueries.selectState("foods", "f1").executeAsOne().dirty)
        db.foodEntityQueries.delete("f1")
        assertEquals(emptyList(), db.recipeItemEntityQueries.selectByRecipe("f1").executeAsList())
    }

    @Test
    fun `migrating from version 4 makes every routine fixed and keeps its days, override and sessions`() {
        File("src/commonMain/sqldelight/databases/4.db").copyTo(file, overwrite = true)
        driver().use { v4 ->
            v4.execute(
                null,
                "INSERT INTO RoutineEntity(id, name, description, isActive, nextDayIndex, createdAt, updatedAt, " +
                    "nextDayIndexSetAt) VALUES ('r1', 'PPL', 'push pull legs', 1, 3, 10, 20, 15)",
                0,
            )
            listOf("Push" to "TRAINING", "Pull" to "TRAINING", "Legs" to "TRAINING", "Rest" to "REST")
                .forEachIndexed { i, (name, kind) ->
                    v4.execute(
                        null,
                        "INSERT INTO RoutineDayEntity(id, routineId, position, name, kind) " +
                            "VALUES ('d$i', 'r1', $i, '$name', '$kind')",
                        0,
                    )
                }
            v4.execute(
                null,
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, routineId, routineDayIndex, dayName) " +
                    "VALUES ('s1', 1000, 2000, 'r1', 2, 'Legs')",
                0,
            )
            AppDatabase.Schema.migrate(v4, 4, AppDatabase.Schema.version)
        }

        val db = AppDatabase(driver())
        val routine = db.routineEntityQueries.selectById("r1").executeAsOne()
        assertEquals("FIXED", routine.restMode)
        assertEquals(0L, routine.restDaysPerWeek)
        assertEquals(0L, routine.restWeekdays)
        assertEquals(listOf("PPL", "push pull legs"), listOf(routine.name, routine.description))
        assertEquals(3L to 15L, routine.nextDayIndex to routine.nextDayIndexSetAt)
        assertEquals(
            listOf("TRAINING", "TRAINING", "TRAINING", "REST"),
            db.routineDayEntityQueries.selectByRoutine("r1").executeAsList().map { it.kind },
        )
        assertEquals(2L, db.workoutSessionEntityQueries.selectById("s1").executeAsOne().routineDayIndex)

        // The new columns are writable, and the routine is tracked for sync again.
        db.routineEntityQueries.setRestPolicy("FLEXIBLE", 2, 0b100_0100, 30, "r1")
        val flexible = db.routineEntityQueries.selectById("r1").executeAsOne()
        assertEquals(
            Triple("FLEXIBLE", 2L, 0b100_0100L),
            Triple(flexible.restMode, flexible.restDaysPerWeek, flexible.restWeekdays)
        )
        assertEquals(1L, db.syncTrackingQueries.selectState("routines", "r1").executeAsOne().dirty)
    }

    @Test
    fun `the schema version is the shipped one plus the migrations`() {
        // 1 -> 2: nextDayIndexSetAt; 2 -> 3: sync change tracking; 3 -> 4: nutrition (SyncMigrationTest in data:sync
        // covers 1 -> 4); 4 -> 5: flexible rest days.
        assertEquals(5L, AppDatabase.Schema.version)
    }
}
