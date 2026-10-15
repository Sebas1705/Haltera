package es.sebas1705.haltera.feature.exercises

import app.cash.turbine.test
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.core.units.Weights
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailIntent
import es.sebas1705.haltera.feature.exercises.ui.detail.ExerciseDetailViewModel
import es.sebas1705.haltera.feature.exercises.ui.detail.LoadText
import es.sebas1705.haltera.feature.exercises.ui.detail.RecordRowKind
import es.sebas1705.haltera.feature.exercises.ui.detail.TopSet
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    private val bench = exercise(
        id = "bench-press",
        en = "Bench press",
        es = "Press de banca",
        primary = listOf(Muscle.CHEST),
        secondary = listOf(Muscle.TRICEPS, Muscle.SHOULDERS),
        steps = LocalizedSteps(en = listOf("Lie down.", " ", "Press."), es = listOf("Túmbate.", "Empuja.")),
        variants = listOf(
            variant(
                "bench-press",
                Equipment.BARBELL,
                frames = listOf("Barbell_Bench_Press/0.jpg", "Barbell_Bench_Press/1.jpg")
            ),
            variant(
                "bench-press",
                Equipment.DUMBBELL,
                LoadType.DUMBBELL,
                instructions = LocalizedSteps(en = listOf("Hold two dumbbells."), es = listOf("Coge dos mancuernas.")),
                position = 1,
            ),
            variant("bench-press", Equipment.SMITH, deprecated = true, position = 2),
        ),
    )
    private val band = exercise(
        id = "band-pull-apart",
        en = "Band pull-apart",
        variants = listOf(variant("band-pull-apart", Equipment.BAND, LoadType.BAND)),
    )

    private val repository = FakeExerciseRepository(listOf(bench, band))
    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() {
        dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "bench-press", language: String = "en") =
        ExerciseDetailViewModel(id, repository, settings, dispatcher).also {
            it.onEvent(ExerciseDetailIntent.SetLanguage(language))
        }

    @Test
    fun `maps the exercise with its first active variant selected`() = runTest {
        val vm = viewModel(language = "es")
        advanceUntilIdle()

        val detail = assertNotNull(vm.uiState.value.detail)
        assertEquals("Press de banca", detail.name)
        assertEquals("bench-press.barbell", detail.selectedVariantId)
        assertEquals(LoadType.BARBELL, detail.loadType)
        assertEquals(listOf("Barbell_Bench_Press/0.jpg", "Barbell_Bench_Press/1.jpg"), detail.frames)
        assertEquals(listOf("Túmbate.", "Empuja."), detail.steps)
        assertEquals(listOf(Muscle.TRICEPS, Muscle.SHOULDERS), detail.secondaryMuscles)
        assertFalse(detail.custom)
        // The deprecated Smith variant is not offered.
        assertEquals(listOf(Equipment.BARBELL, Equipment.DUMBBELL), detail.variants.map { it.equipment })
    }

    @Test
    fun `the most recently trained variant is selected by default`() = runTest {
        repository.history.value = mapOf(
            "bench-press.barbell" to listOf(entry("s1", 1_000, "bench-press.barbell", 60_000L to 8)),
            "bench-press.dumbbell" to listOf(entry("s2", 2_000, "bench-press.dumbbell", 30_000L to 10)),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("bench-press.dumbbell", vm.uiState.value.detail?.selectedVariantId)
        assertEquals(listOf("s2"), vm.uiState.value.history.map { it.sessionId })
        vm.onEvent(ExerciseDetailIntent.SelectVariant("bench-press.barbell"))
        advanceUntilIdle()
        assertEquals("bench-press.barbell", vm.uiState.value.detail?.selectedVariantId, "the user's choice wins")
    }

    @Test
    fun `loads show at most one decimal`() = runTest {
        repository.history.value = mapOf(
            "bench-press.barbell" to listOf(entry("s1", 1_000, "bench-press.barbell", 80_750L to 10)),
        )
        val vm = viewModel()
        advanceUntilIdle()

        // Epley: 80.75 × (1 + 10/30) = 107.67 kg, shown as 107.7 (decimal comma in Spanish).
        assertEquals(
            LoadText(Weights.format(107_667, WeightUnit.KG, decimals = 1), isLevel = false),
            vm.uiState.value.records.first { it.kind == RecordRowKind.ESTIMATED_ONE_RM }.load,
        )
    }

    @Test
    fun `blank steps are dropped`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("Lie down.", "Press."), vm.uiState.value.detail?.steps)
    }

    @Test
    fun `selecting a variant switches frames, instructions and load type`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertEquals("bench-press.barbell", expectMostRecentItem().detail?.selectedVariantId)
            vm.onEvent(ExerciseDetailIntent.SelectVariant("bench-press.dumbbell"))
            val detail = assertNotNull(expectMostRecentItem().detail)
            assertEquals("bench-press.dumbbell", detail.selectedVariantId)
            assertEquals(LoadType.DUMBBELL, detail.loadType)
            assertTrue(detail.frames.isEmpty())
            assertEquals(listOf("Hold two dumbbells."), detail.steps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a missing exercise is reported as not found`() = runTest {
        val vm = viewModel(id = "nope")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.notFound)
        assertNull(vm.uiState.value.detail)
    }

    @Test
    fun `history lists the selected variant's sessions with their top set, newest first`() = runTest {
        repository.history.value = mapOf(
            "bench-press.barbell" to listOf(
                entry("s1", 1_000, "bench-press.barbell", 60_000L to 8, 80_000L to 5, 80_000L to 3),
                entry("s2", 2_000, "bench-press.barbell", 82_500L to 5),
            ),
            "bench-press.dumbbell" to listOf(entry("s3", 500, "bench-press.dumbbell", 30_000L to 10)),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val history = vm.uiState.value.history
        assertEquals(listOf("s2", "s1"), history.map { it.sessionId })
        // Loads follow the app language ("82.5" / "82,5").
        assertEquals(
            TopSet(LoadText(Weights.format(82_500, WeightUnit.KG), isLevel = false), 5, null, null),
            history[0].topSet
        )
        assertEquals(TopSet(LoadText("80", isLevel = false), 5, null, null), history[1].topSet)
        assertEquals(3, history[1].sets)

        vm.onEvent(ExerciseDetailIntent.SelectVariant("bench-press.dumbbell"))
        advanceUntilIdle()
        assertEquals(listOf("s3"), vm.uiState.value.history.map { it.sessionId })
    }

    @Test
    fun `records are formatted in the user's unit and follow unit changes`() = runTest {
        repository.history.value = mapOf(
            "bench-press.barbell" to listOf(
                entry("s1", 1_000, "bench-press.barbell", 100_000L to 1),
                entry("s2", 2_000, "bench-press.barbell", 90_000L to 6),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val records = vm.uiState.value.records.associateBy { it.kind }
        assertEquals(LoadText("100", isLevel = false), records[RecordRowKind.HEAVIEST_LOAD]?.load)
        // Epley: 90 × (1 + 6/30) = 108 kg.
        assertEquals(LoadText("108", isLevel = false), records[RecordRowKind.ESTIMATED_ONE_RM]?.load)
        assertEquals(6L, records[RecordRowKind.MOST_REPS]?.count)
        assertEquals(2_000L, records[RecordRowKind.MOST_REPS]?.at)
        assertEquals(LoadText("540", isLevel = false), records[RecordRowKind.SESSION_VOLUME]?.load)

        settings.setUnit(WeightUnit.LB)
        advanceUntilIdle()
        assertEquals(WeightUnit.LB, vm.uiState.value.unit)
        assertEquals(
            LoadText(Weights.format(100_000, WeightUnit.LB, decimals = 1), isLevel = false),
            vm.uiState.value.records.first { it.kind == RecordRowKind.HEAVIEST_LOAD }.load,
        )
    }

    @Test
    fun `band variants show levels and skip weight-only records`() = runTest {
        repository.history.value = mapOf(
            "band-pull-apart.band" to listOf(entry("s1", 1_000, "band-pull-apart.band", 3L to 15)),
        )
        val vm = viewModel(id = "band-pull-apart")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadText("3", isLevel = true), state.history.single().topSet?.load)
        assertEquals(listOf(RecordRowKind.HEAVIEST_LOAD, RecordRowKind.MOST_REPS), state.records.map { it.kind })
    }

    @Test
    fun `exercises without load show only rep records`() = runTest {
        repository.exercises.value += exercise(
            id = "user-1",
            en = "Burpee",
            tracking = TrackingType.REPS,
            source = ExerciseSource.USER,
            variants = listOf(variant("user-1", Equipment.NONE, LoadType.NONE)),
        )
        repository.history.value = mapOf("user-1.none" to listOf(entry("s1", 1_000, "user-1.none", null to 20)))
        val vm = viewModel(id = "user-1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(assertNotNull(state.detail).custom)
        assertEquals(listOf(RecordRowKind.MOST_REPS), state.records.map { it.kind })
        assertEquals(TopSet(null, 20, null, null), state.history.single().topSet)
    }
}
