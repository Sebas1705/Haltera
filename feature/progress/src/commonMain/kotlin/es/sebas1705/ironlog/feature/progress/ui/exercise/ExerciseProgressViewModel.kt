package es.sebas1705.ironlog.feature.progress.ui.exercise

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.stats.ExerciseProgression
import es.sebas1705.ironlog.domain.usecases.stats.ExerciseRecords
import es.sebas1705.ironlog.domain.usecases.stats.OneRmTrend
import es.sebas1705.ironlog.domain.usecases.stats.ProgressionRow
import es.sebas1705.ironlog.domain.usecases.stats.RepRange
import es.sebas1705.ironlog.domain.usecases.stats.records
import es.sebas1705.ironlog.feature.progress.model.ChartPoint
import es.sebas1705.ironlog.feature.progress.model.ExerciseCharts
import es.sebas1705.ironlog.feature.progress.model.ExerciseLookup
import es.sebas1705.ironlog.feature.progress.model.ProgressMath
import es.sebas1705.ironlog.feature.progress.model.VariantInfo
import es.sebas1705.ironlog.feature.progress.model.bilateralSlots
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
    /** Progression table of the selected variant, latest first. */
    val rows: List<ProgressionRow> = emptyList(),
    val repRanges: Map<RepRange, Int> = emptyMap(),
    val trend: OneRmTrend? = null,
    val records: ExerciseRecords? = null,
    /** Every other variant with history, to compare with. */
    val compareOptions: List<VariantInfo> = emptyList(),
    val compareVariantId: String? = null,
    /** Estimated 1RM per session of the compared variant, oldest first. */
    val compareOneRm: List<ChartPoint> = emptyList(),
    val unit: WeightUnit = WeightUnit.KG,
) : MVIBaseState {
    val selectedExercise: VariantInfo? get() = exercises.firstOrNull { it.exerciseId == selectedExerciseId }
    val selectedVariant: VariantInfo? get() = variants.firstOrNull { it.variantId == selectedVariantId }
    val compareVariant: VariantInfo? get() = compareOptions.firstOrNull { it.variantId == compareVariantId }
}

internal sealed interface ExerciseProgressIntent : MVIBaseIntent {
    data class SelectExercise(val exerciseId: String) : ExerciseProgressIntent

    data class SelectVariant(val variantId: String) : ExerciseProgressIntent

    /** Another variant (of this or another exercise) on the e1RM chart; null removes it. */
    data class Compare(val variantId: String?) : ExerciseProgressIntent
}

/**
 * Per-exercise progress: charts of top set, estimated 1RM (with its trend and an optional comparison)
 * and volume, the progression table, rep ranges and records. History is kept per variant (spec §3.1),
 * so everything shows one variant of the selected exercise at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ExerciseProgressViewModel(
    private val sessions: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settings: SettingsRepository,
    private val routines: RoutineRepository,
) : MVIBaseViewModel<ExerciseProgressState, ExerciseProgressIntent>(ExerciseProgressState()) {

    private val lookup = ExerciseLookup(exerciseRepository)
    private val selectedExercise = MutableStateFlow<String?>(null)
    private val selectedVariant = MutableStateFlow<String?>(null)
    private val compared = MutableStateFlow<String?>(null)

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
            is ExerciseProgressIntent.Compare -> compared.value = intent.variantId
        }
    }

    /** Every variant with finished history, most recently trained first. */
    private fun variantsWithHistory(): Flow<List<VariantInfo>> = sessions.observeHistory().map { history ->
        val variantIds = history.sortedByDescending {
            it.startedAt
        }.flatMap { s -> s.exercises.map { it.variantId } }.distinct()
        val infos = lookup.resolve(variantIds)
        variantIds.mapNotNull { infos[it] }
    }

    private suspend fun observe() {
        val allVariants = variantsWithHistory()
        val exercises = allVariants.map { list -> list.distinctBy { it.exerciseId } }
        val effectiveExercise = combine(exercises, selectedExercise) { options, selected ->
            selected?.takeIf { id -> options.any { it.exerciseId == id } } ?: options.firstOrNull()?.exerciseId
        }.distinctUntilChanged()
        val history: Flow<Pair<String?, List<ExerciseHistoryEntry>>> = effectiveExercise.flatMapLatest { id ->
            if (id == null) {
                flowOf(
                    null to emptyList()
                )
            } else {
                exerciseRepository.observeExerciseHistory(id).map { id to it }
            }
        }
        val compareHistory: Flow<List<ExerciseHistoryEntry>> = compared.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else exerciseRepository.observeVariantHistory(id)
        }
        val base =
            combine(
                allVariants,
                history,
                selectedVariant,
                routines.observeRoutines()
            ) { all, (exerciseId, entries), variantId, list ->
                Inputs(all, exerciseId, entries, variantId, list.bilateralSlots())
            }
        combine(base, compareHistory, compared, settings.settings) { inputs, compareEntries, compareId, userSettings ->
            buildState(inputs, compareId, compareEntries, userSettings.unit)
        }.collect { next -> updateState { next } }
    }

    private class Inputs(
        val all: List<VariantInfo>,
        val exerciseId: String?,
        val entries: List<ExerciseHistoryEntry>,
        val variantId: String?,
        val bilateral: Set<String>,
    )

    private suspend fun buildState(
        inputs: Inputs,
        compareId: String?,
        compareEntries: List<ExerciseHistoryEntry>,
        unit: WeightUnit,
    ): ExerciseProgressState {
        val variantIds = inputs.entries.sortedByDescending { it.sessionStartedAt }.map { it.exercise.variantId }.distinct()
        val infos = lookup.resolve(variantIds)
        val variants = variantIds.mapNotNull { infos[it] }
        val variant = inputs.variantId?.takeIf { id ->
            variants.any {
                it.variantId == id
            }
        } ?: variants.firstOrNull()?.variantId
        val variantHistory = inputs.entries.filter { it.exercise.variantId == variant }
        val rows = ExerciseProgression.table(variantHistory, inputs.bilateral)
        val options = inputs.all.filter { it.variantId != variant }
        val compareVariant = compareId?.takeIf { id -> options.any { it.variantId == id } }
        return ExerciseProgressState(
            loading = false,
            exercises = inputs.all.distinctBy { it.exerciseId },
            selectedExerciseId = inputs.exerciseId,
            variants = variants,
            selectedVariantId = variant,
            charts = ProgressMath.exerciseCharts(rows),
            rows = rows,
            repRanges = ExerciseProgression.repRanges(variantHistory),
            trend = ExerciseProgression.oneRmTrend(rows),
            records = variantHistory.takeIf { it.isNotEmpty() }?.let {
                ExerciseProgression.records(
                    it,
                    inputs.bilateral
                )
            },
            compareOptions = options,
            compareVariantId = compareVariant,
            compareOneRm = if (compareVariant == null) {
                emptyList()
            } else {
                ProgressMath.exerciseCharts(ExerciseProgression.table(compareEntries, inputs.bilateral)).oneRm
            },
            unit = unit,
        )
    }
}
