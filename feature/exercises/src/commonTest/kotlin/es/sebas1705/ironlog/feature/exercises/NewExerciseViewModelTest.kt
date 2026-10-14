package es.sebas1705.ironlog.feature.exercises

import app.cash.turbine.test
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.NewVariant
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.feature.exercises.ui.create.NewExerciseIntent
import es.sebas1705.ironlog.feature.exercises.ui.create.NewExerciseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NewExerciseViewModelTest {
    private val repository = FakeExerciseRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saving without name or equipment shows the errors and saves nothing`() = runTest {
        val vm = NewExerciseViewModel(repository)
        assertFalse(vm.uiState.value.showErrors)

        vm.onEvent(NewExerciseIntent.NameChanged("   "))
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.showErrors)
        assertTrue(state.nameError)
        assertTrue(state.equipmentError)
        assertNull(state.savedExerciseId)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `toggling muscles and equipment keeps selection order`() = runTest {
        val vm = NewExerciseViewModel(repository)
        vm.onEvent(NewExerciseIntent.MuscleToggled(Muscle.LATS))
        vm.onEvent(NewExerciseIntent.MuscleToggled(Muscle.BICEPS))
        vm.onEvent(NewExerciseIntent.MuscleToggled(Muscle.LATS))
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.CABLE))
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.BAND))
        advanceUntilIdle()

        assertEquals(listOf(Muscle.BICEPS), vm.uiState.value.primaryMuscles)
        assertEquals(listOf(Equipment.CABLE, Equipment.BAND), vm.uiState.value.equipment)
    }

    @Test
    fun `saves one name for both languages and one variant per equipment with its default load type`() = runTest {
        val vm = NewExerciseViewModel(repository)
        vm.uiState.test {
            vm.onEvent(NewExerciseIntent.NameChanged("  Landmine press "))
            vm.onEvent(NewExerciseIntent.CategorySelected(ExerciseCategory.CALISTHENICS))
            vm.onEvent(NewExerciseIntent.TrackingSelected(TrackingType.BODYWEIGHT_REPS))
            vm.onEvent(NewExerciseIntent.MuscleToggled(Muscle.SHOULDERS))
            vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.BARBELL))
            vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.KETTLEBELL))
            vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.BODYWEIGHT))
            vm.onEvent(NewExerciseIntent.Save)
            assertEquals("user-1", expectMostRecentItem().savedExerciseId)
            cancelAndIgnoreRemainingEvents()
        }

        val input = repository.created.single()
        assertEquals(LocalizedText("Landmine press", "Landmine press"), input.name)
        assertEquals(ExerciseCategory.CALISTHENICS, input.category)
        assertEquals(TrackingType.BODYWEIGHT_REPS, input.trackingType)
        assertEquals(listOf(Muscle.SHOULDERS), input.primaryMuscles)
        assertEquals(
            listOf(
                NewVariant(Equipment.BARBELL, LoadType.BARBELL),
                NewVariant(Equipment.KETTLEBELL, LoadType.DUMBBELL),
                NewVariant(Equipment.BODYWEIGHT, LoadType.BODYWEIGHT),
            ),
            input.variants,
        )
    }

    @Test
    fun `a failed save is reported and can be retried`() = runTest {
        repository.failCreate = true
        val vm = NewExerciseViewModel(repository)
        vm.onEvent(NewExerciseIntent.NameChanged("Sled push"))
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.OTHER))
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.saving)

        repository.failCreate = false
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.saveFailed)
        assertEquals("user-1", vm.uiState.value.savedExerciseId)
    }
}
