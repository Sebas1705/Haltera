package es.sebas1705.haltera.feature.routines

import es.sebas1705.haltera.core.testing.CoroutineTestRule
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.feature.routines.ui.editor.PendingRestChange
import es.sebas1705.haltera.feature.routines.ui.editor.RoutineEditorIntent
import es.sebas1705.haltera.feature.routines.ui.editor.RoutineEditorViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fixed / flexible rest days in the routine editor (docs/designs/2026-10-16-flexible-rest-days.md). */
class RestPolicyEditorTest {
    private val rule = CoroutineTestRule()
    private val routines = FakeRoutineRepository()
    private val exercises = FakeExerciseRepository(TestCatalog.all)
    private lateinit var ppl: String
    private lateinit var ab: String

    @BeforeTest
    fun setUp() {
        rule.setup()
        runTest {
            // Push · Pull · Legs · Rest (7-day-like weekly plan with one rest).
            ppl = routines.createRoutine("PPL")
            listOf("Push", "Pull", "Legs").forEach { name ->
                routines.addSlot(routines.addDay(ppl, name), SlotSpec(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL)))
            }
            routines.addDay(ppl, "Rest", DayKind.REST)
            // A · B without rest days.
            ab = routines.createRoutine("AB")
            routines.addDay(ab, "A")
            routines.addDay(ab, "B")
            routines.calls.clear()
        }
    }

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(id: String) = RoutineEditorViewModel(id, routines, exercises, english)

    @Test
    fun `a routine starts fixed`() = runTest {
        val state = viewModel(ppl).uiState.value
        assertEquals(RestPolicy.FIXED, state.restPolicy)
        assertFalse(state.weekdayMode)
        assertNull(state.pendingRestChange)
    }

    @Test
    fun `making a routine with rest days flexible asks first, and cancelling changes nothing`() = runTest {
        val vm = viewModel(ppl)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        // One rest day in a 4-day cycle is 1.75 a week: starts at 2.
        assertEquals(PendingRestChange.ToFlexible(1, RestPolicy.flexible(2)), vm.uiState.value.pendingRestChange)
        assertTrue(routines.calls.isEmpty())

        vm.onEvent(RoutineEditorIntent.DismissRestChange)
        assertNull(vm.uiState.value.pendingRestChange)
        assertTrue(routines.calls.isEmpty())
        assertEquals(4, routines.routine(ppl).days.size)
    }

    @Test
    fun `confirming drops the rest day and keeps the training days in order`() = runTest {
        val vm = viewModel(ppl)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.ConfirmToFlexible)

        assertEquals(listOf("setRestPolicy(FLEXIBLE, 2, 0, 0)"), routines.calls)
        assertEquals(listOf("Push", "Pull", "Legs"), vm.uiState.value.days.map { it.name })
        assertTrue(vm.uiState.value.restPolicy.isFlexible)
        assertNull(vm.uiState.value.pendingRestChange)
    }

    @Test
    fun `a routine without rest days becomes flexible straight away`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        assertNull(vm.uiState.value.pendingRestChange)
        assertEquals(listOf("setRestPolicy(FLEXIBLE, 0, 0, 0)"), routines.calls)
    }

    @Test
    fun `choosing the mode the routine already has does nothing`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FIXED))
        assertTrue(routines.calls.isEmpty())
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        assertEquals(1, routines.calls.size)
    }

    @Test
    fun `the stepper sets 0 to 6 rest days a week`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.SetRestDaysPerWeek(3))
        assertEquals(3, vm.uiState.value.restPolicy.restDaysCount)
        vm.onEvent(RoutineEditorIntent.SetRestDaysPerWeek(9))
        assertEquals(6, vm.uiState.value.restPolicy.restDaysCount)
        vm.onEvent(RoutineEditorIntent.SetRestDaysPerWeek(-1))
        assertEquals(0, vm.uiState.value.restPolicy.restDaysCount)
    }

    @Test
    fun `the stepper does nothing on a fixed routine`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestDaysPerWeek(3))
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(3))
        vm.onEvent(RoutineEditorIntent.SetKnowsWeekdays(true))
        assertTrue(routines.calls.isEmpty())
        assertEquals(RestPolicy.FIXED, vm.uiState.value.restPolicy)
    }

    @Test
    fun `I know my rest days shows the chips and saves once a day is picked`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.SetRestDaysPerWeek(2))
        routines.calls.clear()

        vm.onEvent(RoutineEditorIntent.SetKnowsWeekdays(true))
        assertTrue(vm.uiState.value.weekdayMode)
        assertTrue(routines.calls.isEmpty())

        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(3))
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(7))
        assertEquals(listOf(3, 7), vm.uiState.value.restPolicy.weekdays)
        assertEquals(2, vm.uiState.value.restPolicy.restDaysCount)

        // Picking a day again removes it.
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(3))
        assertEquals(listOf(7), vm.uiState.value.restPolicy.weekdays)
    }

    @Test
    fun `at most six rest weekdays can be picked`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        (1..7).forEach { vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(it)) }
        assertEquals((1..6).toList(), vm.uiState.value.restPolicy.weekdays)
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(0))
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(8))
        assertEquals((1..6).toList(), vm.uiState.value.restPolicy.weekdays)
    }

    @Test
    fun `turning I know my rest days off keeps the count as a number`() = runTest {
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(2))
        vm.onEvent(RoutineEditorIntent.ToggleRestWeekday(5))
        vm.onEvent(RoutineEditorIntent.SetKnowsWeekdays(false))

        val policy = vm.uiState.value.restPolicy
        assertFalse(vm.uiState.value.weekdayMode)
        assertFalse(policy.knowsWeekdays)
        assertEquals(2, policy.restDaysCount)
    }

    @Test
    fun `a routine saved with weekdays opens with the chips`() = runTest {
        routines.setRestPolicy(ab, RestPolicy.onWeekdays(listOf(1)))
        assertTrue(viewModel(ab).uiState.value.weekdayMode)
    }

    @Test
    fun `back to fixed offers to append the rest days`() = runTest {
        routines.setRestPolicy(ab, RestPolicy.flexible(3))
        routines.calls.clear()
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FIXED))
        assertEquals(PendingRestChange.ToFixed(3), vm.uiState.value.pendingRestChange)

        vm.onEvent(RoutineEditorIntent.ConfirmToFixed(addRestDays = true, restDayName = "Rest"))
        assertEquals(listOf("setRestPolicy(FIXED, 0, 0, 3)"), routines.calls)
        val days = vm.uiState.value.days
        assertEquals(listOf("A", "B", "Rest", "Rest", "Rest"), days.map { it.name })
        assertEquals(
            listOf(DayKind.TRAINING, DayKind.TRAINING, DayKind.REST, DayKind.REST, DayKind.REST),
            days.map { it.kind }
        )
        assertEquals(RestPolicy.FIXED, vm.uiState.value.restPolicy)
    }

    @Test
    fun `back to fixed without rest days keeps the training days only`() = runTest {
        routines.setRestPolicy(ab, RestPolicy.onWeekdays(listOf(3, 7)))
        routines.calls.clear()
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FIXED))
        assertEquals(PendingRestChange.ToFixed(2), vm.uiState.value.pendingRestChange)
        vm.onEvent(RoutineEditorIntent.ConfirmToFixed(addRestDays = false, restDayName = "Rest"))
        assertEquals(listOf("A", "B"), vm.uiState.value.days.map { it.name })
        assertFalse(vm.uiState.value.weekdayMode)
    }

    @Test
    fun `a flexible routine with no rest days goes back to fixed without asking`() = runTest {
        routines.setRestPolicy(ab, RestPolicy.flexible(0))
        routines.calls.clear()
        val vm = viewModel(ab)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FIXED))
        assertNull(vm.uiState.value.pendingRestChange)
        assertEquals(listOf("setRestPolicy(FIXED, 0, 0, 0)"), routines.calls)
    }

    @Test
    fun `fixed - flexible - fixed keeps every training day and its exercises`() = runTest {
        val before = routines.routine(ppl).days.filter { it.kind == DayKind.TRAINING }
        val vm = viewModel(ppl)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.ConfirmToFlexible)
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FIXED))
        vm.onEvent(RoutineEditorIntent.ConfirmToFixed(addRestDays = true, restDayName = "Rest"))
        val after = vm.uiState.value.days.filter { it.kind == DayKind.TRAINING }
        assertEquals(before.map { it.id to it.slots }, after.map { it.id to it.slots })
        assertEquals(listOf("Push", "Pull", "Legs", "Rest", "Rest"), vm.uiState.value.days.map { it.name })
    }

    @Test
    fun `a failed conversion shows the error and keeps the routine`() = runTest {
        val vm = viewModel(ab)
        routines.failure = IllegalStateException("db")
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        assertTrue(vm.uiState.value.error)
        assertEquals(RestPolicy.FIXED, vm.uiState.value.restPolicy)
    }

    @Test
    fun `the score follows the flexible schedule`() = runTest {
        val vm = viewModel(ppl)
        val fixed = vm.uiState.value.score?.sessionsPerWeek
        vm.onEvent(RoutineEditorIntent.SetRestMode(RestMode.FLEXIBLE))
        vm.onEvent(RoutineEditorIntent.ConfirmToFlexible)
        vm.onEvent(RoutineEditorIntent.SetRestDaysPerWeek(1))
        assertEquals(6.0, vm.uiState.value.score?.sessionsPerWeek)
        assertTrue(fixed != 6.0)
    }
}
