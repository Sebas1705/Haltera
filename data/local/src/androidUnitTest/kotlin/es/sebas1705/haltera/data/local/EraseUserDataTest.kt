package es.sebas1705.ironlog.data.local

import kotlin.test.Test
import kotlin.test.assertEquals

class EraseUserDataTest {
    private val driver = inMemoryDriver()
    private val db = es.sebas1705.ironlog.data.local.db.AppDatabase(driver)

    private fun exec(sql: String) = driver.execute(null, sql, 0)

    private fun count(table: String, where: String = "1 = 1"): Long =
        driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM $table WHERE $where",
            { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            0,
        ).value

    private fun food(id: String, source: String) = exec(
        "INSERT INTO FoodEntity(id, source, nameEn, nameEs, kcal, protein, carbs, fat, updatedAt) " +
            "VALUES ('$id', '$source', 'n', 'n', 1, 1, 1, 1, 1)",
    )

    @Test
    fun `erases user data and keeps the catalogue`() {
        db.exerciseEntityQueries.insertCatalogIfMissing(
            "cat", "Bench", "Banca", "bench", "banca", "STRENGTH", "WEIGHT_REPS", "BEGINNER", "CHEST", "", "[]", "[]", 1L,
        )
        db.exerciseEntityQueries.insertUser(
            "mine", "My", "Mi", "my", "mi", "STRENGTH", "WEIGHT_REPS", "BEGINNER", "CHEST", "", "[]", "[]", 1L,
        )
        food("generic", "GENERIC")
        food("custom", "USER")
        food("off", "OFF")
        exec("INSERT INTO BodyWeightEntity(id, measuredAt, weightGrams, notes) VALUES ('b', 1, 80000, '')")
        exec("INSERT INTO RoutineEntity(id, name, createdAt, updatedAt) VALUES ('r', 'PPL', 1, 1)")
        exec("INSERT INTO WorkoutSessionEntity(id, startedAt, routineId) VALUES ('s', 1, 'r')")
        exec("INSERT INTO SyncCursor(collection, sinceUpdatedAt) VALUES ('routines', 5)")
        assert(count("SyncState") > 0) // The triggers tracked the inserts.

        db.eraseUserData()

        assertEquals(1, count("ExerciseEntity"))
        assertEquals(1, count("ExerciseEntity", "source = 'CATALOG'"))
        assertEquals(1, count("FoodEntity"))
        assertEquals(1, count("FoodEntity", "source = 'GENERIC'"))
        assertEquals(0, count("BodyWeightEntity"))
        assertEquals(0, count("RoutineEntity"))
        assertEquals(0, count("WorkoutSessionEntity"))
        assertEquals(0, count("SyncState"))
        assertEquals(0, count("SyncCursor"))
        // Tracking is back on afterwards.
        db.bodyWeightEntityQueries.upsert("b2", 2L, 81_000L, "")
        assertEquals(1, count("SyncState"))
    }
}
