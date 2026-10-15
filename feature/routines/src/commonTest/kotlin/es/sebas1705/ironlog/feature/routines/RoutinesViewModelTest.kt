package es.sebas1705.ironlog.feature.routines

import app.cash.turbine.test
import es.sebas1705.ironlog.core.testing.CoroutineTestRule
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineFocus
import es.sebas1705.ironlog.feature.routines.ui.list.RoutinesIntent
import es.sebas1705.ironlog.feature.routines.ui.list.RoutinesViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutinesViewModelTest {
    private val rule = CoroutineTestRule()
    private val repo = FakeRoutineRepository()
    private val exercises = FakeExerciseRepository(TestCatalog.all)

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    @Test
    fun `lists routines with the active one first`() = runTest {
        repo.seed(Routine(id = "a", name = "A"))
        repo.seed(Routine(id = "b", name = "B", isActive = true))
        val vm = RoutinesViewModel(repo, exercises)

        vm.uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertEquals(listOf("b", "a"), state.routines.map { it.id })
        }
    }

    @Test
    fun `set active and deactivate go through the repository`() = runTest {
        repo.seed(Routine(id = "a", name = "A"))
        val vm = RoutinesViewModel(repo, exercises)

        vm.onEvent(RoutinesIntent.SetActive("a", active = true))
        assertTrue(repo.routine("a").isActive)
        assertTrue(vm.uiState.value.routines.single().isActive)

        vm.onEvent(RoutinesIntent.SetActive("a", active = false))
        assertEquals("setActive(null)", repo.calls.last())
        assertFalse(repo.routine("a").isActive)
    }

    @Test
    fun `delete waits for confirmation`() = runTest {
        val routine = repo.seed(Routine(id = "a", name = "A"))
        val vm = RoutinesViewModel(repo, exercises)

        vm.onEvent(RoutinesIntent.RequestDelete(routine))
        assertEquals(routine, vm.uiState.value.pendingDelete)
        assertEquals(1, repo.routines.size)

        vm.onEvent(RoutinesIntent.DismissDelete)
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(1, repo.routines.size)

        vm.onEvent(RoutinesIntent.RequestDelete(routine))
        vm.onEvent(RoutinesIntent.ConfirmDelete)
        assertNull(vm.uiState.value.pendingDelete)
        assertTrue(repo.routines.isEmpty())
        assertTrue(vm.uiState.value.routines.isEmpty())
    }

    @Test
    fun `create makes a routine with one training day and opens it once`() = runTest {
        val vm = RoutinesViewModel(repo, exercises)

        vm.uiState.test {
            assertNull(awaitItem().openRoutineId)
            vm.onEvent(RoutinesIntent.Create("My routine", "Day 1"))
            val opened = expectMostRecentItem().openRoutineId
            val routine = repo.routine(checkNotNull(opened))
            assertEquals("My routine", routine.name)
            assertEquals(listOf("Day 1" to DayKind.TRAINING), routine.days.map { it.name to it.kind })

            vm.onEvent(RoutinesIntent.OpenHandled)
            assertNull(expectMostRecentItem().openRoutineId)
        }
    }

    @Test
    fun `a failing write shows an error instead of crashing`() = runTest {
        repo.seed(Routine(id = "a", name = "A"))
        repo.failure = IllegalStateException("disk full")
        val vm = RoutinesViewModel(repo, exercises)

        vm.onEvent(RoutinesIntent.Create("R", "Day 1"))
        assertTrue(vm.uiState.value.error)
        assertNull(vm.uiState.value.openRoutineId)
        assertFalse(vm.uiState.value.creating)

        vm.onEvent(RoutinesIntent.ErrorShown)
        assertFalse(vm.uiState.value.error)
    }

    @Test
    fun `every routine gets a score badge once its exercises load`() = runTest {
        val routine = repo.seed(Routine(id = "a", name = "A"))
        val day = repo.addDay(routine.id, "Push")
        repo.addSlot(day, SlotSpec(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL), sets = 4))
        repo.seed(Routine(id = "empty", name = "Empty"))
        val vm = RoutinesViewModel(repo, exercises)

        val scores = vm.uiState.value.scores
        assertTrue(assertNotNull(scores["a"]).scored)
        assertEquals(RoutineFocus.EMPTY, assertNotNull(scores["empty"]).focus)
    }
}
