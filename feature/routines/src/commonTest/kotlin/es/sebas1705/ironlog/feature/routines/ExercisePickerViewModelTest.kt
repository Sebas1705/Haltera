package es.sebas1705.ironlog.feature.routines

import es.sebas1705.ironlog.core.testing.CoroutineTestRule
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.feature.routines.ui.picker.ExercisePickerIntent
import es.sebas1705.ironlog.feature.routines.ui.picker.ExercisePickerViewModel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExercisePickerViewModelTest {
    private val rule = CoroutineTestRule()
    private val routines = FakeRoutineRepository()
    private val exercises = FakeExerciseRepository(TestCatalog.all)
    private lateinit var dayId: String

    @BeforeTest
    fun setUp() {
        rule.setup()
        runTest { dayId = routines.addDay(routines.createRoutine("R"), "Push") }
    }

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel() = ExercisePickerViewModel(dayId, routines, exercises, english)

    @Test
    fun `search is debounced and only runs for the latest text`() = runTest {
        val vm = viewModel()
        advanceTimeBy(300)
        assertEquals(3, vm.uiState.value.results.size)

        vm.onEvent(ExercisePickerIntent.SetQuery("b"))
        vm.onEvent(ExercisePickerIntent.SetQuery("be"))
        vm.onEvent(ExercisePickerIntent.SetQuery("ben"))
        assertEquals("ben", vm.uiState.value.query)
        advanceTimeBy(300)

        assertEquals(listOf("", "ben"), exercises.searches)
        assertEquals(listOf("bench-press"), vm.uiState.value.results.map { it.id })
    }

    @Test
    fun `filter chips narrow the results and clear resets them`() = runTest {
        val vm = viewModel()
        advanceTimeBy(300)
        vm.onEvent(ExercisePickerIntent.EquipmentSelected(Equipment.DUMBBELL))
        advanceTimeBy(300)
        assertEquals(Equipment.DUMBBELL, vm.uiState.value.equipment)
        assertEquals(listOf("bench-press"), vm.uiState.value.results.map { it.id })

        vm.onEvent(ExercisePickerIntent.CategorySelected(ExerciseCategory.CARDIO))
        vm.onEvent(ExercisePickerIntent.MuscleSelected(Muscle.CHEST))
        advanceTimeBy(300)
        assertEquals(ExerciseFilter(category = ExerciseCategory.CARDIO, muscle = Muscle.CHEST, equipment = Equipment.DUMBBELL), exercises.filters.last())
        assertTrue(vm.uiState.value.results.isEmpty())

        vm.onEvent(ExercisePickerIntent.ClearFilters)
        advanceTimeBy(300)
        assertNull(vm.uiState.value.category)
        assertNull(vm.uiState.value.muscle)
        assertNull(vm.uiState.value.equipment)
        assertEquals(3, vm.uiState.value.results.size)
    }

    @Test
    fun `toggling an exercise shows and hides its variants`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisePickerIntent.Toggle("bench-press"))
        assertEquals("bench-press", vm.uiState.value.expandedId)
        vm.onEvent(ExercisePickerIntent.Toggle("bench-press"))
        assertNull(vm.uiState.value.expandedId)
    }

    @Test
    fun `picking adds a slot with defaults and opens it`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisePickerIntent.Pick(TestCatalog.bench, SlotTarget.Flexible("bench-press")))

        val slotId = vm.uiState.value.addedSlotId
        val slot = routines.routines.single().days.single().slots.single()
        assertEquals(slotId, slot.id)
        assertEquals(SlotTarget.Flexible("bench-press"), slot.target)
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)), slot.progression)

        vm.onEvent(ExercisePickerIntent.OpenHandled)
        assertNull(vm.uiState.value.addedSlotId)
    }

    @Test
    fun `a failed add reports an error`() = runTest {
        val vm = viewModel()
        routines.failure = IllegalStateException("boom")
        vm.onEvent(ExercisePickerIntent.Pick(TestCatalog.plank, SlotTarget.Fixed("plank.none")))
        assertTrue(vm.uiState.value.error)
        assertNull(vm.uiState.value.addedSlotId)
    }
}
