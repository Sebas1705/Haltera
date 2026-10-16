package es.sebas1705.haltera.feature.exercises

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseUsage
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.RoutineUse
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.VariantEdit
import es.sebas1705.haltera.feature.exercises.ui.create.NewExerciseIntent
import es.sebas1705.haltera.feature.exercises.ui.create.NewExerciseViewModel
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailIntent
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The custom exercise editor in edit mode, and delete / restore from the detail (core design §3.1.1). */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomExerciseLifecycleTest {
    private lateinit var dispatcher: TestDispatcher

    private val sled = exercise(
        id = "user-1",
        en = "Sled push",
        es = "Empuje de trineo",
        primary = listOf(Muscle.QUADRICEPS),
        secondary = listOf(Muscle.GLUTES),
        source = ExerciseSource.USER,
        steps = LocalizedSteps(en = listOf("Lean in.", "Drive."), es = listOf("Inclínate.", "Empuja.")),
        variants = listOf(
            variant("user-1", Equipment.MACHINE, LoadType.MACHINE),
            variant("user-1", Equipment.BODYWEIGHT, LoadType.BODYWEIGHT, position = 1),
            variant("user-1", Equipment.BAND, LoadType.BAND, deprecated = true, position = 2),
        ),
    ).copy(author = "Ana")

    private val repository = FakeExerciseRepository(listOf(sled))
    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun editor(language: String = "en", id: String = "user-1") = NewExerciseViewModel(id, repository).also {
        it.onEvent(NewExerciseIntent.SetLanguage(language))
    }

    private fun detail(language: String = "en") = ExerciseDetailViewModel(
        "user-1",
        repository,
        settings,
        dispatcher
    ).also {
        it.onEvent(ExerciseDetailIntent.SetLanguage(language))
    }

    // Editor

    @Test
    fun `the editor loads the exercise in the app language with its active variants`() = runTest {
        repository.usages["user-1"] = ExerciseUsage(variantsWithHistory = setOf("user-1.machine"))
        val vm = editor(language = "es")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.editing)
        assertFalse(state.loading)
        assertEquals("Empuje de trineo", state.name)
        assertEquals("Sled push", state.otherName)
        assertEquals("Inclínate.\nEmpuja.", state.instructions)
        assertEquals(listOf(Muscle.GLUTES), state.secondaryMuscles)
        assertEquals(listOf(Equipment.MACHINE, Equipment.BODYWEIGHT), state.equipment)
        assertEquals(listOf(true, false), state.variants.map { it.locked })
        assertTrue(state.trackingLocked)
    }

    @Test
    fun `saving an edit keeps the ids and both languages`() = runTest {
        val vm = editor()
        advanceUntilIdle()
        vm.onEvent(NewExerciseIntent.NameChanged("Prowler push"))
        vm.onEvent(NewExerciseIntent.CategorySelected(ExerciseCategory.CALISTHENICS))
        vm.onEvent(NewExerciseIntent.LevelSelected(Level.EXPERT))
        vm.onEvent(NewExerciseIntent.SecondaryMuscleToggled(Muscle.CALVES))
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.KETTLEBELL))
        vm.onEvent(NewExerciseIntent.LoadTypeSelected(2, LoadType.BARBELL))
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()

        assertEquals("user-1", vm.uiState.value.savedExerciseId)
        val (id, edit) = repository.updates.single()
        assertEquals("user-1", id)
        // The Spanish name, loaded into the other-language field, is kept.
        assertEquals(LocalizedText(en = "Prowler push", es = "Empuje de trineo"), edit.name)
        assertEquals(ExerciseCategory.CALISTHENICS, edit.category)
        assertEquals(Level.EXPERT, edit.level)
        assertEquals(listOf(Muscle.GLUTES, Muscle.CALVES), edit.secondaryMuscles)
        assertEquals(
            listOf(
                VariantEdit("user-1.machine", Equipment.MACHINE, LoadType.MACHINE),
                VariantEdit("user-1.bodyweight", Equipment.BODYWEIGHT, LoadType.BODYWEIGHT),
                VariantEdit(null, Equipment.KETTLEBELL, LoadType.BARBELL),
            ),
            edit.variants,
        )
        // Untouched instructions are kept exactly, in both languages.
        assertEquals(sled.instructions, edit.instructions)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `typed instructions go to the app language and keep the other language's own steps`() = runTest {
        val vm = editor(language = "es")
        advanceUntilIdle()
        vm.onEvent(NewExerciseIntent.InstructionsChanged("  Agárrate.\n\n Empuja fuerte. "))
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()
        val edit = repository.updates.single().second
        assertEquals(listOf("Agárrate.", "Empuja fuerte."), edit.instructions.es)
        assertEquals(listOf("Lean in.", "Drive."), edit.instructions.en)
    }

    @Test
    fun `a new exercise stores its instructions and the optional other name`() = runTest {
        val vm = NewExerciseViewModel(repository)
        vm.onEvent(NewExerciseIntent.SetLanguage("es"))
        vm.onEvent(NewExerciseIntent.NameChanged("Remo invertido"))
        vm.onEvent(NewExerciseIntent.OtherNameChanged("Inverted row"))
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.BODYWEIGHT))
        vm.onEvent(NewExerciseIntent.InstructionsChanged("Cuélgate.\nTira."))
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()
        val input = repository.created.single()
        assertEquals(LocalizedText(en = "Inverted row", es = "Remo invertido"), input.name)
        assertEquals(LocalizedSteps(listOf("Cuélgate.", "Tira."), listOf("Cuélgate.", "Tira.")), input.instructions)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `a variant with history keeps its load type and its removal is flagged`() = runTest {
        repository.usages["user-1"] = ExerciseUsage(variantsWithHistory = setOf("user-1.machine", "user-1.band"))
        val vm = editor()
        advanceUntilIdle()
        vm.onEvent(NewExerciseIntent.LoadTypeSelected(0, LoadType.DUMBBELL))
        vm.onEvent(NewExerciseIntent.TrackingSelected(TrackingType.TIME))
        assertEquals(LoadType.MACHINE, vm.uiState.value.variants[0].loadType)
        assertEquals(TrackingType.WEIGHT_REPS, vm.uiState.value.trackingType)
        // The band variant was already hidden: it does not count as removed now.
        assertFalse(vm.uiState.value.removesLoggedVariant)

        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.MACHINE))
        assertTrue(vm.uiState.value.removesLoggedVariant)
        // Picking the machine again brings back the same variant.
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.MACHINE))
        assertFalse(vm.uiState.value.removesLoggedVariant)
        assertEquals("user-1.machine", vm.uiState.value.variants.last().id)
        // So does picking the band, hidden before.
        vm.onEvent(NewExerciseIntent.EquipmentToggled(Equipment.BAND))
        assertEquals("user-1.band", vm.uiState.value.variants.last().id)
    }

    @Test
    fun `editing a missing or catalogue exercise is not found, and a failed save can be retried`() = runTest {
        val missing = editor(id = "nope")
        advanceUntilIdle()
        assertTrue(missing.uiState.value.notFound)
        assertFalse(missing.uiState.value.canSave)

        repository.exercises.value += exercise("bench-press", "Bench press")
        val catalogue = editor(id = "bench-press")
        advanceUntilIdle()
        assertTrue(catalogue.uiState.value.notFound)

        repository.failUpdate = true
        val vm = editor()
        advanceUntilIdle()
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saveFailed)
        repository.failUpdate = false
        vm.onEvent(NewExerciseIntent.Save)
        advanceUntilIdle()
        assertEquals("user-1", vm.uiState.value.savedExerciseId)
    }

    // Detail: author, delete, restore

    @Test
    fun `the detail shows the creator of an imported exercise`() = runTest {
        val vm = detail()
        advanceUntilIdle()
        val shown = assertNotNull(vm.uiState.value.detail)
        assertEquals("Ana", shown.author)
        assertTrue(shown.custom)
        assertFalse(shown.deleted)
    }

    @Test
    fun `deleting an unused exercise asks, deletes it for good and closes`() = runTest {
        val vm = detail()
        advanceUntilIdle()
        vm.onEvent(ExerciseDetailIntent.RequestDelete)
        advanceUntilIdle()
        assertEquals(ExerciseUsage(), vm.uiState.value.deleteUsage)
        vm.onEvent(ExerciseDetailIntent.ConfirmDelete)
        advanceUntilIdle()
        assertEquals(listOf("user-1" to true), repository.deletes)
        assertTrue(vm.uiState.value.closed)
        assertNull(vm.uiState.value.deleteUsage)
    }

    @Test
    fun `the delete dialog lists routines and history, and cancelling changes nothing`() = runTest {
        val usage = ExerciseUsage(
            routines = listOf(RoutineUse("r1", "Push", 2)),
            sessions = 3,
            sets = 9,
            variantsWithHistory = setOf("user-1.machine"),
        )
        repository.usages["user-1"] = usage
        val vm = detail()
        advanceUntilIdle()
        vm.onEvent(ExerciseDetailIntent.RequestDelete)
        advanceUntilIdle()
        assertEquals(usage, vm.uiState.value.deleteUsage)
        vm.onEvent(ExerciseDetailIntent.DismissDelete)
        advanceUntilIdle()
        assertNull(vm.uiState.value.deleteUsage)
        assertTrue(repository.deletes.isEmpty())
    }

    @Test
    fun `deleting an exercise with history hides it, stays open and can be restored`() = runTest {
        repository.usages["user-1"] = ExerciseUsage(sessions = 1, sets = 2, variantsWithHistory = setOf("user-1.machine"))
        val vm = detail()
        advanceUntilIdle()
        vm.onEvent(ExerciseDetailIntent.RequestDelete)
        vm.onEvent(ExerciseDetailIntent.ConfirmDelete)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.closed)
        assertTrue(vm.uiState.value.detail!!.deleted)

        // The banner offers Restore.
        vm.onEvent(ExerciseDetailIntent.Restore)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.detail!!.deleted)
    }

    @Test
    fun `a failed delete is shown and catalogue exercises cannot be deleted`() = runTest {
        repository.failDelete = true
        val vm = detail()
        advanceUntilIdle()
        vm.onEvent(ExerciseDetailIntent.RequestDelete)
        vm.onEvent(ExerciseDetailIntent.ConfirmDelete)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deleteFailed)
        assertFalse(vm.uiState.value.closed)
        vm.onEvent(ExerciseDetailIntent.DeleteFailedShown)
        assertFalse(vm.uiState.value.deleteFailed)

        repository.exercises.value += exercise("bench-press", "Bench press")
        val catalogue = ExerciseDetailViewModel("bench-press", repository, settings, dispatcher)
        catalogue.onEvent(ExerciseDetailIntent.SetLanguage("en"))
        advanceUntilIdle()
        catalogue.onEvent(ExerciseDetailIntent.RequestDelete)
        advanceUntilIdle()
        assertNull(catalogue.uiState.value.deleteUsage)
    }
}
