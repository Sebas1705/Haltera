package es.sebas1705.haltera.feature.session

import es.sebas1705.haltera.core.testing.CoroutineTestRule
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadIncrement
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.naming.VariantLabel
import es.sebas1705.haltera.domain.usecases.progression.IncreaseReason
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import es.sebas1705.haltera.domain.usecases.session.SuggestNextLoad
import es.sebas1705.haltera.domain.usecases.stats.RecordKind
import es.sebas1705.haltera.feature.session.timer.RestTimerController
import es.sebas1705.haltera.feature.session.timer.TimerMath
import es.sebas1705.haltera.feature.session.timer.TimerPhase
import es.sebas1705.haltera.feature.session.timer.TimerSnapshot
import es.sebas1705.haltera.feature.session.ui.SessionCatalog
import es.sebas1705.haltera.feature.session.ui.SessionDialog
import es.sebas1705.haltera.feature.session.ui.SessionIntent
import es.sebas1705.haltera.feature.session.ui.SessionViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionViewModelTest {
    private val rule = CoroutineTestRule()
    private val clock = TestClock()
    private val sessions = FakeSessionRepository(clock)
    private val settings = FakeSettingsRepository()
    private val store = FakeTimerStore()
    private val alert = FakeRestAlert()
    private val indicator = FakeIndicator()
    private val timer = RestTimerController(store, alert, settings) { clock.now }
    private val loadSteps = FakeLoadStepStore()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(session: WorkoutSession = routineSession()): SessionViewModel {
        sessions.put(session)
        val catalog = SessionCatalog(
            exercises = FakeExerciseRepository(listOf(BENCH, PLANK, ROW, PULLDOWN)),
            routines = FakeRoutineRepository(listOf(ROUTINE)),
            sessions = sessions,
            suggestNextLoad = SuggestNextLoad(sessions),
        )
        return SessionViewModel(SESSION_ID, sessions, catalog, settings, timer, indicator, loadSteps) { clock.now }
            .also { it.onEvent(SessionIntent.SetLanguage("en")) }
    }

    private fun routineSession(vararg exercises: SessionExercise = arrayOf(benchExercise())) =
        WorkoutSession(
            SESSION_ID,
            startedAt = clock.now,
            routineId = ROUTINE.id,
            routineDayIndex = 0,
            dayName = "Push",
            exercises = exercises.toList()
        )

    private fun benchExercise(variant: String = BENCH_BARBELL) = SessionExercise(
        "e-bench",
        SESSION_ID,
        0,
        variant,
        BENCH_SLOT.id
    )

    private fun pastBench(load: Long, reps: List<Int>, sessionId: String = "old", at: Long = 1L) =
        pastSets(reps.map { load to it }, sessionId = sessionId, at = at)

    /** A finished bench performance with one completed working set per (load, reps) pair. */
    private fun pastSets(
        sets: List<Pair<Long, Int>>,
        sessionId: String = "old",
        at: Long = 1L,
        slotId: String? = BENCH_SLOT.id,
        warmups: List<Long> = emptyList(),
    ) = ExerciseHistoryEntry(
        sessionId,
        at,
        SessionExercise(
            "$sessionId-e",
            sessionId,
            0,
            BENCH_BARBELL,
            slotId,
            sets = warmups.mapIndexed { i, load ->
                SetLog("$sessionId-w$i", "$sessionId-e", i, SetKind.WARMUP, 5, load, completed = true)
            } + sets.mapIndexed { i, (load, r) ->
                SetLog("$sessionId-$i", "$sessionId-e", warmups.size + i, SetKind.WORKING, r, load, completed = true)
            },
        ),
    )

    private fun working(vm: SessionViewModel, cardId: String = "e-bench") =
        cardSets(vm, cardId).filter { it.kind == SetKind.WORKING }

    /** Edits a set through the sheet like the user does. */
    private fun edit(vm: SessionViewModel, setId: String, change: (SetInput) -> SetInput) {
        vm.onEvent(SessionIntent.EditSet(setId))
        vm.onEvent(SessionIntent.ChangeDraft(change(vm.uiState.value.editor!!.draft)))
        vm.onEvent(SessionIntent.SaveDraft)
    }

    private fun cardSets(vm: SessionViewModel, cardId: String = "e-bench") = vm.uiState.value.cards.first {
        it.id == cardId
    }.sets

    // region Prefill

    @Test
    fun firstTimeSlotCreatesPrescribedRowsNotCompleted() = runTest {
        val vm = viewModel()
        val sets = cardSets(vm)
        assertEquals(1 + 3, sets.size, "one warm-up and three working rows")
        assertEquals(SetKind.WARMUP, sets.first().kind)
        assertTrue(sets.none { it.completed })
        assertTrue(sets.drop(1).all { it.reps == 8 && it.loadGrams == null })
        assertEquals(Suggestion.FirstTime, vm.uiState.value.cards.first().suggestion)
        assertEquals(SESSION_ID, indicator.shown)
    }

    @Test
    fun prefillKeepsTheLastLoadWithThePrescribedReps() = runTest {
        sessions.history += pastBench(60_000, listOf(10, 9, 8))
        val vm = viewModel()
        val working = working(vm)
        assertEquals(listOf(8, 8, 8), working.map { it.reps }, "reps start at the prescription")
        assertTrue(working.all { it.loadGrams == 60_000L })
        assertIs<Suggestion.Keep>(vm.uiState.value.cards.first().suggestion)
    }

    @Test
    fun firstWorkingSetStartsAtTheHeaviestSetOfTheLastSessionOfTheSlot() = runTest {
        sessions.history += pastSets(listOf(100_000L to 5), sessionId = "older", at = 1L)
        sessions.history += pastSets(listOf(60_000L to 10, 70_000L to 8, 65_000L to 9), sessionId = "last", at = 2L)
        val vm = viewModel()
        val working = working(vm)
        assertEquals(
            List(3) { 70_000L },
            working.map { it.loadGrams },
            "the latest session's max, not the all-time max"
        )
        assertEquals(List(3) { 8 }, working.map { it.reps })
        assertTrue(working.none { it.completed })
    }

    @Test
    fun withoutSlotHistoryTheVariantsLatestSessionInAnySlotIsUsed() = runTest {
        sessions.history += pastSets(listOf(80_000L to 6, 85_000L to 4), slotId = null)
        val vm = viewModel()
        assertEquals(Suggestion.FirstTime, vm.uiState.value.cards.first().suggestion)
        val working = working(vm)
        assertTrue(working.all { it.loadGrams == 85_000L && it.reps == 8 })
    }

    @Test
    fun warmUpsWithoutHistoryStartAtSixtyPercentOfTheWorkingLoad() = runTest {
        sessions.history += pastSets(listOf(80_000L to 10))
        val vm = viewModel()
        val warmup = cardSets(vm).first()
        assertEquals(SetKind.WARMUP, warmup.kind)
        assertEquals(47_500, warmup.loadGrams, "60 % of 80 kg rounded down to the 2.5 kg step")
        assertTrue(working(vm).all { it.loadGrams == 80_000L })
    }

    @Test
    fun aFlexibleSlotPrefillsFromTheVariantsHistoryInAFreeSession() = runTest {
        sessions.history += ExerciseHistoryEntry(
            "free",
            1L,
            SessionExercise(
                "free-row",
                "free",
                0,
                ROW_DUMBBELL,
                slotId = null,
                sets = listOf(
                    SetLog("f1", "free-row", 0, reps = 10, loadGrams = 26_000, completed = true),
                    SetLog("f2", "free-row", 1, reps = 8, loadGrams = 28_000, completed = true),
                ),
            ),
        )
        val vm = viewModel(routineSession(SessionExercise("e-row", SESSION_ID, 0, ROW_DUMBBELL, ROW_SLOT.id)))
        val card = vm.uiState.value.cards.first()
        assertEquals(2, card.sets.size)
        assertTrue(card.sets.all { it.loadGrams == 28_000L && it.reps == 8 }, "heaviest working set, prescribed reps")
    }

    @Test
    fun warmUpsDoNotInheritFromWorkingSets() = runTest {
        sessions.history += pastSets(listOf(60_000L to 10), warmups = listOf(40_000))
        val vm = viewModel()
        val warmup = cardSets(vm).first()
        assertEquals(SetKind.WARMUP, warmup.kind)
        assertEquals(40_000, warmup.loadGrams)

        edit(vm, working(vm).first().id) { it.copy(loadGrams = 80_000) }
        assertEquals(40_000, sessions.set(warmup.id)!!.loadGrams, "a working-set change never reaches warm-ups")
    }

    @Test
    fun changingASetUpdatesThePendingRowsBelowThatStillFollowIt() = runTest {
        sessions.history += pastBench(60_000, listOf(10, 10, 10))
        val vm = viewModel()
        val (first, second, third) = working(vm)

        edit(vm, first.id) { it.copy(loadGrams = 70_000, reps = 6) }
        vm.onEvent(SessionIntent.ToggleSet(first.id))

        assertTrue(sessions.set(first.id)!!.completed)
        assertEquals(listOf(70_000L, 70_000L), listOf(second, third).map { sessions.set(it.id)!!.loadGrams })
        assertEquals(listOf(6, 6), listOf(second, third).map { sessions.set(it.id)!!.reps })
        assertEquals(setOf(first.id), vm.uiState.value.editedSetIds, "rows updated as defaults are not edited")
    }

    @Test
    fun rowsTheUserEditedOrCompletedAreNeverOverwritten() = runTest {
        sessions.history += pastBench(60_000, listOf(10, 10, 10))
        val vm = viewModel()
        vm.onEvent(SessionIntent.AddSet("e-bench"))
        val rows = working(vm)
        val (first, second, third) = rows
        val fourth = rows[3]

        edit(vm, third.id) { it.copy(loadGrams = 62_500) }
        vm.onEvent(SessionIntent.ToggleSet(second.id))
        edit(vm, first.id) { it.copy(loadGrams = 75_000) }

        assertEquals(60_000, sessions.set(second.id)!!.loadGrams, "completed")
        assertEquals(62_500, sessions.set(third.id)!!.loadGrams, "edited by the user")
        assertEquals(62_500, sessions.set(fourth.id)!!.loadGrams, "followed the edited row above it")
        assertTrue(third.id in vm.uiState.value.editedSetIds)
    }

    @Test
    fun aNewSetCopiesThePreviousSet() = runTest {
        val vm = viewModel()
        edit(vm, working(vm).last().id) { it.copy(loadGrams = 50_000, reps = 9) }
        vm.onEvent(SessionIntent.AddSet("e-bench"))
        val added = working(vm).last()
        assertEquals(50_000, added.loadGrams)
        assertEquals(9, added.reps)
    }

    @Test
    fun anAddedExerciseStartsAtItsHeaviestRecentSet() = runTest {
        sessions.history += pastSets(listOf(60_000L to 10, 72_500L to 6), slotId = null)
        val vm = viewModel(routineSession(SessionExercise("e-plank", SESSION_ID, 0, PLANK_VARIANT, PLANK_SLOT.id)))
        vm.onEvent(SessionIntent.AddExercise(BENCH_BARBELL))
        val added = vm.uiState.value.cards.last()
        assertEquals(List(3) { 72_500L }, added.sets.map { it.loadGrams })
        assertEquals(List(3) { 6 }, added.sets.map { it.reps }, "no prescription: the reps of that set")
    }

    @Test
    fun suggestedIncreasePrefillsTheNewLoadAndShowsTheMarker() = runTest {
        sessions.history += pastBench(60_000, listOf(12, 12, 12))
        val vm = viewModel()
        val card = vm.uiState.value.cards.first()
        val suggestion = assertIs<Suggestion.Increase>(card.suggestion)
        assertEquals(62_500, suggestion.toGrams)
        assertEquals(IncreaseReason.TOP_OF_RANGE, suggestion.reason)
        val working = card.sets.filter { it.kind == SetKind.WORKING }
        assertTrue(working.all { it.loadGrams == 62_500L && it.reps == 8 && !it.completed })
    }

    @Test
    fun rowsAreNotCreatedTwiceAfterRecreation() = runTest {
        viewModel()
        val again = viewModel(sessions.sessions.value.getValue(SESSION_ID))
        assertEquals(4, cardSets(again).size)
    }

    // endregion

    // region Logging and the rest timer

    @Test
    fun completingASetLogsItAndStartsTheSlotRestAtAnAbsoluteInstant() = runTest {
        val vm = viewModel()
        val set = cardSets(vm)[1]
        vm.onEvent(SessionIntent.ToggleSet(set.id))

        assertTrue(sessions.set(set.id)!!.completed)
        val expectedEnd = clock.now + BENCH_SLOT.restBetweenSetsS * 1_000L
        assertEquals(expectedEnd, vm.uiState.value.timer.restEndsAt)
        assertEquals(expectedEnd, store.state.value.restEndsAt, "persisted for process death")
        assertEquals(listOf(expectedEnd), alert.scheduled)
        assertEquals("Bench press", store.state.value.label)
    }

    @Test
    fun theLastSetOfAnExerciseRestsTheSlotRestAfter() = runTest {
        val vm = viewModel()
        cardSets(vm).forEach { vm.onEvent(SessionIntent.ToggleSet(it.id)) }
        assertEquals(clock.now + BENCH_SLOT.restAfterS * 1_000L, vm.uiState.value.timer.restEndsAt)
    }

    @Test
    fun uncheckingASetReopensItWithoutTouchingTheTimer() = runTest {
        val vm = viewModel()
        val set = cardSets(vm)[1]
        vm.onEvent(SessionIntent.ToggleSet(set.id))
        val end = vm.uiState.value.timer.restEndsAt
        vm.onEvent(SessionIntent.ToggleSet(set.id))
        assertFalse(sessions.set(set.id)!!.completed)
        assertEquals(end, vm.uiState.value.timer.restEndsAt)
    }

    @Test
    fun restIsRenderedFromNowAndAdjustsWithAbsoluteTime() = runTest {
        val vm = viewModel()
        vm.onEvent(SessionIntent.ToggleSet(cardSets(vm)[1].id))
        val start = clock.now
        clock.advance(30_000)
        val timerState = vm.uiState.value.timer
        assertEquals(TimerPhase.REST, TimerMath.phase(timerState, clock.now))
        assertEquals(90, TimerMath.remainingSeconds(timerState, clock.now))

        vm.onEvent(SessionIntent.AdjustRest(15))
        assertEquals(start + 135_000, vm.uiState.value.timer.restEndsAt)
        vm.onEvent(SessionIntent.AdjustRest(-15))
        vm.onEvent(SessionIntent.AdjustRest(-15))
        assertEquals(start + 105_000, vm.uiState.value.timer.restEndsAt)

        // Process death: a new controller over the same store renders the same remaining time.
        clock.advance(60_000)
        val restored = RestTimerController(store, FakeRestAlert(), settings) { clock.now }
        assertEquals(15, TimerMath.remainingSeconds(restored.state.value, clock.now))

        vm.onEvent(SessionIntent.SkipRest)
        assertEquals(TimerPhase.IDLE, TimerMath.phase(vm.uiState.value.timer, clock.now))
        assertTrue(alert.scheduled.isEmpty())
    }

    @Test
    fun reducingTheRestNeverEndsItInThePast() = runTest {
        val vm = viewModel()
        vm.onEvent(SessionIntent.ToggleSet(cardSets(vm)[1].id))
        clock.advance(110_000)
        vm.onEvent(SessionIntent.AdjustRest(-15))
        assertEquals(TimerPhase.IDLE, TimerMath.phase(vm.uiState.value.timer, clock.now))
    }

    @Test
    fun timedSetRunsCountdownWorkAndRest() = runTest {
        val vm = viewModel(routineSession(SessionExercise("e-plank", SESSION_ID, 0, PLANK_VARIANT, PLANK_SLOT.id)))
        val set = cardSets(vm, "e-plank").first()
        assertEquals(45, set.durationS)
        val start = clock.now
        vm.onEvent(SessionIntent.StartTimedSet(set.id))

        val t = vm.uiState.value.timer
        assertEquals(start + 3_000, t.countdownEndsAt)
        assertEquals(start + 48_000, t.workEndsAt)
        assertEquals(start + 48_000 + PLANK_SLOT.restBetweenSetsS * 1_000L, t.restEndsAt)
        assertEquals(TimerPhase.COUNTDOWN, TimerMath.phase(t, start + 1_000))
        assertEquals(TimerPhase.WORK, TimerMath.phase(t, start + 10_000))
        assertEquals(TimerPhase.REST, TimerMath.phase(t, start + 50_000))
        assertEquals(listOf(start + 48_000, t.restEndsAt), alert.scheduled)
        assertTrue(sessions.set(set.id)!!.completed)

        // Stopping after 20 s of work stores 20 s and starts the rest now.
        clock.advance(23_000)
        vm.onEvent(SessionIntent.StopTimedSet)
        assertEquals(20, sessions.set(set.id)!!.durationS)
        assertEquals(clock.now + PLANK_SLOT.restBetweenSetsS * 1_000L, vm.uiState.value.timer.restEndsAt)
    }

    @Test
    fun timerOfAnotherSessionIsIgnored() = runTest {
        store.save(TimerSnapshot(sessionId = "other", restEndsAt = clock.now + 60_000))
        val vm = viewModel()
        assertNull(vm.uiState.value.timer.restEndsAt)
    }

    // endregion

    // region Editing

    @Test
    fun editingASetSavesTheDraft() = runTest {
        val vm = viewModel()
        val set = cardSets(vm)[1]
        vm.onEvent(SessionIntent.EditSet(set.id))
        val draft = assertNotNull(vm.uiState.value.editor).draft
        vm.onEvent(SessionIntent.ChangeDraft(draft.copy(loadGrams = 70_000, reps = 6)))
        vm.onEvent(SessionIntent.SaveDraft)
        assertNull(vm.uiState.value.editor)
        assertEquals(70_000, sessions.set(set.id)!!.loadGrams)
        assertEquals(6, sessions.set(set.id)!!.reps)
    }

    @Test
    fun addAndDeleteSets() = runTest {
        val vm = viewModel()
        vm.onEvent(SessionIntent.AddSet("e-bench"))
        assertEquals(5, cardSets(vm).size)
        vm.onEvent(SessionIntent.EditSet(cardSets(vm).last().id))
        vm.onEvent(SessionIntent.DeleteEditedSet)
        assertEquals(4, cardSets(vm).size)
    }

    @Test
    fun flexibleSlotRebuildsRowsForTheChosenVariant() = runTest {
        sessions.history += ExerciseHistoryEntry(
            "old",
            1L,
            SessionExercise(
                "old-row",
                "old",
                0,
                ROW_DUMBBELL,
                ROW_SLOT.id,
                sets = listOf(SetLog("o", "old-row", 0, reps = 10, loadGrams = 24_000, completed = true)),
            ),
        )
        val vm = viewModel(routineSession(SessionExercise("e-row", SESSION_ID, 0, ROW_BARBELL, ROW_SLOT.id)))
        val card = vm.uiState.value.cards.first()
        assertEquals(listOf(ROW_BARBELL, ROW_DUMBBELL), card.variantOptions.map { it.variantId })
        assertEquals(24_000, card.variantOptions.last().lastLoadGrams)

        vm.onEvent(SessionIntent.ChooseVariant("e-row", ROW_DUMBBELL))
        val after = vm.uiState.value.cards.first()
        assertEquals(ROW_DUMBBELL, after.variantId)
        assertEquals(2, after.sets.size)
        assertTrue(after.sets.all { it.loadGrams == 24_000L && it.reps == 8 })
    }

    @Test
    fun addRemoveAndReorderExercises() = runTest {
        val vm = viewModel()
        vm.onEvent(SessionIntent.OpenAddExercise)
        assertEquals(SessionDialog.AddExercise, vm.uiState.value.dialog)
        vm.onEvent(SessionIntent.SearchExercises("plank"))
        assertEquals(listOf("plank"), vm.uiState.value.picker.results.map { it.id })
        vm.onEvent(SessionIntent.AddExercise(PLANK_VARIANT))
        assertNull(vm.uiState.value.dialog)
        val added = vm.uiState.value.cards.last()
        assertEquals(PLANK_VARIANT, added.variantId)
        assertNull(added.slotId)
        assertEquals(3, added.sets.size)

        vm.onEvent(SessionIntent.MoveExercise(added.id, -1))
        assertEquals(added.id, vm.uiState.value.cards.first().id)
        vm.onEvent(SessionIntent.RemoveExercise(added.id))
        assertEquals(listOf("e-bench"), vm.uiState.value.cards.map { it.id })
    }

    @Test
    fun theTitleIsTheExerciseAndTheVariantChipsUseTheVariantLabels() = runTest {
        val vm = viewModel(
            routineSession(SessionExercise("e-pull", SESSION_ID, 0, PULLDOWN_WIDE, PULLDOWN_SLOT.id))
        )
        val card = vm.uiState.value.cards.single()
        assertEquals("Lat Pulldown", card.name.plain("en"))
        assertEquals(VariantLabel.Named(LocalizedText("Wide grip", "Agarre ancho")), card.name.label)
        assertEquals(
            listOf("Wide grip", "Close grip", "CABLE"),
            card.variantOptions.map { o -> o.name.label?.text("en") { it.name } },
        )
        assertTrue(card.variantOptions.all { it.name.plain("en") == "Lat Pulldown" })
        assertEquals("Lat Pulldown", store.state.value.label, "the notification shows the exercise")
    }

    @Test
    fun theLoadStepIsRememberedPerVariant() = runTest {
        val vm = viewModel()
        val set = working(vm).first()
        vm.onEvent(SessionIntent.EditSet(set.id))
        assertNull(vm.uiState.value.editor!!.weightStep, "default step until the user picks one")

        vm.onEvent(SessionIntent.SetLoadStep(1_250, plates = false))
        assertEquals(1_250L, vm.uiState.value.editor!!.weightStep)
        assertEquals(1_250L, loadSteps.step(BENCH_BARBELL))
        vm.onEvent(SessionIntent.CloseEditor)

        vm.onEvent(SessionIntent.EditSet(working(vm).last().id))
        assertEquals(1_250L, vm.uiState.value.editor!!.weightStep, "same variant, same step")
        vm.onEvent(SessionIntent.SetLoadStep(null, plates = false))
        assertNull(loadSteps.step(BENCH_BARBELL))
        assertNull(vm.uiState.value.editor!!.weightStep)
    }

    // endregion

    // region Finish

    @Test
    fun finishDropsPendingRowsAndShowsTheSummary() = runTest {
        sessions.history += pastBench(60_000, listOf(12, 12, 12))
        val vm = viewModel()
        val working = cardSets(vm).filter { it.kind == SetKind.WORKING }
        // Three working sets at the suggested 62.5 kg, all at the top of the range.
        working.forEach { set ->
            vm.onEvent(SessionIntent.EditSet(set.id))
            vm.onEvent(SessionIntent.ChangeDraft(vm.uiState.value.editor!!.draft.copy(reps = 12)))
            vm.onEvent(SessionIntent.SaveDraft)
            vm.onEvent(SessionIntent.ToggleSet(set.id))
        }
        clock.advance(45 * 60_000L)

        vm.onEvent(SessionIntent.RequestFinish)
        assertEquals(SessionDialog.ConfirmFinish, vm.uiState.value.dialog)
        vm.onEvent(SessionIntent.ConfirmFinish)

        assertEquals(1, sessions.finished)
        val session = sessions.sessions.value.getValue(SESSION_ID)
        assertTrue(session.exercises.single().sets.all { it.completed }, "the unused warm-up row is dropped")
        assertEquals(TimerSnapshot(), store.state.value)
        assertEquals(1, indicator.hidden)

        val summary = assertNotNull(vm.uiState.value.summary)
        assertEquals(45 * 60_000L, summary.durationMs)
        assertEquals(3 * 62_500L * 12, summary.volumeGrams)
        assertEquals(3, summary.completedSets)
        assertTrue(RecordKind.HEAVIEST_LOAD in summary.records.single().kinds)
        val increase = summary.increases.single()
        assertEquals(62_500, increase.fromGrams)
        assertEquals(65_000, increase.toGrams)
        assertTrue(vm.uiState.value.finishedHere)
    }

    @Test
    fun discardDeletesTheSession() = runTest {
        val vm = viewModel()
        vm.onEvent(SessionIntent.RequestDiscard)
        vm.onEvent(SessionIntent.ConfirmDiscard)
        assertTrue(vm.uiState.value.discarded)
        assertTrue(SESSION_ID !in sessions.sessions.value)
        assertEquals(1, indicator.hidden)
    }

    // endregion

    // region Log mode (past sessions and saved sessions)

    private val day = 24 * 60 * 60_000L

    /** A past session as Today/Progress create it: already saved with its start and end, no sets yet. */
    private fun pastSession(startedAt: Long = clock.now - 3 * day, endedAt: Long? = startedAt + 60 * 60_000L) =
        routineSession(benchExercise()).copy(startedAt = startedAt, endedAt = endedAt)

    @Test
    fun aSavedSessionOpensInLogModeWithoutTimerNotificationOrService() = runTest {
        // Another session owns the rest timer: log mode must not touch it.
        val live = TimerSnapshot(sessionId = "live", restEndsAt = clock.now + 60_000)
        store.save(live)
        val vm = viewModel(pastSession())

        val state = vm.uiState.value
        assertTrue(state.logMode)
        assertFalse(state.live)
        assertTrue(state.editable)
        assertNull(indicator.shown, "no foreground service")
        val set = cardSets(vm)[1]
        vm.onEvent(SessionIntent.ToggleSet(set.id))
        assertTrue(sessions.set(set.id)!!.completed)
        assertEquals(TimerSnapshot(), vm.uiState.value.timer)
        assertEquals(live, store.state.value, "no rest started")
        assertTrue(alert.scheduled.isEmpty(), "no notification scheduled")
        assertNull(vm.uiState.value.summary)
    }

    @Test
    fun logModePrefillsOnlyFromHistoryBeforeTheSessionDate() = runTest {
        val start = clock.now - 3 * day
        sessions.history += pastBench(100_000, listOf(5, 5, 5), sessionId = "after", at = start + day)
        sessions.history += pastBench(60_000, listOf(10, 9, 8), sessionId = "before", at = start - day)
        val vm = viewModel(pastSession(startedAt = start))

        val working = cardSets(vm).filter { it.kind == SetKind.WORKING }
        assertEquals(listOf(8, 8, 8), working.map { it.reps })
        assertTrue(working.all { it.loadGrams == 60_000L && !it.completed })
        assertIs<Suggestion.Keep>(vm.uiState.value.cards.first().suggestion)
    }

    @Test
    fun saveKeepsTheSavedEndTimeDropsPendingRowsAndShowsTheSummary() = runTest {
        val start = clock.now - 3 * day
        val vm = viewModel(pastSession(startedAt = start, endedAt = start + 50 * 60_000L))
        val working = cardSets(vm).filter { it.kind == SetKind.WORKING }
        working.take(2).forEach { vm.onEvent(SessionIntent.ToggleSet(it.id)) }

        vm.onEvent(SessionIntent.Save)

        val saved = sessions.sessions.value.getValue(SESSION_ID)
        assertEquals(start + 50 * 60_000L, saved.endedAt)
        assertEquals(2, saved.exercises.single().sets.size)
        assertEquals(0, sessions.finished, "already saved: not finished again")
        val summary = assertNotNull(vm.uiState.value.summary)
        assertEquals(50 * 60_000L, summary.durationMs)
        assertEquals(2, summary.completedSets)
        assertFalse(vm.uiState.value.finishedHere, "Done goes back, not to Today")
        assertFalse(vm.uiState.value.editable)
        assertEquals(0, indicator.hidden)
    }

    @Test
    fun requestFinishInLogModeSavesWithoutAConfirmation() = runTest {
        val vm = viewModel(pastSession())
        vm.onEvent(SessionIntent.RequestFinish)
        assertNull(vm.uiState.value.dialog)
        assertNotNull(vm.uiState.value.summary)
    }

    @Test
    fun aSessionLeftInProgressLongAgoIsLoggedAndSavedWithTheDefaultDuration() = runTest {
        val start = clock.now - SessionViewModel.LOG_MODE_AFTER_MS - 1
        val vm = viewModel(pastSession(startedAt = start, endedAt = null))
        assertTrue(vm.uiState.value.logMode)

        vm.onEvent(SessionIntent.Save)

        assertEquals(
            start + SessionViewModel.DEFAULT_LOG_DURATION_MS,
            sessions.sessions.value.getValue(SESSION_ID).endedAt
        )
        assertNotNull(vm.uiState.value.summary)
    }

    @Test
    fun aRecentSessionInProgressStaysLive() = runTest {
        val vm = viewModel(pastSession(startedAt = clock.now - 60 * 60_000L, endedAt = null))
        assertFalse(vm.uiState.value.logMode)
        assertTrue(vm.uiState.value.live)
        assertEquals(SESSION_ID, indicator.shown)
    }

    @Test
    fun editingASavedSessionChangesItsSetsAndKeepsItSaved() = runTest {
        val start = clock.now - 3 * day
        val done = benchExercise().copy(
            sets = listOf(SetLog("s-a", "e-bench", 0, SetKind.WORKING, 8, 60_000, completed = true)),
        )
        val vm = viewModel(routineSession(done).copy(startedAt = start, endedAt = start + 3_600_000))
        assertEquals(1, cardSets(vm).size, "no rows are added to an exercise that has sets")

        vm.onEvent(SessionIntent.EditSet("s-a"))
        vm.onEvent(SessionIntent.ChangeDraft(vm.uiState.value.editor!!.draft.copy(reps = 10, loadGrams = 65_000)))
        vm.onEvent(SessionIntent.SaveDraft)
        vm.onEvent(SessionIntent.AddSet("e-bench"))
        vm.onEvent(SessionIntent.ToggleSet(cardSets(vm)[1].id))
        vm.onEvent(SessionIntent.Save)

        val saved = sessions.sessions.value.getValue(SESSION_ID)
        assertEquals(start + 3_600_000, saved.endedAt)
        assertEquals(listOf(10, 10), saved.exercises.single().sets.map { it.reps })
        assertEquals(listOf(65_000L, 65_000L), saved.exercises.single().sets.map { it.loadGrams })
        assertTrue(vm.uiState.value.closed, "saving an edited session goes back to its detail")
        assertNull(vm.uiState.value.summary, "no second \"Workout complete\"")
    }

    @Test
    fun finishingRemovesTheExercisesWithoutACompletedSet() = runTest {
        val plank = SessionExercise("e-plank", SESSION_ID, 1, PLANK_VARIANT, null)
        val vm = viewModel(routineSession(benchExercise(), plank))
        vm.onEvent(SessionIntent.ToggleSet(working(vm).first().id))

        vm.onEvent(SessionIntent.RequestFinish)
        vm.onEvent(SessionIntent.ConfirmFinish)

        val session = sessions.sessions.value.getValue(SESSION_ID)
        assertEquals(listOf("e-bench"), session.exercises.map { it.id }, "the untouched plank is gone")
        assertEquals(1, session.exercises.single().sets.size)
    }

    @Test
    fun savingALoggedSessionRemovesTheExercisesWithoutACompletedSet() = runTest {
        val plank = SessionExercise("e-plank", SESSION_ID, 1, PLANK_VARIANT, null)
        val start = clock.now - 3 * day
        val vm = viewModel(routineSession(benchExercise(), plank).copy(startedAt = start, endedAt = start + 3_600_000))
        vm.onEvent(SessionIntent.ToggleSet(working(vm).first().id))

        vm.onEvent(SessionIntent.Save)

        assertEquals(listOf("e-bench"), sessions.sessions.value.getValue(SESSION_ID).exercises.map { it.id })
        assertNotNull(vm.uiState.value.summary, "a session logged after the fact still shows its summary")
    }

    @Test
    fun firstTimeWarmUpsFollowTheFirstWorkingLoad() = runTest {
        val vm = viewModel()
        val warmup = cardSets(vm).first()
        assertNull(warmup.loadGrams, "no history: the warm-up starts without a load")

        edit(vm, working(vm).first().id) { it.copy(loadGrams = 80_000) }
        assertEquals(47_500, sessions.set(warmup.id)!!.loadGrams, "60 % of 80 kg rounded down to the 2.5 kg step")

        edit(vm, working(vm).first().id) { it.copy(loadGrams = 100_000) }
        assertEquals(60_000, sessions.set(warmup.id)!!.loadGrams, "a generated warm-up keeps following set 1")

        edit(vm, warmup.id) { it.copy(loadGrams = 40_000) }
        edit(vm, working(vm).first().id) { it.copy(loadGrams = 120_000) }
        assertEquals(40_000, sessions.set(warmup.id)!!.loadGrams, "a warm-up the user edited is never overwritten")
    }

    @Test
    fun deletingInLogModeAsksFirstAndLeavesTheLiveTimerAlone() = runTest {
        val live = TimerSnapshot(sessionId = "live", restEndsAt = clock.now + 60_000)
        store.save(live)
        val vm = viewModel(pastSession())

        vm.onEvent(SessionIntent.RequestDiscard)
        assertEquals(SessionDialog.ConfirmDelete, vm.uiState.value.dialog)
        vm.onEvent(SessionIntent.ConfirmDiscard)

        assertNull(sessions.sessions.value[SESSION_ID])
        assertTrue(vm.uiState.value.discarded)
        assertEquals(live, store.state.value)
        assertEquals(0, indicator.hidden)
    }

    // endregion

    private companion object {
        const val SESSION_ID = "s1"
        const val BENCH_BARBELL = "bench-press.barbell"
        const val PLANK_VARIANT = "plank.none"
        const val ROW_BARBELL = "row.barbell"
        const val ROW_DUMBBELL = "row.dumbbell"
        const val PULLDOWN_WIDE = "lat-pulldown.wide-grip"

        fun exercise(id: String, en: String, tracking: TrackingType, vararg variants: Pair<Equipment, LoadType>) = Exercise(
            id = id,
            name = LocalizedText(en, en),
            category = ExerciseCategory.STRENGTH,
            level = Level.BEGINNER,
            primaryMuscles = listOf(Muscle.CHEST),
            secondaryMuscles = emptyList(),
            instructions = LocalizedSteps.EMPTY,
            trackingType = tracking,
            source = ExerciseSource.CATALOG,
            variants = variants.mapIndexed { i, (equipment, load) ->
                ExerciseVariant("$id.${equipment.name.lowercase()}", id, equipment, load, position = i)
            },
        )

        val BENCH =
            exercise("bench-press", "Bench press", TrackingType.WEIGHT_REPS, Equipment.BARBELL to LoadType.BARBELL)
        val PLANK = exercise("plank", "Plank", TrackingType.TIME, Equipment.NONE to LoadType.NONE)
        val ROW = exercise(
            "row",
            "Row",
            TrackingType.WEIGHT_REPS,
            Equipment.BARBELL to LoadType.BARBELL,
            Equipment.DUMBBELL to LoadType.DUMBBELL,
        )

        val PULLDOWN = exercise("lat-pulldown", "Lat Pulldown", TrackingType.WEIGHT_REPS).copy(
            variants = listOf(
                ExerciseVariant(
                    PULLDOWN_WIDE,
                    "lat-pulldown",
                    Equipment.CABLE,
                    LoadType.MACHINE,
                    LocalizedText("Wide grip", "Agarre ancho"),
                ),
                ExerciseVariant(
                    "lat-pulldown.close-grip",
                    "lat-pulldown",
                    Equipment.CABLE,
                    LoadType.MACHINE,
                    LocalizedText("Close grip", "Agarre cerrado"),
                    position = 1,
                ),
                ExerciseVariant("lat-pulldown.cable", "lat-pulldown", Equipment.CABLE, LoadType.MACHINE, position = 2),
            ),
        )

        val BENCH_SLOT = RoutineSlot(
            id = "slot-bench",
            dayId = "day-1",
            position = 0,
            target = SlotTarget.Fixed(BENCH_BARBELL),
            sets = 3,
            warmupSets = 1,
            prescription = Prescription.RepRange(8, 12),
            restBetweenSetsS = 120,
            restAfterS = 180,
            progression = ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)),
        )
        val PLANK_SLOT = RoutineSlot(
            id = "slot-plank",
            dayId = "day-1",
            position = 1,
            target = SlotTarget.Fixed(PLANK_VARIANT),
            sets = 2,
            prescription = Prescription.Duration(45),
            restBetweenSetsS = 60,
            restAfterS = 60,
        )
        val ROW_SLOT = RoutineSlot(
            id = "slot-row",
            dayId = "day-1",
            position = 2,
            target = SlotTarget.Flexible("row"),
            sets = 2,
            prescription = Prescription.RepRange(8, 12),
        )
        val PULLDOWN_SLOT = RoutineSlot(
            id = "slot-pull",
            dayId = "day-1",
            position = 3,
            target = SlotTarget.Flexible("lat-pulldown"),
            sets = 3,
            prescription = Prescription.RepRange(10, 12),
        )
        val ROUTINE = Routine(
            id = "r1",
            name = "Push",
            days = listOf(
                RoutineDay("day-1", "r1", 0, "Push", slots = listOf(BENCH_SLOT, PLANK_SLOT, ROW_SLOT, PULLDOWN_SLOT)),
            ),
        )
    }
}
