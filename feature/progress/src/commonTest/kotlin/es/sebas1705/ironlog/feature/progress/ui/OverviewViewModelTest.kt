package es.sebas1705.ironlog.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
import es.sebas1705.ironlog.domain.usecases.stats.Comparison
import es.sebas1705.ironlog.domain.usecases.stats.StatsPeriod
import es.sebas1705.ironlog.feature.progress.MainDispatcherTest
import es.sebas1705.ironlog.feature.progress.fakes.FakeRoutineRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.ironlog.feature.progress.model.StatsPeriodSelection
import es.sebas1705.ironlog.feature.progress.performed
import es.sebas1705.ironlog.feature.progress.session
import es.sebas1705.ironlog.feature.progress.set
import es.sebas1705.ironlog.feature.progress.testClock
import es.sebas1705.ironlog.feature.progress.ui.overview.OverviewIntent
import es.sebas1705.ironlog.feature.progress.ui.overview.OverviewViewModel
import es.sebas1705.ironlog.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverviewViewModelTest : MainDispatcherTest() {
    private val sessions = FakeSessionRepository(
        listOf(
            // Previous 28 days (Aug 20 – Sep 16).
            session("p", utc(2026, 9, 1), performed("squat.barbell", set(100.0, 5)), durationMinutes = 40),
            // This period (Sep 17 – Oct 14).
            session("a", utc(2026, 9, 30), performed("squat.barbell", set(105.0, 5)), durationMinutes = 60),
            session(
                "b",
                utc(2026, 10, 13),
                performed("squat.barbell", set(100.0, 5), set(100.0, 5)),
                durationMinutes = 80
            ),
            session("live", utc(2026, 10, 14), performed("squat.barbell", set(120.0, 5)), durationMinutes = null),
        ),
    )

    private fun routine(active: Boolean) = Routine(
        id = "r",
        name = "Full body",
        isActive = active,
        days = listOf(
            DayKind.TRAINING,
            DayKind.REST,
            DayKind.TRAINING,
            DayKind.REST,
            DayKind.TRAINING,
            DayKind.REST,
            DayKind.REST
        )
            .mapIndexed { i, kind -> RoutineDay("d$i", "r", i, "Day $i", kind) },
    )

    private fun viewModel(routines: List<Routine> = emptyList(), selection: StatsPeriodSelection = StatsPeriodSelection()) =
        OverviewViewModel(sessions, FakeRoutineRepository(routines), FakeSettingsRepository(), testClock(), selection)

    @Test
    fun `compares the last 28 days with the 28 before`() = runTest(dispatcher) {
        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertTrue(state.hasHistory)
            val stats = assertNotNull(state.stats)
            assertEquals(Comparison(2.0, 1.0), stats.sessions)
            assertEquals(Comparison(1_525_000.0, 500_000.0), stats.volume)
            assertEquals(Comparison(3.0, 1.0), stats.effectiveSets)
            assertEquals(Comparison(140 * 60_000.0, 40 * 60_000.0), stats.trainingTimeMillis)
            assertEquals(Comparison(70 * 60_000.0, 40 * 60_000.0), stats.averageDurationMillis)
            // "a" beat "p" on load, e1RM and volume; "b" beat "a" on volume only.
            assertEquals(Comparison(4.0, 0.0), stats.newPrs)
            assertEquals(4, stats.buckets.size)
            assertEquals(4, stats.previousBuckets.size)
            assertEquals(13, state.heatmap?.weeks?.size)
            assertEquals(3, state.heatmap?.trainingDays)
            assertNull(stats.adherence)
            assertFalse(state.hasActiveRoutine)
        }
    }

    @Test
    fun `the period is shared and all time has no comparison`() = runTest(dispatcher) {
        val selection = StatsPeriodSelection()
        val vm = viewModel(selection = selection)
        vm.uiState.test {
            awaitItem()
            vm.onEvent(OverviewIntent.SelectPeriod(StatsPeriod.ALL))
            val state = expectMostRecentItem()
            assertEquals(StatsPeriod.ALL, state.period)
            assertEquals(StatsPeriod.ALL, selection.period.value)
            assertNull(state.stats?.previous)
            assertEquals(3.0, state.stats?.sessions?.current)
            assertNull(state.stats?.sessions?.percent)
            assertEquals(53, state.heatmap?.weeks?.size)
        }
    }

    @Test
    fun `adherence uses the active routine`() = runTest(dispatcher) {
        viewModel(listOf(routine(active = true))).uiState.test {
            val state = awaitItem()
            assertTrue(state.hasActiveRoutine)
            val adherence = assertNotNull(state.stats?.adherence)
            assertEquals(3.0, adherence.plannedPerWeek)
            assertEquals(0.5, adherence.loggedPerWeek)
        }
    }

    @Test
    fun `an empty log still shows the structure`() = runTest(dispatcher) {
        sessions.sessions.value = emptyList()
        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertFalse(state.hasHistory)
            assertEquals(0.0, state.stats?.sessions?.current)
            assertEquals(0, state.heatmap?.trainingDays)
        }
    }
}
