package es.sebas1705.ironlog.feature.routines

import app.cash.turbine.test
import es.sebas1705.ironlog.core.testing.CoroutineTestRule
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.feature.routines.model.PrescriptionKind
import es.sebas1705.ironlog.feature.routines.model.ProgressionKind
import es.sebas1705.ironlog.feature.routines.model.SlotLimits
import es.sebas1705.ironlog.feature.routines.ui.slot.SlotEditorIntent
import es.sebas1705.ironlog.feature.routines.ui.slot.SlotEditorViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlotEditorViewModelTest {
    private val rule = CoroutineTestRule()
    private val routines = FakeRoutineRepository()
    private val exercises = FakeExerciseRepository(TestCatalog.all)
    private lateinit var routineId: String
    private lateinit var benchSlot: String
    private lateinit var plankSlot: String

    @BeforeTest
    fun setUp() {
        rule.setup()
        runTest {
            routineId = routines.createRoutine("R")
            val day = routines.addDay(routineId, "Push")
            benchSlot = routines.addSlot(day, SlotSpec(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL)))
            plankSlot = routines.addSlot(day, SlotSpec(SlotTarget.Fixed("plank.none"), prescription = Prescription.Duration(30)))
            routines.calls.clear()
        }
    }

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(slotId: String = benchSlot, unit: WeightUnit = WeightUnit.KG) =
        SlotEditorViewModel(routineId, slotId, routines, exercises, FakeSettingsRepository(unit), english)

    private fun saved(slotId: String = benchSlot) =
        routines.routine(routineId).days.first().slots.first { it.id == slotId }.toSpec()

    private fun SlotEditorViewModel.spec() = checkNotNull(uiState.value.spec)

    @Test
    fun `loads the slot with its exercise, day and unit`() = runTest {
        val state = viewModel(unit = WeightUnit.LB).uiState.value
        assertFalse(state.loading)
        assertFalse(state.missing)
        assertEquals("Push", state.dayName)
        assertEquals(TestCatalog.bench, state.exercise)
        assertEquals(WeightUnit.LB, state.unit)
        assertEquals(listOf(PrescriptionKind.REP_RANGE, PrescriptionKind.REPS), state.allowedKinds)
    }

    @Test
    fun `every change is saved straight away`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            skipItems(1)
            vm.onEvent(SlotEditorIntent.SetSets(5))
            assertEquals(5, awaitItem().spec?.sets)
            vm.onEvent(SlotEditorIntent.SetWarmupSets(2))
            vm.onEvent(SlotEditorIntent.SetRestBetweenSets(120))
            vm.onEvent(SlotEditorIntent.SetRestAfter(180))
            vm.onEvent(SlotEditorIntent.SetSupersetGroup(1))
            vm.onEvent(SlotEditorIntent.SetNotes("Pause on the chest"))
            cancelAndIgnoreRemainingEvents()
        }
        val spec = saved()
        assertEquals(5, spec.sets)
        assertEquals(2, spec.warmupSets)
        assertEquals(120, spec.restBetweenSetsS)
        assertEquals(180, spec.restAfterS)
        assertEquals(1, spec.supersetGroup)
        assertEquals("Pause on the chest", spec.notes)
        assertTrue(routines.calls.all { it == "updateSlot($benchSlot)" })
    }

    @Test
    fun `numbers are clamped to the editor limits`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetSets(0))
        vm.onEvent(SlotEditorIntent.SetWarmupSets(-3))
        vm.onEvent(SlotEditorIntent.SetRestBetweenSets(10_000))
        vm.onEvent(SlotEditorIntent.SetSupersetGroup(0))
        assertEquals(SlotLimits.SETS.first, saved().sets)
        assertEquals(0, saved().warmupSets)
        assertEquals(SlotLimits.REST_S.last, saved().restBetweenSetsS)
        assertEquals(null, saved().supersetGroup)
    }

    @Test
    fun `an unchanged value does not write again`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetSets(3))
        assertTrue(routines.calls.isEmpty())
    }

    @Test
    fun `rep range stays valid when one end crosses the other`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetRepMin(15))
        assertEquals(Prescription.RepRange(15, 15), saved().prescription)
        vm.onEvent(SlotEditorIntent.SetRepMax(6))
        assertEquals(Prescription.RepRange(6, 6), saved().prescription)
        vm.onEvent(SlotEditorIntent.SetRepMax(10))
        assertEquals(Prescription.RepRange(6, 10), vm.spec().prescription)
    }

    @Test
    fun `switching prescription kind keeps the numbers that carry over`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetPrescriptionKind(PrescriptionKind.REPS))
        assertEquals(Prescription.Reps(12), saved().prescription)
        vm.onEvent(SlotEditorIntent.SetReps(5))
        vm.onEvent(SlotEditorIntent.SetPrescriptionKind(PrescriptionKind.REP_RANGE))
        assertEquals(Prescription.RepRange(5, 5), saved().prescription)
    }

    @Test
    fun `a timed exercise only offers duration`() = runTest {
        val vm = viewModel(plankSlot)
        assertEquals(listOf(PrescriptionKind.DURATION), vm.uiState.value.allowedKinds)
        assertFalse(vm.uiState.value.prescriptionMismatch)
        assertFalse(vm.uiState.value.progressionApplies)
        vm.onEvent(SlotEditorIntent.SetDuration(1))
        assertEquals(Prescription.Duration(SlotLimits.DURATION_S.first), saved(plankSlot).prescription)
    }

    @Test
    fun `double progression defaults its increment from the load type and is editable`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetProgressionKind(ProgressionKind.DOUBLE))
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)), saved().progression)

        vm.onEvent(SlotEditorIntent.SetSessionsAtTop(2))
        vm.onEvent(SlotEditorIntent.SetIncrement(LoadIncrement.Weight(1_250)))
        assertEquals(ProgressionRule.DoubleProgression(2, LoadIncrement.Weight(1_250)), saved().progression)

        vm.onEvent(SlotEditorIntent.SetProgressionKind(ProgressionKind.LINEAR))
        vm.onEvent(SlotEditorIntent.SetEveryNSessions(3))
        assertEquals(ProgressionRule.Linear(LoadIncrement.Weight(1_250), 3), saved().progression)

        vm.onEvent(SlotEditorIntent.SetProgressionKind(ProgressionKind.NONE))
        assertEquals(ProgressionRule.None, saved().progression)
    }

    @Test
    fun `switching to dumbbells moves a default increment to the dumbbell default and counts both hands`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetProgressionKind(ProgressionKind.LINEAR))
        vm.onEvent(SlotEditorIntent.SetFixedVariant(TestCatalog.BENCH_DUMBBELL))

        val spec = saved()
        assertEquals(SlotTarget.Fixed(TestCatalog.BENCH_DUMBBELL), spec.target)
        assertEquals(ProgressionRule.Linear(LoadIncrement.Weight(2_000), 1), spec.progression)
        assertTrue(spec.bilateral)
        assertTrue(vm.uiState.value.bilateralApplies)
    }

    @Test
    fun `a custom increment survives a variant change`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetProgressionKind(ProgressionKind.DOUBLE))
        vm.onEvent(SlotEditorIntent.SetIncrement(LoadIncrement.Weight(5_000)))
        vm.onEvent(SlotEditorIntent.SetFixedVariant(TestCatalog.BENCH_DUMBBELL))
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(5_000)), saved().progression)
    }

    @Test
    fun `increments are clamped`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetProgressionKind(ProgressionKind.DOUBLE))
        vm.onEvent(SlotEditorIntent.SetIncrement(LoadIncrement.Weight(-500)))
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(0)), saved().progression)
    }

    @Test
    fun `flexible target allows any variant or a chosen set`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.SetFlexible)
        assertEquals(SlotTarget.Flexible("bench-press"), saved().target)
        assertTrue(vm.uiState.value.isAllowed(TestCatalog.BENCH_DUMBBELL))

        // Unticking one of "any" keeps the others.
        vm.onEvent(SlotEditorIntent.ToggleAllowedVariant(TestCatalog.BENCH_DUMBBELL))
        assertEquals(SlotTarget.Flexible("bench-press", listOf(TestCatalog.BENCH_BARBELL)), saved().target)
        assertFalse(vm.uiState.value.isAllowed(TestCatalog.BENCH_DUMBBELL))

        // The last allowed variant cannot be unticked.
        vm.onEvent(SlotEditorIntent.ToggleAllowedVariant(TestCatalog.BENCH_BARBELL))
        assertEquals(SlotTarget.Flexible("bench-press", listOf(TestCatalog.BENCH_BARBELL)), saved().target)

        // Ticking every variant again means "any".
        vm.onEvent(SlotEditorIntent.ToggleAllowedVariant(TestCatalog.BENCH_DUMBBELL))
        assertEquals(SlotTarget.Flexible("bench-press"), saved().target)

        vm.onEvent(SlotEditorIntent.ToggleAllowedVariant(TestCatalog.BENCH_DUMBBELL))
        vm.onEvent(SlotEditorIntent.SetAnyVariant)
        assertEquals(SlotTarget.Flexible("bench-press"), saved().target)

        vm.onEvent(SlotEditorIntent.SetFixedVariant(TestCatalog.BENCH_BARBELL))
        assertEquals(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL), saved().target)
    }

    @Test
    fun `delete asks first, then closes the editor`() = runTest {
        val vm = viewModel()
        vm.onEvent(SlotEditorIntent.RequestDelete)
        assertTrue(vm.uiState.value.confirmDelete)
        vm.onEvent(SlotEditorIntent.DismissDelete)
        assertFalse(vm.uiState.value.confirmDelete)
        assertTrue(routines.calls.isEmpty())

        vm.onEvent(SlotEditorIntent.RequestDelete)
        vm.onEvent(SlotEditorIntent.ConfirmDelete)
        assertTrue(vm.uiState.value.deleted)
        assertEquals(listOf(plankSlot), routines.routine(routineId).days.first().slots.map { it.id })
    }

    @Test
    fun `an unknown slot shows as missing and ignores edits`() = runTest {
        val vm = viewModel("nope")
        assertTrue(vm.uiState.value.missing)
        vm.onEvent(SlotEditorIntent.SetSets(4))
        assertTrue(routines.calls.isEmpty())
    }

    @Test
    fun `a failed save reports an error and keeps saving later changes`() = runTest {
        val vm = viewModel()
        routines.failure = IllegalStateException("boom")
        vm.onEvent(SlotEditorIntent.SetSets(4))
        assertTrue(vm.uiState.value.error)
        vm.onEvent(SlotEditorIntent.ErrorShown)

        routines.failure = null
        vm.onEvent(SlotEditorIntent.SetSets(6))
        assertEquals(6, saved().sets)
    }
}
