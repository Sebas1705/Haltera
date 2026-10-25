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
    fun `migrating from version 5 keeps custom exercises, their history and sync state, and adds author and origin`() {
        File("src/commonMain/sqldelight/databases/5.db").copyTo(file, overwrite = true)
        driver().use { v5 ->
            v5.execute(
                null,
                "INSERT INTO ExerciseEntity(id, source, nameEn, nameEs, nameEnSearch, nameEsSearch, category, " +
                    "trackingType, level, primaryMuscles, secondaryMuscles, instructionsEn, instructionsEs, deprecated, " +
                    "updatedAt) VALUES ('user-1', 'USER', 'Sled', 'Trineo', 'sled', 'trineo', 'STRENGTH', 'WEIGHT_REPS', " +
                    "'BEGINNER', 'QUADRICEPS', '', '[\"Push.\"]', '[]', 0, 5)",
                0,
            )
            v5.execute(
                null,
                "INSERT INTO ExerciseVariantEntity(id, exerciseId, source, position, equipment, loadType, frames, " +
                    "updatedAt) VALUES ('user-1.machine', 'user-1', 'USER', 0, 'MACHINE', 'MACHINE', '[]', 5)",
                0,
            )
            v5.execute(
                null,
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, dayName) VALUES ('s1', 1000, 2000, '')",
                0,
            )
            v5.execute(
                null,
                "INSERT INTO SessionExerciseEntity(id, sessionId, position, variantId) VALUES ('e1', 's1', 0, 'user-1.machine')",
                0,
            )
            AppDatabase.Schema.migrate(v5, 5, AppDatabase.Schema.version)
        }

        val db = AppDatabase(driver())
        val sled = db.exerciseEntityQueries.selectById("user-1").executeAsOne()
        assertEquals("Sled", sled.nameEn)
        assertEquals("[\"Push.\"]", sled.instructionsEn)
        assertNull(sled.author)
        assertNull(sled.originId)
        assertEquals(listOf("user-1.machine"), db.exerciseEntityQueries.variantsInSessions("user-1").executeAsList())
        assertEquals(1L, db.syncTrackingQueries.selectState("exercises", "user-1").executeAsOne().dirty)

        // The new columns are usable, the update is tracked for sync and the origin is found.
        db.exerciseEntityQueries.setShareOrigin(author = "Ana", originId = "user-abc", id = "user-1")
        assertEquals("user-1", db.exerciseEntityQueries.selectUserByOrigin("user-abc").executeAsOne().id)
        assertEquals("Ana", db.exerciseEntityQueries.selectById("user-1").executeAsOne().author)
        assertEquals(0L, db.syncTrackingQueries.selectState("exercises", "user-1").executeAsOne().deleted)
    }

    @Test
    fun `migrating from version 6 keeps the data and adds the health tables without sync tracking`() {
        File("src/commonMain/sqldelight/databases/6.db").copyTo(file, overwrite = true)
        driver().use { v6 ->
            v6.execute(
                null,
                "INSERT INTO BodyWeightEntity(id, measuredAt, weightGrams, notes) VALUES ('bw1', 5, 80000, '')",
                0,
            )
            v6.execute(
                null,
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, dayName) VALUES ('s1', 1000, 2000, 'Push')",
                0,
            )
            v6.execute(
                null,
                "INSERT INTO MealEntryEntity(id, date, meal, foodId, grams, position, createdAt) " +
                    "VALUES ('m1', '2026-10-15', 'LUNCH', 'usda-1', 100, 0, 1)",
                0,
            )
            AppDatabase.Schema.migrate(v6, 6, AppDatabase.Schema.version)
        }

        val db = AppDatabase(driver())
        assertEquals(80_000L, db.bodyWeightEntityQueries.selectLatest().executeAsOne().weightGrams)
        assertEquals(2000L, db.workoutSessionEntityQueries.selectById("s1").executeAsOne().endedAt)
        assertEquals(100.0, db.mealEntryEntityQueries.selectByDate("2026-10-15").executeAsOne().grams)

        val dirtyBefore = db.syncTrackingQueries.countDirty().executeAsOne()
        db.healthDayEntityQueries.upsert("2026-10-16", 8_500, 320.5, 2_300.0, 57, 445, 9)
        db.healthExportEntityQueries.upsert("WORKOUT", "s1", "1000|2000|Push", 9)
        val day = db.healthDayEntityQueries.selectBetween("2026-10-01", "2026-10-31").executeAsOne()
        assertEquals(8_500L, day.steps)
        assertEquals(445L, day.sleepMinutes)
        assertEquals("s1", db.healthExportEntityQueries.selectFinishedSessionsSince(0).executeAsOne().id)
        // Health tables are device-only: writing them queues nothing for the cloud.
        assertEquals(dirtyBefore, db.syncTrackingQueries.countDirty().executeAsOne())
    }

    /** A version 7 database with routine and free sessions, slots with and without both hands, upgraded to the latest. */
    private fun migratedFromV7(): AppDatabase {
        File("src/commonMain/sqldelight/databases/7.db").copyTo(file, overwrite = true)
        val setRow = "INSERT INTO SetLogEntity(id, sessionExerciseId, position, kind, completed, completedAt) VALUES "
        driver().use { v7 ->
            listOf(
                "INSERT INTO ExerciseEntity(id, source, nameEn, nameEs, nameEnSearch, nameEsSearch, category, " +
                    "trackingType, level, primaryMuscles, secondaryMuscles, instructionsEn, instructionsEs, deprecated, " +
                    "updatedAt) VALUES ('user-1', 'USER', 'Press', 'Press', 'press', 'press', 'STRENGTH', 'WEIGHT_REPS', " +
                    "'BEGINNER', 'CHEST', '', '[]', '[]', 0, 5)",
                "INSERT INTO ExerciseVariantEntity(id, exerciseId, source, position, equipment, loadType, frames, " +
                    "updatedAt) VALUES ('user-1.db', 'user-1', 'USER', 0, 'DUMBBELL', 'DUMBBELL', '[]', 5)",
                "INSERT INTO RoutineEntity(id, name, description, isActive, nextDayIndex, createdAt, updatedAt) " +
                    "VALUES ('r1', 'Upper', '', 1, 0, 10, 20)",
                "INSERT INTO RoutineDayEntity(id, routineId, position, name, kind) VALUES ('d1', 'r1', 0, 'A', 'TRAINING')",
                "INSERT INTO RoutineSlotEntity(id, dayId, position, target, sets, prescription, restBetweenSetsS, " +
                    "restAfterS, bilateral, progression) VALUES ('both', 'd1', 0, '{}', 3, '{}', 90, 120, 1, '{}')",
                "INSERT INTO RoutineSlotEntity(id, dayId, position, target, sets, prescription, restBetweenSetsS, " +
                    "restAfterS, bilateral, progression) VALUES ('one', 'd1', 1, '{}', 3, '{}', 90, 120, 0, '{}')",
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, routineId, routineDayIndex, dayName) " +
                    "VALUES ('s1', 1000, 2000, 'r1', 0, 'A')",
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, dayName) VALUES ('s2', 3000, 4000, NULL)",
                // The session of a routine deleted on alpha.4: the link is gone, the day index was left behind.
                "INSERT INTO WorkoutSessionEntity(id, startedAt, endedAt, routineDayIndex, dayName) VALUES ('s3', 5000, 6000, 1, 'B')",
                // From the "Both hands" slot, from a one-hand slot, added outside the plan.
                "INSERT INTO SessionExerciseEntity(id, sessionId, position, variantId, slotId) VALUES ('e1', 's1', 0, 'user-1.db', 'both')",
                "INSERT INTO SessionExerciseEntity(id, sessionId, position, variantId, slotId) VALUES ('e2', 's1', 1, 'user-1.db', 'one')",
                "INSERT INTO SessionExerciseEntity(id, sessionId, position, variantId, slotId) VALUES ('e3', 's1', 2, 'user-1.db', NULL)",
                // A free session, and an exercise whose slot was removed before the upgrade (the link is already NULL).
                "INSERT INTO SessionExerciseEntity(id, sessionId, position, variantId, slotId) VALUES ('e4', 's2', 0, 'user-1.db', NULL)",
                // Sets of a past session ticked days after it ended, one ticked during it, one before a moved start.
                setRow + "('t1', 'e1', 0, 'WORKING', 1, 999999)",
                setRow + "('t2', 'e4', 0, 'WORKING', 1, 3500)",
                setRow + "('t3', 'e1', 1, 'WORKING', 1, 10)",
                "UPDATE SyncState SET dirty = 0",
            ).forEach { v7.execute(null, it, 0) }
            AppDatabase.Schema.migrate(v7, 7, AppDatabase.Schema.version)
        }
        return AppDatabase(driver())
    }

    @Test
    fun `migrating from version 7 backfills both hands, the routine name and set times without queueing uploads`() {
        val db = migratedFromV7()
        val flags = db.sessionExerciseEntityQueries.selectBySessions(listOf("s1", "s2")).executeAsList()
            .associate { it.id to it.bilateral }
        assertEquals(mapOf("e1" to 1L, "e2" to 0L, "e3" to 0L, "e4" to 0L), flags)
        // Completion times of finished sessions are brought inside the session.
        assertEquals(2000L, db.setLogEntityQueries.selectById("t1").executeAsOne().completedAt)
        assertEquals(3500L, db.setLogEntityQueries.selectById("t2").executeAsOne().completedAt)
        assertEquals(1000L, db.setLogEntityQueries.selectById("t3").executeAsOne().completedAt)
        // The routine name is kept from the routine that still exists; a session without one gets none and no day.
        val s1 = db.workoutSessionEntityQueries.selectById("s1").executeAsOne()
        assertEquals("Upper", s1.routineName)
        assertEquals(0L, s1.routineDayIndex)
        val s3 = db.workoutSessionEntityQueries.selectById("s3").executeAsOne()
        assertNull(s3.routineName)
        assertNull(s3.routineDayIndex)
        assertEquals("B", s3.dayName)
        // The backfill queues nothing for the cloud: older documents decode to the same values.
        assertEquals(0L, db.syncTrackingQueries.countDirty().executeAsOne())
        assertEquals("0", db.metaQueries.get("syncApplying").executeAsOneOrNull())
    }

    @Test
    fun `after the upgrade the snapshots outlive slots and routines`() {
        val db = migratedFromV7()
        // The snapshot survives the slot: removing it clears the link only.
        db.routineSlotEntityQueries.delete("both")
        val e1 = db.sessionExerciseEntityQueries.selectById("e1").executeAsOne()
        assertNull(e1.slotId)
        assertEquals(1L, e1.bilateral)
        // New exercises snapshot the slot's flag when they are logged.
        db.sessionExerciseEntityQueries.insert("e6", "s2", 2, "user-1.db", "one")
        db.routineSlotEntityQueries.update(
            target = "{}", sets = 3, warmupSets = 0, prescription = "{}", restBetweenSetsS = 90, restAfterS = 120,
            supersetGroup = null, bilateral = 1, notes = "", progression = "{}", id = "one",
        )
        db.sessionExerciseEntityQueries.insert("e7", "s2", 3, "user-1.db", "one")
        db.sessionExerciseEntityQueries.insert("e8", "s2", 4, "user-1.db", null)
        assertEquals(0L, db.sessionExerciseEntityQueries.selectById("e6").executeAsOne().bilateral)
        assertEquals(1L, db.sessionExerciseEntityQueries.selectById("e7").executeAsOne().bilateral)
        assertEquals(0L, db.sessionExerciseEntityQueries.selectById("e8").executeAsOne().bilateral)

        // Deleting the routine keeps the name and clears the day (EXPORT-FORMAT.md: routine_day_index empty without one).
        db.routineEntityQueries.delete("r1")
        val orphan = db.workoutSessionEntityQueries.selectById("s1").executeAsOne()
        assertNull(orphan.routineId)
        assertNull(orphan.routineDayIndex)
        assertEquals("Upper", orphan.routineName)
    }

    @Test
    fun `migrating from version 1 to the latest also creates the health tables`() {
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        driver().use { v1 -> AppDatabase.Schema.migrate(v1, 1, AppDatabase.Schema.version) }
        val db = AppDatabase(driver())
        assertEquals(0L, db.healthDayEntityQueries.countAll().executeAsOne())
        assertEquals(emptyList(), db.healthExportEntityQueries.selectByKind("WORKOUT").executeAsList())
    }

    @Test
    fun `the schema version is the shipped one plus the migrations`() {
        // 1 -> 2: nextDayIndexSetAt; 2 -> 3: sync change tracking; 3 -> 4: nutrition (SyncMigrationTest in data:sync
        // covers 1 -> 4); 4 -> 5: flexible rest days; 5 -> 6: author and origin of custom exercises;
        // 6 -> 7: Health Connect cache and export ledger;
        // 7 -> 8: the "Both hands" snapshot of logged exercises.
        assertEquals(8L, AppDatabase.Schema.version)
    }
}
