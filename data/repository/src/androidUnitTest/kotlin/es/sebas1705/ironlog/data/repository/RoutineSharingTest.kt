package es.sebas1705.ironlog.data.repository

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.NewExercise
import es.sebas1705.ironlog.data.models.NewVariant
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.repository.share.DefaultRoutineSharing
import es.sebas1705.ironlog.data.repository.share.ImportPreview
import es.sebas1705.ironlog.data.repository.share.ImportPreviewResult
import es.sebas1705.ironlog.data.repository.share.RoutineDecodeResult
import es.sebas1705.ironlog.data.repository.share.RoutineExport
import es.sebas1705.ironlog.data.repository.share.RoutineShareCode
import es.sebas1705.ironlog.data.repository.sql.HistoryLoader
import es.sebas1705.ironlog.data.repository.sql.SqlExerciseRepository
import es.sebas1705.ironlog.data.repository.sql.SqlRoutineRepository
import es.sebas1705.ironlog.data.routines.FamousRoutines
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineSharingTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(42L)

    private class Env(
        val routines: RoutineRepository,
        val exercises: ExerciseRepository,
        val sharing: DefaultRoutineSharing,
    )

    private fun TestScope.env(): Env {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val routines = SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock)
        val exercises = SqlExerciseRepository(db, HistoryLoader(db), dispatcher, clock)
        return Env(routines, exercises, DefaultRoutineSharing(routines, exercises, clock))
    }

    private suspend fun Env.seedRoutine(): String {
        val custom = exercises.createCustom(
            NewExercise(
                name = LocalizedText("Sled push", "Empuje de trineo"),
                category = ExerciseCategory.STRENGTH,
                trackingType = TrackingType.WEIGHT_REPS,
                variants = listOf(NewVariant(Equipment.OTHER, LoadType.MACHINE)),
            ),
        )
        val id = routines.createRoutine("Push", "Chest day")
        val day = routines.addDay(id, "A")
        routines.addDay(id, "Rest", DayKind.REST)
        routines.addSlot(
            day,
            SlotSpec(
                SlotTarget.Fixed("bench-press.barbell"),
                sets = 5,
                prescription = Prescription.Reps(5),
                progression = ProgressionRule.Linear(LoadIncrement.Weight(2_500)),
                supersetGroup = 1,
                notes = "Pause",
            ),
        )
        routines.addSlot(day, SlotSpec(SlotTarget.Flexible("squat", listOf("squat.barbell"))))
        routines.addSlot(day, SlotSpec(SlotTarget.Fixed(custom.variants.single().id)))
        return id
    }

    private fun ready(result: ImportPreviewResult): ImportPreview =
        assertIs<ImportPreviewResult.Ready>(result).preview

    @Test
    fun `export and import round trip creates a new copy`() = runTest {
        val env = env()
        val id = env.seedRoutine()
        val json = env.sharing.export(id)!!
        val preview = ready(env.sharing.preview(json))
        assertTrue(preview.missingIds.isEmpty())
        assertEquals("Bench Press", preview.names["bench-press.barbell"]?.en)

        val copyId = env.sharing.import(preview, dropMissing = false)
        assertNotEquals(id, copyId)
        val original = env.routines.getRoutine(id)!!
        val copy = env.routines.getRoutine(copyId)!!
        assertEquals(original.name, copy.name)
        assertEquals(original.description, copy.description)
        assertEquals(original.days.map { it.name to it.kind }, copy.days.map { it.name to it.kind })
        assertEquals(original.days[0].slots[0].toSpec(), copy.days[0].slots[0].toSpec())
        assertEquals(original.days[0].slots[1].toSpec(), copy.days[0].slots[1].toSpec())
        assertEquals(2, env.routines.observeRoutines().first().size)
    }

    @Test
    fun `custom exercises are embedded and recreated with fresh ids`() = runTest {
        val env = env()
        val id = env.seedRoutine()
        val json = env.sharing.export(id)!!
        val file = assertIs<RoutineDecodeResult.Valid>(RoutineExport.decode(json)).file
        val embedded = file.customExercises.single()
        assertEquals("Sled push", embedded.name.en)

        val copyId = env.sharing.import(ready(env.sharing.preview(json)), dropMissing = false)
        val target = env.routines.getRoutine(copyId)!!.days[0].slots[2].target as SlotTarget.Fixed
        assertNotEquals(embedded.variants.single().id, target.variantId)
        assertTrue(target.variantId.startsWith("user-"))
        val created = env.exercises.getExercise(env.exercises.getVariant(target.variantId)!!.exerciseId)!!
        assertEquals(ExerciseSource.USER, created.source)
        assertEquals("Sled push", created.name.en)
    }

    @Test
    fun `missing catalogue ids are reported and can be dropped or imported as custom`() = runTest {
        val env = env()
        val id = env.seedRoutine()
        val json = env.sharing.export(id)!!.replace("bench-press.barbell", "zercher-squat.barbell")
        val preview = ready(env.sharing.preview(json))
        assertEquals(listOf("zercher-squat.barbell"), preview.missingIds)

        val dropped = env.routines.getRoutine(env.sharing.import(preview, dropMissing = true))!!
        assertEquals(2, dropped.days[0].slots.size)

        val kept = env.routines.getRoutine(env.sharing.import(preview, dropMissing = false))!!
        assertEquals(3, kept.days[0].slots.size)
        val placeholder = env.exercises.getVariant((kept.days[0].slots[0].target as SlotTarget.Fixed).variantId)!!
        assertEquals(Equipment.BARBELL, placeholder.equipment)
        assertEquals("zercher-squat", env.exercises.getExercise(placeholder.exerciseId)!!.name.en)
    }

    @Test
    fun `corrupt json and other formats are invalid`() = runTest {
        val env = env()
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("{not json"))
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("""{"format":"other","version":1}"""))
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("""{"format":"ironlog-routine","version":1}"""))
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("ironlog:r1:%%%"))
    }

    @Test
    fun `a newer version is reported as unsupported`() = runTest {
        val env = env()
        val result = env.sharing.preview("""{"format":"ironlog-routine","version":2,"routine":{"name":"X"}}""")
        assertEquals(2, assertIs<ImportPreviewResult.UnsupportedVersion>(result).version)
    }

    @Test
    fun `history is never exported`() = runTest {
        val env = env()
        val json = env.sharing.export(env.seedRoutine())!!
        val keys = Json.parseToJsonElement(json).jsonObject.keys
        assertEquals(setOf("format", "version", "exportedAt", "routine", "customExercises"), keys)
        assertTrue("\"exportedAt\":42" in json)
        assertNull(env.sharing.export("nope"))
    }

    @Test
    fun `qr payload round trips through deflate and base64url`() = runTest {
        val env = env()
        val json = env.sharing.export(env.seedRoutine())!!
        val code = RoutineShareCode.encode(json)
        assertTrue(code.startsWith(RoutineShareCode.PREFIX))
        assertTrue(code.length < json.length)
        assertTrue(code.removePrefix(RoutineShareCode.PREFIX).all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(json, RoutineShareCode.decode(code))
        assertNull(RoutineShareCode.decode("ironlog:r1:AAAA"))
        assertNull(RoutineShareCode.decode("hello"))
        // The preview accepts the QR payload directly.
        assertTrue(ready(env.sharing.preview(code)).missingIds.isEmpty())
    }
}
