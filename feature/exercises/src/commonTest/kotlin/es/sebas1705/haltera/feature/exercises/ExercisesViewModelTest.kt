package es.sebas1705.haltera.feature.exercises

import app.cash.turbine.test
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.feature.exercises.ui.ExerciseRow
import es.sebas1705.haltera.feature.exercises.ui.ExercisesIntent
import es.sebas1705.haltera.feature.exercises.ui.ExercisesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    private val bench = exercise(
        id = "bench-press",
        en = "Bench press",
        es = "Press de banca",
        variants = listOf(
            variant("bench-press", Equipment.BARBELL),
            variant("bench-press", Equipment.DUMBBELL, LoadType.DUMBBELL),
            variant("bench-press", Equipment.SMITH, deprecated = true),
        ),
    )
    private val squat = exercise(
        id = "squat",
        en = "Squat",
        es = "Sentadilla",
        primary = listOf(Muscle.QUADRICEPS, Muscle.GLUTES),
    )
    private val running = exercise(
        id = "running",
        en = "Running",
        es = "Carrera",
        category = ExerciseCategory.CARDIO,
        primary = listOf(Muscle.FULL_BODY),
        variants = listOf(variant("running", Equipment.NONE, LoadType.NONE)),
    )
    private val mine = exercise(
        id = "user-1",
        en = "My curl",
        source = ExerciseSource.USER,
        primary = listOf(Muscle.BICEPS)
    )

    private val repository = FakeExerciseRepository(listOf(bench, squat, running, mine))

    @BeforeTest
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ExercisesViewModel(repository, dispatcher)

    @Test
    fun `waits for the language before searching`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loading)
        assertTrue(repository.searches.isEmpty())
    }

    @Test
    fun `lists localised rows sorted in the app language`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("es"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(listOf("Carrera", "My curl", "Press de banca", "Sentadilla"), state.items.map { it.name })
        assertEquals("es", repository.searches.last().second)
    }

    @Test
    fun `rows show the main muscle, active equipment without NONE and the custom badge`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        advanceUntilIdle()

        val rows = vm.uiState.value.items.associateBy { it.id }
        assertEquals(
            ExerciseRow(
                "bench-press",
                "Bench press",
                Muscle.CHEST,
                listOf(Equipment.BARBELL, Equipment.DUMBBELL),
                custom = false
            ),
            rows["bench-press"],
        )
        assertEquals(Muscle.QUADRICEPS, rows["squat"]?.mainMuscle)
        assertEquals(emptyList(), rows["running"]?.equipment)
        assertEquals(true, rows["user-1"]?.custom)
    }

    @Test
    fun `filter chips update the state and the repository filter`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        vm.onEvent(ExercisesIntent.CategorySelected(ExerciseCategory.CARDIO))
        advanceUntilIdle()
        assertEquals(ExerciseCategory.CARDIO, vm.uiState.value.category)
        assertEquals(listOf("running"), vm.uiState.value.items.map { it.id })

        vm.onEvent(ExercisesIntent.CategorySelected(null))
        vm.onEvent(ExercisesIntent.MuscleSelected(Muscle.QUADRICEPS))
        advanceUntilIdle()
        assertEquals(listOf("squat"), vm.uiState.value.items.map { it.id })

        vm.onEvent(ExercisesIntent.MuscleSelected(null))
        vm.onEvent(ExercisesIntent.EquipmentSelected(Equipment.DUMBBELL))
        advanceUntilIdle()
        assertEquals(listOf("bench-press"), vm.uiState.value.items.map { it.id })
        assertEquals(ExerciseFilter(equipment = Equipment.DUMBBELL), repository.searches.last().first)
    }

    @Test
    fun `deprecated variants do not match an equipment filter`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        vm.onEvent(ExercisesIntent.EquipmentSelected(Equipment.SMITH))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertTrue(vm.uiState.value.hasFilters)
    }

    @Test
    fun `typing is debounced and searches with the trimmed query`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        advanceUntilIdle()
        val before = repository.searches.size

        vm.onEvent(ExercisesIntent.QueryChanged("sq"))
        vm.onEvent(ExercisesIntent.QueryChanged("squ "))
        advanceTimeBy(ExercisesViewModel.QUERY_DEBOUNCE_MS - 1)
        assertEquals("squ ", vm.uiState.value.query)
        assertEquals(before, repository.searches.size)

        advanceUntilIdle()
        assertEquals(before + 1, repository.searches.size)
        assertEquals("squ", repository.searches.last().first.query)
        assertEquals(listOf("squat"), vm.uiState.value.items.map { it.id })
    }

    @Test
    fun `clear filters resets query and chips and lists everything`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        vm.onEvent(ExercisesIntent.QueryChanged("nothing matches this"))
        vm.onEvent(ExercisesIntent.CategorySelected(ExerciseCategory.SPORT))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())

        vm.onEvent(ExercisesIntent.ClearFilters)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.hasFilters)
        assertEquals(4, state.items.size)
    }

    @Test
    fun `new exercises appear in the open list`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        vm.uiState.test {
            assertEquals(4, expectMostRecentItem().items.size)
            repository.exercises.value += exercise(id = "user-2", en = "Another", source = ExerciseSource.USER)
            assertEquals(5, awaitItem().items.size)
        }
    }

    @Test
    fun `a failing search shows the error and retry recovers`() = runTest {
        repository.failSearch = true
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.error)

        repository.failSearch = false
        vm.onEvent(ExercisesIntent.Retry)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.error)
        assertEquals(4, vm.uiState.value.items.size)
    }
}
