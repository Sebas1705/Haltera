package es.sebas1705.haltera.dataimport

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.importer.ExerciseDecision
import es.sebas1705.haltera.data.repository.importer.ExistingPolicy
import es.sebas1705.haltera.data.repository.importer.HistoryImporter
import es.sebas1705.haltera.data.repository.importer.ImportError
import es.sebas1705.haltera.data.repository.importer.ImportOptions
import es.sebas1705.haltera.data.repository.importer.ImportPreview
import es.sebas1705.haltera.data.repository.importer.ImportSummary
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import es.sebas1705.haltera.data.repository.importer.ParsedImport
import es.sebas1705.haltera.data.repository.importer.UnmatchedExercise
import es.sebas1705.haltera.data.repository.importer.ExerciseSuggestion
import es.sebas1705.haltera.data.repository.importer.SuggestionConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HEVY = "title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index," +
    "set_type,weight_kg,reps,distance_km,duration_seconds,rpe\n" +
    "Push,\"29 Apr 2025, 18:00\",\"29 Apr 2025, 19:00\",,Cable Fly,,,0,normal,20,10,,,\n"

private class FakeImporter : HistoryImporter {
    var imports = 0
    var previews = 0
    var lastPreviewOptions: ImportOptions? = null
    var lastOptions: ImportOptions? = null
    var fail = false
    var unmatched = listOf(UnmatchedExercise("cable fly", "Cable Fly", 1, 1))

    override suspend fun preview(parsed: ParsedImport, options: ImportOptions) = previewOf(parsed, options)

    private fun previewOf(parsed: ParsedImport, options: ImportOptions): ImportPreview {
        previews++
        lastPreviewOptions = options
        return ImportPreview(
            source = parsed.source,
            sessions = parsed.sessions.size,
            sets = parsed.setCount,
            firstStartedAt = parsed.sessions.minOfOrNull { it.startedAt },
            lastStartedAt = parsed.sessions.maxOfOrNull { it.startedAt },
            bodyWeights = 0,
            existingSessions = 0,
            existingBodyWeights = 0,
            matchedExercises = 0,
            unmatched = unmatched,
            skippedRows = parsed.skippedRows,
            needsUnitChoice = parsed.needsUnitChoice,
        )
    }

    override suspend fun import(parsed: ParsedImport, options: ImportOptions): ImportSummary {
        imports++
        lastOptions = options
        check(!fail) { "database is full" }
        return ImportSummary(parsed.sessions.size, 0, 0, parsed.setCount, 0, 1)
    }
}

private class FakeFiles(var file: PickedImportFile? = null, var fail: Boolean = false) : ImportFileBridge {
    var picks = 0

    override suspend fun pick(): PickedImportFile? {
        picks++
        check(!fail) { "cannot read" }
        return file
    }
}

private class FakeExercises(val matches: (String) -> Boolean = { true }) : ExerciseRepository {
    val queries = mutableListOf<String>()
    private val fly = Exercise(
        id = "fly",
        name = LocalizedText("Fly", "Aperturas"),
        category = ExerciseCategory.STRENGTH,
        level = Level.BEGINNER,
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList(),
        instructions = LocalizedSteps.EMPTY,
        trackingType = TrackingType.WEIGHT_REPS,
        source = ExerciseSource.CATALOG,
    )

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> {
        queries += filter.query
        return flowOf(if (matches(filter.query)) listOf(fly) else emptyList())
    }

    override fun observeExercise(id: String): Flow<Exercise?> = flowOf(null)
    override suspend fun getExercise(id: String): Exercise? = null
    override suspend fun getVariant(id: String): ExerciseVariant? = null
    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> = emptyList()
    override suspend fun createCustom(input: NewExercise): Exercise = error("not used")
    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> = flowOf(
        emptyList()
    )
    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> = flowOf(
        emptyList()
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class DataImportViewModelTest {
    private val importer = FakeImporter()
    private val files = FakeFiles(PickedImportFile("hevy.csv", HEVY.encodeToByteArray()))
    private val settings = KeyValueSettingsRepository(MapSettings())

    private var exercises = FakeExercises()

    private fun viewModel() = DataImportViewModel(
        importer,
        files,
        exercises,
        settings,
        UnconfinedTestDispatcher()
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a picked file shows a preview and writes nothing until confirmed`() {
        val vm = viewModel()

        vm.onEvent(DataImportIntent.Pick)

        val review = assertNotNull(vm.uiState.value.review)
        assertEquals(1, review.preview.sessions)
        assertEquals(1, review.preview.sets)
        assertEquals(ExistingPolicy.SKIP, review.existing)
        assertEquals(0, importer.imports)
    }

    @Test
    fun `the unit starts from the unit setting`() {
        settings.setUnit(WeightUnit.LB)

        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        assertEquals(ImportWeightUnit.LB, vm.uiState.value.review?.unit)
    }

    @Test
    fun `cancelling the picker shows nothing`() {
        files.file = null
        val vm = viewModel()

        vm.onEvent(DataImportIntent.Pick)

        assertNull(vm.uiState.value.review)
        assertNull(vm.uiState.value.outcome)
        assertTrue(!vm.uiState.value.busy)
    }

    @Test
    fun `an unrecognised file fails loudly and nothing is written`() {
        files.file = PickedImportFile("notes.txt", "hello,world\n1,2\n".encodeToByteArray())
        val vm = viewModel()

        vm.onEvent(DataImportIntent.Pick)

        assertEquals(ImportOutcome.Failed(ImportError.UNRECOGNISED), vm.uiState.value.outcome)
        assertNull(vm.uiState.value.review)
        assertEquals(0, importer.imports)
    }

    @Test
    fun `a file that cannot be read is reported`() {
        files.fail = true
        val vm = viewModel()

        vm.onEvent(DataImportIntent.Pick)

        assertEquals(ImportOutcome.Failed(null), vm.uiState.value.outcome)
        assertEquals(0, importer.imports)
    }

    @Test
    fun `cancelling the preview discards the file`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        vm.onEvent(DataImportIntent.Cancel)

        assertNull(vm.uiState.value.review)
        assertEquals(0, importer.imports)
    }

    @Test
    fun `confirming imports with the chosen options and mappings`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)
        vm.onEvent(DataImportIntent.SetUnit(ImportWeightUnit.LB))
        vm.onEvent(DataImportIntent.OpenMapping("cable fly"))
        assertEquals("fly", vm.uiState.value.search?.results?.single()?.exerciseId)
        vm.onEvent(DataImportIntent.MapTo(MappedExercise("fly", "Fly")))
        assertNull(vm.uiState.value.search)

        vm.onEvent(DataImportIntent.Confirm)

        val options = assertNotNull(importer.lastOptions)
        assertEquals(ImportWeightUnit.LB, options.weightUnit)
        assertEquals(mapOf("cable fly" to ExerciseDecision.MapTo("fly")), options.decisions)
        assertIs<ImportOutcome.Done>(vm.uiState.value.outcome)
        assertNull(vm.uiState.value.review)
    }

    @Test
    fun `creating a custom exercise is the default and can be restored after mapping`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)
        vm.onEvent(DataImportIntent.OpenMapping("cable fly"))
        vm.onEvent(DataImportIntent.MapTo(MappedExercise("fly", "Fly")))

        vm.onEvent(DataImportIntent.CreateCustom("cable fly"))
        vm.onEvent(DataImportIntent.Confirm)

        assertTrue(importer.lastOptions?.decisions.orEmpty().isEmpty())
    }

    @Test
    fun `a high confidence suggestion is pre-selected and the user can still create a custom exercise`() {
        importer.unmatched = listOf(
            UnmatchedExercise("bench pres", "Bench Pres", 3, 1, suggestion("bench-press", SuggestionConfidence.HIGH)),
            UnmatchedExercise("seated leg press", "Seated Leg Press", 2, 1, suggestion("leg-press", SuggestionConfidence.MEDIUM)),
            UnmatchedExercise("zumba", "Zumba", 1, 1),
        )
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        val review = assertNotNull(vm.uiState.value.review)
        // Only the high one is selected; the medium one and the name without a suggestion stay "create".
        assertEquals(mapOf("bench pres" to MappedExercise("bench-press", "Bench Press")), review.mapped)

        vm.onEvent(DataImportIntent.CreateCustom("bench pres"))
        // Recomputing the preview (another policy) does not select it again.
        vm.onEvent(DataImportIntent.SetExisting(ExistingPolicy.REPLACE))
        assertTrue(vm.uiState.value.review!!.mapped.isEmpty())

        vm.onEvent(DataImportIntent.AcceptSuggestion("seated leg press"))
        vm.onEvent(DataImportIntent.AcceptSuggestion("bench pres"))
        vm.onEvent(DataImportIntent.AcceptSuggestion("zumba")) // no suggestion: nothing happens
        vm.onEvent(DataImportIntent.Confirm)
        assertEquals(
            mapOf(
                "seated leg press" to ExerciseDecision.MapTo("leg-press"),
                "bench pres" to ExerciseDecision.MapTo("bench-press"),
            ),
            importer.lastOptions?.decisions,
        )
    }

    @Test
    fun `a high suggestion is confirmed as is when the user does nothing`() {
        importer.unmatched = listOf(
            UnmatchedExercise("bench pres", "Bench Pres", 3, 1, suggestion("bench-press", SuggestionConfidence.HIGH)),
        )
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)
        vm.onEvent(DataImportIntent.Confirm)
        assertEquals(mapOf("bench pres" to ExerciseDecision.MapTo("bench-press")), importer.lastOptions?.decisions)
    }

    private fun suggestion(id: String, confidence: SuggestionConfidence) =
        ExerciseSuggestion(
            id,
            LocalizedText.same(if (id == "bench-press") "Bench Press" else "Leg Press"),
            confidence
        )

    @Test
    fun `a write failure is reported and the preview closes`() {
        importer.fail = true
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        vm.onEvent(DataImportIntent.Confirm)

        assertEquals(ImportOutcome.Failed(null), vm.uiState.value.outcome)
        assertNull(vm.uiState.value.review)
    }

    @Test
    fun `changing what to do with existing sessions recomputes the preview`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)
        val before = importer.previews

        vm.onEvent(DataImportIntent.SetExisting(ExistingPolicy.REPLACE))

        assertEquals(before + 1, importer.previews)
        assertEquals(ExistingPolicy.REPLACE, importer.lastPreviewOptions?.existing)
        assertEquals(ExistingPolicy.REPLACE, vm.uiState.value.review?.existing)
    }

    @Test
    fun `changing the unit does not recompute the preview`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)
        val before = importer.previews

        vm.onEvent(DataImportIntent.SetUnit(ImportWeightUnit.LB))

        assertEquals(before, importer.previews)
        assertEquals(ImportWeightUnit.LB, vm.uiState.value.review?.unit)
    }

    @Test
    fun `the mapping search starts from the name and falls back to its longest word`() {
        exercises = FakeExercises(matches = { !it.contains(" ") })
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        vm.onEvent(DataImportIntent.OpenMapping("cable fly"))

        assertEquals(listOf("Cable Fly", "Cable"), exercises.queries)
        assertEquals("Cable", vm.uiState.value.search?.query)
        assertEquals(1, vm.uiState.value.search?.results?.size)
    }

    @Test
    fun `typing shows the query at once and closing the search keeps the review`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)
        vm.onEvent(DataImportIntent.OpenMapping("cable fly"))

        vm.onEvent(DataImportIntent.Search("fl"))
        assertEquals("fl", vm.uiState.value.search?.query)
        vm.onEvent(DataImportIntent.CloseMapping)

        assertNull(vm.uiState.value.search)
        assertNotNull(vm.uiState.value.review)
        assertTrue(vm.uiState.value.review?.mapped.orEmpty().isEmpty())
    }

    @Test
    fun `an unknown name cannot be mapped`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        vm.onEvent(DataImportIntent.OpenMapping("not in the file"))

        assertNull(vm.uiState.value.search)
    }

    @Test
    fun `picking again while a preview is open does nothing`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Pick)

        vm.onEvent(DataImportIntent.Pick)

        assertEquals(1, files.picks)
    }

    @Test
    fun `confirming without a preview does nothing and the outcome can be dismissed`() {
        val vm = viewModel()
        vm.onEvent(DataImportIntent.Confirm)
        assertEquals(0, importer.imports)

        vm.onEvent(DataImportIntent.Pick)
        vm.onEvent(DataImportIntent.Confirm)
        vm.onEvent(DataImportIntent.DismissOutcome)

        assertNull(vm.uiState.value.outcome)
        assertEquals(1, importer.imports)
    }

    @Test
    fun `a strong file without units asks for one`() {
        files.file = PickedImportFile(
            "strong.csv",
            (
                "Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE\n" +
                    "2025-05-01 10:00:00,W,1h,Squat,1,100,5,0,0,,,\n"
                ).encodeToByteArray(),
        )
        val vm = viewModel()

        vm.onEvent(DataImportIntent.Pick)

        assertEquals(true, vm.uiState.value.review?.preview?.needsUnitChoice)
    }
}
