package es.sebas1705.haltera.data.repository

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.foods.FoodImporter
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.export.ByteArrayOutput
import es.sebas1705.haltera.data.repository.export.SqlDataExporter
import es.sebas1705.haltera.data.repository.off.OpenFoodFactsClient
import es.sebas1705.haltera.data.repository.share.DefaultRoutineSharing
import es.sebas1705.haltera.data.repository.sql.SqlNutritionRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import es.sebas1705.haltera.data.repository.importer.ExerciseDecision
import es.sebas1705.haltera.data.repository.importer.ExerciseMatcher
import es.sebas1705.haltera.data.repository.importer.ExistingPolicy
import es.sebas1705.haltera.data.repository.importer.HistoryFileParser
import es.sebas1705.haltera.data.repository.importer.ImportOptions
import es.sebas1705.haltera.data.repository.importer.ImportSource
import es.sebas1705.haltera.data.repository.importer.ImportSummary
import es.sebas1705.haltera.data.repository.importer.ParsedImport
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import es.sebas1705.haltera.data.repository.importer.ParseOptions
import es.sebas1705.haltera.data.repository.importer.SqlHistoryImporter
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class HistoryImporterTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(1_000L)
    private val utc = ParseOptions(TimeZone.UTC)

    private class Env(
        val importer: SqlHistoryImporter,
        val sessions: SqlSessionRepository,
        val exercises: SqlExerciseRepository,
        val bodyWeights: SqlBodyWeightRepository,
    )

    private fun TestScope.env(): Env {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val history = HistoryLoader(db)
        val exercises = SqlExerciseRepository(db, history, dispatcher, clock)
        return Env(
            SqlHistoryImporter(db, exercises, dispatcher),
            SqlSessionRepository(db, history, dispatcher, clock),
            exercises,
            SqlBodyWeightRepository(db, dispatcher, clock),
        )
    }

    private fun parse(name: String) = HistoryFileParser.parse(fixtureBytes(name), utc)

    private suspend fun Env.history(): List<WorkoutSession> = sessions.observeHistory(10_000).first()

    // ----- matcher -----

    private suspend fun matcher(env: Env) =
        ExerciseMatcher(env.exercises.search(ExerciseFilter(), "en", Int.MAX_VALUE).first())

    @Test
    fun `names match the catalogue regardless of case, accents, spacing and punctuation`() = runTest {
        val m = matcher(env())

        assertEquals("bench-press.barbell", m.match("Bench Press")?.variantId)
        assertEquals("bench-press.barbell", m.match("  BENCH   press ")?.variantId)
        assertEquals("bench-press.barbell", m.match("Press de banca")?.variantId)
        assertEquals("bench-press.barbell", m.match("PRESS DE BANCÁ")?.variantId)
        assertEquals("plank.bodyweight", m.match("plank")?.variantId)
        assertNull(m.match("Overhead Press"))
        assertNull(m.match(""))
    }

    @Test
    fun `equipment in the name picks the variant`() = runTest {
        val m = matcher(env())

        assertEquals("bench-press.dumbbell", m.match("Bench Press (Dumbbell)")?.variantId)
        assertEquals("bench-press.barbell", m.match("Bench Press (Barbell)")?.variantId)
        assertEquals("bench-press.dumbbell", m.match("Dumbbell Bench Press")?.variantId)
        assertEquals("squat.barbell", m.match("Barbell Squat")?.variantId)
        assertEquals("squat.barbell", m.match("Squat (Barbell)")?.variantId)
    }

    @Test
    fun `equipment the exercise does not have is not guessed`() = runTest {
        val m = matcher(env())

        assertNull(m.match("Squat (Dumbbell)"))
        assertNull(m.match("Bench Press (Cable)"))
    }

    @Test
    fun `the users own exercise wins over the catalogue for the same name`() = runTest {
        val e = env()
        val own = e.exercises.createCustom(
            NewExercise(
                name = LocalizedText.same("Bench Press"),
                category = ExerciseCategory.STRENGTH,
                trackingType = TrackingType.WEIGHT_REPS,
                variants = listOf(NewVariant(Equipment.OTHER, LoadType.DUMBBELL)),
            ),
        )

        assertEquals(own.id, matcher(e).match("bench press")?.exerciseId)
    }

    // ----- preview -----

    @Test
    fun `preview counts what a file holds and lists the unmatched names once`() = runTest {
        val e = env()

        val preview = e.importer.preview(parse("hevy.csv"), ImportOptions())

        assertEquals(2, preview.sessions)
        assertEquals(8, preview.sets)
        assertEquals(0, preview.existingSessions)
        assertEquals(2, preview.matchedExercises) // Bench Press (Barbell) and Plank
        assertEquals(listOf("Triceps Pushdown \"Rope\"", "Pull Up", "Running"), preview.unmatched.map { it.name })
        assertEquals(2, preview.unmatched.first().sets)
        assertEquals(1, preview.skippedRows)
        assertTrue(preview.firstStartedAt!! < preview.lastStartedAt!!)
        assertTrue(e.history().isEmpty(), "a preview writes nothing")
    }

    // ----- import -----

    @Test
    fun `importing writes finished sessions with sets, notes and the title`() = runTest {
        val e = env()

        val summary = e.importer.import(parse("hevy.csv"), ImportOptions())

        assertEquals(2, summary.sessionsWritten)
        assertEquals(8, summary.sets)
        assertEquals(3, summary.exercisesCreated)
        val history = e.history()
        assertEquals(2, history.size)
        val run = history.first() // latest first
        assertEquals("Morning Run", run.dayName)
        assertNull(run.routineId)
        val push = history.last()
        assertEquals("Push Day", push.dayName)
        assertEquals("Felt strong, good session", push.notes)
        assertEquals(false, push.inProgress)
        val bench = push.exercises.first()
        assertEquals("bench-press.barbell", bench.variantId)
        assertEquals("Pause on chest", bench.notes)
        assertEquals(listOf(40_000L, 82_500L, 82_500L), bench.sets.map { it.loadGrams })
        assertEquals(listOf(SetKind.WARMUP, SetKind.WORKING, SetKind.FAILURE), bench.sets.map { it.kind })
        assertTrue(bench.sets.all { it.completed && it.completedAt == push.startedAt })
        assertEquals(8.5, bench.sets[1].rpe)
        assertEquals(60, push.exercises.first { it.variantId == "plank.bodyweight" }.sets.single().durationS)
    }

    @Test
    fun `importing the same file twice never duplicates and skipping is the default`() = runTest {
        val e = env()
        e.importer.import(parse("hevy.csv"), ImportOptions())
        val exercisesBefore = e.exercises.search(ExerciseFilter(), "en", Int.MAX_VALUE).first().size

        val preview = e.importer.preview(parse("hevy.csv"), ImportOptions())
        val second = e.importer.import(parse("hevy.csv"), ImportOptions())

        assertEquals(2, preview.existingSessions)
        assertTrue(preview.unmatched.isEmpty(), "the custom exercises created by the first import now match")
        assertEquals(0, second.sessionsWritten)
        assertEquals(2, second.sessionsSkipped)
        assertEquals(0, second.sets)
        assertEquals(0, second.exercisesCreated)
        assertEquals(2, e.history().size)
        assertEquals(8, e.history().sumOf { s -> s.exercises.sumOf { it.sets.size } })
        assertEquals(exercisesBefore, e.exercises.search(ExerciseFilter(), "en", Int.MAX_VALUE).first().size)
    }

    @Test
    fun `replacing existing sessions rewrites them without duplicates`() = runTest {
        val e = env()
        e.importer.import(parse("hevy.csv"), ImportOptions())
        val ids = e.history().map { it.id }.toSet()

        val summary = e.importer.import(parse("hevy.csv"), ImportOptions(existing = ExistingPolicy.REPLACE))

        assertEquals(0, summary.sessionsWritten)
        assertEquals(2, summary.sessionsReplaced)
        assertEquals(8, summary.sets)
        assertEquals(ids, e.history().map { it.id }.toSet())
        assertEquals(8, e.history().sumOf { s -> s.exercises.sumOf { it.sets.size } })
    }

    @Test
    fun `the same session in two imports of overlapping files is written once`() = runTest {
        val e = env()
        val full = fixtureBytes("hevy.csv").decodeToString()
        val firstOnly = full.lines().filter { !it.contains("Morning Run") }.joinToString("\n")

        e.importer.import(HistoryFileParser.parse(firstOnly.encodeToByteArray(), utc), ImportOptions())
        val second = e.importer.import(HistoryFileParser.parse(full.encodeToByteArray(), utc), ImportOptions())

        assertEquals(1, second.sessionsWritten)
        assertEquals(1, second.sessionsSkipped)
        assertEquals(2, e.history().size)
    }

    @Test
    fun `the user maps an unmatched name to an existing exercise or creates a custom one`() = runTest {
        val e = env()
        val parsed = parse("hevy.csv")
        val preview = e.importer.preview(parsed, ImportOptions())
        val pushdownKey = preview.unmatched.first { it.name.startsWith("Triceps") }.key
        val pullUpKey = preview.unmatched.first { it.name == "Pull Up" }.key

        val summary = e.importer.import(
            parsed,
            ImportOptions(
                decisions = mapOf(
                    pushdownKey to ExerciseDecision.MapTo("squat"),
                    pullUpKey to ExerciseDecision.CreateCustom,
                ),
            ),
        )

        // Pull Up and Running are created; the mapped one is not.
        assertEquals(2, summary.exercisesCreated)
        val push = e.history().last()
        assertEquals("squat.barbell", push.exercises[1].variantId)
        val pullUp = e.exercises.getVariant(push.exercises[3].variantId)!!
        val created = e.exercises.getExercise(pullUp.exerciseId)!!
        assertEquals("Pull Up", created.name.en)
        assertEquals(TrackingType.BODYWEIGHT_REPS, created.trackingType)
        assertEquals(Equipment.BODYWEIGHT, pullUp.equipment)
    }

    @Test
    fun `custom exercises get tracking and category from the data`() = runTest {
        val e = env()
        e.importer.import(parse("fitnotes.csv"), ImportOptions())

        val all = e.exercises.search(ExerciseFilter(query = "treadmill"), "en", 10).first()
        val treadmill = all.single()
        assertEquals(ExerciseCategory.CARDIO, treadmill.category)
        assertEquals(TrackingType.DISTANCE_TIME, treadmill.trackingType)
        val overhead = e.exercises.search(ExerciseFilter(query = "overhead press"), "en", 10).first().single()
        assertEquals(TrackingType.WEIGHT_REPS, overhead.trackingType)
        assertEquals(listOf(es.sebas1705.haltera.data.models.Muscle.SHOULDERS), overhead.primaryMuscles)
    }

    @Test
    fun `loads without a unit use the unit the user chose`() = runTest {
        val e = env()

        e.importer.import(parse("strong-ios.csv"), ImportOptions(weightUnit = ImportWeightUnit.LB))

        val squat = e.history().last().exercises.first()
        assertEquals(listOf(27_216L, 45_359L, 46_493L), squat.sets.map { it.loadGrams }) // 60, 100, 102.5 lb
    }

    @Test
    fun `explicit pounds are converted whatever the chosen unit`() = runTest {
        val e = env()

        e.importer.import(parse("fitnotes-ios.csv"), ImportOptions(weightUnit = ImportWeightUnit.KG))

        val sets = e.history().single().exercises.single().sets
        assertEquals(listOf(102_058L, 61_235L), sets.map { it.loadGrams }) // 225 and 135 lb
    }

    @Test
    fun `imported sessions are tracked for sync like any logged session`() = runTest {
        val e = env()
        e.importer.import(parse("strong-android.csv"), ImportOptions())

        val id = e.history().single().id
        val state = db.syncTrackingQueries.selectState("sessions", id).executeAsOneOrNull()
        assertNotNull(state)
        assertEquals(1L, state.dirty)
    }

    @Test
    fun `a failure while writing leaves the logbook as it was`() = runTest {
        val e = env()
        val parsed = parse("strong-android.csv")
        val first = parsed.sessions.single()
        // Two sessions with one id: the second insert violates the primary key after the first was written.
        val clash = parsed.copy(sessions = listOf(first, first.copy(startedAt = first.startedAt + 1)))

        val failed = runCatching { e.importer.import(clash, ImportOptions()) }.isFailure

        assertTrue(failed)
        assertTrue(e.history().isEmpty())
        assertTrue(e.bodyWeights.observeAll().first().isEmpty())
    }

    // ----- body weight and the haltera round trip -----

    @Test
    fun `a haltera export imported into an empty logbook gives back the same history`() = runTest {
        val source = inMemoryDatabase().withTestCatalog()
        val seeded = seedLogbook(source)
        val zip = exportZip(source, this)

        val parsed = HistoryFileParser.parse(zip, utc)
        val e = env()
        val preview = e.importer.preview(parsed, ImportOptions())
        e.importer.import(parsed, ImportOptions())

        assertEquals(seeded.sessions, preview.sessions)
        assertEquals(seeded.sets, preview.sets)
        assertEquals(2, preview.bodyWeights)
        assertEquals(listOf("Leg press, 45°"), preview.unmatched.map { it.name })
        val imported = e.history()
        assertEquals(seeded.sessions, imported.size)
        val original = SqlSessionRepository(
            source,
            HistoryLoader(source),
            UnconfinedTestDispatcher(testScheduler),
            clock
        )
            .observeHistory(100).first()
        assertEquals(original.map { it.id }, imported.map { it.id })
        original.zip(imported).forEach { (a, b) ->
            assertEquals(a.startedAt, b.startedAt)
            assertEquals(a.endedAt, b.endedAt)
            assertEquals(a.dayName, b.dayName)
            assertEquals(a.notes, b.notes)
            assertEquals(a.bodyWeightGrams, b.bodyWeightGrams)
            assertEquals(a.exercises.map { it.notes }, b.exercises.map { it.notes })
            val sa = a.exercises.flatMap { it.sets }.filter { it.completed }
            val sb = b.exercises.flatMap { it.sets }
            assertEquals(sa.map { it.kind }, sb.map { it.kind })
            assertEquals(sa.map { it.reps }, sb.map { it.reps })
            assertEquals(sa.map { it.loadGrams }, sb.map { it.loadGrams })
            assertEquals(sa.map { it.loadDetail }, sb.map { it.loadDetail })
            assertEquals(sa.map { it.durationS }, sb.map { it.durationS })
            assertEquals(sa.map { it.rpe }, sb.map { it.rpe })
            assertEquals(sa.map { it.completedAt }, sb.map { it.completedAt })
        }
        // Catalogue exercises keep their variants; the user's own exercise was recreated by name.
        assertEquals(listOf("bench-press.barbell"), imported.last().exercises.map { it.variantId }.take(1))
        val custom = e.exercises.getVariant(imported.last().exercises[1].variantId)!!
        assertEquals("Leg press, 45°", e.exercises.getExercise(custom.exerciseId)!!.name.en)
        assertEquals(Equipment.MACHINE, custom.equipment)
        assertEquals(
            listOf(81_000L, 80_450L),
            e.bodyWeights.observeAll().first().map { it.weightGrams }.sortedDescending(),
        )
        // Importing it again changes nothing.
        val again = e.importer.import(parsed, ImportOptions())
        assertEquals(0, again.sessionsWritten)
        assertEquals(0, again.bodyWeights)
        assertEquals(2, e.bodyWeights.observeAll().first().size)
        assertEquals(seeded.sessions, e.history().size)
    }

    @Test
    fun `a single sessions csv of the export imports too`() = runTest {
        val e = env()
        val parsed = parse("haltera-sessions.csv")

        e.importer.import(parsed, ImportOptions())

        val session = e.history().single()
        assertEquals("s-001", session.id)
        assertEquals(81_500L, session.bodyWeightGrams)
        assertEquals(82_500L, session.exercises.first().sets[1].loadGrams)
    }

    // ----- performance -----

    @Test
    fun `fifty thousand rows parse and import in bounded time`() = runTest {
        val text = buildString {
            append("title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,")
            append("set_type,weight_kg,reps,distance_km,duration_seconds,rpe\n")
            for (session in 0 until BIG_SESSIONS) {
                val day = 1 + session % 28
                val month = 1 + session / 28 % 12
                val year = 2010 + session / 28 / 12
                val start = "$day ${MONTHS[month - 1]} $year, 10:00"
                for (set in 0 until ROWS_PER_SESSION) {
                    val exercise = "Exercise ${set / 5}"
                    append(
                        "\"Workout $session\",\"$start\",\"$start\",\"\",\"$exercise\",\"\",\"\",${set % 5},\"normal\","
                    )
                    append("${20 + set % 7}.5,${5 + set % 6},,,\n")
                }
            }
        }
        val parseTime = TimeSource.Monotonic.markNow()
        val parsed = HistoryFileParser.parse(text.encodeToByteArray(), utc)
        val parseElapsed = parseTime.elapsedNow()

        assertEquals(BIG_SESSIONS * ROWS_PER_SESSION, parsed.setCount)
        assertEquals(BIG_SESSIONS, parsed.sessions.size)
        assertTrue(parseElapsed < 20.seconds, "parsing took $parseElapsed")

        val e = env()
        val importTime = TimeSource.Monotonic.markNow()
        val summary = e.importer.import(parsed, ImportOptions())
        val importElapsed = importTime.elapsedNow()

        assertEquals(BIG_SESSIONS * ROWS_PER_SESSION, summary.sets)
        assertTrue(importElapsed < 60.seconds, "import took $importElapsed")
    }

    // ----- more importer cases -----

    private fun csv(text: String) = HistoryFileParser.parse(text.encodeToByteArray(), utc)

    @Test
    fun `spanish names with equipment match the variant`() = runTest {
        val m = matcher(env())

        assertEquals("bench-press.dumbbell", m.match("Press de banca (Mancuernas)")?.variantId)
        assertEquals("plank.bodyweight", m.match("Plancha")?.variantId)
    }

    @Test
    fun `body weight entries are skipped when they exist and replaced on request`() = runTest {
        val e = env()
        val file = "date,measured_at,weight_kg,weight_grams,unit,notes,id\n" +
            "2026-10-15,2026-10-15T07:00:00Z,80.45,80450,kg,first,w-001\n"
        val first = e.importer.import(csv(file), ImportOptions())
        assertEquals(1, first.bodyWeights)
        val preview = e.importer.preview(csv(file.replace("80450", "79000")), ImportOptions())
        assertEquals(1, preview.existingBodyWeights)

        val skipped = e.importer.import(csv(file.replace("80450", "79000")), ImportOptions())
        assertEquals(0, skipped.bodyWeights)
        assertEquals(80_450L, e.bodyWeights.observeAll().first().single().weightGrams)

        val replaced = e.importer.import(
            csv(file.replace("80450", "79000")),
            ImportOptions(existing = ExistingPolicy.REPLACE)
        )
        assertEquals(1, replaced.bodyWeights)
        assertEquals(79_000L, e.bodyWeights.observeAll().first().single().weightGrams)
        assertEquals(1L, db.syncTrackingQueries.selectState("bodyWeight", "w-001").executeAsOneOrNull()?.dirty)
    }

    @Test
    fun `mapping to an exercise that no longer exists falls back to a custom exercise`() = runTest {
        val e = env()
        val parsed = parse("strong-android.csv")
        val key = e.importer.preview(parsed, ImportOptions()).unmatched.first().key

        val summary = e.importer.import(parsed, ImportOptions(decisions = mapOf(key to ExerciseDecision.MapTo("gone"))))

        assertEquals(1, e.history().size)
        assertTrue(summary.exercisesCreated >= 1)
    }

    @Test
    fun `the preview for replacing lists the names of sessions that already exist`() = runTest {
        val e = env()
        e.importer.import(parse("hevy.csv"), ImportOptions(decisions = emptyMap()))

        val skip = e.importer.preview(parse("hevy.csv"), ImportOptions())
        val replace = e.importer.preview(parse("hevy.csv"), ImportOptions(existing = ExistingPolicy.REPLACE))

        assertEquals(0, skip.matchedExercises, "nothing is left to write")
        assertEquals(5, replace.matchedExercises, "the custom exercises of the first import now match")
    }

    @Test
    fun `custom exercises take tracking and equipment from what was logged`() = runTest {
        val e = env()
        val file = "title,start_time,exercise_title,weight_kg,reps,distance_km,duration_seconds\n" +
            "T,2025-05-01 10:00,Rowing,,,2,480\n" +
            "T,2025-05-01 10:00,Dead Hang,,,,45\n" +
            "T,2025-05-01 10:00,Weighted Carry,30,,,60\n" +
            "T,2025-05-01 10:00,Cable Crunch (Cable),40,12,,\n" +
            "T,2025-05-01 10:00,Burpee,,20,,\n"

        e.importer.import(csv(file), ImportOptions())

        suspend fun created(name: String) = e.exercises.search(ExerciseFilter(query = name), "en", 5).first()
            .single { it.name.en == name }
        assertEquals(TrackingType.DISTANCE_TIME, created("Rowing").trackingType)
        assertEquals(TrackingType.TIME, created("Dead Hang").trackingType)
        assertEquals(TrackingType.WEIGHT_TIME, created("Weighted Carry").trackingType)
        val crunch = created("Cable Crunch (Cable)")
        assertEquals(TrackingType.WEIGHT_REPS, crunch.trackingType)
        assertEquals(Equipment.CABLE, crunch.variants.single().equipment)
        assertEquals(LoadType.MACHINE, crunch.variants.single().loadType)
        assertEquals(TrackingType.BODYWEIGHT_REPS, created("Burpee").trackingType)
        assertEquals(Equipment.BODYWEIGHT, created("Burpee").variants.single().equipment)
    }

    @Test
    fun `the same unmatched name in many sessions creates one exercise`() = runTest {
        val e = env()

        val summary = e.importer.import(parse("fitnotes.csv"), ImportOptions())

        // Treadmill and Overhead Press (not in the test catalogue): one each, whatever the number of sets. FitNotes'
        // "Flat Barbell Bench Press" is an alias of the barbell bench press, so it creates nothing.
        assertEquals(2, summary.exercisesCreated)
        val bench = e.history().flatMap { it.exercises }.filter { it.variantId == "bench-press.barbell" }
        assertEquals(1, bench.size, "the FitNotes default name went to the catalogue exercise")
        assertEquals(1, e.exercises.search(ExerciseFilter(query = "treadmill"), "en", 10).first().size)
    }

    @Test
    fun `an empty parsed file writes nothing`() = runTest {
        val e = env()

        val summary = e.importer.import(ParsedImport(ImportSource.HEVY, emptyList()), ImportOptions())

        assertEquals(ImportSummary(0, 0, 0, 0, 0, 0), summary)
        assertTrue(e.history().isEmpty())
    }

    // ----- seeded logbook for the round trip -----

    private class Seeded(val sessions: Int, val sets: Int)

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    @Suppress("LongParameterList")
    private fun AppDatabase.set(
        id: String,
        exercise: String,
        position: Long,
        kind: String = "WORKING",
        reps: Long? = null,
        load: Long? = null,
        detail: String? = null,
        duration: Long? = null,
        rpe: Double? = null,
        done: String? = "2026-10-14T18:00:00Z",
    ) = setLogEntityQueries.insert(
        id, exercise, position, kind, reps, load, detail, duration, null, rpe, if (done != null) 1L else 0L,
        done?.let(
            ::at
        ),
    )

    private fun seedLogbook(source: AppDatabase): Seeded {
        source.exerciseEntityQueries.insertUser(
            id = "user-ex1", nameEn = "Leg press, 45°", nameEs = "Prensa 45°", nameEnSearch = "leg press 45",
            nameEsSearch = "prensa 45", category = "STRENGTH", trackingType = "WEIGHT_REPS", level = "BEGINNER",
            primaryMuscles = "QUADRICEPS", secondaryMuscles = "GLUTES", instructionsEn = "[]", instructionsEs = "[]",
            updatedAt = 1,
        )
        source.exerciseVariantEntityQueries.insertUser(
            "user-ex1.machine",
            "user-ex1",
            0,
            "MACHINE",
            "MACHINE",
            null,
            null,
            1
        )
        source.workoutSessionEntityQueries.insert("s-001", at("2026-10-14T17:30:00Z"), null, null, "Push, A")
        source.workoutSessionEntityQueries.finish(at("2026-10-14T18:40:00Z"), "s-001")
        source.workoutSessionEntityQueries.updateNotes("Felt \"great\"\nPR attempt", "s-001")
        source.sessionExerciseEntityQueries.insert("e-001", "s-001", 0, "bench-press.barbell", null)
        source.sessionExerciseEntityQueries.updateNotes("tempo 3-1-1", "e-001")
        source.set("t-001", "e-001", 0, "WARMUP", reps = 10, load = 20_000)
        source.set("t-002", "e-001", 1, "WORKING", reps = 5, load = 82_500, rpe = 8.5)
        source.set("t-003", "e-001", 2, "FAILURE", reps = 3, load = 82_500, rpe = 10.0)
        source.set("t-004", "e-001", 3, "DROP", reps = 8, load = 60_125, done = "2026-10-14T18:10:00Z")
        source.set("t-005", "e-001", 4, "WORKING", reps = 5, load = 82_500, done = null)
        source.sessionExerciseEntityQueries.insert("e-002", "s-001", 1, "user-ex1.machine", null)
        source.set("t-006", "e-002", 0, reps = 12, load = 7, detail = """{"type":"machinePlate"}""")
        source.workoutSessionEntityQueries.insert("s-002", at("2026-10-15T23:30:00Z"), null, null, null)
        source.workoutSessionEntityQueries.finish(at("2026-10-16T00:10:00Z"), "s-002")
        source.sessionExerciseEntityQueries.insert("e-003", "s-002", 0, "plank.bodyweight", null)
        source.set("t-007", "e-003", 0, duration = 60, done = "2026-10-15T23:45:00Z")
        source.sessionExerciseEntityQueries.insert("e-004", "s-002", 1, "bench-press.dumbbell", null)
        source.set("t-008", "e-004", 0, reps = 10, load = 30_000, rpe = 7.0, done = "2026-10-15T23:50:00Z")
        source.bodyWeightEntityQueries.upsert("w-001", at("2026-10-15T07:00:00Z"), 80_450, "morning, fasted")
        source.bodyWeightEntityQueries.upsert("w-000", at("2026-10-01T07:00:00Z"), 81_000, "")
        return Seeded(sessions = 2, sets = 7)
    }

    private suspend fun exportZip(source: AppDatabase, scope: TestScope): ByteArray {
        val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        val history = HistoryLoader(source)
        val exercises = SqlExerciseRepository(source, history, dispatcher, clock)
        val routines = SqlRoutineRepository(source, FamousRoutines.all, dispatcher, clock)
        val http = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) })
        val nutrition = SqlNutritionRepository(
            source,
            FoodImporter(source, now = clock),
            OpenFoodFactsClient(http, "1.0", now = clock),
            KeyValueSettingsRepository(MapSettings()),
            dispatcher,
            clock,
        )
        val exporter = SqlDataExporter(
            source,
            history,
            exercises,
            nutrition,
            SqlBodyWeightRepository(source, dispatcher, clock),
            routines,
            DefaultRoutineSharing(routines, exercises, clock),
            dispatcher,
        )
        val out = ByteArrayOutput()
        exporter.export(out, "1.0.0", at("2026-10-16T09:15:30Z"), TimeZone.of("Europe/Madrid"))
        return out.toByteArray()
    }

    private companion object {
        const val BIG_SESSIONS = 1_000
        const val ROWS_PER_SESSION = 50
        val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }
}
