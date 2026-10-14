package es.sebas1705.ironlog.feature.routines

import es.sebas1705.ironlog.core.testing.CoroutineTestRule
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.feature.routines.ui.editor.PendingDelete
import es.sebas1705.ironlog.feature.routines.ui.editor.RoutineEditorIntent
import es.sebas1705.ironlog.feature.routines.ui.editor.RoutineEditorViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutineEditorViewModelTest {
    private val rule = CoroutineTestRule()
    private val routines = FakeRoutineRepository()
    private val exercises = FakeExerciseRepository(TestCatalog.all)
    private lateinit var routineId: String

    @BeforeTest
    fun setUp() {
        rule.setup()
        runTest { seed() }
    }

    private suspend fun seed() {
        routineId = routines.createRoutine("PPL", "Push pull legs")
        val push = routines.addDay(routineId, "Push")
        routines.addDay(routineId, "Pull")
        routines.addDay(routineId, "Legs")
        routines.addSlot(push, SlotSpec(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL)))
        routines.addSlot(push, SlotSpec(SlotTarget.Flexible("plank")))
        routines.calls.clear()
    }

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(id: String = routineId) = RoutineEditorViewModel(id, routines, exercises, english)

    private fun RoutineEditorViewModel.dayNames() = uiState.value.days.map { it.name }

    @Test
    fun `loads the routine, its text drafts and the exercises of its slots`() = runTest {
        val state = viewModel().uiState.value
        assertFalse(state.loading)
        assertEquals("PPL", state.name)
        assertEquals("Push pull legs", state.description)
        assertEquals(listOf("Push", "Pull", "Legs"), state.days.map { it.name })
        assertEquals(setOf("bench-press", "plank"), state.index.exercises.keys)
    }

    @Test
    fun `name and description are saved as they are typed`() = runTest {
        val vm = viewModel()
        vm.onEvent(RoutineEditorIntent.SetName("PPL v2"))
        vm.onEvent(RoutineEditorIntent.SetDescription("Six days"))

        assertEquals("PPL v2", vm.uiState.value.name)
        assertEquals(listOf("updateRoutine(PPL v2, Push pull legs)", "updateRoutine(PPL v2, Six days)"), routines.calls)
        assertEquals("Six days", routines.routine(routineId).description)
    }

    @Test
    fun `typing is not overwritten by the routine coming back from the database`() = runTest {
        val vm = viewModel()
        vm.onEvent(RoutineEditorIntent.SetName("P"))
        routines.addDay(routineId, "Rest", DayKind.REST)
        assertEquals("P", vm.uiState.value.name)
    }

    @Test
    fun `days can be added as training or rest, any cycle length`() = runTest {
        val vm = viewModel()
        vm.onEvent(RoutineEditorIntent.AddDay("Rest", DayKind.REST))
        vm.onEvent(RoutineEditorIntent.AddDay("Day 5", DayKind.TRAINING))

        val days = vm.uiState.value.days
        assertEquals(5, days.size)
        assertEquals(
            listOf(DayKind.TRAINING, DayKind.TRAINING, DayKind.TRAINING, DayKind.REST, DayKind.TRAINING),
            days.map { it.kind }
        )
    }

    @Test
    fun `rename trims, ignores blank names and keeps the kind`() = runTest {
        val vm = viewModel()
        val pull = vm.uiState.value.days[1]
        vm.onEvent(RoutineEditorIntent.RenameDay(pull.id, "  Back  "))
        vm.onEvent(RoutineEditorIntent.RenameDay(pull.id, "   "))

        assertEquals(listOf("updateDay(Back, TRAINING)"), routines.calls)
        assertEquals(listOf("Push", "Back", "Legs"), vm.dayNames())
    }

    @Test
    fun `toggling a day to rest keeps its name`() = runTest {
        val vm = viewModel()
        val legs = vm.uiState.value.days[2]
        vm.onEvent(RoutineEditorIntent.SetDayKind(legs.id, DayKind.REST))
        vm.onEvent(RoutineEditorIntent.SetDayKind(legs.id, DayKind.REST))

        assertEquals(listOf("updateDay(Legs, REST)"), routines.calls)
        assertEquals(DayKind.REST, vm.uiState.value.days[2].kind)
    }

    @Test
    fun `days move up and down and never past the ends`() = runTest {
        val vm = viewModel()
        val (push, _, legs) = vm.uiState.value.days
        vm.onEvent(RoutineEditorIntent.MoveDay(push.id, -1))
        vm.onEvent(RoutineEditorIntent.MoveDay(legs.id, 1))
        assertTrue(routines.calls.isEmpty())

        vm.onEvent(RoutineEditorIntent.MoveDay(push.id, 1))
        assertEquals(listOf("Pull", "Push", "Legs"), vm.dayNames())
        vm.onEvent(RoutineEditorIntent.MoveDay(legs.id, -1))
        assertEquals(listOf("Pull", "Legs", "Push"), vm.dayNames())
    }

    @Test
    fun `slots reorder within their day`() = runTest {
        val vm = viewModel()
        val (bench, plank) = vm.uiState.value.days.first().slots
        vm.onEvent(RoutineEditorIntent.MoveSlot(bench.id, -1))
        assertTrue(routines.calls.isEmpty())

        vm.onEvent(RoutineEditorIntent.MoveSlot(plank.id, -1))
        assertEquals(listOf(plank.id, bench.id), vm.uiState.value.days.first().slots.map { it.id })
    }

    @Test
    fun `deleting a day or a slot needs confirmation`() = runTest {
        val vm = viewModel()
        val push = vm.uiState.value.days.first()

        vm.onEvent(RoutineEditorIntent.RequestDeleteSlot(push.slots.first().id))
        assertIs<PendingDelete.Slot>(vm.uiState.value.pendingDelete)
        vm.onEvent(RoutineEditorIntent.DismissDelete)
        assertNull(vm.uiState.value.pendingDelete)
        assertTrue(routines.calls.isEmpty())

        vm.onEvent(RoutineEditorIntent.RequestDeleteSlot(push.slots.first().id))
        vm.onEvent(RoutineEditorIntent.ConfirmDelete)
        assertEquals(1, vm.uiState.value.days.first().slots.size)

        vm.onEvent(RoutineEditorIntent.RequestDeleteDay(push.id))
        assertEquals(PendingDelete.Day(vm.uiState.value.days.first()), vm.uiState.value.pendingDelete)
        vm.onEvent(RoutineEditorIntent.ConfirmDelete)
        assertEquals(listOf("Pull", "Legs"), vm.dayNames())
        assertEquals(listOf(0, 1), vm.uiState.value.days.map { it.position })
    }

    @Test
    fun `the star sets and clears the active routine`() = runTest {
        val vm = viewModel()
        vm.onEvent(RoutineEditorIntent.SetActive(true))
        assertTrue(vm.uiState.value.routine!!.isActive)
        vm.onEvent(RoutineEditorIntent.SetActive(false))
        assertEquals(listOf("setActive($routineId)", "setActive(null)"), routines.calls)
    }

    @Test
    fun `a routine deleted elsewhere shows as missing`() = runTest {
        val vm = viewModel()
        routines.deleteRoutine(routineId)
        assertTrue(vm.uiState.value.missing)
        assertTrue(viewModel("nope").uiState.value.missing)
    }

    @Test
    fun `write failures surface as an error`() = runTest {
        val vm = viewModel()
        routines.failure = IllegalStateException("boom")
        vm.onEvent(RoutineEditorIntent.AddDay("X", DayKind.TRAINING))
        assertTrue(vm.uiState.value.error)
        assertEquals(3, vm.uiState.value.days.size)
    }
}
