package es.sebas1705.ironlog.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.feature.progress.MainDispatcherTest
import es.sebas1705.ironlog.feature.progress.fakes.FakeRoutineRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.ironlog.feature.progress.model.MonthKey
import es.sebas1705.ironlog.feature.progress.performed
import es.sebas1705.ironlog.feature.progress.session
import es.sebas1705.ironlog.feature.progress.set
import es.sebas1705.ironlog.feature.progress.testClock
import es.sebas1705.ironlog.feature.progress.ui.calendar.CalendarIntent
import es.sebas1705.ironlog.feature.progress.ui.calendar.CalendarViewModel
import es.sebas1705.ironlog.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    private fun viewModel() = CalendarViewModel(sessions, routines, settings, testClock())

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
}
