package es.sebas1705.haltera.feature.routines

import app.cash.turbine.test
import es.sebas1705.haltera.core.testing.CoroutineTestRule
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineOrigin
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TemplateDay
import es.sebas1705.haltera.feature.routines.ui.catalogue.CatalogueViewModel
import es.sebas1705.haltera.feature.routines.ui.catalogue.TemplatePreviewIntent
import es.sebas1705.haltera.feature.routines.ui.catalogue.TemplatePreviewViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplatePreviewViewModelTest {
    private val rule = CoroutineTestRule()

    private val template = RoutineTemplate(
        id = "full-body",
        version = 2,
        name = LocalizedText(en = "Full body", es = "Cuerpo completo"),
        description = LocalizedText(en = "Three days a week", es = "Tres días a la semana"),
        credit = "Haltera",
        level = Level.BEGINNER,
        days = listOf(
            TemplateDay(
                LocalizedText("A", "A"),
                slots = listOf(
                    SlotSpec(SlotTarget.Fixed(TestCatalog.BENCH_BARBELL)),
                    SlotSpec(SlotTarget.Flexible("plank"))
                ),
            ),
            TemplateDay(LocalizedText("Rest", "Descanso"), DayKind.REST),
        ),
    )
    private val routines = FakeRoutineRepository(templates = listOf(template))
    private val exercises = FakeExerciseRepository(TestCatalog.all)

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private fun viewModel(id: String = template.id, language: String = "es") =
        TemplatePreviewViewModel(id, routines, exercises, { language })

    @Test
    fun `catalogue lists the bundled templates`() {
        val vm = CatalogueViewModel(routines, exercises, english)
        assertEquals(listOf(template), vm.uiState.value.templates)
        assertEquals(setOf(template.id), vm.uiState.value.scores.keys)
        assertEquals("en", vm.uiState.value.language)
    }

    @Test
    fun `preview resolves the exercises of every day`() = runTest {
        val state = viewModel().uiState.value
        assertEquals(template, state.template)
        assertEquals(setOf("bench-press", "plank"), state.index.exercises.keys)
        assertTrue(assertNotNull(state.score).scored)
    }

    @Test
    fun `with no active routine the copy becomes active and opens in the editor`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().makeActive)
            vm.onEvent(TemplatePreviewIntent.AddToMyRoutines)
            val created = assertNotNull(expectMostRecentItem().createdRoutineId)

            assertEquals(listOf("copyTemplate(full-body, es)", "setActive($created)"), routines.calls)
            val copy = routines.routine(created)
            assertEquals("Cuerpo completo", copy.name)
            assertEquals(listOf("A", "Descanso"), copy.days.map { it.name })
            assertEquals(RoutineOrigin("full-body", 2), copy.origin)
            assertTrue(copy.isActive)

            vm.onEvent(TemplatePreviewIntent.OpenHandled)
            assertNull(expectMostRecentItem().createdRoutineId)
        }
    }

    @Test
    fun `an existing active routine is kept unless asked`() = runTest {
        routines.seed(Routine(id = "mine", name = "Mine", isActive = true))
        val vm = viewModel(language = "en")
        assertFalse(vm.uiState.value.makeActive)

        vm.onEvent(TemplatePreviewIntent.AddToMyRoutines)
        assertEquals(listOf("copyTemplate(full-body, en)"), routines.calls)
        assertTrue(routines.routine("mine").isActive)
        assertEquals("Full body", routines.routines.last().name)
    }

    @Test
    fun `opting in makes the copy active even when another routine is`() = runTest {
        routines.seed(Routine(id = "mine", name = "Mine", isActive = true))
        val vm = viewModel()
        vm.onEvent(TemplatePreviewIntent.SetMakeActive(true))
        vm.onEvent(TemplatePreviewIntent.AddToMyRoutines)

        val created = assertNotNull(vm.uiState.value.createdRoutineId)
        assertTrue(routines.routine(created).isActive)
        assertFalse(routines.routine("mine").isActive)
    }

    @Test
    fun `a failed copy reports an error and allows retrying`() = runTest {
        val vm = viewModel()
        routines.failure = IllegalStateException("boom")
        vm.onEvent(TemplatePreviewIntent.AddToMyRoutines)
        assertTrue(vm.uiState.value.error)
        assertFalse(vm.uiState.value.copying)
        assertNull(vm.uiState.value.createdRoutineId)

        routines.failure = null
        vm.onEvent(TemplatePreviewIntent.AddToMyRoutines)
        assertNotNull(vm.uiState.value.createdRoutineId)
    }

    @Test
    fun `an unknown template does nothing`() = runTest {
        val vm = viewModel(id = "missing")
        assertNull(vm.uiState.value.template)
        vm.onEvent(TemplatePreviewIntent.AddToMyRoutines)
        assertTrue(routines.calls.isEmpty())
    }
}
