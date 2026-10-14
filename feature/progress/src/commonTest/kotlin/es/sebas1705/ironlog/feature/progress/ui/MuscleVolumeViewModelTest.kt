package es.sebas1705.ironlog.feature.progress.ui

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.feature.progress.MainDispatcherTest
import es.sebas1705.ironlog.feature.progress.exercise
import es.sebas1705.ironlog.feature.progress.fakes.FakeExerciseRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSessionRepository
import es.sebas1705.ironlog.feature.progress.fakes.FakeSettingsRepository
import es.sebas1705.ironlog.feature.progress.model.MuscleVolume
import es.sebas1705.ironlog.feature.progress.performed
import es.sebas1705.ironlog.feature.progress.session
import es.sebas1705.ironlog.feature.progress.set
import es.sebas1705.ironlog.feature.progress.testClock
import es.sebas1705.ironlog.feature.progress.ui.volume.MuscleVolumeViewModel
import es.sebas1705.ironlog.feature.progress.utc
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MuscleVolumeViewModelTest : MainDispatcherTest() {

    @Test
    fun `weekly volume per primary muscle over the last four weeks`() = runTest(dispatcher) {
        val sessions = FakeSessionRepository(
            listOf(
                session("a", utc(2026, 9, 22), performed("row.barbell", set(80.0, 10))),
                session(
                    "b",
                    utc(2026, 10, 13),
                    performed("row.barbell", set(80.0, 5)),
                    performed("curl.barbell", set(30.0, 10))
                ),
                session("too-old", utc(2026, 9, 20), performed("row.barbell", set(80.0, 10))),
            ),
        )
        val exercises = FakeExerciseRepository(
            listOf(exercise("row", Muscle.MIDDLE_BACK, Muscle.LATS), exercise("curl", Muscle.BICEPS)),
            sessions,
        )
        val vm = MuscleVolumeViewModel(sessions, exercises, FakeSettingsRepository(), testClock())

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertEquals(
                listOf(LocalDate(2026, 9, 21), LocalDate(2026, 9, 28), LocalDate(2026, 10, 5), LocalDate(2026, 10, 12)),
                state.weekStarts,
            )
            assertEquals(
                listOf(
                    MuscleVolume(Muscle.LATS, listOf(800_000, 0, 0, 400_000)),
                    MuscleVolume(Muscle.MIDDLE_BACK, listOf(800_000, 0, 0, 400_000)),
                    MuscleVolume(Muscle.BICEPS, listOf(0, 0, 0, 300_000)),
                ),
                state.muscles,
            )
            assertEquals(800_000L, state.maxWeekVolume)
        }
        assertEquals(utc(2026, 9, 21, hour = 0) to utc(2026, 10, 19, hour = 0), sessions.requestedRanges.single())
    }
}
