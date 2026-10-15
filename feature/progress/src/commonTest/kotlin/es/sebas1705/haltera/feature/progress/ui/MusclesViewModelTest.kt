package es.sebas1705.haltera.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.domain.usecases.routinescore.MuscleGroup
import es.sebas1705.haltera.domain.usecases.stats.BalanceKind
import es.sebas1705.haltera.domain.usecases.stats.Comparison
import es.sebas1705.haltera.domain.usecases.stats.DayWindow
import es.sebas1705.haltera.domain.usecases.stats.StatsPeriod
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
import es.sebas1705.haltera.feature.progress.ui.muscles.MusclesIntent
import es.sebas1705.haltera.feature.progress.ui.muscles.MusclesViewModel
import es.sebas1705.haltera.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusclesViewModelTest : MainDispatcherTest() {
    private val sessions = FakeSessionRepository(
        listOf(
            session("old", utc(2026, 9, 10), performed("bench.barbell", set(80.0, 5), set(80.0, 5))),
            session(
                "new",
                utc(2026, 10, 12),
                performed("bench.barbell", set(80.0, 5), set(80.0, 5), set(80.0, 5), set(80.0, 5)),
                performed("row.barbell", set(60.0, 10), set(60.0, 10)),
            ),
        ),
    )
    private val exercises = FakeExerciseRepository(
        listOf(exercise("bench", Muscle.CHEST, Muscle.TRICEPS), exercise("row", Muscle.MIDDLE_BACK)),
        sessions,
    )

    private fun viewModel(selection: StatsPeriodSelection = StatsPeriodSelection()) =
        MusclesViewModel(sessions, exercises, FakeRoutineRepository(), FakeSettingsRepository(), testClock(), selection)

    @Test
    fun `weekly sets and volume per muscle against the previous period`() = runTest(dispatcher) {
        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            val today = LocalDate(2026, 10, 14).toEpochDays().toInt()
            assertEquals(DayWindow(today - 27, today), state.current)
            assertEquals(
                listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.BACK),
                state.muscles.map { it.group }
            )
            val chest = state.muscles.first()
            assertEquals(Comparison(1.0, 0.5), chest.setsPerWeek) // 4 sets / 4 weeks vs 2 / 4
            assertEquals(Comparison(400_000.0, 200_000.0), chest.volumePerWeek)
            assertEquals(1.0, state.maxSets)
            val pushPull = state.balance.first { it.kind == BalanceKind.PUSH_PULL }
            assertEquals(2.0, pushPull.ratio) // 4 push sets vs 2 pull sets
            assertEquals(0.5, state.previousBalance.first { it.kind == BalanceKind.PUSH_PULL }.first)
        }
    }

    @Test
    fun `selecting a period updates the shared selection`() = runTest(dispatcher) {
        val selection = StatsPeriodSelection()
        val vm = viewModel(selection)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(MusclesIntent.SelectPeriod(StatsPeriod.ALL))
            val state = expectMostRecentItem()
            assertEquals(StatsPeriod.ALL, selection.period.value)
            assertEquals(null, state.previous)
            assertTrue(state.previousBalance.isEmpty())
            assertEquals(null, state.muscles.first().setsPerWeek.previous)
        }
    }
}
