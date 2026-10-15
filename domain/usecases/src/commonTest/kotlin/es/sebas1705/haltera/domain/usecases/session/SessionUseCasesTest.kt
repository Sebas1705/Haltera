package es.sebas1705.haltera.domain.usecases.session

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.LoadIncrement
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SessionUseCasesTest {
    private val variant = "bench-press.barbell"
    private val slot = RoutineSlot(
        id = "slot",
        dayId = "d0",
        position = 0,
        target = SlotTarget.Fixed(variant),
        sets = 2,
        prescription = Prescription.RepRange(8, 12),
        progression = ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)),
    )

    private fun performance(sessionId: String, startedAt: Long, reps: Int, load: Long) = ExerciseHistoryEntry(
        sessionId,
        startedAt,
        SessionExercise(
            id = "e-$sessionId",
            sessionId = sessionId,
            position = 0,
            variantId = variant,
            slotId = slot.id,
            sets = List(
                2
            ) { SetLog("$sessionId-$it", "e-$sessionId", it, reps = reps, loadGrams = load, completed = true) },
        ),
    )

    @Test
    fun `the suggestion for a session only looks at sessions before it`() = runTest {
        val sessions = FakeSessions()
        // Inserted out of order: the later session (not at the top of the range) comes first.
        sessions.slotHistory += performance("later", startedAt = 3_000, reps = 8, load = 102_500)
        sessions.slotHistory += performance("earlier", startedAt = 1_000, reps = 12, load = 100_000)
        val suggest = SuggestNextLoad(sessions)

        assertEquals(Suggestion.Keep(102_500), suggest(slot, variant))
        val past = assertIs<Suggestion.Increase>(suggest(slot, variant, before = 2_000))
        assertEquals(100_000L to 102_500L, past.fromGrams to past.toGrams)
        assertEquals(Suggestion.FirstTime, suggest(slot, variant, before = 1_000))
    }

    private val routine = Routine(
        id = "r",
        name = "Upper/Lower",
        isActive = true,
        days = listOf(
            RoutineDay("d0", "r", 0, "Upper", DayKind.TRAINING),
            RoutineDay("d1", "r", 1, "Lower", DayKind.TRAINING),
            RoutineDay("d2", "r", 2, "Rest", DayKind.REST),
        ),
    )

    @Test
    fun `today's plan follows logged past sessions, in or out of order`() = runTest {
        val sessions = FakeSessions()
        val routines = FakeRoutines(routine)
        val plan = ObserveTodayPlan(routines, sessions)
        val log = LogPastSession(sessions)
        assertEquals(0, plan().first()!!.next!!.dayIndex)

        log(PastSessionTarget.RoutineDay("r", 0), startedAt = 5_000, durationMillis = 3_600_000)
        assertEquals(1, plan().first()!!.next!!.dayIndex)

        // An older Lower logged afterwards does not move the cycle back.
        log(PastSessionTarget.RoutineDay("r", 1), startedAt = 1_000, durationMillis = 60_000)
        assertEquals(1, plan().first()!!.next!!.dayIndex)

        // Lower after Upper: the next day wraps past the rest day back to Upper, with the rest hint.
        log(PastSessionTarget.RoutineDay("r", 1), startedAt = 9_000, durationMillis = 60_000)
        val next = plan().first()!!.next!!
        assertEquals(0, next.dayIndex)
        assertEquals(listOf(2), next.restDaysSkipped)
    }

    @Test
    fun `an override applies until a newer session starts`() = runTest {
        val sessions = FakeSessions()
        val routines = FakeRoutines(routine.copy(nextDayIndex = 1, nextDayIndexSetAt = 10_000))
        val plan = ObserveTodayPlan(routines, sessions)
        val log = LogPastSession(sessions)
        log(PastSessionTarget.RoutineDay("r", 1), startedAt = 2_000, durationMillis = 0)
        assertEquals(1, plan().first()!!.next!!.dayIndex)

        log(PastSessionTarget.RoutineDay("r", 0), startedAt = 11_000, durationMillis = 0)
        assertEquals(1, plan().first()!!.next!!.dayIndex)
        log(PastSessionTarget.RoutineDay("r", 1), startedAt = 12_000, durationMillis = 0)
        assertEquals(0, plan().first()!!.next!!.dayIndex)
    }

    @Test
    fun `no active routine gives no plan`() = runTest {
        assertNull(ObserveTodayPlan(FakeRoutines(null), FakeSessions())().first())
    }

    @Test
    fun `logging a past session creates it at its time and saves it with its duration`() = runTest {
        val sessions = FakeSessions()
        val log = LogPastSession(sessions)

        val day = log(PastSessionTarget.RoutineDay("r", 1), startedAt = 1_000, durationMillis = 90_000)
        val free = log(PastSessionTarget.Free, startedAt = 5_000, durationMillis = -5)

        assertEquals(listOf("day:r:1@1000", "free@5000"), sessions.starts)
        assertEquals(listOf<Pair<String, Long?>>(day to 91_000L, free to 5_000L), sessions.finished)
    }

    @Test
    fun `options list training days, active routine first`() {
        val other = Routine(
            id = "o",
            name = "Full body",
            days = listOf(RoutineDay("x", "o", 0, "Full", DayKind.TRAINING)),
        )
        val options = LogPastSession.options(listOf(other, routine))
        assertEquals(
            listOf("Upper/Lower·Upper·true", "Upper/Lower·Lower·true", "Full body·Full·false"),
            options.map { "${it.routineName}·${it.dayName}·${it.activeRoutine}" },
        )
        assertEquals(PastSessionTarget.RoutineDay("r", 1), options[1].target)
    }
}
