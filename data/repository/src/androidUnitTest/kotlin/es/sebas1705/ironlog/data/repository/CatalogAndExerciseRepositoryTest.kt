package es.sebas1705.ironlog.data.repository

import app.cash.turbine.test
import es.sebas1705.ironlog.data.local.catalog.CatalogImporter
import es.sebas1705.ironlog.data.models.CatalogSummary
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.NewExercise
import es.sebas1705.ironlog.data.models.NewVariant
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.repository.sql.HistoryLoader
import es.sebas1705.ironlog.data.repository.sql.SqlCatalogRepository
import es.sebas1705.ironlog.data.repository.sql.SqlExerciseRepository
import es.sebas1705.ironlog.data.repository.sql.SqlSessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogAndExerciseRepositoryTest {
    private val db = inMemoryDatabase()
    private val clock = TestClock()

    @Test
    fun `catalogue summary reflects the import`() = runTest {
        val repository = SqlCatalogRepository(
            db,
            CatalogImporter(db, now = clock),
            UnconfinedTestDispatcher(testScheduler)
        )

        repository.observeSummary().test {
            assertEquals(CatalogSummary(version = null, exercises = 0, variants = 0), awaitItem())
            repository.importBundled(TEST_CATALOG)
            assertEquals(CatalogSummary(version = 1, exercises = 3, variants = 4), expectMostRecentItem())
        }
    }

    @Test
    fun `search returns exercises with variants and filters`() = runTest {
        db.withTestCatalog()
        val repository = SqlExerciseRepository(db, HistoryLoader(db), UnconfinedTestDispatcher(testScheduler), clock)

        val all = repository.search(ExerciseFilter(), "en").first()
        assertEquals(listOf("bench-press", "plank", "squat"), all.map { it.id })
        assertEquals(listOf("bench-press.barbell", "bench-press.dumbbell"), all.first().variants.map { it.id })
        assertEquals(listOf("b/0.jpg"), all.first().variants.first().frames)
        assertEquals(listOf("Túmbate."), all.first().instructions.es)

        assertEquals(
            listOf("bench-press"),
            repository.search(ExerciseFilter(query = "PRÉSS"), "es").first().map { it.id }
        )
        assertEquals(
            listOf("squat"),
            repository.search(ExerciseFilter(muscle = Muscle.QUADRICEPS), "en").first().map { it.id }
        )
        assertEquals(
            listOf("bench-press"),
            repository.search(ExerciseFilter(equipment = Equipment.DUMBBELL), "en").first().map { it.id },
        )
        assertEquals(
            listOf("plank", "bench-press", "squat"),
            repository.search(ExerciseFilter(), "es").first().map { it.id }
        )
    }

    @Test
    fun `custom exercise gets a user id and appears in search`() = runTest {
        db.withTestCatalog()
        val repository = SqlExerciseRepository(db, HistoryLoader(db), UnconfinedTestDispatcher(testScheduler), clock)

        val created = repository.createCustom(
            NewExercise(
                name = LocalizedText(en = "Landmine Press", es = "Press con mina"),
                category = ExerciseCategory.STRENGTH,
                trackingType = TrackingType.WEIGHT_REPS,
                primaryMuscles = listOf(Muscle.SHOULDERS),
                variants = listOf(
                    NewVariant(Equipment.BARBELL, LoadType.BARBELL),
                    NewVariant(Equipment.BARBELL, LoadType.BARBELL)
                ),
            ),
        )

        assertTrue(created.id.startsWith("user-"))
        assertEquals(ExerciseSource.USER, created.source)
        assertEquals(listOf("${created.id}.barbell", "${created.id}.barbell-2"), created.variants.map { it.id })
        assertEquals(listOf(created.id), repository.search(ExerciseFilter(query = "mina"), "es").first().map { it.id })
        assertEquals(created, repository.getExercise(created.id))
    }

    @Test
    fun `custom exercise without variants gets a single NONE variant`() = runTest {
        db.withTestCatalog()
        val repository = SqlExerciseRepository(db, HistoryLoader(db), UnconfinedTestDispatcher(testScheduler), clock)

        val created = repository.createCustom(
            NewExercise(LocalizedText.same("Stretch"), ExerciseCategory.MOBILITY, TrackingType.TIME),
        )

        assertEquals(listOf(Equipment.NONE), created.variants.map { it.equipment })
    }

    @Test
    fun `variant history lists finished sessions latest first`() = runTest {
        db.withTestCatalog()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val history = HistoryLoader(db)
        val exercises = SqlExerciseRepository(db, history, dispatcher, clock)
        val sessions = SqlSessionRepository(db, history, dispatcher, clock)

        repeat(2) { i ->
            clock.millis = 10_000L * (i + 1)
            val session = sessions.startFree()
            val e = sessions.addExercise(session, "squat.barbell")
            sessions.logSet(e, SetInput(reps = 5, loadGrams = 100_000L + i * 2_500))
            sessions.finish(session)
        }
        sessions.addExercise(sessions.startFree(), "squat.barbell") // in progress: not history

        val entries = exercises.observeVariantHistory("squat.barbell").first()
        assertEquals(listOf(20_000L, 10_000L), entries.map { it.sessionStartedAt })
        assertEquals(listOf(102_500L), entries.first().exercise.sets.map { it.loadGrams })
        assertEquals(2, exercises.observeExerciseHistory("squat").first().size)
        assertNull(exercises.getVariant("nope"))
        assertEquals(
            setOf("squat.barbell", "plank.bodyweight"),
            exercises.getVariants(listOf("squat.barbell", "plank.bodyweight", "x")).map { it.id }.toSet()
        )
    }
}
