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
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.session.SuggestNextLoad
import es.sebas1705.haltera.feature.session.timer.RestTimerController
import es.sebas1705.haltera.feature.session.ui.ExerciseLink
import es.sebas1705.haltera.feature.session.ui.SessionCatalog
import es.sebas1705.haltera.feature.session.ui.SessionIntent
import es.sebas1705.haltera.feature.session.ui.SessionViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Statistics" and "Description" on an exercise card: the ViewModel only asks the screen to navigate (the shell
 * opens Progress → exercise progress or Exercises → detail on top of the session). The session, its sets, the
 * rest timer and the notification are untouched, so back returns to the same running session.
 */
class SessionLinksTest {
    private val rule = CoroutineTestRule()
    private val clock = TestClock(now = 10 * DAY)
    private val sessions = FakeSessionRepository(clock)
    private val settings = FakeSettingsRepository()
    private val store = FakeTimerStore()
    private val indicator = FakeIndicator()
    private val timer = RestTimerController(store, FakeRestAlert(), settings) { clock.now }

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(session: WorkoutSession): SessionViewModel {
        sessions.put(session)
        val catalog = SessionCatalog(
            exercises = FakeExerciseRepository(listOf(FLY, ROW)),
            routines = FakeRoutineRepository(listOf(ROUTINE)),
            sessions = sessions,
            suggestNextLoad = SuggestNextLoad(sessions),
            timeZone = { TimeZone.UTC },
        )
        return SessionViewModel(SESSION_ID, sessions, catalog, settings, timer, indicator, FakeLoadStepStore()) {
            clock.now
        }.also { it.onEvent(SessionIntent.SetLanguage("en")) }
    }

    private fun live(vararg exercises: SessionExercise) = WorkoutSession(
        SESSION_ID,
        startedAt = clock.now,
        routineId = ROUTINE.id,
        routineDayIndex = 0,
        dayName = "Push",
        exercises = exercises.toList(),
    )

    private val fly = SessionExercise("e-fly", SESSION_ID, 0, FLY_DUMBBELL, FLY_SLOT.id)
    private val row = SessionExercise("e-row", SESSION_ID, 1, ROW_BARBELL, ROW_SLOT.id)

    private fun sets(vm: SessionViewModel, cardId: String) = vm.uiState.value.cards.first { it.id == cardId }.sets

    @Test
    fun statisticsOpenForTheCardsExerciseAndVariantWithoutTouchingTheSession() = runTest {
        val vm = viewModel(live(fly, row))
        vm.onEvent(SessionIntent.ToggleSet(sets(vm, "e-fly").first().id))
        val timerBefore = store.state.value
        assertNotNull(timerBefore.restEndsAt, "a rest is running")
        val sessionBefore = sessions.sessions.value.getValue(SESSION_ID)

        vm.onEvent(SessionIntent.OpenStatistics("e-fly"))

        assertEquals(ExerciseLink.Statistics("chest-fly", FLY_DUMBBELL), vm.uiState.value.link)
        assertEquals(timerBefore, store.state.value, "the rest timer keeps running")
        assertEquals(SESSION_ID, indicator.shown, "the notification and service stay")
        assertEquals(0, indicator.hidden)
        assertEquals(sessionBefore, sessions.sessions.value.getValue(SESSION_ID), "nothing is written")
        assertEquals(0, sessions.finished)

        vm.onEvent(SessionIntent.LinkOpened)
        assertNull(vm.uiState.value.link, "opened once: coming back does not open it again")
        assertEquals(timerBefore, vm.uiState.value.timer)
        assertTrue(vm.uiState.value.live)
    }

    @Test
    fun descriptionOpensForTheCardsExerciseAndCanOpenAgain() = runTest {
        val vm = viewModel(live(fly, row))
        vm.onEvent(SessionIntent.OpenDescription("e-row"))
        assertEquals(ExerciseLink.Description("row"), vm.uiState.value.link)
        vm.onEvent(SessionIntent.LinkOpened)
        vm.onEvent(SessionIntent.OpenDescription("e-row"))
        assertEquals(ExerciseLink.Description("row"), vm.uiState.value.link)
    }

    @Test
    fun statisticsFollowTheVariantChosenInAFlexibleSlot() = runTest {
        val vm = viewModel(live(row))
        vm.onEvent(SessionIntent.ChooseVariant("e-row", ROW_DUMBBELL))
        vm.onEvent(SessionIntent.OpenStatistics("e-row"))
        assertEquals(ExerciseLink.Statistics("row", ROW_DUMBBELL), vm.uiState.value.link)
    }

    @Test
    fun anExerciseAddedWithPlusLinksToo() = runTest {
        val vm = viewModel(live())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        val card = vm.uiState.value.cards.single()
        assertEquals("chest-fly", card.exerciseId)
        vm.onEvent(SessionIntent.OpenStatistics(card.id))
        assertEquals(ExerciseLink.Statistics("chest-fly", FLY_DUMBBELL), vm.uiState.value.link)
    }

    @Test
    fun anUnknownCardOrVariantOpensNothing() = runTest {
        val gone = SessionExercise("e-gone", SESSION_ID, 1, "deleted.variant", null)
        val vm = viewModel(live(fly, gone))
        assertNull(vm.uiState.value.cards.first { it.id == "e-gone" }.exerciseId)
        vm.onEvent(SessionIntent.OpenStatistics("e-gone"))
        vm.onEvent(SessionIntent.OpenDescription("e-gone"))
        vm.onEvent(SessionIntent.OpenStatistics("no-such-card"))
        assertNull(vm.uiState.value.link)
    }

    @Test
    fun linksWorkInLogModeAndOnTheSummary() = runTest {
        val start = clock.now - 3 * DAY
        val vm = viewModel(live(fly).copy(startedAt = start, endedAt = start + DAY / 24))
        assertTrue(vm.uiState.value.logMode)
        vm.onEvent(SessionIntent.OpenStatistics("e-fly"))
        assertEquals(ExerciseLink.Statistics("chest-fly", FLY_DUMBBELL), vm.uiState.value.link)
        vm.onEvent(SessionIntent.LinkOpened)

        vm.onEvent(SessionIntent.ToggleSet(sets(vm, "e-fly").first().id))
        vm.onEvent(SessionIntent.Save)
        assertNotNull(vm.uiState.value.summary)
        vm.onEvent(SessionIntent.OpenDescription("e-fly"))
        assertEquals(ExerciseLink.Description("chest-fly"), vm.uiState.value.link)
    }

    private companion object {
        const val SESSION_ID = "s1"
        const val DAY = 24 * 60 * 60_000L
        const val FLY_DUMBBELL = "chest-fly.dumbbell"
        const val ROW_BARBELL = "row.barbell"
        const val ROW_DUMBBELL = "row.dumbbell"

        fun exercise(id: String, name: String, vararg variants: Pair<Equipment, LoadType>) = Exercise(
            id = id,
            name = LocalizedText(name, name),
            category = ExerciseCategory.STRENGTH,
            level = Level.BEGINNER,
            primaryMuscles = listOf(Muscle.CHEST),
            secondaryMuscles = emptyList(),
            instructions = LocalizedSteps.EMPTY,
            trackingType = TrackingType.WEIGHT_REPS,
            source = ExerciseSource.CATALOG,
            variants = variants.mapIndexed { i, (equipment, load) ->
                ExerciseVariant("$id.${equipment.name.lowercase()}", id, equipment, load, position = i)
            },
        )

        val FLY = exercise("chest-fly", "Chest Fly", Equipment.DUMBBELL to LoadType.DUMBBELL)
        val ROW = exercise("row", "Row", Equipment.BARBELL to LoadType.BARBELL, Equipment.DUMBBELL to LoadType.DUMBBELL)
        val FLY_SLOT = RoutineSlot(
            id = "slot-fly",
            dayId = "d1",
            position = 0,
            target = SlotTarget.Fixed(FLY_DUMBBELL),
            sets = 3,
            prescription = Prescription.RepRange(10, 12),
        )
        val ROW_SLOT = RoutineSlot(
            id = "slot-row",
            dayId = "d1",
            position = 1,
            target = SlotTarget.Flexible("row"),
            sets = 2,
            prescription = Prescription.RepRange(8, 12),
        )
        val ROUTINE = Routine(
            id = "r1",
            name = "Push",
            days = listOf(RoutineDay("d1", "r1", 0, "Push", slots = listOf(FLY_SLOT, ROW_SLOT))),
        )
    }
}
