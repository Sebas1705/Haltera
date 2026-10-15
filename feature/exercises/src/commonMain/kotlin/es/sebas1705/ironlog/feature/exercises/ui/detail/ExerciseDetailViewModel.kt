package es.sebas1705.ironlog.feature.exercises.ui.detail

import androidx.compose.runtime.Immutable
import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.core.units.formatVolume
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.domain.usecases.stats.PersonalRecords
import es.sebas1705.ironlog.domain.usecases.stats.Record
import es.sebas1705.ironlog.domain.usecases.stats.Stats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Immutable
data class VariantOption(val id: String, val equipment: Equipment, val name: String?)

/** The exercise as shown, localised, for the selected variant. */
@Immutable
data class ExerciseDetail(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val level: Level,
    val trackingType: TrackingType,
    val custom: Boolean,
    val primaryMuscles: List<Muscle>,
    val secondaryMuscles: List<Muscle>,
    /** Selectable variants: the active ones (all of them if every variant is deprecated). */
    val variants: List<VariantOption>,
    val selectedVariantId: String,
    val loadType: LoadType,
    /** Frame paths of the selected variant (relative to free-exercise-db's `exercises` folder). */
    val frames: List<String>,
    /** Instruction steps of the selected variant, falling back to the exercise's. */
    val steps: List<String>,
)

/** A load to show: a weight already formatted in the user's unit, or a band/stack level. */
@Immutable
data class LoadText(val value: String, val isLevel: Boolean)

/** The best set of a session: heaviest load, then most reps, then longest duration and distance. */
@Immutable
data class TopSet(val load: LoadText?, val reps: Int?, val durationS: Int?, val distanceM: Int?)

@Immutable
data class HistoryRow(val sessionId: String, val at: Long, val sets: Int, val topSet: TopSet?)

@Immutable
data class RecordRow(val kind: RecordRowKind, val load: LoadText?, val count: Long?, val at: Long)

enum class RecordRowKind { HEAVIEST_LOAD, ESTIMATED_ONE_RM, MOST_REPS, SESSION_VOLUME }

@Immutable
data class ExerciseDetailState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val error: Boolean = false,
    val detail: ExerciseDetail? = null,
    val unit: WeightUnit = WeightUnit.KG,
    /** Latest sessions of the selected variant, newest first. */
    val history: List<HistoryRow> = emptyList(),
    /** Records of the selected variant that apply to its tracking and load type. */
    val records: List<RecordRow> = emptyList(),
) : MVIBaseState

sealed interface ExerciseDetailIntent : MVIBaseIntent {
    data class SetLanguage(val language: String) : ExerciseDetailIntent
    data class SelectVariant(val variantId: String) : ExerciseDetailIntent
}

/** Detail of one exercise: media, variants, instructions, muscles, history and PRs (spec §7). */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModel(
    private val exerciseId: String,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MVIBaseViewModel<ExerciseDetailState, ExerciseDetailIntent>(ExerciseDetailState()) {

    private val language = MutableStateFlow<String?>(null)
    private val selectedVariant = MutableStateFlow<String?>(null)

    init {
        observeExercise()
        observeHistory()
    }

    override fun intentHandler(intent: ExerciseDetailIntent): Job = execute {
        when (intent) {
            is ExerciseDetailIntent.SetLanguage -> language.value = intent.language
            is ExerciseDetailIntent.SelectVariant -> selectedVariant.value = intent.variantId
        }
    }

    private fun observeExercise() = execute {
        combine(
            exercises.observeExercise(exerciseId),
            language.filterNotNull(),
            selectedVariant,
            // The most recently trained variant is the default, so its records and history show first.
            exercises.observeExerciseHistory(exerciseId, limit = 1)
                .map { it.firstOrNull()?.exercise?.variantId }
                .distinctUntilChanged(),
        ) { exercise, lang, sel, recent ->
            exercise?.toDetail(lang, sel, recent)
        }
            .flowOn(dispatcher)
            .catch { e ->
                if (e is CancellationException) throw e
                updateState { it.copy(loading = false, error = true) }
            }
            .collect { detail ->
                updateState { it.copy(loading = false, notFound = detail == null, detail = detail) }
            }
    }

    private fun observeHistory() = execute {
        uiState.map { it.detail?.let { d -> HistoryKey(d.selectedVariantId, d.loadType, d.trackingType) } }
            .filterNotNull()
            .distinctUntilChanged()
            .flatMapLatest { key ->
                val units = settings.settings.map { it.unit }.distinctUntilChanged()
                combine(exercises.observeVariantHistory(key.variantId), units) { history, unit ->
                    HistoryMapping(
                        unit = unit,
                        history = history.take(HISTORY_ROWS).map { it.toRow(key.loadType, unit) },
                        records = Stats.personalRecords(history).toRows(key.tracking, key.loadType, unit),
                    )
                }.flowOn(dispatcher)
            }
            .catch { e ->
                if (e is CancellationException) throw e
                updateState { it.copy(history = emptyList(), records = emptyList()) }
            }
            .collect { m -> updateState { it.copy(unit = m.unit, history = m.history, records = m.records) } }
    }

    private data class HistoryKey(val variantId: String, val loadType: LoadType, val tracking: TrackingType)

    private data class HistoryMapping(val unit: WeightUnit, val history: List<HistoryRow>, val records: List<RecordRow>)

    companion object {
        /** Sessions listed in the history section; records use the whole history. */
        const val HISTORY_ROWS = 10
    }
}

internal fun Exercise.toDetail(
    language: String,
    selectedVariantId: String?,
    recentVariantId: String? = null,
): ExerciseDetail {
    val selectable = variants.filterNot { it.deprecated }.ifEmpty { variants }
    // A deprecated variant chosen earlier stays selectable so its history is still reachable.
    val selected = variants.firstOrNull { it.id == selectedVariantId }
        ?: variants.firstOrNull { it.id == recentVariantId }
        ?: selectable.firstOrNull()
    val options = (selectable + listOfNotNull(selected)).distinctBy { it.id }
    return ExerciseDetail(
        id = id,
        name = name.get(language),
        category = category,
        level = level,
        trackingType = trackingType,
        custom = source == ExerciseSource.USER,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        variants = options.map { VariantOption(it.id, it.equipment, it.name?.get(language)) },
        selectedVariantId = selected?.id.orEmpty(),
        loadType = selected?.loadType ?: LoadType.NONE,
        frames = selected?.frames.orEmpty(),
        steps = (selected?.instructions?.get(language)?.takeIf { it.isNotEmpty() } ?: instructions.get(language))
            .map { it.trim() }
            .filter { it.isNotEmpty() },
    )
}

/** Loads of bands are levels; everything else is grams. */
internal fun loadText(value: Long, loadType: LoadType, unit: WeightUnit): LoadText =
    if (loadType == LoadType.BAND) {
        LoadText(
            value.toString(),
            isLevel = true
        )
    } else {
        LoadText(Weights.format(value, unit, decimals = LOAD_DECIMALS), isLevel = false)
    }

internal fun ExerciseHistoryEntry.toRow(loadType: LoadType, unit: WeightUnit): HistoryRow {
    val counted = Stats.countedSets(exercise.sets)
    return HistoryRow(
        sessionId = sessionId,
        at = sessionStartedAt,
        sets = counted.size,
        topSet = counted.maxWithOrNull(TOP_SET_ORDER)?.let { set ->
            TopSet(
                load = set.loadGrams?.let { loadText(it, loadType, unit) },
                reps = set.reps,
                durationS = set.durationS,
                distanceM = set.distanceM,
            )
        },
    )
}

private val TOP_SET_ORDER: Comparator<SetLog> = compareBy<SetLog>(
    { it.loadGrams ?: Long.MIN_VALUE },
    { it.reps ?: 0 },
    { it.durationS ?: 0 },
    { it.distanceM ?: 0 },
)

internal fun PersonalRecords.toRows(tracking: TrackingType, loadType: LoadType, unit: WeightUnit): List<RecordRow> {
    val weighted = tracking.tracksLoad && loadType != LoadType.NONE
    val inGrams = weighted && loadType != LoadType.BAND
    fun Record.asLoad(kind: RecordRowKind) = RecordRow(kind, loadText(value, loadType, unit), null, at)
    return buildList {
        if (weighted) heaviestLoad?.let { add(it.asLoad(RecordRowKind.HEAVIEST_LOAD)) }
        if (inGrams && tracking.tracksReps) bestEstimatedOneRm?.let { add(it.asLoad(RecordRowKind.ESTIMATED_ONE_RM)) }
        if (tracking.tracksReps) mostReps?.let { add(RecordRow(RecordRowKind.MOST_REPS, null, it.value, it.at)) }
        if (inGrams && tracking.tracksReps) {
            bestSessionVolume?.let {
                add(
                    RecordRow(
                        RecordRowKind.SESSION_VOLUME,
                        LoadText(Weights.formatVolume(it.value, unit), false),
                        null,
                        it.at
                    )
                )
            }
        }
    }
}

/** Loads show at most one decimal, like the rest of the app ("107,7 kg"). */
private const val LOAD_DECIMALS = 1
