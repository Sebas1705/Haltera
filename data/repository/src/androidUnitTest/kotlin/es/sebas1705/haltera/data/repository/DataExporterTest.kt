package es.sebas1705.haltera.data.repository

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.local.foods.FoodImporter
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.export.ByteArrayOutput
import es.sebas1705.haltera.data.repository.export.ByteSink
import es.sebas1705.haltera.data.repository.export.DataExporter
import es.sebas1705.haltera.data.repository.export.ExportCounts
import es.sebas1705.haltera.data.repository.export.ExportFormat
import es.sebas1705.haltera.data.repository.export.SqlDataExporter
import es.sebas1705.haltera.data.repository.off.OpenFoodFactsClient
import es.sebas1705.haltera.data.repository.share.DefaultRoutineSharing
import es.sebas1705.haltera.data.repository.share.RoutineDecodeResult
import es.sebas1705.haltera.data.repository.share.RoutineExport
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlBodyMeasurementRepository
import es.sebas1705.haltera.data.repository.sql.SqlBodyWeightRepository
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlNutritionRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val EXPORT_FOODS = """
    {"foodsVersion":1,"contentHash":"h","deprecatedIds":[],"foods":[
    {"id":"usda-1","name":{"en":"Rolled oats","es":"Copos de avena"},"unit":"G","kcal":380,"protein":13,
     "carbs":68,"fat":6,"fiber":10,"sugar":1,"salt":0,"serving":null},
    {"id":"usda-2","name":{"en":"Whole milk","es":"Leche entera"},"unit":"ML","kcal":60,"protein":3,
     "carbs":5,"fat":3,"fiber":0,"sugar":5,"salt":0.1,"serving":{"name":{"en":"glass","es":"vaso"},"grams":250}},
    {"id":"usda-4","name":{"en":"Chicken breast, raw","es":"Pechuga de pollo, cruda"},"unit":"G","kcal":120,
     "protein":22.5,"carbs":0,"fat":2.6,"fiber":null,"sugar":null,"salt":0.1,"serving":null}
    ]}
""".trimIndent()

private const val APP_VERSION = "1.2.3-test"
private const val SETS_PER_EXERCISE = 20
private const val EXERCISES_PER_SESSION = 5
private const val BIG_SESSIONS = 100
private const val CHUNK_LIMIT = 65_536

@OptIn(ExperimentalTime::class)
class DataExporterTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(at("2026-10-16T09:15:30Z"))
    private val zone = TimeZone.of("Europe/Madrid")

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()

    private fun TestScope.exporter(pageSize: Int = SqlDataExporter.PAGE_SIZE): DataExporter {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val history = HistoryLoader(db)
        val exercises = SqlExerciseRepository(db, history, dispatcher, clock)
        val routines = SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock)
        val http = HttpClient(MockEngine { respond("{}", HttpStatusCode.NotFound) })
        FoodImporter(db, now = clock).importIfNewer(EXPORT_FOODS)
        val nutrition = SqlNutritionRepository(
            db,
            FoodImporter(db, now = clock),
            OpenFoodFactsClient(http, "1.0", now = clock),
            KeyValueSettingsRepository(MapSettings()),
            dispatcher,
            clock,
        )
        return SqlDataExporter(
            db,
            history,
            exercises,
            nutrition,
            SqlBodyWeightRepository(db, dispatcher, clock),
            SqlBodyMeasurementRepository(db, dispatcher, clock),
            routines,
            DefaultRoutineSharing(routines, exercises, clock),
            dispatcher,
            pageSize,
        )
    }

    private class Archive(val counts: ExportCounts, val files: Map<String, ByteArray>) {
        fun text(name: String): String = files.getValue(name).decodeToString()
    }

    private suspend fun DataExporter.exportArchive(): Archive {
        val out = ByteArrayOutput()
        val counts = export(out, APP_VERSION, clock(), ZONE)
        return Archive(counts, unzip(out.toByteArray()))
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> files[entry.name] = zip.readBytes() }
        }
        return files
    }

    // ----- fixture -----

    private fun addSession(id: String, startedAt: String, endedAt: String?, dayName: String?, notes: String = "") {
        db.workoutSessionEntityQueries.insert(id, at(startedAt), null, null, dayName)
        endedAt?.let { db.workoutSessionEntityQueries.finish(at(it), id) }
        if (notes.isNotEmpty()) db.workoutSessionEntityQueries.updateNotes(notes, id)
    }

    private fun addExercise(id: String, sessionId: String, position: Long, variantId: String, notes: String = "") {
        db.sessionExerciseEntityQueries.insert(id, sessionId, position, variantId, null)
        if (notes.isNotEmpty()) db.sessionExerciseEntityQueries.updateNotes(notes, id)
    }

    @Suppress("LongParameterList")
    private fun addSet(
        id: String,
        exerciseId: String,
        position: Long,
        kind: String = "WORKING",
        reps: Long? = null,
        loadGrams: Long? = null,
        loadDetail: String? = null,
        durationS: Long? = null,
        rpe: Double? = null,
        completedAt: String? = "2026-10-14T18:00:00Z",
    ) {
        db.setLogEntityQueries.insert(
            id, exerciseId, position, kind, reps, loadGrams, loadDetail, durationS, null, rpe,
            if (completedAt != null) 1L else 0L, completedAt?.let(::at),
        )
    }

    private fun fixture() {
        customExerciseFixture()
        sessionFixture()
        bodyWeightAndMealFixture()
    }

    private fun customExerciseFixture() {
        db.exerciseEntityQueries.insertUser(
            id = "user-ex1", nameEn = "Leg press, 45°", nameEs = "Prensa 45°", nameEnSearch = "leg press 45",
            nameEsSearch = "prensa 45", category = "STRENGTH", trackingType = "WEIGHT_REPS", level = "BEGINNER",
            primaryMuscles = "QUADRICEPS", secondaryMuscles = "GLUTES", instructionsEn = "[]", instructionsEs = "[]",
            updatedAt = 1,
        )
        db.exerciseVariantEntityQueries.insertUser(
            id = "user-ex1.machine",
            exerciseId = "user-ex1",
            position = 0,
            equipment = "MACHINE",
            loadType = "MACHINE",
            nameEn = null,
            nameEs = null,
            updatedAt = 1,
        )
    }

    private fun sessionFixture() {
        addSession(
            "s-001",
            "2026-10-14T17:30:00Z",
            "2026-10-14T18:40:00Z",
            "Push, A",
            notes = "Felt \"great\"\nPR attempt",
        )
        addExercise("e-001", "s-001", 0, "bench-press.barbell", notes = "tempo 3-1-1")
        addSet("t-001", "e-001", 0, "WARMUP", reps = 10, loadGrams = 20_000)
        addSet("t-002", "e-001", 1, "WORKING", reps = 5, loadGrams = 82_500, rpe = 8.5)
        addSet("t-003", "e-001", 2, "FAILURE", reps = 3, loadGrams = 82_500, rpe = 10.0)
        addSet("t-004", "e-001", 3, "DROP", reps = 8, loadGrams = 60_125, completedAt = "2026-10-14T18:10:00Z")
        addSet("t-005", "e-001", 4, "WORKING", reps = 5, loadGrams = 82_500, completedAt = null)
        addExercise("e-002", "s-001", 1, "user-ex1.machine")
        addSet(
            "t-006",
            "e-002",
            0,
            reps = 12,
            loadGrams = 7,
            loadDetail = """{"type":"machinePlate"}""",
        )

        addSession("s-002", "2026-10-15T23:30:00Z", null, null)
        addExercise("e-003", "s-002", 0, "plank.bodyweight")
        addSet("t-007", "e-003", 0, durationS = 60)
        addExercise("e-004", "s-002", 1, "bench-press.dumbbell")
        addSet("t-008", "e-004", 0, reps = 10, loadGrams = 30_000, rpe = 7.0, completedAt = "2026-10-15T23:45:00Z")

        addSession("s-003", "2026-10-15T08:00:00Z", "2026-10-15T08:30:00Z", "Empty")
        addExercise("e-005", "s-003", 0, "squat.barbell")
        addSet("t-009", "e-005", 0, reps = 5, loadGrams = 100_000, completedAt = null)
    }

    private fun bodyWeightAndMealFixture() {
        db.bodyWeightEntityQueries.upsert("w-001", at("2026-10-15T07:00:00Z"), 80_450, "morning, fasted")
        db.bodyWeightEntityQueries.upsert("w-000", at("2026-10-01T07:00:00Z"), 81_000, "")
        measurementFixture()

        db.mealEntryEntityQueries.insert(
            "m-001",
            "2026-10-15",
            "BREAKFAST",
            "usda-1",
            50.0,
            null,
            0,
            at("2026-10-15T07:30:00Z")
        )
        db.mealEntryEntityQueries.insert(
            "m-002",
            "2026-10-15",
            "BREAKFAST",
            "usda-2",
            250.0,
            1.0,
            1,
            at("2026-10-15T07:31:00Z")
        )
        db.mealEntryEntityQueries.insert(
            "m-003",
            "2026-10-15",
            "DINNER",
            "usda-4",
            200.0,
            null,
            0,
            at("2026-10-15T20:00:00Z")
        )
        db.mealEntryEntityQueries.insert(
            "m-004",
            "2026-10-15",
            "LUNCH",
            "gone-food",
            100.0,
            null,
            0,
            at("2026-10-15T14:00:00Z")
        )
        db.mealEntryEntityQueries.insert(
            "m-005",
            "2026-10-16",
            "SNACK",
            "usda-1",
            33.333,
            0.5,
            0,
            at("2026-10-16T09:00:00Z")
        )
    }

    private fun measurementFixture() {
        val q = db.bodyMeasurementEntityQueries
        q.upsert("bm-002", at("2026-10-15T07:05:00Z"), "WAIST", "", 825, "navel, relaxed")
        q.upsert("bm-001", at("2026-10-01T07:05:00Z"), "WAIST", "", 840, "")
        q.upsert("bm-003", at("2026-10-15T07:06:00Z"), "BODY_FAT", "", 185, "")
        q.upsert("bm-004", at("2026-10-15T22:30:00Z"), "CUSTOM", "Gemelo, izq.", 371, "\"tape\"")
        q.upsert("bm-005", at("2026-10-15T07:05:00Z"), "ARM", "", 1, "")
    }

    private suspend fun TestScope.routineFixture() {
        val routines = SqlRoutineRepository(db, FamousRoutines.all, UnconfinedTestDispatcher(testScheduler), clock)
        val id = routines.createRoutine("Upper, Lower", "Two days")
        val a = routines.addDay(id, "Upper")
        routines.addSlot(a, SlotSpec(SlotTarget.Fixed("bench-press.barbell")))
        routines.addDay(id, "Rest", DayKind.REST)
    }

    // ----- golden files -----

    private fun golden(name: String, actual: String) {
        val file = File("src/androidUnitTest/resources/export/$name")
        if (System.getenv("UPDATE_GOLDEN") != null) {
            file.parentFile.mkdirs()
            file.writeBytes(actual.encodeToByteArray())
            return
        }
        val expected = DataExporterTest::class.java.getResourceAsStream("/export/$name")
            ?.readBytes()?.decodeToString()
            ?: error("Missing golden file $name (run with UPDATE_GOLDEN=1)")
        assertEquals(expected, actual, "golden file $name")
    }

    @Test
    fun `every file matches its golden file`() = runTest {
        fixture()
        routineFixture()
        val archive = exporter().exportArchive()

        golden("sessions.csv", archive.text("sessions.csv"))
        golden("body_weight.csv", archive.text("body_weight.csv"))
        golden("body_measurements.csv", archive.text("body_measurements.csv"))
        golden("meal_entries.csv", archive.text("meal_entries.csv"))
        golden("routines.json", archive.text("routines.json"))
        golden("exercises.json", archive.text("exercises.json"))
    }

    @Test
    fun `the archive holds the documented files and the manifest counts them`() = runTest {
        fixture()
        routineFixture()
        val archive = exporter().exportArchive()

        assertEquals(
            listOf(
                "sessions.csv",
                "body_weight.csv",
                "body_measurements.csv",
                "meal_entries.csv",
                "routines.json",
                "exercises.json",
                "manifest.json"
            ),
            archive.files.keys.toList(),
        )
        val manifest = Json.parseToJsonElement(archive.text("manifest.json")).jsonObject
        assertEquals("haltera-export", manifest.getValue("format").jsonPrimitive.content)
        assertEquals(3, manifest.getValue("version").jsonPrimitive.int)
        assertEquals(APP_VERSION, manifest.getValue("appVersion").jsonPrimitive.content)
        assertEquals(AppDatabase.Schema.version, manifest.getValue("schemaVersion").jsonPrimitive.content.toLong())
        assertEquals("2026-10-16T09:15:30Z", manifest.getValue("exportedAt").jsonPrimitive.content)
        val counts = manifest.getValue("counts").jsonObject
        assertEquals(
            mapOf(
                "sessions" to 2,
                "sets" to 7,
                "bodyWeightEntries" to 2,
                "mealEntries" to 5,
                "routines" to 1,
                "customExercises" to 1,
                "bodyMeasurements" to 5,
            ),
            counts.mapValues { it.value.jsonPrimitive.int },
        )
        assertEquals(ExportCounts(2, 7, 2, 5, 1, 1, bodyMeasurements = 5), archive.counts)
        assertEquals(
            listOf("sessions.csv", "body_weight.csv", "body_measurements.csv", "meal_entries.csv", "routines.json", "exercises.json"),
            manifest.getValue("files").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `completed sets only, exercise ids are the stable ones and the bundled catalogue is not exported`() = runTest {
        fixture()
        val archive = exporter().exportArchive()
        val sessions = archive.text("sessions.csv")

        assertTrue("bench-press,bench-press.barbell,Bench Press,barbell" in sessions)
        assertTrue("user-ex1,user-ex1.machine" in sessions)
        assertTrue("t-005" !in sessions, "an unfinished set is not a logged set")
        assertTrue("s-003" !in sessions)
        val exercises = Json.parseToJsonElement(archive.text("exercises.json")).jsonObject
        assertEquals(
            listOf("user-ex1"),
            exercises.getValue("exercises").jsonArray.map {
                it.jsonObject.getValue("id").jsonPrimitive.content
            }
        )
        assertTrue("squat" !in archive.text("exercises.json"))
    }

    @Test
    fun `the date is the local date and the instant is UTC`() = runTest {
        fixture()
        val sessions = exporter().exportArchive().text("sessions.csv")

        // 23:30Z on the 15th is already the 16th in Madrid (UTC+2).
        assertTrue("\r\n2026-10-16,s-002,2026-10-15T23:30:00Z," in sessions)
        assertTrue("\r\n2026-10-14,s-001,2026-10-14T17:30:00Z,2026-10-14T18:40:00Z," in sessions)
    }

    @Test
    fun `routines are the routine format, one per routine, readable by the routine decoder`() = runTest {
        fixture()
        routineFixture()
        val text = exporter().exportArchive().text("routines.json")

        val array = Json.parseToJsonElement(text) as JsonArray
        assertEquals(1, array.size)
        val decoded = RoutineExport.decode(array.single().toString())
        assertTrue(decoded is RoutineDecodeResult.Valid)
        assertEquals("Upper, Lower", decoded.file.routine.name)
        assertEquals(listOf("Upper", "Rest"), decoded.file.routine.days.map { it.name })
    }

    @Test
    fun `an empty logbook still gives a valid archive`() = runTest {
        val archive = exporter().exportArchive()

        assertEquals(ExportCounts(), archive.counts)
        assertEquals(
            ExportFormat.SESSION_COLUMNS.joinToString(","),
            archive.text("sessions.csv").trim().removePrefix("﻿")
        )
        assertEquals("[]\n", archive.text("routines.json"))
        assertEquals(
            ExportFormat.BODY_MEASUREMENT_COLUMNS.joinToString(","),
            archive.text("body_measurements.csv").trim().removePrefix("\uFEFF"),
        )
        assertTrue(Json.parseToJsonElement(archive.text("manifest.json")) is JsonObject)
    }

    @Test
    fun `the page size does not change the output`() = runTest {
        fixture()
        routineFixture()
        val whole = exporter().exportArchive()
        val paged = exporter(pageSize = 1).exportArchive()

        listOf(
            "sessions.csv",
            "body_weight.csv",
            "body_measurements.csv",
            "meal_entries.csv",
            "routines.json",
            "exercises.json",
        ).forEach {
            assertEquals(whole.text(it), paged.text(it), it)
        }
    }

    @Test
    fun `the locale does not change the file`() = runTest {
        fixture()
        routineFixture()
        val reference = Locale.getDefault()
        val english: Archive
        try {
            Locale.setDefault(Locale.US)
            english = exporter().exportArchive()
            listOf("es-ES", "de-DE", "fr-FR", "ar-EG").forEach { tag ->
                Locale.setDefault(Locale.forLanguageTag(tag))
                val other = exporter().exportArchive()
                english.files.forEach { (name, bytes) ->
                    assertTrue(bytes.contentEquals(other.files.getValue(name)), "$name under $tag")
                }
            }
        } finally {
            Locale.setDefault(reference)
        }
        // Decimal points, not commas, in the numbers.
        assertTrue(",80.45," in english.text("body_weight.csv"))
        assertTrue(",82.5,cm,825," in english.text("body_measurements.csv"))
    }

    @Test
    fun `ten thousand sets stream out in bounded chunks`() = runTest {
        val sets = BIG_SESSIONS * EXERCISES_PER_SESSION * SETS_PER_EXERCISE
        db.transaction {
            repeat(BIG_SESSIONS) { s ->
                val day = 1 + s % 28
                val sessionId = "big-$s"
                db.workoutSessionEntityQueries.insert(
                    sessionId,
                    at("2025-01-01T10:00:00Z") + s * 86_400_000L,
                    null,
                    null,
                    null
                )
                repeat(EXERCISES_PER_SESSION) { e ->
                    val exerciseId = "big-$s-$e"
                    db.sessionExerciseEntityQueries.insert(
                        exerciseId,
                        sessionId,
                        e.toLong(),
                        "bench-press.barbell",
                        null
                    )
                    repeat(SETS_PER_EXERCISE) { n ->
                        db.setLogEntityQueries.insert(
                            "$exerciseId-$n", exerciseId, n.toLong(), "WORKING", 8, 50_000L + day * 500L, null,
                            null, null, null, 1, at("2025-01-01T11:00:00Z"),
                        )
                    }
                }
            }
        }
        var largestWrite = 0
        var total = 0L
        val sink = object : ByteSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                largestWrite = maxOf(largestWrite, length)
                total += length
            }
        }
        val counts = exporter().export(sink, APP_VERSION, clock(), zone)

        assertEquals(sets, counts.sets)
        assertEquals(BIG_SESSIONS, counts.sessions)
        assertTrue(largestWrite <= CHUNK_LIMIT, "the sink got a $largestWrite byte write")
        assertTrue(total > sets * 100L)

        // Read it back: every set is one line, in session order.
        val out = ByteArrayOutput()
        exporter().export(out, APP_VERSION, clock(), zone)
        val lines = unzip(
            out.toByteArray()
        ).getValue("sessions.csv").decodeToString().lines().filter { it.isNotBlank() }
        assertEquals(sets + 1, lines.size)
        assertTrue(lines[1].startsWith("2025-01-01,big-0,"))
        assertTrue(lines.last().contains(",big-99-4-19"))
    }

    @Test
    fun `the file name carries the local date`() {
        assertEquals("haltera-export-2026-10-16.zip", ExportFormat.fileName(at("2026-10-16T09:15:30Z"), zone))
        assertEquals("haltera-export-2026-10-17.zip", ExportFormat.fileName(at("2026-10-16T23:30:00Z"), zone))
    }

    private companion object {
        val ZONE: TimeZone = TimeZone.of("Europe/Madrid")
    }
}
