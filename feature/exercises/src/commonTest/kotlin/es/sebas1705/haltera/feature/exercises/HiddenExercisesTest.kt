package es.sebas1705.haltera.feature.exercises

import androidx.lifecycle.SavedStateHandle
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.feature.exercises.ui.ExercisesIntent
import es.sebas1705.haltera.feature.exercises.ui.ExercisesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
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
import kotlin.test.assertTrue

/** The "Hidden" chip of the Exercises tab: soft-deleted custom exercises can be restored at any time. */
@OptIn(ExperimentalCoroutinesApi::class)
class HiddenExercisesTest {
    private lateinit var dispatcher: TestDispatcher

    private val bench = exercise(id = "bench-press", en = "Bench press", es = "Press de banca")
    private val sled = exercise(
        id = "user-sled",
        en = "Sled push",
        es = "Empuje de trineo",
        source = ExerciseSource.USER,
        primary = listOf(Muscle.QUADRICEPS),
        variants = listOf(variant("user-sled", Equipment.MACHINE)),
    ).copy(deprecated = true)
    private val atlas = exercise(
        id = "user-atlas",
        en = "Atlas carry",
        es = "Transporte de atlas",
        source = ExerciseSource.USER
    )
        .copy(deprecated = true)
    private val live = exercise(id = "user-live", en = "Live curl", es = "Curl vivo", source = ExerciseSource.USER)

    private val repository = FakeExerciseRepository(listOf(bench, sled, atlas, live))

    @BeforeTest
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(language: String = "en") = ExercisesViewModel(repository, dispatcher).also {
        it.onEvent(ExercisesIntent.SetLanguage(language))
    }

    @Test
    fun `hidden exercises are listed apart, sorted in the app language, never in the search results`() = runTest {
        val vm = viewModel(language = "es")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(listOf("Empuje de trineo", "Transporte de atlas"), state.hidden.map { it.name })
        assertTrue(state.hidden.all { it.custom })
        assertEquals(listOf("bench-press", "user-live"), state.items.map { it.id }.sorted())
        assertFalse(state.showingHidden, "the list shows the catalogue until the chip is tapped")
    }

    @Test
    fun `the chip toggles the hidden list and clears the search and filters`() = runTest {
        val vm = viewModel()
        vm.onEvent(ExercisesIntent.QueryChanged("press"))
        vm.onEvent(ExercisesIntent.MuscleSelected(Muscle.CHEST))
        advanceUntilIdle()

        vm.onEvent(ExercisesIntent.ToggleHidden)
        advanceUntilIdle()
        var state = vm.uiState.value
        assertTrue(state.showingHidden)
        assertEquals("", state.query)
        assertEquals(null, state.muscle)

        vm.onEvent(ExercisesIntent.ToggleHidden)
        assertFalse(vm.uiState.value.showingHidden)

        // Typing or choosing a filter leaves the hidden list.
        vm.onEvent(ExercisesIntent.ToggleHidden)
        vm.onEvent(ExercisesIntent.QueryChanged("s"))
        state = vm.uiState.value
        assertFalse(state.showingHidden)
        vm.onEvent(ExercisesIntent.ToggleHidden)
        vm.onEvent(ExercisesIntent.EquipmentSelected(Equipment.MACHINE))
        assertFalse(vm.uiState.value.showingHidden)
    }

    @Test
    fun `restoring brings the exercise back to the list at once and the chip goes with the last one`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(ExercisesIntent.ToggleHidden)

        vm.onEvent(ExercisesIntent.Restore("user-sled"))
        advanceUntilIdle()
        var state = vm.uiState.value
        assertEquals(listOf("user-atlas"), state.hidden.map { it.id })
        assertTrue(state.showingHidden, "still one to restore")
        assertTrue("user-sled" in state.items.map { it.id }, "back in the list and search")

        vm.onEvent(ExercisesIntent.Restore("user-atlas"))
        advanceUntilIdle()
        state = vm.uiState.value
        assertTrue(state.hidden.isEmpty())
        assertFalse(state.showingHidden, "nothing hidden: the chip and the list disappear")
        assertTrue("user-atlas" in state.items.map { it.id })

        // Something hidden again later does not reopen the list by itself.
        repository.exercises.value = repository.exercises.value.map {
            if (it.id == "user-live") it.copy(deprecated = true) else it
        }
        advanceUntilIdle()
        assertEquals(listOf("user-live"), vm.uiState.value.hidden.map { it.id })
        assertFalse(vm.uiState.value.showingHidden)
    }

    @Test
    fun `the Hidden chip survives process death`() = runTest {
        val saved = SavedStateHandle()
        val before = ExercisesViewModel(repository, dispatcher, saved).also {
            it.onEvent(ExercisesIntent.SetLanguage("en"))
        }
        advanceUntilIdle()
        before.onEvent(ExercisesIntent.ToggleHidden)
        advanceUntilIdle()
        assertTrue(before.uiState.value.showingHidden)

        // The process dies: a new ViewModel is created from the saved state only.
        val restored = saved.afterProcessDeath()
        val after = ExercisesViewModel(repository, dispatcher, restored)
        assertTrue(after.uiState.value.hiddenSelected, "restored before anything loads")
        after.onEvent(ExercisesIntent.SetLanguage("en"))
        advanceUntilIdle()
        val state = after.uiState.value
        assertTrue(state.showingHidden)
        assertEquals(listOf("user-atlas", "user-sled"), state.hidden.map { it.id })

        // Leaving the hidden list is saved too.
        after.onEvent(ExercisesIntent.ToggleHidden)
        advanceUntilIdle()
        val again = ExercisesViewModel(repository, dispatcher, restored.afterProcessDeath())
        again.onEvent(ExercisesIntent.SetLanguage("en"))
        advanceUntilIdle()
        assertFalse(again.uiState.value.showingHidden)
    }

    @Test
    fun `a restored Hidden chip is dropped when nothing is hidden any more`() = runTest {
        val saved = SavedStateHandle(mapOf("exercises.hidden" to true))
        val vm = ExercisesViewModel(FakeExerciseRepository(listOf(bench, live)), dispatcher, saved)
        vm.onEvent(ExercisesIntent.SetLanguage("en"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hiddenSelected)
        assertFalse(vm.uiState.value.showingHidden)
        assertEquals(false, saved.get<Boolean>("exercises.hidden"))
    }

    @Test
    fun `a failed restore is reported once and keeps the exercise hidden`() = runTest {
        repository.failRestore = true
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEvent(ExercisesIntent.Restore("user-sled"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.restoreFailed)
        assertEquals(2, vm.uiState.value.hidden.size)
        vm.onEvent(ExercisesIntent.RestoreFailedShown)
        assertFalse(vm.uiState.value.restoreFailed)
    }
}

/** What a new process gets back: a fresh handle with the saved values only. */
internal fun SavedStateHandle.afterProcessDeath() = SavedStateHandle(keys().associateWith { get<Any?>(it) })
