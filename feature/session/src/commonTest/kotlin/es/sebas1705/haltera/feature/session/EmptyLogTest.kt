package es.sebas1705.haltera.feature.session

import es.sebas1705.haltera.core.testing.CoroutineTestRule
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.session.SuggestNextLoad
import es.sebas1705.haltera.feature.session.timer.RestTimerController
import es.sebas1705.haltera.feature.session.ui.SessionCatalog
import es.sebas1705.haltera.feature.session.ui.SessionDialog
import es.sebas1705.haltera.feature.session.ui.SessionIntent
import es.sebas1705.haltera.feature.session.ui.SessionViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A session logged after the fact ("Log past session", or a saved session edited) is not kept with nothing in it:
 * Save and back ask to log a set or delete it (QA alpha.5). Live sessions are unchanged.
 */
class EmptyLogTest {
    private val rule = CoroutineTestRule()
    private val clock = TestClock(now = 10 * DAY)
    private val sessions = FakeSessionRepository(clock)
    private val settings = FakeSettingsRepository()
    private val timer = RestTimerController(FakeTimerStore(), FakeRestAlert(), settings) { clock.now }

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(session: WorkoutSession): SessionViewModel {
        sessions.put(session)
        val catalog = SessionCatalog(
            exercises = FakeExerciseRepository(listOf(FLY)),
            routines = FakeRoutineRepository(emptyList()),
            sessions = sessions,
            suggestNextLoad = SuggestNextLoad(sessions),
            timeZone = { TimeZone.UTC },
        )
        return SessionViewModel(SESSION_ID, sessions, catalog, settings, timer, FakeIndicator(), FakeLoadStepStore()) {
            clock.now
        }.also { it.onEvent(SessionIntent.SetLanguage("en")) }
    }

    /** What LogPastSession creates: a finished session three days ago, here with one exercise and its empty rows. */
    private fun past(vararg exercises: SessionExercise): WorkoutSession {
        val start = clock.now - 3 * DAY
        return WorkoutSession(SESSION_ID, startedAt = start, endedAt = start + HOUR, exercises = exercises.toList())
    }

    private fun fly() = SessionExercise("e-fly", SESSION_ID, 0, FLY_DUMBBELL)

    @Test
    fun savingAPastSessionWithNothingCompletedAsksInsteadOfSaving() = runTest {
        val vm = viewModel(past(fly()))
        assertTrue(vm.uiState.value.logMode)

        vm.onEvent(SessionIntent.Save)
        assertEquals(SessionDialog.EmptyLog, vm.uiState.value.dialog)
        assertNull(vm.uiState.value.summary)
        assertFalse(vm.uiState.value.closed)

        // "Keep logging": tick a set, then Save works as before.
        vm.onEvent(SessionIntent.DismissDialog)
        logOneSet(vm)
        vm.onEvent(SessionIntent.Save)
        assertNull(vm.uiState.value.dialog)
        assertNotNull(vm.uiState.value.summary)
    }

    @Test
    fun aPastSessionWithoutExercisesCanOnlyBeDeleted() = runTest {
        val vm = viewModel(past())
        vm.onEvent(SessionIntent.RequestFinish)
        assertEquals(SessionDialog.EmptyLog, vm.uiState.value.dialog)

        vm.onEvent(SessionIntent.ConfirmDiscard)
        assertTrue(vm.uiState.value.discarded)
        assertNull(sessions.getSession(SESSION_ID))
    }

    @Test
    fun backFromAnEmptyPastSessionAsksAndBackFromALoggedOneLeaves() = runTest {
        val vm = viewModel(past(fly()))
        vm.onEvent(SessionIntent.RequestLeave)
        assertEquals(SessionDialog.EmptyLog, vm.uiState.value.dialog)
        assertNotNull(sessions.getSession(SESSION_ID))

        vm.onEvent(SessionIntent.DismissDialog)
        logOneSet(vm)
        vm.onEvent(SessionIntent.RequestLeave)
        assertNull(vm.uiState.value.dialog)
        assertTrue(vm.uiState.value.closed)
    }

    @Test
    fun finishingALiveSessionWithNothingDoneAsksToKeepTrainingOrDiscard() = runTest {
        val vm = viewModel(WorkoutSession(SESSION_ID, startedAt = clock.now, exercises = listOf(fly())))
        assertFalse(vm.uiState.value.logMode)
        vm.onEvent(SessionIntent.RequestFinish)
        assertEquals(SessionDialog.EmptyLog, vm.uiState.value.dialog)
        // "Keep training", log a set: Finish asks as before.
        vm.onEvent(SessionIntent.DismissDialog)
        logOneSet(vm)
        vm.onEvent(SessionIntent.RequestFinish)
        assertEquals(SessionDialog.ConfirmFinish, vm.uiState.value.dialog)
    }

    @Test
    fun discardingAnEmptyLiveSessionDeletesIt() = runTest {
        val vm = viewModel(WorkoutSession(SESSION_ID, startedAt = clock.now))
        vm.onEvent(SessionIntent.RequestFinish)
        assertEquals(SessionDialog.EmptyLog, vm.uiState.value.dialog)
        vm.onEvent(SessionIntent.ConfirmDiscard)
        assertTrue(vm.uiState.value.discarded)
        assertNull(sessions.getSession(SESSION_ID))
    }

    /** Completes one fly set: a row without reps opens the editor on tick, which completes it when saved. */
    private fun logOneSet(vm: SessionViewModel) {
        if (vm.uiState.value.cards.single().sets.isEmpty()) vm.onEvent(SessionIntent.AddSet("e-fly"))
        vm.onEvent(SessionIntent.ToggleSet(vm.uiState.value.cards.single().sets.first().id))
        vm.uiState.value.editor?.let { editor ->
            vm.onEvent(SessionIntent.ChangeDraft(editor.draft.copy(reps = 10, loadGrams = 12_000)))
            vm.onEvent(SessionIntent.SaveDraft)
        }
        assertTrue(vm.uiState.value.cards.single().sets.any { it.completed })
    }

    private companion object {
        const val SESSION_ID = "s1"
        const val HOUR = 60 * 60_000L
        const val DAY = 24 * HOUR
        const val FLY_DUMBBELL = "chest-fly.dumbbell"

        val FLY = Exercise(
            id = "chest-fly",
            name = LocalizedText("Chest Fly", "Aperturas"),
            category = ExerciseCategory.STRENGTH,
            level = Level.BEGINNER,
            primaryMuscles = listOf(Muscle.CHEST),
            secondaryMuscles = emptyList(),
            instructions = LocalizedSteps.EMPTY,
            trackingType = TrackingType.WEIGHT_REPS,
            source = ExerciseSource.CATALOG,
            variants = listOf(ExerciseVariant(FLY_DUMBBELL, "chest-fly", Equipment.DUMBBELL, LoadType.DUMBBELL)),
        )
    }
}
