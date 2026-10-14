package es.sebas1705.ironlog.feature.progress.ui.exercise

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.stats.PersonalRecords
import es.sebas1705.ironlog.domain.usecases.stats.Stats
import es.sebas1705.ironlog.feature.progress.model.ExerciseCharts
import es.sebas1705.ironlog.feature.progress.model.ExerciseLookup
import es.sebas1705.ironlog.feature.progress.model.ProgressMath
import es.sebas1705.ironlog.feature.progress.model.VariantInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal data class ExerciseProgressState(
    val loading: Boolean = true,
    /** One variant per exercise with history (its most recent one), most recently trained first. */
    val exercises: List<VariantInfo> = emptyList(),
    val selectedExerciseId: String? = null,
    /** Variants of the selected exercise with history, most recent first. */
    val variants: List<VariantInfo> = emptyList(),
    val selectedVariantId: String? = null,
    val charts: ExerciseCharts = ExerciseCharts(),
    val records: PersonalRecords? = null,
    val unit: WeightUnit = WeightUnit.KG,
) : MVIBaseState {
    val selectedExercise: VariantInfo? get() = exercises.firstOrNull { it.exerciseId == selectedExerciseId }
    val selectedVariant: VariantInfo? get() = variants.firstOrNull { it.variantId == selectedVariantId }
}

internal sealed interface ExerciseProgressIntent : MVIBaseIntent {
    data class SelectExercise(val exerciseId: String) : ExerciseProgressIntent

    data class SelectVariant(val variantId: String) : ExerciseProgressIntent
}

/**
 * Per-exercise progress: charts of top-set load, estimated 1RM and volume, plus the PR list. History is
 * kept per variant (spec §3.1), so the charts show one variant of the selected exercise at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ExerciseProgressViewModel(
    private val sessions: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settings: SettingsRepository,
) : MVIBaseViewModel<ExerciseProgressState, ExerciseProgressIntent>(ExerciseProgressState()) {

    private val lookup = ExerciseLookup(exerciseRepository)
    private val selectedExercise = MutableStateFlow<String?>(null)
    private val selectedVariant = MutableStateFlow<String?>(null)

    init {
        execute { observe() }
    }

    override fun intentHandler(intent: ExerciseProgressIntent): Job = execute {
        when (intent) {
            is ExerciseProgressIntent.SelectExercise -> {
                selectedVariant.value = null
                selectedExercise.value = intent.exerciseId
            }
            is ExerciseProgressIntent.SelectVariant -> selectedVariant.value = intent.variantId
        }
    }

    private fun exercisesWithHistory(): Flow<List<VariantInfo>> = sessions.observeHistory().map { history ->
        val variantIds = history.sortedByDescending {
            it.startedAt
        }.flatMap { s -> s.exercises.map { it.variantId } }.distinct()
        val infos = lookup.resolve(variantIds)
        variantIds.mapNotNull { infos[it] }.distinctBy { it.exerciseId }
    }

    private suspend fun observe() {
        val exercises = exercisesWithHistory()
        val effectiveExercise = combine(exercises, selectedExercise) { options, selected ->
            selected?.takeIf { id -> options.any { it.exerciseId == id } } ?: options.firstOrNull()?.exerciseId
        }.distinctUntilChanged()
        val history: Flow<Pair<String?, List<ExerciseHistoryEntry>>> = effectiveExercise.flatMapLatest { id ->
            if (id == null) {
                flowOf(null to emptyList())
            } else {
                exerciseRepository.observeExerciseHistory(id).map { id to it }
            }
        }
        combine(
            exercises,
            history,
            selectedVariant,
            settings.settings
        ) { options, (exerciseId, entries), variantId, userSettings ->
            buildState(options, exerciseId, entries, variantId, userSettings.unit)
        }.collect { next -> updateState { next } }
    }

    private suspend fun buildState(
        options: List<VariantInfo>,
        exerciseId: String?,
        entries: List<ExerciseHistoryEntry>,
        variantId: String?,
        unit: WeightUnit,
    ): ExerciseProgressState {
        val variantIds = entries.sortedByDescending { it.sessionStartedAt }.map { it.exercise.variantId }.distinct()
        val infos = lookup.resolve(variantIds)
        val variants = variantIds.mapNotNull { infos[it] }
        val variant = variantId?.takeIf { id -> variants.any { it.variantId == id } } ?: variants.firstOrNull()?.variantId
        val variantHistory = entries.filter { it.exercise.variantId == variant }
        return ExerciseProgressState(
            loading = false,
            exercises = options,
            selectedExerciseId = exerciseId,
            variants = variants,
            selectedVariantId = variant,
            charts = ProgressMath.exerciseCharts(variantHistory),
            records = variantHistory.takeIf { it.isNotEmpty() }?.let { Stats.personalRecords(it) },
            unit = unit,
        )
    }
}
