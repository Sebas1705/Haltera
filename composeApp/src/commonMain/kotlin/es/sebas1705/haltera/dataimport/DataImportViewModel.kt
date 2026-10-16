package es.sebas1705.haltera.dataimport

import co.touchlab.kermit.Logger
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.core.units.appLanguage
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.importer.ExerciseDecision
import es.sebas1705.haltera.data.repository.importer.ExistingPolicy
import es.sebas1705.haltera.data.repository.importer.HistoryFileParser
import es.sebas1705.haltera.data.repository.importer.HistoryImporter
import es.sebas1705.haltera.data.repository.importer.ImportError
import es.sebas1705.haltera.data.repository.importer.ImportFormatException
import es.sebas1705.haltera.data.repository.importer.ImportOptions
import es.sebas1705.haltera.data.repository.importer.ImportPreview
import es.sebas1705.haltera.data.repository.importer.ImportSummary
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import es.sebas1705.haltera.data.repository.importer.ParseOptions
import es.sebas1705.haltera.data.repository.importer.ParsedImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/** An existing exercise the user chose for a name of the file. */
data class MappedExercise(val exerciseId: String, val name: String)

/** The preview the user confirms: what the file holds and what the user decided so far. */
data class ImportReview(
    val fileName: String,
    val preview: ImportPreview,
    val unit: ImportWeightUnit,
    val existing: ExistingPolicy,
    /** By normalised name; a name without an entry is created as a custom exercise. */
    val mapped: Map<String, MappedExercise> = emptyMap(),
    val working: Boolean = false,
)

/** The "Map to..." search for one unmatched name. */
data class MappingSearch(
    val key: String,
    val name: String,
    val query: String = "",
    val results: List<MappedExercise> = emptyList()
)

sealed interface ImportOutcome {
    data class Done(val summary: ImportSummary) : ImportOutcome

    /** [error] is null for a file that could not be read at all. */
    data class Failed(val error: ImportError?) : ImportOutcome
}

data class DataImportState(
    val busy: Boolean = false,
    val review: ImportReview? = null,
    val search: MappingSearch? = null,
    val outcome: ImportOutcome? = null,
) : MVIBaseState

sealed interface DataImportIntent : MVIBaseIntent {
    data object Pick : DataImportIntent
    data class SetUnit(val unit: ImportWeightUnit) : DataImportIntent
    data class SetExisting(val policy: ExistingPolicy) : DataImportIntent
    data class OpenMapping(val key: String) : DataImportIntent
    data class Search(val query: String) : DataImportIntent
    data class MapTo(val exercise: MappedExercise) : DataImportIntent
    data class CreateCustom(val key: String) : DataImportIntent
    data object CloseMapping : DataImportIntent
    data object Confirm : DataImportIntent
    data object Cancel : DataImportIntent
    data object DismissOutcome : DataImportIntent
}

/**
 * Settings → Data → Import (docs/IMPORT-FORMATS.md): pick a file, read it, show what it holds, and write it only
 * when the user confirms. The parsed file is kept here, not in the state, so a large import is not compared on
 * every state update.
 */
class DataImportViewModel(
    private val importer: HistoryImporter,
    private val files: ImportFileBridge,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MVIBaseViewModel<DataImportState, DataImportIntent>(DataImportState()) {

    private var parsed: ParsedImport? = null

    override fun intentHandler(intent: DataImportIntent): Job = when (intent) {
        DataImportIntent.Pick -> execute { pick() }
        is DataImportIntent.SetUnit -> execute { change { it.copy(unit = intent.unit) } }
        is DataImportIntent.SetExisting -> execute { change(refresh = true) { it.copy(existing = intent.policy) } }
        is DataImportIntent.OpenMapping -> execute { openMapping(intent.key) }
        is DataImportIntent.Search -> execute { search(intent.query) }
        is DataImportIntent.MapTo -> execute { map(intent.exercise) }
        is DataImportIntent.CreateCustom -> execute { change { it.copy(mapped = it.mapped - intent.key) } }
        DataImportIntent.CloseMapping -> execute { updateState { it.copy(search = null) } }
        DataImportIntent.Confirm -> execute { confirm() }
        DataImportIntent.Cancel -> execute { discard() }
        DataImportIntent.DismissOutcome -> execute { updateState { it.copy(outcome = null) } }
    }

    private suspend fun pick() {
        if (currentState.busy || currentState.review != null) return
        updateState { it.copy(busy = true, outcome = null) }
        try {
            val file = files.pick()
            if (file == null) {
                updateState { it.copy(busy = false) }
                return
            }
            val zone = TimeZone.currentSystemDefault()
            val result = withContext(parseDispatcher) { HistoryFileParser.parse(file.bytes, ParseOptions(zone)) }
            val unit = if (settings.settings.value.unit == WeightUnit.LB) ImportWeightUnit.LB else ImportWeightUnit.KG
            val preview = importer.preview(result, ImportOptions(unit, ExistingPolicy.SKIP))
            parsed = result
            updateState { it.copy(busy = false, review = ImportReview(file.name, preview, unit, ExistingPolicy.SKIP)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImportFormatException) {
            Logger.w(e) { "Import file not accepted: ${e.error}" }
            updateState { it.copy(busy = false, outcome = ImportOutcome.Failed(e.error)) }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Import file could not be read" }
            updateState { it.copy(busy = false, outcome = ImportOutcome.Failed(null)) }
        }
    }

    /** Applies [edit] to the review; with [refresh] the preview is computed again (it depends on the policy). */
    private suspend fun change(refresh: Boolean = false, edit: (ImportReview) -> ImportReview) {
        val review = currentState.review ?: return
        val next = edit(review)
        val file = parsed
        val preview = if (refresh && file != null) importer.preview(file, options(next)) else next.preview
        updateState { state -> state.copy(review = state.review?.let { edit(it).copy(preview = preview) }) }
    }

    private fun options(review: ImportReview) = ImportOptions(
        weightUnit = review.unit,
        existing = review.existing,
        decisions = review.mapped.mapValues { ExerciseDecision.MapTo(it.value.exerciseId) },
    )

    private suspend fun openMapping(key: String) {
        val name = currentState.review?.preview?.unmatched?.firstOrNull { it.key == key }?.name ?: return
        updateState { it.copy(search = MappingSearch(key, name)) }
        // Start from the name without what the exporters add in brackets or quotes; else its longest word.
        val base = name.replace(Regex("""\(.*?\)|".*?""""), " ").trim().replace(Regex("""\s+"""), " ")
        search(base)
        if (currentState.search?.results.isNullOrEmpty()) {
            base.split(' ').maxByOrNull { it.length }?.takeIf { it.isNotEmpty() && it != base }?.let { search(it) }
        }
    }

    private suspend fun search(query: String) {
        val current = currentState.search ?: return
        // The text field shows the query at once; the results follow and are dropped if the query moved on.
        updateState { state -> state.copy(search = state.search?.copy(query = query)) }
        val results = exercises.search(ExerciseFilter(query = query), appLanguage(), MAX_RESULTS).first()
            .map { MappedExercise(it.id, it.name.get(appLanguage())) }
        updateState { state ->
            val search = state.search
            if (search != null && search.key == current.key && search.query == query) {
                state.copy(search = search.copy(results = results))
            } else {
                state
            }
        }
    }

    private suspend fun map(exercise: MappedExercise) {
        val key = currentState.search?.key ?: return
        change { it.copy(mapped = it.mapped + (key to exercise)) }
        updateState { it.copy(search = null) }
    }

    private suspend fun confirm() {
        val review = currentState.review ?: return
        val file = parsed ?: return
        if (review.working) return
        updateState { state -> state.copy(review = state.review?.copy(working = true)) }
        val outcome = try {
            ImportOutcome.Done(importer.import(file, options(review)))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Import failed" }
            ImportOutcome.Failed(null)
        }
        parsed = null
        updateState { it.copy(review = null, search = null, outcome = outcome) }
    }

    private fun discard() {
        parsed = null
        updateState { it.copy(review = null, search = null, busy = false) }
    }

    private companion object {
        const val MAX_RESULTS = 30
    }
}
