package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.LoadIncrement
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.RoutineOrigin
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.share.DefaultRoutineSharing
import es.sebas1705.haltera.data.repository.share.ImportPreview
import es.sebas1705.haltera.data.repository.share.ImportPreviewResult
import es.sebas1705.haltera.data.repository.share.QrCapacity
import es.sebas1705.haltera.data.repository.share.RoutineDecodeResult
import es.sebas1705.haltera.data.repository.share.RoutineExport
import es.sebas1705.haltera.data.repository.share.RoutineShareCode
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
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

    /** The receiver of a shared routine: another phone, with its own database. */
    private val otherDb = inMemoryDatabase().withTestCatalog()

    private fun TestScope.env(database: AppDatabase = db): Env {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val routines = SqlRoutineRepository(database, FamousRoutines.all, dispatcher, clock)
        val exercises = SqlExerciseRepository(database, HistoryLoader(database), dispatcher, clock)
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
    fun `custom exercises are embedded and recreated with fresh ids on another phone`() = runTest {
        val env = env()
        val id = env.seedRoutine()
        val json = env.sharing.export(id)!!
        val file = assertIs<RoutineDecodeResult.Valid>(RoutineExport.decode(json)).file
        val embedded = file.customExercises.single()
        assertEquals("Sled push", embedded.name.en)

        val receiver = env(otherDb)
        val copyId = receiver.sharing.import(ready(receiver.sharing.preview(json)), dropMissing = false)
        val target = receiver.routines.getRoutine(copyId)!!.days[0].slots[2].target as SlotTarget.Fixed
        assertNotEquals(embedded.variants.single().id, target.variantId)
        assertTrue(target.variantId.startsWith("user-"))
        val created = receiver.exercises.getExercise(receiver.exercises.getVariant(target.variantId)!!.exerciseId)!!
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
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("""{"format":"haltera-routine","version":1}"""))
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("ironlog:r1:%%%"))
    }

    @Test
    fun `a newer version is reported as unsupported`() = runTest {
        val env = env()
        val result = env.sharing.preview("""{"format":"haltera-routine","version":3,"routine":{"name":"X"}}""")
        assertEquals(3, assertIs<ImportPreviewResult.UnsupportedVersion>(result).version)
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
    fun `v1 qr payload still round trips through deflate and base64url`() = runTest {
        val env = env()
        val json = env.sharing.export(env.seedRoutine())!!
        val code = RoutineShareCode.encodeV1(json)
        assertTrue(code.startsWith(RoutineShareCode.PREFIX_V1))
        assertTrue(code.length < json.length)
        assertTrue(code.removePrefix(RoutineShareCode.PREFIX_V1).all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(json, RoutineShareCode.decode(code))
        // The prefix is read case-insensitively; the payload is not touched.
        assertEquals(json, RoutineShareCode.decode("IRONLOG:R1:" + code.removePrefix(RoutineShareCode.PREFIX_V1)))
        assertNull(RoutineShareCode.decode("ironlog:r1:AAAA"))
        assertNull(RoutineShareCode.decode("hello"))
        // The preview accepts the QR payload directly.
        assertTrue(ready(env.sharing.preview(code)).missingIds.isEmpty())
    }

    @Test
    fun `v2 qr payload round trips through deflate and base45 in the alphanumeric charset`() = runTest {
        val env = env()
        val id = env.seedRoutine()
        val json = env.sharing.export(id)!!
        val code = RoutineShareCode.encode(json)
        assertTrue(code.startsWith(RoutineShareCode.PREFIX))
        assertTrue(QrCapacity.isAlphanumeric(code))
        assertEquals(json, RoutineShareCode.decode(code))
        assertEquals(json, RoutineShareCode.decode("haltera:r2:" + code.removePrefix(RoutineShareCode.PREFIX) + "\n"))
        assertNull(RoutineShareCode.decode("HALTERA:R2:AAAA"))
        assertNull(RoutineShareCode.decode("HALTERA:R2:abc"))
        // A routine not copied from the catalogue is shared as v2.
        val shared = env.sharing.shareCode(id)!!
        assertTrue(shared.startsWith(RoutineShareCode.PREFIX))
        assertTrue(ready(env.sharing.preview(shared)).missingIds.isEmpty())
        assertNull(env.sharing.shareCode("nope"))
    }

    @Test
    fun `codes and files written under the former Ironlog name are still read`() = runTest {
        val env = env()
        val id = env.seedRoutine()
        val json = env.sharing.export(id)!!
        assertTrue(""""format":"haltera-routine"""" in json)
        val legacyJson = json.replace("haltera-routine", "ironlog-routine")
        assertTrue(ready(env.sharing.preview(legacyJson)).missingIds.isEmpty())

        val v2 = RoutineShareCode.encode(json).replaceFirst(RoutineShareCode.PREFIX, "IRONLOG:R2:")
        assertEquals(json, RoutineShareCode.decode(v2))
        assertTrue(RoutineShareCode.isCode(v2))
        assertTrue(ready(env.sharing.preview(v2)).missingIds.isEmpty())

        val v1 = RoutineShareCode.encodeV1(json)
        assertTrue(v1.startsWith("ironlog:r1:"))
        assertEquals(json, RoutineShareCode.decode(v1))

        val template = "IRONLOG:T1:PPL-REDDIT:1"
        assertTrue(RoutineShareCode.isTemplateCode(template))
        assertEquals(RoutineOrigin("ppl-reddit", 1), RoutineShareCode.decodeTemplate(template))
        assertEquals(RoutineOrigin("ppl-reddit", 1), ready(env.sharing.preview(template, language = "en")).template)
    }

    @Test
    fun `an unmodified catalogue copy is shared as a template code and imported as a catalogue copy`() = runTest {
        val env = env()
        val id = env.routines.copyTemplate("ppl-reddit", "es")
        val code = env.sharing.shareCode(id)!!
        assertEquals("HALTERA:T1:PPL-REDDIT:1", code)
        assertTrue(QrCapacity.isAlphanumeric(code))
        assertEquals(RoutineOrigin("ppl-reddit", 1), RoutineShareCode.decodeTemplate(code.lowercase()))
        assertNull(RoutineShareCode.decode(code))

        val preview = ready(env.sharing.preview(code, language = "en"))
        val template = FamousRoutines.byId("ppl-reddit")!!
        assertEquals(RoutineOrigin("ppl-reddit", 1), preview.template)
        assertEquals(template.name.en, preview.file.routine.name)
        assertEquals(template.days.size, preview.file.routine.days.size)

        val copy = env.routines.getRoutine(env.sharing.import(preview, dropMissing = true))!!
        assertNotEquals(id, copy.id)
        assertEquals(template.name.en, copy.name)
        assertEquals(RoutineOrigin("ppl-reddit", 1), copy.origin)
        assertEquals(template.days.flatMap { it.slots }, copy.days.flatMap { d -> d.slots.map { it.toSpec() } })
        // The copy is unmodified too, so it shares as the same code.
        assertEquals(code, env.sharing.shareCode(copy.id))
    }

    @Test
    fun `a modified catalogue copy is shared in full`() = runTest {
        val env = env()
        val id = env.routines.copyTemplate("ppl-reddit", "en")
        val slot = env.routines.getRoutine(id)!!.days[0].slots[0]
        env.routines.updateSlot(slot.id, slot.toSpec().copy(sets = slot.sets + 1))
        assertTrue(env.sharing.shareCode(id)!!.startsWith(RoutineShareCode.PREFIX))

        val renamed = env.routines.copyTemplate("ppl-reddit", "en")
        env.routines.updateRoutine(renamed, "My PPL", "")
        assertTrue(env.sharing.shareCode(renamed)!!.startsWith(RoutineShareCode.PREFIX))
    }

    @Test
    fun `bad template codes are invalid or unsupported`() = runTest {
        val env = env()
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("HALTERA:T1:NOT-A-TEMPLATE:1"))
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("HALTERA:T1:PPL-REDDIT"))
        assertIs<ImportPreviewResult.Invalid>(env.sharing.preview("HALTERA:T1:PPL_REDDIT:1"))
        val newer = env.sharing.preview("HALTERA:T1:PPL-REDDIT:99")
        assertEquals(99, assertIs<ImportPreviewResult.UnsupportedVersion>(newer).version)
        // Every bundled template id fits the template code.
        FamousRoutines.all.forEach { t ->
            val code = RoutineShareCode.encodeTemplate(t.id, t.version)
            assertEquals(RoutineOrigin(t.id, t.version), RoutineShareCode.decodeTemplate(code))
        }
    }

    @Test
    fun `a 6-day ppl needs a smaller qr as v2 and a tiny one as a template code`() = runTest {
        val env = env()
        val id = env.routines.copyTemplate("ppl-reddit", "en")
        assertEquals(6, env.routines.getRoutine(id)!!.days.count { it.kind == DayKind.TRAINING })
        val json = env.sharing.export(id)!!
        val v1 = RoutineShareCode.encodeV1(json)
        val v2 = RoutineShareCode.encode(json)
        val template = env.sharing.shareCode(id)!!

        val v1Version = QrCapacity.version(v1)!!
        val v2Version = QrCapacity.version(v2)!!
        val templateVersion = QrCapacity.version(template)!!
        println(
            "6-day PPL share code: json=${json.length} chars; " +
                "v1=${v1.length} chars (byte mode, ${QrCapacity.dataBits(v1, v1Version)} bits, " +
                "version $v1Version, ${QrCapacity.modules(v1Version)} modules); " +
                "v2=${v2.length} chars (alphanumeric, ${QrCapacity.dataBits(v2, v2Version)} bits, " +
                "version $v2Version, ${QrCapacity.modules(v2Version)} modules); " +
                "template=${template.length} chars (version $templateVersion, ${QrCapacity.modules(templateVersion)} modules)",
        )
        assertTrue(!QrCapacity.isAlphanumeric(v1) && QrCapacity.isAlphanumeric(v2))
        assertTrue(QrCapacity.dataBits(v2, v2Version) < QrCapacity.dataBits(v1, v2Version))
        assertTrue(v2Version < v1Version, "v2 version $v2Version, v1 version $v1Version")
        assertTrue(template.length < 32)
        assertEquals(1, templateVersion)
        assertEquals(json, RoutineShareCode.decode(v2))
    }
}
