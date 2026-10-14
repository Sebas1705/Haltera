package es.sebas1705.ironlog.feature.progress.ui

import es.sebas1705.ironlog.core.ui.components.PastSessionForm
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.progress.MainDispatcherTest
import es.sebas1705.ironlog.feature.progress.exercise
import es.sebas1705.ironlog.feature.progress.fakes.FakeExerciseRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeRoutineRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.ironlog.feature.progress.performed
import es.sebas1705.ironlog.feature.progress.session
import es.sebas1705.ironlog.feature.progress.set
import es.sebas1705.ironlog.feature.progress.testClock
import es.sebas1705.ironlog.feature.progress.ui.session.DetailDialog
import es.sebas1705.ironlog.feature.progress.ui.session.SessionDetailIntent
import es.sebas1705.ironlog.feature.progress.ui.session.SessionDetailViewModel
import es.sebas1705.ironlog.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionDetailViewModelTest : MainDispatcherTest() {
    private val sessions = FakeSessionRepository(
        listOf(session("s1", utc(2026, 10, 10, hour = 18, minute = 30), performed("row.barbell", set(80.0, 10)))),
    )

    private fun viewModel() = SessionDetailViewModel(
        sessionId = "s1",
        sessions = sessions,
        routines = FakeRoutineRepository(),
        exercises = FakeExerciseRepository(listOf(exercise("row", Muscle.LATS)), sessions),
        settings = FakeSettingsRepository(),
        clock = testClock(),
    )

    @Test
    fun `edit date and time starts from the saved values and updates start and end`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(SessionDetailIntent.EditTimes)
        val dialog = assertIs<DetailDialog.EditTimes>(vm.uiState.value.dialog)
        assertEquals(PastSessionForm(utc(2026, 10, 10, hour = 0), 18, 30, 60, null), dialog.initial)
        assertEquals(utc(2026, 10, 14, hour = 0), dialog.maxDateUtcMillis)

        vm.onEvent(SessionDetailIntent.SaveTimes(PastSessionForm(utc(2026, 10, 9, hour = 0), 7, 15, 90, null)))

        val saved = sessions.sessions.value.single()
        assertEquals(utc(2026, 10, 9, hour = 7, minute = 15), saved.startedAt)
        assertEquals(utc(2026, 10, 9, hour = 8, minute = 45), saved.endedAt)
        val state = vm.uiState.value
        assertNull(state.dialog)
        assertEquals(utc(2026, 10, 9, hour = 7, minute = 15), state.summary!!.startedAt, "the detail follows")
        assertEquals(90 * 60_000L, state.summary!!.durationMillis)
    }

    @Test
    fun `delete asks for a confirmation, then deletes and leaves`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(SessionDetailIntent.RequestDelete)
        assertEquals(DetailDialog.ConfirmDelete, vm.uiState.value.dialog)
        vm.onEvent(SessionDetailIntent.DismissDialog)
        assertEquals(1, sessions.sessions.value.size, "dismissing keeps it")

        vm.onEvent(SessionDetailIntent.RequestDelete)
        vm.onEvent(SessionDetailIntent.ConfirmDelete)

        assertTrue(sessions.sessions.value.isEmpty())
        assertTrue(vm.uiState.value.deleted)
    }

    @Test
    fun `a session deleted elsewhere (from the editor) makes the detail leave`() = runTest(dispatcher) {
        val vm = viewModel()
        assertFalse(vm.uiState.value.deleted)
        sessions.sessions.value = emptyList()
        assertTrue(vm.uiState.value.deleted)
    }

    @Test
    fun `a failed change is reported and the session is kept`() = runTest(dispatcher) {
        val vm = viewModel()
        sessions.failWrites = true
        vm.onEvent(SessionDetailIntent.RequestDelete)
        vm.onEvent(SessionDetailIntent.ConfirmDelete)
        assertTrue(vm.uiState.value.failed)
        assertFalse(vm.uiState.value.deleted)
        assertEquals(1, sessions.sessions.value.size)
    }
}
