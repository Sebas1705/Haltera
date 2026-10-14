package es.sebas1705.ironlog.data.local

import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SchemaQueriesTest {
    private val db = inMemoryDatabase()

    private val catalog = """
        {"catalogVersion":1,"contentHash":"h","deprecatedIds":["old-lift"],"exercises":[
          {"id":"bench-press","name":{"en":"Bench Press","es":"Press de banca"},"category":"STRENGTH",
           "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["CHEST"],"secondaryMuscles":["TRICEPS"],
           "instructions":{"en":[],"es":[]},
           "variants":[{"id":"bench-press.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]},
                       {"id":"bench-press.dumbbell","equipment":"DUMBBELL","loadType":"DUMBBELL","frames":[]}]},
          {"id":"squat","name":{"en":"Squat","es":"Sentadilla"},"category":"STRENGTH",
           "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":["GLUTES"],
           "instructions":{"en":[],"es":[]},
           "variants":[{"id":"squat.barbell","equipment":"BARBELL","loadType":"BARBELL","frames":[]}]},
          {"id":"running","name":{"en":"Running","es":"Carrera a pie"},"category":"CARDIO",
           "trackingType":"DISTANCE_TIME","level":"BEGINNER","primaryMuscles":["QUADRICEPS"],"secondaryMuscles":[],
           "instructions":{"en":[],"es":[]},
           "variants":[{"id":"running.outdoor","equipment":"NONE","loadType":"NONE","frames":[]}]},
          {"id":"tricep-extension","name":{"en":"Triceps Extension","es":"Extensión de tríceps"},"category":"STRENGTH",
           "trackingType":"WEIGHT_REPS","level":"BEGINNER","primaryMuscles":["TRICEPS"],"secondaryMuscles":[],
           "instructions":{"en":[],"es":[]},
           "variants":[{"id":"tricep-extension.cable","equipment":"CABLE","loadType":"MACHINE","frames":[]}]}
        ]}
    """.trimIndent()

    private fun search(
        query: String = "",
        category: String? = null,
        muscle: String? = null,
        equipment: String? = null,
        language: String = "en",
    ) = db.exerciseEntityQueries.search(SearchText.query(query), category, muscle, equipment, language, 100)
        .executeAsList().map { it.id }

    @Test
    fun `normalize lowers case, folds accents and collapses spaces`() {
        assertEquals("extension de triceps", SearchText.normalize("  Extensión  de TRÍCEPS "))
        assertEquals("cana nino", SearchText.normalize("Caña Niño"))
        assertEquals("50 press", SearchText.query("50% pre_ss"))
    }

    @Test
    fun `search is accent and case insensitive in both languages`() {
        CatalogImporter(db, now = { 0L }).importIfNewer(catalog)

        assertEquals(listOf("tricep-extension"), search("TRICEPS"))
        assertEquals(listOf("tricep-extension"), search("extensión de tríceps"))
        assertEquals(listOf("tricep-extension"), search("extension de triceps"))
        assertEquals(listOf("squat"), search("sentadilla"))
        assertEquals(4, search().size)
    }

    @Test
    fun `filters by category, primary muscle and equipment`() {
        CatalogImporter(db, now = { 0L }).importIfNewer(catalog)

        assertEquals(listOf("running"), search(category = "CARDIO"))
        assertEquals(listOf("running", "squat"), search(muscle = "QUADRICEPS"))
        assertEquals(emptyList(), search(muscle = "GLUTES"))
        assertEquals(listOf("bench-press"), search(equipment = "DUMBBELL"))
        assertEquals(listOf("bench-press", "squat"), search(equipment = "BARBELL"))
        assertEquals(listOf("squat"), search(query = "squat", equipment = "BARBELL", muscle = "QUADRICEPS"))
    }

    @Test
    fun `results are sorted by name in the requested language`() {
        CatalogImporter(db, now = { 0L }).importIfNewer(catalog)

        assertEquals(listOf("bench-press", "running", "squat", "tricep-extension"), search(language = "en"))
        assertEquals(listOf("running", "tricep-extension", "bench-press", "squat"), search(language = "es"))
    }

    @Test
    fun `deleting a routine cascades to days and slots and detaches sessions`() {
        CatalogImporter(db, now = { 0L }).importIfNewer(catalog)
        db.routineEntityQueries.insert("r1", "Mine", "", null, null, 0, 0)
        db.routineDayEntityQueries.insert("d1", "r1", 0, "A", "TRAINING")
        db.routineSlotEntityQueries.insert("s1", "d1", 0, "{}", 3, 0, "{}", 90, 90, null, 0, "", "{}")
        db.workoutSessionEntityQueries.insert("w1", 10, "r1", 0, "A")
        db.sessionExerciseEntityQueries.insert("e1", "w1", 0, "squat.barbell", "s1")

        db.routineEntityQueries.delete("r1")

        assertNull(db.routineDayEntityQueries.selectById("d1").executeAsOneOrNull())
        assertNull(db.routineSlotEntityQueries.selectById("s1").executeAsOneOrNull())
        val session = db.workoutSessionEntityQueries.selectById("w1").executeAsOne()
        assertNull(session.routineId)
        assertNull(db.sessionExerciseEntityQueries.selectById("e1").executeAsOne().slotId)
    }

    @Test
    fun `deleting a session cascades to its exercises and sets`() {
        CatalogImporter(db, now = { 0L }).importIfNewer(catalog)
        db.workoutSessionEntityQueries.insert("w1", 10, null, null, null)
        db.sessionExerciseEntityQueries.insert("e1", "w1", 0, "squat.barbell", null)
        db.setLogEntityQueries.insert("set1", "e1", 0, "WORKING", 5, 100_000, null, null, null, null, 1, 20)

        db.workoutSessionEntityQueries.delete("w1")

        assertNull(db.setLogEntityQueries.selectById("set1").executeAsOneOrNull())
        assertNull(db.sessionExerciseEntityQueries.selectById("e1").executeAsOneOrNull())
    }

    @Test
    fun `foreign keys reject unknown variants`() {
        db.workoutSessionEntityQueries.insert("w1", 10, null, null, null)

        assertFailsWith<Exception> { db.sessionExerciseEntityQueries.insert("e1", "w1", 0, "nope.none", null) }
    }
}
