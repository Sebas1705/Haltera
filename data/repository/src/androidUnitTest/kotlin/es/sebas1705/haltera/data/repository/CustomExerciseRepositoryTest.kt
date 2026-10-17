package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.DeleteOutcome
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.RoutineUse
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.VariantEdit
import es.sebas1705.haltera.data.models.toEdit
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edit, usage, delete policy and restore of custom exercises (core design §3.1.1). */
class CustomExerciseRepositoryTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(1_000L)

    private class Repos(
        val exercises: SqlExerciseRepository,
        val routines: SqlRoutineRepository,
        val sessions: SqlSessionRepository,
    )

    private fun TestScope.repos(): Repos {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return Repos(
            SqlExerciseRepository(db, HistoryLoader(db), dispatcher, clock),
            SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock),
            SqlSessionRepository(db, HistoryLoader(db), dispatcher, clock),
        )
    }

    private suspend fun Repos.sled(): Exercise = exercises.createCustom(
        NewExercise(
            name = LocalizedText("Sled push", "Empuje de trineo"),
            category = ExerciseCategory.STRENGTH,
            trackingType = TrackingType.WEIGHT_REPS,
            primaryMuscles = listOf(Muscle.QUADRICEPS),
            instructions = LocalizedSteps(listOf("Push."), listOf("Empuja.")),
            variants = listOf(
                NewVariant(Equipment.MACHINE, LoadType.MACHINE),
                NewVariant(Equipment.BODYWEIGHT, LoadType.BODYWEIGHT),
            ),
        ),
    )

    /** A finished session with [sets] completed sets (and one not completed) of [variantId]. */
    private suspend fun Repos.log(variantId: String, sets: Int = 2): String {
        val session = sessions.startFree(startedAt = clock.millis)
        val exercise = sessions.addExercise(session, variantId)
        repeat(sets) { sessions.logSet(exercise, SetInput(reps = 10, loadGrams = 50_000)) }
        sessions.logSet(exercise, SetInput(reps = 10, loadGrams = 50_000, completed = false))
        sessions.finish(session, endedAt = clock.millis + 1)
        return session
    }

    private fun syncState(id: String) = db.syncTrackingQueries.selectState("exercises", id).executeAsOneOrNull()

    private suspend fun Repos.searchIds(query: String = "") =
        exercises.search(ExerciseFilter(query = query), "en").first().map { it.id }

    // Edit

    @Test
    fun `an edit changes every field and keeps the exercise and variant ids`() = runTest {
        val r = repos()
        val sled = r.sled()
        val machine = sled.variants[0]
        clock.millis = 2_000
        val edited = r.exercises.updateCustom(
            sled.id,
            sled.toEdit().copy(
                name = LocalizedText("Prowler push", "Empuje de prowler"),
                category = ExerciseCategory.CALISTHENICS,
                trackingType = TrackingType.TIME,
                level = Level.EXPERT,
                primaryMuscles = listOf(Muscle.GLUTES, Muscle.QUADRICEPS),
                secondaryMuscles = listOf(Muscle.CALVES),
                instructions = LocalizedSteps(listOf("Lean.", "Drive."), listOf("Inclínate.", "Empuja.")),
                variants = listOf(VariantEdit(machine.id, Equipment.SMITH, LoadType.BARBELL)),
            ),
        )
        assertEquals(sled.id, edited.id)
        assertEquals(LocalizedText("Prowler push", "Empuje de prowler"), edited.name)
        assertEquals(ExerciseCategory.CALISTHENICS, edited.category)
        assertEquals(TrackingType.TIME, edited.trackingType)
        assertEquals(Level.EXPERT, edited.level)
        assertEquals(listOf(Muscle.GLUTES, Muscle.QUADRICEPS), edited.primaryMuscles)
        assertEquals(listOf(Muscle.CALVES), edited.secondaryMuscles)
        assertEquals(listOf("Lean.", "Drive."), edited.instructions.en)
        assertEquals(listOf("Inclínate.", "Empuja."), edited.instructions.es)
        // Not logged: the variant may change equipment and load type in place (same id).
        assertEquals(listOf(machine.id), edited.variants.map { it.id })
        assertEquals(Equipment.SMITH to LoadType.BARBELL, edited.variants[0].equipment to edited.variants[0].loadType)
        // The unused bodyweight variant left out is deleted.
        assertNull(r.exercises.getVariant(sled.variants[1].id))
        // Search follows the new names, accent-insensitive, in both languages.
        assertEquals(listOf(sled.id), r.searchIds("prowler"))
        val spanish = r.exercises.search(ExerciseFilter(query = "EMPUJE DE PROWLER"), "es").first()
        assertEquals(listOf(sled.id), spanish.map { it.id })
        assertTrue(r.searchIds("sled").isEmpty())
        assertEquals(1L, syncState(sled.id)?.dirty)
    }

    @Test
    fun `history keeps pointing at the edited exercise`() = runTest {
        val r = repos()
        val sled = r.sled()
        val machine = sled.variants[0].id
        val session = r.log(machine)
        r.exercises.updateCustom(sled.id, sled.toEdit().copy(name = LocalizedText("Prowler", "Prowler")))

        val history = r.exercises.observeVariantHistory(machine).first()
        assertEquals(listOf(session), history.map { it.sessionId })
        assertEquals(machine, r.sessions.getSession(session)!!.exercises.single().variantId)
        assertEquals("Prowler", r.exercises.getExercise(r.exercises.getVariant(machine)!!.exerciseId)!!.name.en)
    }

    @Test
    fun `new variants get unique ids and keep the edit order`() = runTest {
        val r = repos()
        val sled = r.sled()
        val edit = sled.toEdit()
        val edited = r.exercises.updateCustom(
            sled.id,
            edit.copy(
                variants = listOf(
                    VariantEdit(null, Equipment.MACHINE, LoadType.MACHINE),
                    edit.variants[1],
                    edit.variants[0],
                ),
            ),
        )
        assertEquals(
            listOf("${sled.id}.machine-2", "${sled.id}.bodyweight", "${sled.id}.machine"),
            edited.variants.map { it.id },
        )
        assertEquals(listOf(0, 1, 2), edited.variants.map { it.position })
    }

    @Test
    fun `a removed variant with history is deprecated, kept for history and hidden from pickers`() = runTest {
        val r = repos()
        val sled = r.sled()
        val (machine, bodyweight) = sled.variants
        r.log(machine.id)
        val edit = sled.toEdit()
        val edited = r.exercises.updateCustom(sled.id, edit.copy(variants = listOf(edit.variants[1])))

        val kept = r.exercises.getVariant(machine.id)!!
        assertTrue(kept.deprecated)
        assertEquals(listOf(bodyweight.id, machine.id), edited.variants.map { it.id })
        assertEquals(1, r.exercises.observeVariantHistory(machine.id).first().size)
        val listed = r.exercises.search(ExerciseFilter(query = "sled"), "en").first().single()
        assertEquals(listOf(bodyweight.id), listed.variants.map { it.id })

        // Choosing it again restores the same variant, with its history.
        val again = edited.toEdit().variants + VariantEdit(machine.id, machine.equipment, machine.loadType)
        val restored = r.exercises.updateCustom(sled.id, edited.toEdit().copy(variants = again))
        assertFalse(restored.variants.first { it.id == machine.id }.deprecated)
    }

    @Test
    fun `a removed variant a routine uses is deprecated, not deleted`() = runTest {
        val r = repos()
        val sled = r.sled()
        val routine = r.routines.createRoutine("R")
        val day = r.routines.addDay(routine, "A")
        r.routines.addSlot(day, SlotSpec(SlotTarget.Fixed(sled.variants[1].id)))
        val edit = sled.toEdit()
        r.exercises.updateCustom(sled.id, edit.copy(variants = listOf(edit.variants[0])))
        assertTrue(r.exercises.getVariant(sled.variants[1].id)!!.deprecated)
        val target = r.routines.getRoutine(routine)!!.days[0].slots[0].target as SlotTarget.Fixed
        assertEquals(sled.variants[1].id, target.variantId)
    }

    @Test
    fun `what history depends on cannot change`() = runTest {
        val r = repos()
        val sled = r.sled()
        val machine = sled.variants[0]
        r.log(machine.id)
        val edit = sled.toEdit()
        assertFailsWith<IllegalStateException> {
            r.exercises.updateCustom(sled.id, edit.copy(trackingType = TrackingType.TIME))
        }
        assertFailsWith<IllegalStateException> {
            r.exercises.updateCustom(
                sled.id,
                edit.copy(variants = listOf(VariantEdit(machine.id, Equipment.CABLE, LoadType.MACHINE))),
            )
        }
        assertFailsWith<IllegalStateException> {
            r.exercises.updateCustom(
                sled.id,
                edit.copy(variants = listOf(VariantEdit(machine.id, Equipment.MACHINE, LoadType.DUMBBELL))),
            )
        }
        // The variant without history can still change, and nothing above was written.
        val edited = r.exercises.updateCustom(
            sled.id,
            edit.copy(
                variants = listOf(
                    edit.variants[0],
                    VariantEdit(sled.variants[1].id, Equipment.BAND, LoadType.BAND),
                ),
            ),
        )
        assertEquals(TrackingType.WEIGHT_REPS, edited.trackingType)
        assertEquals(Equipment.MACHINE, edited.variants[0].equipment)
        assertEquals(Equipment.BAND, edited.variants[1].equipment)
    }

    @Test
    fun `invalid edits are rejected`() = runTest {
        val r = repos()
        val sled = r.sled()
        val edit = sled.toEdit()
        assertFailsWith<IllegalArgumentException> { r.exercises.updateCustom("bench-press", edit) }
        assertFailsWith<IllegalArgumentException> { r.exercises.updateCustom("nope", edit) }
        assertFailsWith<IllegalArgumentException> {
            r.exercises.updateCustom(sled.id, edit.copy(name = LocalizedText(" ", "x")))
        }
        assertFailsWith<IllegalArgumentException> {
            r.exercises.updateCustom(sled.id, edit.copy(variants = emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            r.exercises.updateCustom(
                sled.id,
                edit.copy(variants = listOf(VariantEdit("bench-press.barbell", Equipment.BARBELL, LoadType.BARBELL))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            r.exercises.updateCustom(sled.id, edit.copy(variants = listOf(edit.variants[0], edit.variants[0])))
        }
        assertEquals(sled.name, r.exercises.getExercise(sled.id)!!.name)
    }

    // Usage

    @Test
    fun `usage lists routines, sessions and completed sets`() = runTest {
        val r = repos()
        val sled = r.sled()
        val (machine, bodyweight) = sled.variants
        val push = r.routines.createRoutine("Push")
        val pushDay = r.routines.addDay(push, "A")
        r.routines.addSlot(pushDay, SlotSpec(SlotTarget.Fixed(machine.id)))
        r.routines.addSlot(pushDay, SlotSpec(SlotTarget.Flexible(sled.id, listOf(bodyweight.id))))
        r.routines.addSlot(pushDay, SlotSpec(SlotTarget.Fixed("bench-press.barbell")))
        val legs = r.routines.createRoutine("Legs")
        r.routines.addSlot(r.routines.addDay(legs, "B"), SlotSpec(SlotTarget.Flexible(sled.id)))
        val other = r.routines.addDay(r.routines.createRoutine("Other"), "C")
        r.routines.addSlot(other, SlotSpec(SlotTarget.Fixed("squat.barbell")))
        r.log(machine.id, sets = 3)
        r.log(bodyweight.id, sets = 1)

        val usage = r.exercises.usage(sled.id)
        assertEquals(listOf(RoutineUse(legs, "Legs", 1), RoutineUse(push, "Push", 2)), usage.routines)
        assertEquals(2, usage.sessions)
        assertEquals(4, usage.sets)
        assertEquals(setOf(machine.id, bodyweight.id), usage.variantsWithHistory)
        assertEquals(setOf(machine.id, bodyweight.id), usage.variantsInRoutines)
        assertTrue(usage.inRoutines && usage.hasHistory)

        val fresh = r.exercises.createCustom(
            NewExercise(LocalizedText.same("Fresh"), ExerciseCategory.STRENGTH, TrackingType.REPS),
        )
        val none = r.exercises.usage(fresh.id)
        assertFalse(none.inRoutines || none.hasHistory)
        assertEquals(0, none.sessions)
        assertEquals(0, r.exercises.usage("nope").sets)
    }

    @Test
    fun `an exercise id that prefixes another one is not counted as used`() = runTest {
        val r = repos()
        val sled = r.sled()
        val routine = r.routines.createRoutine("R")
        // A target naming an id that only starts like the exercise's id.
        r.routines.addSlot(r.routines.addDay(routine, "A"), SlotSpec(SlotTarget.Flexible("${sled.id}x")))
        assertFalse(r.exercises.usage(sled.id).inRoutines)
    }

    // Delete policy

    @Test
    fun `an unused exercise is deleted for good and leaves a sync tombstone`() = runTest {
        val r = repos()
        val sled = r.sled()
        assertEquals(DeleteOutcome.DELETED, r.exercises.deleteCustom(sled.id, removeFromRoutines = false))
        assertNull(r.exercises.getExercise(sled.id))
        assertTrue(r.exercises.getVariants(sled.variants.map { it.id }).isEmpty())
        assertEquals(1L, syncState(sled.id)?.deleted)
        assertEquals(1L, syncState(sled.id)?.dirty)
    }

    @Test
    fun `an exercise in routines is kept unless removing it from them is allowed`() = runTest {
        val r = repos()
        val sled = r.sled()
        val routine = r.routines.createRoutine("Push")
        val day = r.routines.addDay(routine, "A")
        r.routines.addSlot(day, SlotSpec(SlotTarget.Fixed("bench-press.barbell")))
        r.routines.addSlot(day, SlotSpec(SlotTarget.Fixed(sled.variants[0].id)))
        r.routines.addSlot(day, SlotSpec(SlotTarget.Fixed("squat.barbell")))
        r.routines.addSlot(day, SlotSpec(SlotTarget.Flexible(sled.id)))
        val before = r.routines.getRoutine(routine)!!

        assertEquals(DeleteOutcome.USED_IN_ROUTINES, r.exercises.deleteCustom(sled.id, removeFromRoutines = false))
        assertNotNull(r.exercises.getExercise(sled.id))
        assertEquals(4, r.routines.getRoutine(routine)!!.days[0].slots.size)

        clock.millis = 9_000
        assertEquals(DeleteOutcome.DELETED, r.exercises.deleteCustom(sled.id, removeFromRoutines = true))
        val after = r.routines.getRoutine(routine)!!
        val slots = after.days[0].slots
        val left = slots.map { (it.target as SlotTarget.Fixed).variantId }
        assertEquals(listOf("bench-press.barbell", "squat.barbell"), left)
        assertEquals(listOf(0, 1), slots.map { it.position })
        assertEquals(before.days[0].slots[0].id, slots[0].id)
        assertEquals(9_000, after.updatedAt)
        assertNull(r.exercises.getExercise(sled.id))
    }

    @Test
    fun `an exercise with history is hidden, keeps its history and can be restored`() = runTest {
        val r = repos()
        val sled = r.sled()
        val session = r.log(sled.variants[0].id)
        assertEquals(DeleteOutcome.HIDDEN, r.exercises.deleteCustom(sled.id, removeFromRoutines = false))

        val hidden = r.exercises.getExercise(sled.id)!!
        assertTrue(hidden.deprecated)
        assertEquals(sled.variants.map { it.id }, hidden.variants.map { it.id })
        assertTrue(r.searchIds("sled").isEmpty())
        assertTrue(sled.id !in r.searchIds())
        assertEquals(listOf(session), r.exercises.observeExerciseHistory(sled.id).first().map { it.sessionId })
        assertEquals(sled.variants[0].id, r.sessions.getSession(session)!!.exercises.single().variantId)
        // A soft delete syncs as an update (the payload carries deprecated), not a tombstone.
        assertEquals(0L, syncState(sled.id)?.deleted)
        assertEquals(1L, syncState(sled.id)?.dirty)

        r.exercises.restoreCustom(sled.id)
        assertFalse(r.exercises.getExercise(sled.id)!!.deprecated)
        assertEquals(listOf(sled.id), r.searchIds("sled"))
    }

    @Test
    fun `hidden custom exercises are listed live until they are restored`() = runTest {
        val r = repos()
        val sled = r.sled()
        r.log(sled.variants[0].id)
        val other = r.exercises.createCustom(
            NewExercise(LocalizedText.same("Atlas carry"), ExerciseCategory.STRENGTH, TrackingType.WEIGHT_REPS),
        )
        r.log(other.variants[0].id)
        assertEquals(emptyList(), r.exercises.observeHiddenCustom().first())

        r.exercises.deleteCustom(sled.id, removeFromRoutines = false)
        r.exercises.deleteCustom(other.id, removeFromRoutines = false)
        // Sorted by English name, with every variant so the detail and the list can show them.
        val hidden = r.exercises.observeHiddenCustom().first()
        assertEquals(listOf(other.id, sled.id), hidden.map { it.id })
        assertEquals(sled.variants.map { it.id }, hidden.last().variants.map { it.id })
        assertTrue(hidden.all { it.deprecated })

        // As if the soft delete had been uploaded: the restore must make it dirty again.
        db.syncTrackingQueries.clearAllDirty()
        assertEquals(0L, syncState(sled.id)?.dirty)
        r.exercises.restoreCustom(sled.id)
        assertEquals(listOf(other.id), r.exercises.observeHiddenCustom().first().map { it.id })
        assertEquals(listOf(sled.id), r.searchIds("sled"), "searchable again at once")
        assertEquals(1L, syncState(sled.id)?.dirty, "the restore syncs as an update")
        assertEquals(0L, syncState(sled.id)?.deleted)
    }

    @Test
    fun `deprecated catalogue exercises are never listed as hidden`() = runTest {
        val r = repos()
        db.exerciseEntityQueries.deprecate(updatedAt = 2_000L, id = "bench-press")
        assertEquals(emptyList(), r.exercises.observeHiddenCustom().first())
    }

    @Test
    fun `an exercise only added to an in-progress session counts as history`() = runTest {
        val r = repos()
        val sled = r.sled()
        val session = r.sessions.startFree()
        r.sessions.addExercise(session, sled.variants[1].id)
        assertEquals(DeleteOutcome.HIDDEN, r.exercises.deleteCustom(sled.id, removeFromRoutines = true))
        assertEquals(1, r.sessions.getSession(session)!!.exercises.size)
    }

    @Test
    fun `used in routines and with history removes the slots and hides it`() = runTest {
        val r = repos()
        val sled = r.sled()
        val routine = r.routines.createRoutine("Push")
        r.routines.addSlot(r.routines.addDay(routine, "A"), SlotSpec(SlotTarget.Fixed(sled.variants[0].id)))
        r.log(sled.variants[0].id)
        assertEquals(DeleteOutcome.HIDDEN, r.exercises.deleteCustom(sled.id, removeFromRoutines = true))
        assertTrue(r.routines.getRoutine(routine)!!.days[0].slots.isEmpty())
        assertTrue(r.exercises.getExercise(sled.id)!!.deprecated)
    }

    @Test
    fun `catalogue exercises cannot be deleted`() = runTest {
        val r = repos()
        assertFailsWith<IllegalArgumentException> {
            r.exercises.deleteCustom("bench-press", removeFromRoutines = true)
        }
        assertFailsWith<IllegalArgumentException> { r.exercises.deleteCustom("nope", removeFromRoutines = true) }
        r.exercises.restoreCustom("bench-press") // no-op on catalogue rows
        assertNotNull(r.exercises.getExercise("bench-press"))
    }

    // Fuzzy search path (ExerciseSearch + SearchIndexCache, rebuilt on exercise/variant writes)

    @Test
    fun `fuzzy search follows edits, soft deletes and restores immediately`() = runTest {
        val r = repos()
        val sled = r.sled()
        assertEquals(listOf(sled.id), r.searchIds("sled push").filter { it.startsWith("user-") })

        // Renamed: found by the new name (with a typo), no longer by the old one.
        r.exercises.updateCustom(sled.id, sled.toEdit().copy(name = LocalizedText("Prowler march", "Marcha prowler")))
        assertEquals(sled.id, r.searchIds("prowler").first())
        assertEquals(sled.id, r.searchIds("prowlr march").first())
        assertTrue(sled.id !in r.searchIds("sled push"))
        val spanish = r.exercises.search(ExerciseFilter(query = "marcha"), "es").first().map { it.id }
        assertEquals(sled.id, spanish.first())

        // Soft-deleted (it has history): never returned; restored: returned again.
        r.log(sled.variants[0].id)
        assertEquals(DeleteOutcome.HIDDEN, r.exercises.deleteCustom(sled.id, removeFromRoutines = false))
        assertTrue(sled.id !in r.searchIds("prowler"))
        r.exercises.restoreCustom(sled.id)
        assertEquals(sled.id, r.searchIds("prowler").first())

        // Deleted for good: gone from search too.
        val temp = r.exercises.createCustom(
            NewExercise(LocalizedText.same("Zercher walk"), ExerciseCategory.STRENGTH, TrackingType.WEIGHT_REPS),
        )
        assertEquals(temp.id, r.searchIds("zercher walk").first())
        assertEquals(DeleteOutcome.DELETED, r.exercises.deleteCustom(temp.id, removeFromRoutines = false))
        assertTrue(temp.id !in r.searchIds("zercher walk"))
    }

    @Test
    fun `a live search emits the edited, hidden and restored exercise without a new query`() = runTest {
        val r = repos()
        val sled = r.sled()
        r.log(sled.variants[0].id)
        r.exercises.search(ExerciseFilter(query = "prowler"), "en").test {
            assertTrue(awaitItem().none { it.id == sled.id })
            r.exercises.updateCustom(sled.id, sled.toEdit().copy(name = LocalizedText.same("Prowler")))
            assertEquals(sled.id, awaitUntil { list -> list.any { it.id == sled.id } }.first().id)
            r.exercises.deleteCustom(sled.id, removeFromRoutines = false)
            awaitUntil { list -> list.none { it.id == sled.id } }
            r.exercises.restoreCustom(sled.id)
            awaitUntil { list -> list.any { it.id == sled.id } }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Next emission matching [predicate] (intermediate emissions of the same change are skipped). */
    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun `a removed equipment variant stops matching the equipment filter of search`() = runTest {
        val r = repos()
        val sled = r.sled()
        val bodyweight = ExerciseFilter(query = "sled push", equipment = Equipment.BODYWEIGHT)
        assertTrue(sled.id in r.exercises.search(bodyweight, "en").first().map { it.id })
        val edit = sled.toEdit()
        r.exercises.updateCustom(sled.id, edit.copy(variants = listOf(edit.variants[0])))
        assertTrue(sled.id !in r.exercises.search(bodyweight, "en").first().map { it.id })
        val machine = ExerciseFilter(query = "sled push", equipment = Equipment.MACHINE)
        assertTrue(sled.id in r.exercises.search(machine, "en").first().map { it.id })
    }

    // Share origin

    @Test
    fun `origin and author are stored and found`() = runTest {
        val r = repos()
        val imported = r.exercises.createCustom(
            NewExercise(
                LocalizedText.same("Landmine press"),
                ExerciseCategory.STRENGTH,
                TrackingType.WEIGHT_REPS,
                author = "Ana",
                originId = "user-abc",
            ),
        )
        assertEquals("Ana", imported.author)
        assertEquals("user-abc", imported.originId)
        assertEquals(imported.id, r.exercises.findByOrigin("user-abc")?.id)
        // The user's own exercise is found by its id.
        val own = r.sled()
        assertEquals(own.id, r.exercises.findByOrigin(own.id)?.id)
        assertNull(own.author)
        assertNull(r.exercises.findByOrigin("user-zzz"))
        assertNull(r.exercises.findByOrigin("bench-press"))
        // Edits keep the origin.
        r.exercises.updateCustom(imported.id, imported.toEdit().copy(name = LocalizedText.same("Landmine")))
        assertEquals("Ana", r.exercises.getExercise(imported.id)!!.author)
    }
}
