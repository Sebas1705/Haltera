package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.DeleteOutcome
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.VariantEdit
import es.sebas1705.haltera.data.models.toEdit
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
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Custom exercises inside shared routines (share format v2, core design §10.1): creator name, instructions,
 * origin dedupe, backward compatibility with version 1 and the legacy codes, and the QR size fallback. The sender
 * and the receiver are two phones with their own databases.
 */
class SharedCustomExercisesTest {
    private val senderDb = inMemoryDatabase().withTestCatalog()
    private val receiverDb = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(42L)

    private class Phone(
        val db: AppDatabase,
        val routines: RoutineRepository,
        val exercises: ExerciseRepository,
        val sessions: SessionRepository,
        val sharing: DefaultRoutineSharing,
    ) {
        fun userExercises() = db.exerciseEntityQueries.selectUser().executeAsList()

        suspend fun preview(text: String): ImportPreview =
            assertIs<ImportPreviewResult.Ready>(sharing.preview(text)).preview

        suspend fun import(text: String): String = sharing.import(preview(text), dropMissing = false)
    }

    private fun TestScope.phone(db: AppDatabase): Phone {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val routines = SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock)
        val exercises = SqlExerciseRepository(db, HistoryLoader(db), dispatcher, clock)
        val sessions = SqlSessionRepository(db, HistoryLoader(db), dispatcher, clock)
        return Phone(db, routines, exercises, sessions, DefaultRoutineSharing(routines, exercises, clock))
    }

    private fun file(json: String) = assertIs<RoutineDecodeResult.Valid>(RoutineExport.decode(json)).file

    /** A routine with only catalogue exercises. */
    private suspend fun Phone.catalogueRoutine(): String {
        val id = routines.createRoutine("Plain")
        routines.addSlot(routines.addDay(id, "A"), SlotSpec(SlotTarget.Fixed("bench-press.barbell")))
        return id
    }

    /** A routine with the sled push (with instructions), pinned and flexible. Returns routine and exercise ids. */
    private suspend fun Phone.customRoutine(
        instructions: List<String> = listOf("Lean in.", "Drive."),
    ): Pair<String, String> {
        val sled = exercises.createCustom(
            NewExercise(
                name = LocalizedText("Sled push", "Empuje de trineo"),
                category = ExerciseCategory.STRENGTH,
                trackingType = TrackingType.WEIGHT_REPS,
                instructions = LocalizedSteps(instructions, instructions.map { "es: $it" }),
                variants = listOf(NewVariant(Equipment.MACHINE, LoadType.MACHINE)),
            ),
        )
        val id = routines.createRoutine("Sled day")
        val day = routines.addDay(id, "A")
        routines.addSlot(day, SlotSpec(SlotTarget.Fixed(sled.variants.single().id)))
        routines.addSlot(day, SlotSpec(SlotTarget.Flexible(sled.id)))
        return id to sled.id
    }

    @Test
    fun `a routine without custom exercises is still written as version 1`() = runTest {
        val sender = phone(senderDb)
        val json = sender.sharing.export(sender.catalogueRoutine(), author = "Ana")!!
        assertTrue(""""version":1""" in json)
        assertTrue(file(json).customExercises.isEmpty())
        assertFalse("Ana" in json)
    }

    @Test
    fun `custom exercises carry the sharer name, instructions and origin as version 2`() = runTest {
        val sender = phone(senderDb)
        val (id, sledId) = sender.customRoutine()
        val shared = file(sender.sharing.export(id, author = "  Ana  ")!!)
        assertEquals(2, shared.version)
        assertEquals(RoutineExport.VERSION, shared.version)
        val embedded = shared.customExercises.single()
        assertEquals("Ana", embedded.author)
        assertEquals(sledId, embedded.originId)
        assertEquals(listOf("Lean in.", "Drive."), embedded.instructions?.en)
        assertEquals(listOf("es: Lean in.", "es: Drive."), embedded.instructions?.es)

        // A blank name writes no author at all.
        listOf(null, "", "   ").forEach { blank ->
            val anonymous = sender.sharing.export(id, author = blank)!!
            assertFalse("\"author\"" in anonymous, "author=$blank")
            assertNull(file(anonymous).customExercises.single().author)
        }
    }

    @Test
    fun `the receiver gets the exercise as their own, by its creator`() = runTest {
        val sender = phone(senderDb)
        val (id, sledId) = sender.customRoutine()
        val json = sender.sharing.export(id, author = "Ana")!!

        val receiver = phone(receiverDb)
        val preview = receiver.preview(json)
        assertEquals(listOf("Ana"), preview.authors)
        assertTrue(preview.existingCustomIds.isEmpty())
        val copy = receiver.routines.getRoutine(receiver.sharing.import(preview, dropMissing = false))!!

        val exercise = receiver.exercises.getExercise(receiver.userExercises().single().id)!!
        assertNotEquals(sledId, exercise.id)
        assertEquals(ExerciseSource.USER, exercise.source)
        assertEquals("Ana", exercise.author)
        assertEquals(sledId, exercise.originId)
        assertEquals(LocalizedText("Sled push", "Empuje de trineo"), exercise.name)
        assertEquals(listOf("Lean in.", "Drive."), exercise.instructions.en)
        val slots = copy.days[0].slots
        assertEquals(exercise.variants.single().id, (slots[0].target as SlotTarget.Fixed).variantId)
        assertEquals(exercise.id, (slots[1].target as SlotTarget.Flexible).exerciseId)
        // The receiver owns it: editing keeps the creator.
        receiver.exercises.updateCustom(exercise.id, exercise.toEdit().copy(name = LocalizedText.same("Sled")))
        assertEquals("Ana", receiver.exercises.getExercise(exercise.id)!!.author)
    }

    @Test
    fun `re-sharing an imported exercise keeps its creator and origin`() = runTest {
        val sender = phone(senderDb)
        val (id, sledId) = sender.customRoutine()
        val receiver = phone(receiverDb)
        val copyId = receiver.import(sender.sharing.export(id, "Ana")!!)

        val reshared = receiver.sharing.export(copyId, author = "Bea")!!
        val again = file(reshared).customExercises.single()
        assertEquals("Ana", again.author)
        assertEquals(sledId, again.originId)

        // The creator importing it back reuses their own exercise.
        val back = sender.preview(reshared)
        assertEquals(setOf(again.id), back.existingCustomIds)
        sender.sharing.import(back, dropMissing = false)
        assertEquals(listOf(sledId), sender.userExercises().map { it.id })
    }

    @Test
    fun `importing the same shared exercise twice reuses the first copy`() = runTest {
        val sender = phone(senderDb)
        val (id, _) = sender.customRoutine()
        val json = sender.sharing.export(id, "Ana")!!
        val receiver = phone(receiverDb)
        val first = receiver.routines.getRoutine(receiver.import(json))!!

        val preview = receiver.preview(json)
        assertEquals(setOf(preview.file.customExercises.single().id), preview.existingCustomIds)
        val second = receiver.routines.getRoutine(receiver.sharing.import(preview, dropMissing = false))!!
        assertEquals(1, receiver.userExercises().size)
        assertEquals(first.days[0].slots.map { it.target }, second.days[0].slots.map { it.target })
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `importing a routine you shared yourself reuses your own exercise`() = runTest {
        val phone = phone(senderDb)
        val (id, sledId) = phone.customRoutine()
        val original = phone.routines.getRoutine(id)!!
        val copy = phone.routines.getRoutine(phone.import(phone.sharing.export(id)!!))!!
        assertEquals(listOf(sledId), phone.userExercises().map { it.id })
        assertEquals(original.days[0].slots.map { it.target }, copy.days[0].slots.map { it.target })
    }

    @Test
    fun `a copy deleted with history is restored by importing it again`() = runTest {
        val sender = phone(senderDb)
        val (id, _) = sender.customRoutine()
        val json = sender.sharing.export(id, "Ana")!!
        val receiver = phone(receiverDb)
        receiver.import(json)
        val copy = receiver.exercises.getExercise(receiver.userExercises().single().id)!!
        val session = receiver.sessions.startFree()
        receiver.sessions.addExercise(session, copy.variants.single().id)
        assertEquals(DeleteOutcome.HIDDEN, receiver.exercises.deleteCustom(copy.id, removeFromRoutines = true))

        receiver.import(json)
        assertEquals(listOf(copy.id), receiver.userExercises().map { it.id })
        assertFalse(receiver.exercises.getExercise(copy.id)!!.deprecated)
    }

    @Test
    fun `a copy deleted for good is created again on the next import`() = runTest {
        val sender = phone(senderDb)
        val (id, _) = sender.customRoutine()
        val json = sender.sharing.export(id, "Ana")!!
        val receiver = phone(receiverDb)
        val copyId = receiver.import(json)
        val first = receiver.userExercises().single().id
        receiver.routines.deleteRoutine(copyId)
        assertEquals(DeleteOutcome.DELETED, receiver.exercises.deleteCustom(first, removeFromRoutines = false))

        receiver.import(json)
        val again = receiver.userExercises().single()
        assertNotEquals(first, again.id)
        assertEquals("Ana", again.author)
    }

    @Test
    fun `a shared variant the reused copy lacks is added to it`() = runTest {
        val sender = phone(senderDb)
        val (id, sledId) = sender.customRoutine()
        val receiver = phone(receiverDb)
        receiver.import(sender.sharing.export(id, "Ana")!!)

        // The creator adds a dumbbell variant and uses it.
        val edit = sender.exercises.getExercise(sledId)!!.toEdit()
        val edited = sender.exercises.updateCustom(
            sledId,
            edit.copy(variants = edit.variants + VariantEdit(null, Equipment.DUMBBELL, LoadType.DUMBBELL)),
        )
        sender.routines.addSlot(
            sender.routines.getRoutine(id)!!.days[0].id,
            SlotSpec(SlotTarget.Fixed(edited.variants[1].id))
        )

        val copy = receiver.routines.getRoutine(receiver.import(sender.sharing.export(id, "Ana")!!))!!
        val reused = receiver.exercises.getExercise(receiver.userExercises().single().id)!!
        assertEquals(listOf(Equipment.MACHINE, Equipment.DUMBBELL), reused.variants.map { it.equipment })
        assertEquals(
            listOf(reused.variants[0].id, reused.variants[1].id),
            copy.days[0].slots.mapNotNull { (it.target as? SlotTarget.Fixed)?.variantId },
        )
    }

    /** A version 1 file as written by older app versions: no instructions, author or origin. */
    private val legacyV1 = """
        {"format":"haltera-routine","version":1,"exportedAt":7,
         "routine":{"name":"Old","description":"","days":[{"name":"A","kind":"TRAINING","slots":[
           {"target":{"type":"fixed","variantId":"user-old.machine"},"sets":3},
           {"target":{"type":"fixed","variantId":"bench-press.barbell"},"sets":5}]}]},
         "customExercises":[{"id":"user-old","name":{"en":"Old lift","es":"Ejercicio viejo"},
           "category":"STRENGTH","level":"BEGINNER","primaryMuscles":["CHEST"],"secondaryMuscles":[],
           "trackingType":"WEIGHT_REPS",
           "variants":[{"id":"user-old.machine","equipment":"MACHINE","loadType":"MACHINE"}]}]}
    """.trimIndent()

    @Test
    fun `a version 1 file and every older code still import their custom exercises`() = runTest {
        val receiver = phone(receiverDb)
        val v1Code = RoutineShareCode.encodeV1(legacyV1)
        val inputs = listOf(
            legacyV1,
            legacyV1.replace(RoutineExport.FORMAT, RoutineExport.LEGACY_FORMAT),
            RoutineShareCode.encode(legacyV1),
            RoutineShareCode.encode(legacyV1).replaceFirst(RoutineShareCode.PREFIX, RoutineShareCode.LEGACY_PREFIX),
            v1Code,
            RoutineShareCode.PREFIX_V1.uppercase() + v1Code.removePrefix(RoutineShareCode.PREFIX_V1),
        )
        inputs.forEach { input ->
            val preview = receiver.preview(input)
            assertTrue(preview.missingIds.isEmpty(), input.take(20))
            assertTrue(preview.authors.isEmpty())
            val routine = receiver.routines.getRoutine(receiver.sharing.import(preview, dropMissing = false))!!
            assertEquals(2, routine.days[0].slots.size)
        }
        // The six imports share one copy: version 1 exercises dedupe by their id (`user-<uuid>`).
        val copy = receiver.exercises.getExercise(receiver.userExercises().single().id)!!
        assertNull(copy.author)
        assertEquals("user-old", copy.originId)
        assertEquals(LocalizedText("Old lift", "Ejercicio viejo"), copy.name)
        assertTrue(copy.instructions.en.isEmpty())
        assertEquals(6, receiver.routines.observeRoutines().first().size)
    }

    @Test
    fun `a file from a newer format version is refused`() = runTest {
        val receiver = phone(receiverDb)
        val newer = legacyV1.replace(""""version":1""", """"version":3""")
        assertEquals(
            3,
            assertIs<ImportPreviewResult.UnsupportedVersion>(receiver.sharing.preview(newer)).version,
        )
        assertTrue(receiver.userExercises().isEmpty())
    }

    @Test
    fun `version 2 files tolerate unknown fields and cut an overlong creator name`() = runTest {
        val receiver = phone(receiverDb)
        val json = legacyV1.replace(""""version":1""", """"version":2,"future":true""")
            .replace(""""id":"user-old",""", """"id":"user-old","author":"${"x".repeat(500)}","shiny":1,""")
        receiver.import(json)
        assertEquals(60, receiver.exercises.getExercise(receiver.userExercises().single().id)!!.author?.length)
    }

    /** Text deflate cannot shrink much, as long instructions in several languages would be. */
    private fun noise(length: Int, seed: Int): String {
        val random = Random(seed)
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789 "
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    @Test
    fun `the QR code leaves the instructions out when the full code does not fit`() = runTest {
        val sender = phone(senderDb)
        val (id, _) = sender.customRoutine(instructions = (1..6).map { noise(250, it) })
        val full = RoutineShareCode.encode(sender.sharing.export(id, "Ana")!!)
        assertTrue(full.length > RoutineShareCode.MAX_QR_LENGTH, "full code ${full.length}")

        val code = sender.sharing.shareCode(id, "Ana")!!
        assertTrue(code.length <= RoutineShareCode.MAX_QR_LENGTH, "QR code ${code.length}")
        assertTrue(QrCapacity.isAlphanumeric(code))
        val embedded = file(RoutineShareCode.decode(code)!!).customExercises.single()
        assertNull(embedded.instructions)
        assertEquals("Ana", embedded.author)
        // The file keeps them.
        assertEquals(6, file(sender.sharing.export(id, "Ana")!!).customExercises.single().instructions?.en?.size)

        // The QR code imports on the other phone, without the instructions.
        val receiver = phone(receiverDb)
        receiver.import(code)
        val copy = receiver.exercises.getExercise(receiver.userExercises().single().id)!!
        assertTrue(copy.instructions.en.isEmpty())
        assertEquals("Ana", copy.author)
    }

    @Test
    fun `a small routine keeps the instructions in its QR code`() = runTest {
        val sender = phone(senderDb)
        val (id, _) = sender.customRoutine()
        val code = sender.sharing.shareCode(id, "Ana")!!
        assertTrue(code.startsWith(RoutineShareCode.PREFIX))
        assertEquals(2, file(RoutineShareCode.decode(code)!!).customExercises.single().instructions?.en?.size)
    }

    @Test
    fun `a routine too large even without instructions returns the full code`() = runTest {
        val sender = phone(senderDb)
        val id = sender.routines.createRoutine("Huge")
        val day = sender.routines.addDay(id, "A")
        repeat(HUGE) { i ->
            val e = sender.exercises.createCustom(
                NewExercise(
                    LocalizedText(noise(60, i), noise(60, 100 + i)),
                    ExerciseCategory.STRENGTH,
                    TrackingType.REPS,
                ),
            )
            sender.routines.addSlot(day, SlotSpec(SlotTarget.Fixed(e.variants.single().id)))
        }
        val code = sender.sharing.shareCode(id, "Ana")!!
        // Too large: the export screen shows the "share it as a file" message instead of a QR code.
        assertTrue(code.length > RoutineShareCode.MAX_QR_LENGTH)
        assertEquals(HUGE, file(RoutineShareCode.decode(code)!!).customExercises.size)
    }

    private companion object {
        const val HUGE = 40
    }
}
