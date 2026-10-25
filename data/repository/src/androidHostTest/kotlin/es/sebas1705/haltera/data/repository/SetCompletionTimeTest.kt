package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlRoutineRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import es.sebas1705.haltera.data.routines.FamousRoutines
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Completion times of sets lie inside their session: "Log past session" never stamps "now" (QA alpha.5). */
class SetCompletionTimeTest {
    private val db = inMemoryDatabase().withTestCatalog()
    private val clock = TestClock(NOW)

    private fun TestScope.repos(): Pair<SqlRoutineRepository, SqlSessionRepository> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return SqlRoutineRepository(db, FamousRoutines.all, dispatcher, clock) to
            SqlSessionRepository(db, HistoryLoader(db), dispatcher, clock)
    }

    private suspend fun SqlSessionRepository.times(sessionId: String): List<Long?> =
        getSession(sessionId)!!.exercises.flatMap { e -> e.sets.sortedBy { it.position }.map { it.completedAt } }

    @Test
    fun `sets of a past routine session logged days later complete inside the session`() = runTest {
        val (routines, sessions) = repos()
        val routine = routines.createRoutine("R")
        routines.addSlot(routines.addDay(routine, "A"), SlotSpec(SlotTarget.Fixed("bench-press.barbell")))
        // What LogPastSession does: start at the past time, finish with its duration, then the user logs the sets.
        val start = NOW - 5 * DAY
        val id = sessions.startFromRoutineDay(routine, 0, startedAt = start)
        sessions.finish(id, endedAt = start + HOUR)
        val exercise = sessions.getSession(id)!!.exercises.single().id
        repeat(3) { sessions.logSet(exercise, SetInput(reps = 5, loadGrams = 100_000)) }

        assertEquals(List(3) { start + HOUR }, sessions.times(id))
    }

    @Test
    fun `a free past session, an added exercise and a set ticked later stay inside too`() = runTest {
        val (_, sessions) = repos()
        val start = NOW - 2 * DAY
        val id = sessions.startFree(startedAt = start)
        sessions.finish(id, endedAt = start + HOUR)
        val exercise = sessions.addExercise(id, "squat.barbell")
        val pending = sessions.logSet(exercise, SetInput(reps = 5, loadGrams = 80_000, completed = false))
        assertEquals(listOf<Long?>(null), sessions.times(id))

        sessions.updateSet(pending, SetInput(reps = 5, loadGrams = 80_000, completed = true))
        assertEquals(listOf<Long?>(start + HOUR), sessions.times(id))
        sessions.updateSet(pending, SetInput(reps = 5, loadGrams = 80_000, completed = false))
        assertEquals(listOf<Long?>(null), sessions.times(id))
    }

    @Test
    fun `a live session stamps the time the set is ticked`() = runTest {
        val (_, sessions) = repos()
        val id = sessions.startFree()
        val exercise = sessions.addExercise(id, "squat.barbell")
        clock.millis = NOW + 90_000
        sessions.logSet(exercise, SetInput(reps = 5, loadGrams = 80_000))

        assertEquals(listOf<Long?>(NOW + 90_000), sessions.times(id))
        // Finishing later keeps it; ticking while the session is finished but still going on uses now as well.
        sessions.finish(id, endedAt = NOW + HOUR)
        clock.millis = NOW + 2 * 60_000
        sessions.logSet(exercise, SetInput(reps = 5, loadGrams = 80_000))
        assertEquals(listOf<Long?>(NOW + 90_000, NOW + 2 * 60_000), sessions.times(id))
    }

    @Test
    fun `editing a session's times moves its sets and keeps them inside`() = runTest {
        val (_, sessions) = repos()
        val start = NOW - 3 * DAY
        val id = sessions.startFree(startedAt = start)
        val exercise = sessions.addExercise(id, "squat.barbell")
        clock.millis = start + 10 * MINUTE
        sessions.logSet(exercise, SetInput(reps = 5, loadGrams = 80_000))
        clock.millis = start + 50 * MINUTE
        sessions.logSet(exercise, SetInput(reps = 5, loadGrams = 80_000))
        sessions.finish(id, endedAt = start + HOUR)

        // The date was wrong by a day: the sets keep their offsets.
        sessions.updateTimes(id, start - DAY, start - DAY + HOUR)
        assertEquals(listOf<Long?>(start - DAY + 10 * MINUTE, start - DAY + 50 * MINUTE), sessions.times(id))

        // It was shorter: a set after the new end moves to the end.
        sessions.updateTimes(id, start - DAY, start - DAY + 30 * MINUTE)
        assertEquals(listOf<Long?>(start - DAY + 10 * MINUTE, start - DAY + 30 * MINUTE), sessions.times(id))

        // Reopened (no end): only the start bounds them.
        sessions.updateTimes(id, start - DAY + 20 * MINUTE, null)
        assertEquals(listOf<Long?>(start - DAY + 30 * MINUTE, start - DAY + 50 * MINUTE), sessions.times(id))
        assertNull(sessions.getSession(id)!!.endedAt)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val NOW = 1_790_812_800_000L
    }
}
