package es.sebas1705.ironlog.feature.today.ui

import app.cash.turbine.test
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedSteps
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.domain.usecases.session.ObserveTodayPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val routines = FakeRoutineRepository()
    private val sessions = FakeSessionRepository()
    private val bodyWeight = FakeBodyWeightRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(unit: WeightUnit = WeightUnit.KG) = TodayViewModel(
        observeTodayPlan = ObserveTodayPlan(routines),
        sessions = sessions,
        routines = routines,
        exercises = FakeExerciseRepository(listOf(BENCH, ROW)),
        bodyWeight = bodyWeight,
        settings = FakeSettingsRepository(unit),
        clock = TodayClock(now = { NOW }, timeZone = TimeZone.UTC),
    )

    @Test
    fun `no active routine shows the empty state`() {
        val state = viewModel().uiState.value

        assertTrue(state.planLoaded)
        assertNull(state.plan)
        assertNull(state.inProgress)
    }

    @Test
    fun `free session without a routine opens the new session`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(TodayIntent.StartFree)
            assertEquals(TodayEffect.OpenSession("session-free"), awaitItem())
        }
        assertEquals(listOf<Pair<String, Int>?>(null), sessions.started)
        assertFalse(vm.uiState.value.starting)
    }

    @Test
    fun `next day of the active routine with resolved exercises`() {
        routines.active.value = ROUTINE.copy(nextDayIndex = 1)

        val plan = assertNotNull(viewModel().uiState.value.plan)
        val next = assertNotNull(plan.next)

        assertEquals("Upper/Lower", plan.routineName)
        assertEquals(1, next.dayIndex)
        assertEquals(4, next.dayCount)
        assertEquals("Pull", next.dayName)
        assertFalse(next.planSaysRest)
        assertEquals(listOf("Row"), next.exercises.map { it.name?.en })
        assertEquals(Equipment.BARBELL, next.exercises.single().equipment)
        assertEquals(Prescription.Reps(5), next.exercises.single().prescription)
        assertEquals(listOf(false, false, true, false), plan.days.map { it.isRest })
    }

    @Test
    fun `flexible slot resolves the exercise name without equipment`() {
        routines.active.value = ROUTINE

        val slot = assertNotNull(viewModel().uiState.value.plan?.next).exercises.single()

        assertEquals("Bench press", slot.name?.en)
        assertTrue(slot.flexible)
        assertNull(slot.equipment)
    }

    @Test
    fun `pointer on a rest day shows the rest hint and the following training day`() {
        routines.active.value = ROUTINE.copy(nextDayIndex = 2)

        val next = assertNotNull(viewModel().uiState.value.plan?.next)

        assertTrue(next.planSaysRest)
        assertEquals(3, next.dayIndex)
        assertEquals("Legs", next.dayName)
    }

    @Test
    fun `routine without training days has a plan but no next day`() {
        routines.active.value = ROUTINE.copy(days = listOf(day(0, "Off", DayKind.REST)))

        val plan = assertNotNull(viewModel().uiState.value.plan)

        assertNull(plan.next)
    }

    @Test
    fun `start opens a session for the next day`() = runTest {
        routines.active.value = ROUTINE.copy(nextDayIndex = 1)
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(TodayIntent.StartNextDay)
            assertEquals(TodayEffect.OpenSession("session-r1-1"), awaitItem())
        }
        assertEquals(listOf<Pair<String, Int>?>("r1" to 1), sessions.started)
    }

    @Test
    fun `choosing another day moves the cycle pointer`() {
        routines.active.value = ROUTINE
        val vm = viewModel()

        vm.onEvent(TodayIntent.OpenDayPicker)
        assertTrue(vm.uiState.value.dayPickerOpen)
        vm.onEvent(TodayIntent.ChooseDay(3))

        assertEquals(listOf("r1" to 3), routines.pointerChanges)
        assertFalse(vm.uiState.value.dayPickerOpen)
        assertEquals("Legs", vm.uiState.value.plan?.next?.dayName)
    }

    @Test
    fun `starting a picked day opens it without moving the pointer`() = runTest {
        routines.active.value = ROUTINE
        val vm = viewModel()

        vm.effects.test {
            vm.onEvent(TodayIntent.StartDay(3))
            assertEquals(TodayEffect.OpenSession("session-r1-3"), awaitItem())
        }
        assertTrue(routines.pointerChanges.isEmpty())
    }

    @Test
    fun `a failed start reports an error`() {
        routines.active.value = ROUTINE
        sessions.failStart = true
        val vm = viewModel()

        vm.onEvent(TodayIntent.StartNextDay)

        assertEquals(TodayError.START_FAILED, vm.uiState.value.error)
        assertFalse(vm.uiState.value.starting)
        vm.onEvent(TodayIntent.ErrorShown)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `session in progress is resumable`() = runTest {
        sessions.inProgress.value = WorkoutSession(id = "s9", startedAt = NOW - 60_000, dayName = "Push")
        val vm = viewModel()

        assertEquals(InProgressUi("s9", NOW - 60_000, "Push"), vm.uiState.value.inProgress)
        vm.effects.test {
            vm.onEvent(TodayIntent.ResumeSession)
            assertEquals(TodayEffect.OpenSession("s9"), awaitItem())
        }

        sessions.inProgress.value = null
        assertNull(vm.uiState.value.inProgress)
    }

    @Test
    fun `week strip marks trained days and totals the week`() {
        val monday = at(2026, 10, 12, 18)
        val set = SetLog("set", "e", 0, reps = 5, loadGrams = 100_000, completed = true)
        sessions.sessions.value = listOf(
            WorkoutSession("a", monday, endedAt = monday + 1, exercises = listOf(SessionExercise("e", "a", 0, "v", sets = listOf(set)))),
            WorkoutSession("b", NOW, exercises = emptyList()),
            // Last Sunday: outside the week.
            WorkoutSession("c", at(2026, 10, 11, 18), endedAt = 1),
        )

        val week = viewModel().uiState.value.week

        assertEquals((1..7).toList(), week.days.map { it.isoDayOfWeek })
        assertEquals((12..18).toList(), week.days.map { it.dayOfMonth })
        assertEquals(listOf(true, false, true, false, false, false, false), week.days.map { it.trained })
        assertEquals(listOf(false, false, true, false, false, false, false), week.days.map { it.isToday })
        assertEquals(2, week.sessionCount)
        assertEquals(500_000, week.volumeGrams)
    }

    @Test
    fun `logging body weight converts from the user unit`() {
        val vm = viewModel(WeightUnit.LB)

        vm.onEvent(TodayIntent.OpenWeightDialog)
        vm.onEvent(TodayIntent.WeightInputChanged("180,5"))
        vm.onEvent(TodayIntent.SaveWeight)

        assertNull(vm.uiState.value.weightDialog)
        assertEquals(Weights.lbToGrams(180.5), vm.uiState.value.latestWeight?.weightGrams)
        assertEquals(WeightUnit.LB, vm.uiState.value.unit)
    }

    @Test
    fun `invalid body weight keeps the dialog open with an error`() {
        val vm = viewModel()

        vm.onEvent(TodayIntent.OpenWeightDialog)
        vm.onEvent(TodayIntent.WeightInputChanged("abc"))
        vm.onEvent(TodayIntent.SaveWeight)

        assertEquals(WeightDialogUi("abc", invalid = true), vm.uiState.value.weightDialog)
        assertTrue(bodyWeight.entries.value.isEmpty())
        vm.onEvent(TodayIntent.WeightInputChanged("80"))
        assertFalse(assertNotNull(vm.uiState.value.weightDialog).invalid)
    }

    @Test
    fun `weight parsing accepts commas and rejects out of range values`() {
        assertEquals(72.5, TodayViewModel.parseWeight(" 72,5 "))
        assertEquals(80.0, TodayViewModel.parseWeight("80"))
        assertNull(TodayViewModel.parseWeight("0"))
        assertNull(TodayViewModel.parseWeight("-3"))
        assertNull(TodayViewModel.parseWeight("5000"))
        assertNull(TodayViewModel.parseWeight(""))
    }

    @Test
    fun `elapsed time formatting`() {
        assertEquals("0:00", formatElapsed(-5))
        assertEquals("5:07", formatElapsed(307_000))
        assertEquals("1:02:03", formatElapsed(3_723_000))
    }

    private companion object {
        /** Wednesday 14 October 2026, 10:00 UTC. */
        val NOW = at(2026, 10, 14, 10)

        fun at(year: Int, month: Int, day: Int, hour: Int): Long =
            LocalDateTime(year, month, day, hour, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()

        fun exercise(id: String, name: String) = Exercise(
            id = id,
            name = LocalizedText(en = name, es = name),
            category = ExerciseCategory.STRENGTH,
            level = Level.BEGINNER,
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            instructions = LocalizedSteps.EMPTY,
            trackingType = TrackingType.WEIGHT_REPS,
            source = ExerciseSource.CATALOG,
            variants = listOf(ExerciseVariant("$id.barbell", id, Equipment.BARBELL, LoadType.BARBELL)),
        )

        val BENCH = exercise("bench-press", "Bench press")
        val ROW = exercise("row", "Row")

        fun day(position: Int, name: String, kind: DayKind = DayKind.TRAINING, slots: List<RoutineSlot> = emptyList()) =
            RoutineDay(
                id = "d$position",
                routineId = "r1",
                position = position,
                name = name,
                kind = kind,
                slots = slots
            )

        val ROUTINE = Routine(
            id = "r1",
            name = "Upper/Lower",
            isActive = true,
            days = listOf(
                day(0, "Push", slots = listOf(RoutineSlot("s0", "d0", 0, SlotTarget.Flexible("bench-press")))),
                day(
                    1,
                    "Pull",
                    slots = listOf(
                        RoutineSlot("s1", "d1", 0, SlotTarget.Fixed("row.barbell"), prescription = Prescription.Reps(5))
                    ),
                ),
                day(2, "Rest", DayKind.REST),
                day(3, "Legs"),
            ),
        )
    }
}
