package es.sebas1705.ironlog.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.progress.MainDispatcherTest
import es.sebas1705.ironlog.feature.progress.exercise
import es.sebas1705.ironlog.feature.progress.fakes.FakeExerciseRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import es.sebas1705.ironlog.feature.progress.performed
import es.sebas1705.ironlog.feature.progress.session
import es.sebas1705.ironlog.feature.progress.set
import es.sebas1705.ironlog.feature.progress.ui.exercise.ExerciseProgressIntent
import es.sebas1705.ironlog.feature.progress.ui.exercise.ExerciseProgressViewModel
import es.sebas1705.ironlog.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseProgressViewModelTest : MainDispatcherTest() {
    private val first = utc(2026, 10, 1)
    private val second = utc(2026, 10, 8)
    private val third = utc(2026, 10, 10)
    private val sessions = FakeSessionRepository(
        listOf(
            session("s1", first, performed("bench.barbell", set(100.0, 5))),
            session(
                "s2",
                second,
                performed("bench.barbell", set(105.0, 5), set(95.0, 8)),
                performed("squat.barbell", set(140.0, 3))
            ),
            session("s3", third, performed("bench.dumbbell", set(30.0, 10))),
            session("live", utc(2026, 10, 14), performed("deadlift.barbell", set(180.0, 1)), durationMinutes = null),
        ),
    )
    private val exercises = FakeExerciseRepository(
        listOf(
            exercise("bench", Muscle.CHEST, equipment = listOf(Equipment.BARBELL, Equipment.DUMBBELL)),
            exercise("squat", Muscle.QUADRICEPS),
            exercise("deadlift", Muscle.HAMSTRINGS),
        ),
        sessions,
    )

    private fun viewModel() = ExerciseProgressViewModel(sessions, exercises, FakeSettingsRepository())

    @Test
    fun `lists exercises with finished history, most recent first, and selects the latest variant`() = runTest(
        dispatcher
    ) {
        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertEquals(listOf("bench", "squat"), state.exercises.map { it.exerciseId })
            assertEquals("bench", state.selectedExerciseId)
            assertEquals(listOf("bench.dumbbell", "bench.barbell"), state.variants.map { it.variantId })
            assertEquals("bench.dumbbell", state.selectedVariantId)
            assertEquals(listOf(ChartPoint(third, 30_000.0)), state.charts.topSet)
        }
    }

    @Test
    fun `charts and records follow the selected variant`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(ExerciseProgressIntent.SelectVariant("bench.barbell"))
            val state = expectMostRecentItem()
            assertEquals(listOf(ChartPoint(first, 100_000.0), ChartPoint(second, 105_000.0)), state.charts.topSet)
            // Epley 100 × 5 = 116.667 kg; 105 × 5 = 122.5 kg.
            assertEquals(listOf(ChartPoint(first, 116_667.0), ChartPoint(second, 122_500.0)), state.charts.oneRm)
            assertEquals(listOf(ChartPoint(first, 500_000.0), ChartPoint(second, 1_285_000.0)), state.charts.volume)
            val records = state.records!!
            assertEquals(105_000L, records.heaviestLoad?.value)
            assertEquals("s2", records.heaviestLoad?.sessionId)
            assertEquals(8L, records.mostReps?.value)
            assertEquals(1_285_000L, records.bestSessionVolume?.value)
        }
    }

    @Test
    fun `selecting another exercise resets the variant`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onEvent(ExerciseProgressIntent.SelectVariant("bench.barbell"))
            vm.onEvent(ExerciseProgressIntent.SelectExercise("squat"))
            val state = expectMostRecentItem()
            assertEquals("squat", state.selectedExerciseId)
            assertEquals("squat.barbell", state.selectedVariantId)
            assertEquals(listOf(ChartPoint(second, 140_000.0)), state.charts.topSet)
        }
    }

    @Test
    fun `no finished history shows an empty state`() = runTest(dispatcher) {
        sessions.sessions.value = sessions.sessions.value.filter { it.inProgress }
        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertTrue(state.exercises.isEmpty())
            assertNull(state.selectedExerciseId)
            assertNull(state.records)
        }
    }
}
