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
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import es.sebas1705.haltera.domain.usecases.session.SuggestNextLoad
import es.sebas1705.haltera.feature.session.load.LoadDetail
import es.sebas1705.haltera.feature.session.timer.RestTimerController
import es.sebas1705.haltera.feature.session.ui.SessionCatalog
import es.sebas1705.haltera.feature.session.ui.SessionIntent
import es.sebas1705.haltera.feature.session.ui.SessionViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first set of an exercise in a new session (owner rule of 2026-10-15): the best load of the same
 * exercise + equipment on the most recent day it was done, in any context. Set N then copies set N-1.
 * Includes the owner's report: dumbbell flyes ("Aperturas", `chest-fly.dumbbell`) done for the second time.
 */
class FirstSetDefaultTest {
    private val rule = CoroutineTestRule()
    private val clock = TestClock(now = 30 * DAY + 18 * HOUR)
    private val sessions = FakeSessionRepository(clock)
    private val settings = FakeSettingsRepository()
    private val timer = RestTimerController(FakeTimerStore(), FakeRestAlert(), settings) { clock.now }

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(session: WorkoutSession, zone: TimeZone = TimeZone.UTC): SessionViewModel {
        sessions.put(session)
        val catalog = SessionCatalog(
            exercises = FakeExerciseRepository(listOf(FLY, PULL_UP, ASSISTED_DIP, PLANK, ROW)),
            routines = FakeRoutineRepository(listOf(ROUTINE)),
            sessions = sessions,
            suggestNextLoad = SuggestNextLoad(sessions),
            timeZone = { zone },
        )
        return SessionViewModel(SESSION_ID, sessions, catalog, settings, timer, FakeIndicator(), FakeLoadStepStore()) {
            clock.now
        }.also { it.onEvent(SessionIntent.SetLanguage("es")) }
    }

    private fun freeSession(vararg exercises: SessionExercise) =
        WorkoutSession(SESSION_ID, startedAt = clock.now, exercises = exercises.toList())

    private fun routineSession(vararg exercises: SessionExercise) = WorkoutSession(
        SESSION_ID,
        startedAt = clock.now,
        routineId = ROUTINE.id,
        routineDayIndex = 0,
        dayName = "Push",
        exercises = exercises.toList(),
    )

    private var nextSet = 0

    private fun done(load: Long?, reps: Int? = 10, kind: SetKind = SetKind.WORKING, duration: Int? = null) =
        SetLog("h-${nextSet++}", "", 0, kind, reps, load, durationS = duration, completed = true)

    /** A finished performance of [variant] in session [id] started at [at]. */
    private fun history(id: String, at: Long, variant: String, slotId: String? = null, vararg sets: SetLog) {
        sessions.history += ExerciseHistoryEntry(
            id,
            at,
            SessionExercise(
                "$id-$variant",
                id,
                0,
                variant,
                slotId,
                sets = sets.mapIndexed { i, s -> s.copy(sessionExerciseId = "$id-$variant", position = i) },
            ),
        )
    }

    private fun added(vm: SessionViewModel) = vm.uiState.value.cards.last()

    private fun workingLoads(vm: SessionViewModel, cardId: String? = null): List<Long?> {
        val card = cardId?.let { id -> vm.uiState.value.cards.first { it.id == id } } ?: added(vm)
        return card.sets.filter { it.kind == SetKind.WORKING }.map { it.loadGrams }
    }

    // region The owner's case: dumbbell flyes for the second time

    @Test
    fun dumbbellFliesAddedWithPlusStartAtYesterdaysBest() = runTest {
        history("yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(10_000, 12), done(12_000, 10), done(12_000, 10))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
        assertEquals(List(3) { 10 }, added(vm).sets.map { it.reps }, "no prescription: the reps of that set")
        assertTrue(added(vm).sets.none { it.completed })
    }

    @Test
    fun aHeavierSetMarkedToFailureIsTheBest() = runTest {
        // Regression: only WORKING sets were read, so a top set logged as "Failure" was ignored (12 kg instead of 14).
        history(
            "yesterday",
            clock.now - DAY,
            FLY_DUMBBELL,
            null,
            done(12_000),
            done(12_000),
            done(14_000, 8, SetKind.FAILURE),
        )
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 14_000L }, workingLoads(vm))
        assertEquals(8, added(vm).sets.first().reps)
    }

    @Test
    fun aPerformanceWithOnlyFailureSetsStillCounts() = runTest {
        history("yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(13_000, 9, SetKind.FAILURE))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 13_000L }, workingLoads(vm))
    }

    @Test
    fun theCableVariantIsAnotherHistory() = runTest {
        history("yesterday", clock.now - DAY, FLY_CABLE, null, done(30_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { null }, workingLoads(vm), "dumbbell flyes were never done: no load")

        vm.onEvent(SessionIntent.AddExercise(FLY_CABLE))
        assertEquals(List(3) { 30_000L }, workingLoads(vm))
    }

    @Test
    fun warmUpsAreNotTheBest() = runTest {
        history(
            "yesterday",
            clock.now - DAY,
            FLY_DUMBBELL,
            null,
            done(16_000, 5, SetKind.WARMUP),
            done(12_000),
        )
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
    }

    @Test
    fun aMorePastDayDoesNotCountEvenIfHeavier() = runTest {
        history("last-week", clock.now - 7 * DAY, FLY_DUMBBELL, null, done(20_000))
        history("yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(12_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
    }

    @Test
    fun noHistoryStartsWithoutALoad() = runTest {
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { null }, workingLoads(vm))
    }

    @Test
    fun anExerciseLeftWithoutCompletedSetsIsSkipped() = runTest {
        history("older", clock.now - 3 * DAY, FLY_DUMBBELL, null, done(12_000))
        sessions.history += ExerciseHistoryEntry(
            "empty",
            clock.now - DAY,
            SessionExercise(
                "empty-e",
                "empty",
                0,
                FLY_DUMBBELL,
                null,
                sets = listOf(SetLog("x", "empty-e", 0, SetKind.WORKING, 10, 40_000, completed = false)),
            ),
        )
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
    }

    // endregion

    // region Context: routine slots, free sessions, past sessions, the same day

    @Test
    fun aRoutineSlotUsesAMoreRecentFreeSession() = runTest {
        // Regression: the slot's own (older) history won over a more recent free session of the same variant.
        history("slot-last-week", clock.now - 7 * DAY, FLY_DUMBBELL, FLY_SLOT.id, done(10_000), done(10_000))
        history("free-yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(14_000), done(12_000))
        val vm = viewModel(routineSession(SessionExercise("e-fly", SESSION_ID, 0, FLY_DUMBBELL, FLY_SLOT.id)))
        assertEquals(List(3) { 14_000L }, workingLoads(vm, "e-fly"))
    }

    @Test
    fun aFreeSessionUsesTheRoutineSession() = runTest {
        history("routine-yesterday", clock.now - DAY, FLY_DUMBBELL, FLY_SLOT.id, done(12_000), done(12_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
    }

    @Test
    fun aRoutineSlotReachedForTheFirstTimeUsesTheFreeHistory() = runTest {
        history("free", clock.now - 2 * DAY, FLY_DUMBBELL, null, done(12_000, 11))
        val vm = viewModel(routineSession(SessionExercise("e-fly", SESSION_ID, 0, FLY_DUMBBELL, FLY_SLOT.id)))
        val card = vm.uiState.value.cards.single()
        assertEquals(Suggestion.FirstTime, card.suggestion, "the slot itself has no history")
        assertEquals(List(3) { 12_000L }, workingLoads(vm, "e-fly"))
        assertEquals(List(3) { 10 }, card.sets.map { it.reps }, "the prescribed reps")
    }

    @Test
    fun aSuggestedIncreaseAboveTheLastBestIsUsed() = runTest {
        history(
            "slot",
            clock.now - DAY,
            FLY_DUMBBELL,
            FLY_SLOT.id,
            done(12_000, 12),
            done(12_000, 12),
            done(12_000, 12)
        )
        val vm = viewModel(routineSession(SessionExercise("e-fly", SESSION_ID, 0, FLY_DUMBBELL, FLY_SLOT.id)))
        assertIs<Suggestion.Increase>(vm.uiState.value.cards.single().suggestion)
        assertEquals(List(3) { 14_000L }, workingLoads(vm, "e-fly"))
    }

    @Test
    fun anIncreaseFromOlderSlotHistoryNeverStartsBelowAMoreRecentBest() = runTest {
        history(
            "slot",
            clock.now - 5 * DAY,
            FLY_DUMBBELL,
            FLY_SLOT.id,
            done(10_000, 12),
            done(10_000, 12),
            done(10_000, 12)
        )
        history("free", clock.now - DAY, FLY_DUMBBELL, null, done(16_000, 8))
        val vm = viewModel(routineSession(SessionExercise("e-fly", SESSION_ID, 0, FLY_DUMBBELL, FLY_SLOT.id)))
        val suggestion = assertIs<Suggestion.Increase>(vm.uiState.value.cards.single().suggestion)
        assertEquals(12_000, suggestion.toGrams)
        assertEquals(List(3) { 16_000L }, workingLoads(vm, "e-fly"))
    }

    @Test
    fun anEarlierSessionTheSameDayIsTheLastDay() = runTest {
        history("yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(20_000))
        history("this-morning", clock.now - 9 * HOUR, FLY_DUMBBELL, null, done(12_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
    }

    @Test
    fun twoSessionsOnThePreviousDayBothCount() = runTest {
        history("yesterday-evening", clock.now - 20 * HOUR, FLY_DUMBBELL, null, done(10_000))
        history("yesterday-morning", clock.now - 30 * HOUR, FLY_DUMBBELL, null, done(14_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 14_000L }, workingLoads(vm))
    }

    @Test
    fun theDayBoundaryIsTheUsersLocalMidnight() = runTest {
        // Now is 18:00 UTC. 22:30 UTC two days ago is 00:30 of the next day at UTC+2 (Madrid in summer time).
        history("late", clock.now - 2 * DAY + 4 * HOUR + 30 * 60_000L, FLY_DUMBBELL, null, done(10_000))
        history("earlier", clock.now - 2 * DAY + 2 * HOUR, FLY_DUMBBELL, null, done(16_000))
        val utc = viewModel(freeSession())
        utc.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 16_000L }, workingLoads(utc), "same UTC day: both count")
    }

    @Test
    fun theDayBoundaryInAnotherTimeZoneSplitsTheDays() = runTest {
        history("late", clock.now - 2 * DAY + 4 * HOUR + 30 * 60_000L, FLY_DUMBBELL, null, done(10_000))
        history("earlier", clock.now - 2 * DAY + 2 * HOUR, FLY_DUMBBELL, null, done(16_000))
        val madrid = viewModel(freeSession(), UtcOffset(hours = 2).asTimeZone())
        madrid.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 10_000L }, workingLoads(madrid), "00:30 local is already the next day")
    }

    @Test
    fun aSessionLoggedForAPastDateOnlySeesWhatCameBefore() = runTest {
        history("before", clock.now - 5 * DAY, FLY_DUMBBELL, null, done(10_000))
        history("after", clock.now - DAY, FLY_DUMBBELL, null, done(14_000))
        val past = freeSession().copy(startedAt = clock.now - 3 * DAY, endedAt = clock.now - 3 * DAY + HOUR)
        val vm = viewModel(past)
        assertTrue(vm.uiState.value.logMode)
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 10_000L }, workingLoads(vm))
    }

    @Test
    fun aLiveSessionUsesASessionLoggedAfterwardsForYesterday() = runTest {
        // "Log past session" saves a session with yesterday's start: it is yesterday for today's defaults.
        history("logged-for-yesterday", clock.now - DAY - 2 * HOUR, FLY_DUMBBELL, null, done(12_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        assertEquals(List(3) { 12_000L }, workingLoads(vm))
    }

    @Test
    fun aBarbellRowWithoutHistoryShowsAndSavesTheBarAndNeedsRepsToBeTicked() = runTest {
        // QA alpha.5: the sheet showed "Total 20 kg / bar only" but saved "— × 10", and ticking a row saved "— × 0".
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(ROW_BARBELL))
        val bar = settings.settings.value.barWeightGrams
        val set = added(vm).sets.first { it.kind == SetKind.WORKING }
        assertEquals(bar, set.loadGrams, "the row shows the load its editor shows")
        assertEquals(null, set.reps)

        // No reps to count: ticking opens the editor instead of completing "— × 0"; saving it completes the set.
        vm.onEvent(SessionIntent.ToggleSet(set.id))
        assertFalse(added(vm).sets.first { it.id == set.id }.completed)
        val draft = vm.uiState.value.editor!!.draft
        assertEquals(bar, draft.loadGrams)
        assertTrue(draft.completed)
        vm.onEvent(SessionIntent.ChangeDraft(draft.copy(reps = 8)))
        vm.onEvent(SessionIntent.SaveDraft)

        val saved = added(vm).sets.first { it.id == set.id }
        assertTrue(saved.completed)
        assertEquals(bar, saved.loadGrams)
        assertEquals(8, saved.reps)
        assertEquals(LoadDetail.Barbell(bar), LoadDetail.decode(saved.loadDetail))
    }

    @Test
    fun tickingARowStoredWithoutALoadSavesTheBarItShows() = runTest {
        // A row written before this fix (or by another phone) has reps but no load.
        val row = SetLog("s-old", "e-row", 0, SetKind.WORKING, reps = 8)
        val vm = viewModel(freeSession(SessionExercise("e-row", SESSION_ID, 0, ROW_BARBELL, sets = listOf(row))))
        vm.onEvent(SessionIntent.ToggleSet("s-old"))
        val saved = vm.uiState.value.cards.single().sets.single()
        assertTrue(saved.completed)
        assertEquals(settings.settings.value.barWeightGrams, saved.loadGrams)
        assertEquals(8, saved.reps)
    }

    @Test
    fun anEditorSavedWithoutRepsDoesNotCompleteTheSet() = runTest {
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(ROW_BARBELL))
        val set = added(vm).sets.first { it.kind == SetKind.WORKING }
        vm.onEvent(SessionIntent.EditSet(set.id))
        vm.onEvent(SessionIntent.ChangeDraft(vm.uiState.value.editor!!.draft.copy(reps = 0, completed = true)))
        vm.onEvent(SessionIntent.SaveDraft)
        assertFalse(added(vm).sets.first { it.id == set.id }.completed)
    }

    @Test
    fun anAddedSetCopiesTheRowAboveAndTheFirstRowOfAnEmptyCardUsesTheBest() = runTest {
        history("yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(12_000))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        val card = added(vm)
        card.sets.forEach {
            vm.onEvent(SessionIntent.EditSet(it.id))
            vm.onEvent(SessionIntent.DeleteEditedSet)
        }
        assertTrue(added(vm).sets.isEmpty())
        vm.onEvent(SessionIntent.AddSet(card.id))
        assertEquals(listOf(12_000L), workingLoads(vm))
    }

    // endregion

    // region Flexible slots

    @Test
    fun aFlexibleSlotPrefillsTheChosenVariantFromItsOwnLastDay() = runTest {
        history("barbell", clock.now - DAY, ROW_BARBELL, null, done(60_000))
        history("dumbbell-old", clock.now - 4 * DAY, ROW_DUMBBELL, ROW_SLOT.id, done(30_000))
        history("dumbbell", clock.now - 2 * DAY, ROW_DUMBBELL, null, done(24_000), done(26_000))
        val vm = viewModel(routineSession(SessionExercise("e-row", SESSION_ID, 0, ROW_BARBELL, ROW_SLOT.id)))
        assertEquals(List(2) { 60_000L }, workingLoads(vm, "e-row"))
        assertEquals(
            26_000,
            vm.uiState.value.cards.single().variantOptions.last().lastLoadGrams,
            "the picker shows the best"
        )

        vm.onEvent(SessionIntent.ChooseVariant("e-row", ROW_DUMBBELL))
        assertEquals(
            List(2) { 26_000L },
            workingLoads(vm, "e-row"),
            "the dumbbell's latest day, not the slot's older one"
        )
    }

    // endregion

    // region Load kinds

    @Test
    fun weightedBodyweightStartsAtTheHeaviestAddedLoad() = runTest {
        history("yesterday", clock.now - DAY, PULL_UP_BW, null, done(5_000, 8), done(10_000, 5), done(null, 12))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(PULL_UP_BW))
        assertEquals(List(3) { 10_000L }, workingLoads(vm))
        assertEquals(5, added(vm).sets.first().reps)
    }

    @Test
    fun assistedStartsAtTheLeastAssistance() = runTest {
        history("yesterday", clock.now - DAY, DIP_ASSISTED, null, done(-30_000, 8), done(-20_000, 6))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(DIP_ASSISTED))
        assertEquals(List(3) { -20_000L }, workingLoads(vm))
    }

    @Test
    fun pureBodyweightStartsWithTheLastRepsAndNoLoad() = runTest {
        history("yesterday", clock.now - DAY, PULL_UP_BW, null, done(null, 12), done(null, 9))
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(PULL_UP_BW))
        assertEquals(List(3) { null }, workingLoads(vm))
        assertEquals(List(3) { 9 }, added(vm).sets.map { it.reps })
    }

    @Test
    fun aTimedExerciseKeepsTheLastDurationAndNeverGetsALoad() = runTest {
        history(
            "yesterday",
            clock.now - DAY,
            PLANK_VARIANT,
            null,
            done(null, null, duration = 50),
            done(null, null, duration = 40)
        )
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(PLANK_VARIANT))
        assertTrue(added(vm).sets.all { it.loadGrams == null && it.reps == null })
        assertEquals(List(3) { 40 }, added(vm).sets.map { it.durationS })
    }

    // endregion

    @Test
    fun theFirstRowIsNotChangedAfterTheSessionStarted() = runTest {
        val vm = viewModel(freeSession())
        vm.onEvent(SessionIntent.AddExercise(FLY_DUMBBELL))
        // History written later (e.g. another device) does not rewrite rows already prefilled.
        history("yesterday", clock.now - DAY, FLY_DUMBBELL, null, done(12_000))
        assertNull(workingLoads(vm).first())
    }

    private companion object {
        const val SESSION_ID = "s-today"
        const val HOUR = 60 * 60_000L
        const val DAY = 24 * HOUR
        const val FLY_DUMBBELL = "chest-fly.dumbbell"
        const val FLY_CABLE = "chest-fly.cable"
        const val PULL_UP_BW = "pull-up.bodyweight"
        const val DIP_ASSISTED = "dip.machine"
        const val PLANK_VARIANT = "plank.none"
        const val ROW_BARBELL = "row.barbell"
        const val ROW_DUMBBELL = "row.dumbbell"

        fun exercise(
            id: String,
            name: LocalizedText,
            tracking: TrackingType,
            vararg variants: Pair<Equipment, LoadType>,
        ) = Exercise(
            id = id,
            name = name,
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

        val FLY = exercise(
            "chest-fly",
            LocalizedText("Chest Fly", "Aperturas"),
            TrackingType.WEIGHT_REPS,
            Equipment.DUMBBELL to LoadType.DUMBBELL,
            Equipment.CABLE to LoadType.MACHINE,
        )
        val PULL_UP = exercise(
            "pull-up",
            LocalizedText("Pull-up", "Dominada"),
            TrackingType.WEIGHT_REPS,
            Equipment.BODYWEIGHT to LoadType.BODYWEIGHT,
        )
        val ASSISTED_DIP = exercise(
            "dip",
            LocalizedText("Dip", "Fondos"),
            TrackingType.WEIGHT_REPS,
            Equipment.MACHINE to LoadType.BODYWEIGHT,
        )
        val PLANK =
            exercise("plank", LocalizedText("Plank", "Plancha"), TrackingType.TIME, Equipment.NONE to LoadType.NONE)
        val ROW = exercise(
            "row",
            LocalizedText("Row", "Remo"),
            TrackingType.WEIGHT_REPS,
            Equipment.BARBELL to LoadType.BARBELL,
            Equipment.DUMBBELL to LoadType.DUMBBELL,
        )

        val FLY_SLOT = RoutineSlot(
            id = "slot-fly",
            dayId = "day-1",
            position = 0,
            target = SlotTarget.Fixed(FLY_DUMBBELL),
            sets = 3,
            prescription = Prescription.RepRange(10, 12),
            progression = ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_000)),
        )
        val ROW_SLOT = RoutineSlot(
            id = "slot-row",
            dayId = "day-1",
            position = 1,
            target = SlotTarget.Flexible("row"),
            sets = 2,
            prescription = Prescription.RepRange(8, 12),
        )
        val ROUTINE = Routine(
            id = "r1",
            name = "Push",
            days = listOf(RoutineDay("day-1", "r1", 0, "Push", slots = listOf(FLY_SLOT, ROW_SLOT))),
        )
    }
}
