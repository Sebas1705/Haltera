package es.sebas1705.haltera.feature.progress.ui

import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.domain.usecases.session.LogPastSession
import es.sebas1705.haltera.domain.usecases.session.ObserveTodayPlan
import es.sebas1705.haltera.feature.progress.MainDispatcherTest
import es.sebas1705.haltera.feature.progress.exercise
import es.sebas1705.haltera.feature.progress.fakes.FakeExerciseRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeRoutineRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.haltera.feature.progress.model.StatsPeriodSelection
import es.sebas1705.haltera.feature.progress.performed
import es.sebas1705.haltera.feature.progress.session
import es.sebas1705.haltera.feature.progress.set
import es.sebas1705.haltera.feature.progress.testClock
import es.sebas1705.haltera.feature.progress.ui.calendar.CalendarViewModel
import es.sebas1705.haltera.feature.progress.ui.overview.OverviewViewModel
import es.sebas1705.haltera.feature.progress.ui.session.SessionDetailViewModel
import es.sebas1705.haltera.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * QA alpha.4: on 9 October the calendar showed 5,052 kg and the overview 5,604 kg for the same session, because only
 * the overview counted both dumbbells of a bilateral slot. Every screen now sums with TrainingMath.volume, which reads the
 * "Both hands" snapshot of each logged exercise (taken from its slot when it was logged, e2 here), never the routine.
 */
class VolumeConsistencyTest : MainDispatcherTest() {
    private val routine = Routine(
        id = "r",
        name = "Upper",
        days = listOf(
            RoutineDay(
                id = "d",
                routineId = "r",
                position = 0,
                name = "Upper",
                slots = listOf(
                    RoutineSlot("slot-bench", "d", 0, SlotTarget.Fixed("bench.barbell")),
                    RoutineSlot("slot-db", "d", 1, SlotTarget.Fixed("press.dumbbell"), bilateral = true),
                ),
            ),
        ),
    )
    private val sessions = FakeSessionRepository(
        listOf(
            session(
                "s1",
                utc(2026, 10, 9),
                performed("bench.barbell", set(100.0, 5), set(100.0, 5)).copy(id = "e1", slotId = "slot-bench"),
                performed(
                    "press.dumbbell",
                    set(24.0, 10),
                    set(24.0, 8)
                ).copy(id = "e2", slotId = "slot-db", bilateral = true),
                // Added during the session (no slot): one dumbbell.
                performed("curl.dumbbell", set(12.0, 12)).copy(id = "e3"),
                routineId = "r",
                dayName = "Upper",
            ),
        ),
    )
    private val routines = FakeRoutineRepository(listOf(routine))
    private val exercises = FakeExerciseRepository(
        listOf(
            exercise("bench", Muscle.CHEST),
            exercise("press", Muscle.CHEST, equipment = listOf(Equipment.DUMBBELL)),
            exercise("curl", Muscle.BICEPS, equipment = listOf(Equipment.DUMBBELL)),
        ),
        sessions,
    )

    /** 2 × 100 × 5 + (24 × 10 + 24 × 8) × 2 + 12 × 12, in gram-reps. */
    private val expected = 1_000_000L + 864_000L + 144_000L

    @Test
    fun `calendar, session detail and overview show the same volume`() = runTest(dispatcher) {
        val calendar = CalendarViewModel(
            sessions,
            routines,
            FakeSettingsRepository(),
            testClock(),
            ObserveTodayPlan(routines, sessions),
            LogPastSession(sessions),
        )
        val day = calendar.uiState.value.sessionsByDay.getValue(LocalDate(2026, 10, 9)).single()
        assertEquals(expected, day.volumeGrams)

        val detail = SessionDetailViewModel("s1", sessions, routines, exercises, FakeSettingsRepository(), testClock())
        val state = detail.uiState.value
        assertEquals(expected, state.summary!!.volumeGrams)
        assertEquals(listOf(1_000_000L, 864_000L, 144_000L), state.exercises.map { it.volumeGrams })

        val overview = OverviewViewModel(
            sessions,
            routines,
            FakeSettingsRepository(),
            testClock(),
            StatsPeriodSelection()
        )
        assertEquals(expected.toDouble(), overview.uiState.value.stats!!.volume.current)
    }

    @Test
    fun `toggling the flag, removing the slot or deleting the routine never changes past volume`() = runTest(
        dispatcher
    ) {
        val calendar = CalendarViewModel(
            sessions,
            routines,
            FakeSettingsRepository(),
            testClock(),
            ObserveTodayPlan(routines, sessions),
            LogPastSession(sessions),
        )
        val detail = SessionDetailViewModel("s1", sessions, routines, exercises, FakeSettingsRepository(), testClock())
        val overview = OverviewViewModel(
            sessions,
            routines,
            FakeSettingsRepository(),
            testClock(),
            StatsPeriodSelection()
        )

        fun assertUnchanged() {
            assertEquals(
                expected,
                calendar.uiState.value.sessionsByDay.getValue(LocalDate(2026, 10, 9)).single().volumeGrams
            )
            assertEquals(expected, detail.uiState.value.summary!!.volumeGrams)
            assertEquals(864_000L, detail.uiState.value.exercises.single { it.id == "e2" }.volumeGrams)
            assertEquals(expected.toDouble(), overview.uiState.value.stats!!.volume.current)
        }
        assertUnchanged()

        // "Both hands" switched off: only sessions logged afterwards count one dumbbell.
        routines.routines.value = listOf(
            routine.copy(days = routine.days.map { d -> d.copy(slots = d.slots.map { it.copy(bilateral = false) }) }),
        )
        assertUnchanged()

        // The slot removed and the routine deleted: ON DELETE SET NULL clears the links, the snapshot stays.
        routines.routines.value = emptyList()
        sessions.sessions.value = sessions.sessions.value.map { s ->
            s.copy(routineId = null, exercises = s.exercises.map { it.copy(slotId = null) })
        }
        assertUnchanged()
    }
}
