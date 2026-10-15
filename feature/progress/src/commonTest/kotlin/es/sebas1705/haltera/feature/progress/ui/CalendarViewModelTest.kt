package es.sebas1705.haltera.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.domain.usecases.session.LogPastSession
import es.sebas1705.haltera.domain.usecases.session.ObserveTodayPlan
import es.sebas1705.haltera.feature.progress.MainDispatcherTest
import es.sebas1705.haltera.feature.progress.fakes.FakeRoutineRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.haltera.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.haltera.feature.progress.model.MonthKey
import es.sebas1705.haltera.feature.progress.performed
import es.sebas1705.haltera.feature.progress.session
import es.sebas1705.haltera.feature.progress.set
import es.sebas1705.haltera.feature.progress.testClock
import es.sebas1705.haltera.feature.progress.ui.calendar.CalendarEffect
import es.sebas1705.haltera.feature.progress.ui.calendar.CalendarIntent
import es.sebas1705.haltera.feature.progress.ui.calendar.CalendarViewModel
import es.sebas1705.haltera.feature.progress.utc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class CalendarViewModelTest : MainDispatcherTest() {
    private val sessions = FakeSessionRepository(
        listOf(
            session(
                "push",
                utc(2026, 10, 3),
                performed("bench.barbell", set(100.0, 5)),
                routineId = "r1",
                dayName = "Push"
            ),
            session("morning", utc(2026, 10, 14, hour = 8)),
            session("evening", utc(2026, 10, 14, hour = 19), durationMinutes = null),
            session("november", utc(2026, 11, 2)),
            session("september", utc(2026, 9, 30)),
        ),
    )
    private val routines = FakeRoutineRepository(listOf(Routine(id = "r1", name = "PPL")))
    private val settings = FakeSettingsRepository(WeightUnit.LB)

    private fun viewModel(routines: FakeRoutineRepository = this.routines) = CalendarViewModel(
        sessions,
        routines,
        settings,
        testClock(),
        ObserveTodayPlan(routines, sessions),
        LogPastSession(sessions),
    )

    @Test
    fun `starts on the current month with today selected and groups its sessions by day`() = runTest(dispatcher) {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(MonthKey(2026, 10), state.month)
            assertEquals(LocalDate(2026, 10, 14), state.selectedDay)
            assertFalse(state.loading)
            assertEquals(WeightUnit.LB, state.unit)
            assertEquals(setOf(LocalDate(2026, 10, 3), LocalDate(2026, 10, 14)), state.sessionsByDay.keys)
            assertEquals(listOf("morning", "evening"), state.selectedSessions.map { it.id })
            assertTrue(state.selectedSessions[1].inProgress)
        }
        assertEquals(utc(2026, 10, 1, hour = 0) to utc(2026, 11, 1, hour = 0), sessions.requestedRanges.single())
    }

    @Test
    fun `a selected day lists its sessions with routine and day names`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(CalendarIntent.SelectDay(LocalDate(2026, 10, 3)))
            val summary = expectMostRecentItem().selectedSessions.single()
            assertEquals("PPL", summary.routineName)
            assertEquals("Push", summary.dayName)
            assertEquals(1, summary.exerciseCount)
            assertEquals(500_000L, summary.volumeGrams)
            assertEquals(3_600_000L, summary.durationMillis)
        }
    }

    @Test
    fun `month navigation loads the other months and clears the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(CalendarIntent.NextMonth)
            val november = expectMostRecentItem()
            assertEquals(MonthKey(2026, 11), november.month)
            assertNull(november.selectedDay)
            assertEquals(setOf(LocalDate(2026, 11, 2)), november.sessionsByDay.keys)

            vm.onEvent(CalendarIntent.PreviousMonth)
            vm.onEvent(CalendarIntent.PreviousMonth)
            val september = expectMostRecentItem()
            assertEquals(MonthKey(2026, 9), september.month)
            assertEquals(setOf(LocalDate(2026, 9, 30)), september.sessionsByDay.keys)

            vm.onEvent(CalendarIntent.NextMonth)
            assertEquals(LocalDate(2026, 10, 14), expectMostRecentItem().selectedDay)
        }
        assertEquals(utc(2026, 11, 1, hour = 0) to utc(2026, 12, 1, hour = 0), sessions.requestedRanges[1])
    }

    private val ppl = Routine(
        id = "r1",
        name = "PPL",
        isActive = true,
        days = listOf(
            RoutineDay("d0", "r1", 0, "Push", DayKind.TRAINING),
            RoutineDay("d1", "r1", 1, "Pull", DayKind.TRAINING),
            RoutineDay("d2", "r1", 2, "Rest", DayKind.REST),
        ),
    )

    @Test
    fun `a past day can get a session logged, which opens it and shows up in the calendar`() = runTest(dispatcher) {
        val vm = viewModel(FakeRoutineRepository(listOf(ppl)))
        vm.onEvent(CalendarIntent.SelectDay(LocalDate(2026, 10, 12)))
        assertTrue(vm.uiState.value.canAddOnSelectedDay)

        vm.onEvent(CalendarIntent.OpenLogPast)
        val dialog = assertNotNull(vm.uiState.value.logPast)
        assertEquals(listOf("Push", "Pull"), dialog.options.map { it.dayName })
        // The active routine's next day is preselected (Push: no session of it records a day).
        assertEquals("0", dialog.initial.choiceKey)
        assertEquals(utc(2026, 10, 12, hour = 0), dialog.initial.dateUtcMillis)
        assertEquals(18 to 0, dialog.initial.hour to dialog.initial.minute, "a past day starts at 18:00")
        assertEquals(utc(2026, 10, 14, hour = 0), dialog.maxDateUtcMillis)

        vm.effects.test {
            vm.onEvent(CalendarIntent.ConfirmLogPast(dialog.initial.copy(durationMinutes = 45, choiceKey = "1")))
            val opened = assertIs<CalendarEffect.OpenSession>(awaitItem())
            val logged = sessions.sessions.value.single { it.id == opened.sessionId }
            assertEquals(utc(2026, 10, 12, hour = 18), logged.startedAt)
            assertEquals(utc(2026, 10, 12, hour = 18, minute = 45), logged.endedAt)
            assertEquals(1, logged.routineDayIndex)
        }
        assertNull(vm.uiState.value.logPast)
        assertEquals(1, vm.uiState.value.sessionsByDay.getValue(LocalDate(2026, 10, 12)).size)
    }

    @Test
    fun `future days cannot get a session and a free session can be logged`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(CalendarIntent.SelectDay(LocalDate(2026, 10, 20)))
        assertFalse(vm.uiState.value.canAddOnSelectedDay)
        vm.onEvent(CalendarIntent.OpenLogPast)
        assertNull(vm.uiState.value.logPast)

        vm.onEvent(CalendarIntent.SelectDay(LocalDate(2026, 10, 14)))
        vm.onEvent(CalendarIntent.OpenLogPast)
        val dialog = assertNotNull(vm.uiState.value.logPast)
        assertEquals("free", dialog.initial.choiceKey, "no training day to preselect")
        assertEquals(11 to 0, dialog.initial.hour to dialog.initial.minute, "today: an hour ago")
        vm.effects.test {
            vm.onEvent(CalendarIntent.ConfirmLogPast(dialog.initial))
            val opened = assertIs<CalendarEffect.OpenSession>(awaitItem())
            assertNull(sessions.sessions.value.single { it.id == opened.sessionId }.routineId)
        }
    }
}
