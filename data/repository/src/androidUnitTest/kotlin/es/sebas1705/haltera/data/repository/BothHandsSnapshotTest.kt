package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The "Both hands" flag is snapshotted on each logged exercise (schema 8, core design §3.3): nothing done to the
 * routine afterwards changes what was logged, so past dumbbell volume never changes.
 */
class BothHandsSnapshotTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(1_000_000L)

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

    private class Plan(val routineId: String, val press: String, val curl: String, val row: String, val customVariant: String)

    /** Day A: dumbbell press (both hands), a custom dumbbell curl (both hands), a one-arm row (one hand). */
    private suspend fun Repos.plan(): Plan {
        val custom = exercises.createCustom(
            NewExercise(
                name = LocalizedText.same("Cable-free curl"),
                category = ExerciseCategory.STRENGTH,
                trackingType = TrackingType.WEIGHT_REPS,
                variants = listOf(NewVariant(Equipment.DUMBBELL, LoadType.DUMBBELL)),
            ),
        )
        val variant = custom.variants.single().id
        val routineId = routines.createRoutine("Upper")
        val day = routines.addDay(routineId, "A")
        val press = routines.addSlot(day, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = true))
        val curl = routines.addSlot(day, SlotSpec(SlotTarget.Fixed(variant), bilateral = true))
        val row = routines.addSlot(day, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = false))
        return Plan(routineId, press, curl, row, variant)
    }

    /** Logs a past session of day A at [at] with two 20 kg × 10 sets per exercise. */
    private suspend fun Repos.logPast(routineId: String, at: Long): String {
        val id = sessions.startFromRoutineDay(routineId, 0, startedAt = at)
        sessions.finish(id, endedAt = at + HOUR)
        sessions.getSession(id)!!.exercises.forEach { e ->
            repeat(2) { sessions.logSet(e.id, SetInput(reps = 10, loadGrams = 20_000)) }
        }
        return id
    }

    private suspend fun Repos.flags(sessionId: String): List<Boolean> =
        sessions.getSession(sessionId)!!.exercises.sortedBy { it.position }.map { it.bilateral }

    /** Volume as TrainingMath.volume sums it (domain:usecases is not visible here): load × reps, doubled for both hands. */
    private fun SessionExercise.volume(): Long =
        sets.filter { it.completed }.sumOf { (it.loadGrams ?: 0) * (it.reps ?: 0) } * if (bilateral) 2 else 1

    private suspend fun Repos.volume(sessionId: String): Long = sessions.getSession(sessionId)!!.exercises.sumOf {
        it.volume()
    }

    @Test
    fun `a past routine session snapshots each slot's flag`() = runTest {
        val r = repos()
        val plan = r.plan()
        val past = r.logPast(plan.routineId, at = clock.millis - 3 * DAY)

        assertEquals(listOf(true, true, false), r.flags(past))
        // Press and curl 2 × 20 kg × 10 × 2 hands = 800 kg each, row 400 kg: 2,000 kg in gram-reps.
        assertEquals(2_000_000L, r.volume(past))
    }

    @Test
    fun `a live session snapshots at start, exercises added from a slot or ad hoc at the time they are added`() = runTest {
        val r = repos()
        val plan = r.plan()
        val live = r.sessions.startFromRoutineDay(plan.routineId, 0)
        assertEquals(listOf(true, true, false), r.flags(live))

        val fromSlot = r.sessions.addExercise(live, "bench-press.dumbbell", plan.press)
        val adHoc = r.sessions.addExercise(live, "bench-press.dumbbell")
        val exercises = r.sessions.getSession(live)!!.exercises.associateBy { it.id }
        assertEquals(true, exercises.getValue(fromSlot).bilateral)
        assertEquals(false, exercises.getValue(adHoc).bilateral)
        // A free session has no slots: one implement.
        val free = r.sessions.startFree(startedAt = clock.millis - DAY)
        r.sessions.addExercise(free, "bench-press.dumbbell")
        assertEquals(listOf(false), r.flags(free))
    }

    @Test
    fun `toggling the switch changes future sessions only`() = runTest {
        val r = repos()
        val plan = r.plan()
        val before = r.logPast(plan.routineId, at = clock.millis - 3 * DAY)
        val volume = r.volume(before)

        r.routines.updateSlot(plan.press, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = false))
        r.routines.updateSlot(plan.row, SlotSpec(SlotTarget.Fixed("bench-press.dumbbell"), bilateral = true))

        assertEquals(listOf(true, true, false), r.flags(before))
        assertEquals(volume, r.volume(before))
        val after = r.logPast(plan.routineId, at = clock.millis - DAY)
        assertEquals(listOf(false, true, true), r.flags(after))
    }

    @Test
    fun `removing the slot keeps the snapshot`() = runTest {
        val r = repos()
        val plan = r.plan()
        val past = r.logPast(plan.routineId, at = clock.millis - 3 * DAY)
        val volume = r.volume(past)

        r.routines.deleteSlot(plan.press)

        val press = r.sessions.getSession(past)!!.exercises.single { it.position == 0 }
        assertNull(press.slotId)
        assertEquals(true, press.bilateral)
        assertEquals(volume, r.volume(past))
    }

    @Test
    fun `deleting the routine keeps the snapshot`() = runTest {
        val r = repos()
        val plan = r.plan()
        val past = r.logPast(plan.routineId, at = clock.millis - 3 * DAY)
        val volume = r.volume(past)

        r.routines.deleteRoutine(plan.routineId)

        val session = r.sessions.getSession(past)!!
        assertNull(session.routineId)
        assertEquals(listOf(true, true, false), r.flags(past))
        assertEquals(volume, r.volume(past))
    }

    @Test
    fun `deleting a custom exercise used in a routine keeps the snapshot`() = runTest {
        val r = repos()
        val plan = r.plan()
        val past = r.logPast(plan.routineId, at = clock.millis - 3 * DAY)
        val volume = r.volume(past)
        val customId = r.exercises.getVariants(listOf(plan.customVariant)).single().exerciseId

        r.exercises.deleteCustom(customId, removeFromRoutines = true)

        val curl = r.sessions.getSession(past)!!.exercises.single { it.variantId == plan.customVariant }
        assertNull(curl.slotId)
        assertEquals(true, curl.bilateral)
        assertEquals(volume, r.volume(past))
        assertEquals(
            listOf(plan.press, plan.row),
            r.routines.getRoutine(plan.routineId)!!.days.single().slots.map { it.id }
        )
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
