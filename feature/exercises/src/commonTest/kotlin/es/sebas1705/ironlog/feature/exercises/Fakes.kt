package es.sebas1705.ironlog.feature.exercises

import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.datastore.UserSettings
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedSteps
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.NewExercise
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [ExerciseRepository] that filters like the SQL one (minus accent folding). */
class FakeExerciseRepository(initial: List<Exercise> = emptyList()) : ExerciseRepository {
    val exercises = MutableStateFlow(initial)
    val history = MutableStateFlow<Map<String, List<ExerciseHistoryEntry>>>(emptyMap())
    val searches = mutableListOf<Pair<ExerciseFilter, String>>()
    val created = mutableListOf<NewExercise>()
    var failSearch = false
    var failCreate = false

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> {
        searches += filter to language
        if (failSearch) return flow { error("search failed") }
        return exercises.map { all ->
            all.filter { it.matches(filter) }.sortedBy { it.name.get(language) }.take(limit)
        }
    }

    override fun observeExercise(id: String): Flow<Exercise?> = exercises.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun getExercise(id: String): Exercise? = exercises.value.firstOrNull { it.id == id }

    override suspend fun getVariant(id: String): ExerciseVariant? =
        exercises.value.flatMap { it.variants }.firstOrNull { it.id == id }

    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> =
        exercises.value.flatMap { it.variants }.filter { it.id in ids }

    override suspend fun createCustom(input: NewExercise): Exercise {
        if (failCreate) error("disk full")
        created += input
        val id = "user-${created.size}"
        val exercise = Exercise(
            id = id,
            name = input.name,
            category = input.category,
            level = input.level,
            primaryMuscles = input.primaryMuscles,
            secondaryMuscles = input.secondaryMuscles,
            instructions = input.instructions,
            trackingType = input.trackingType,
            source = ExerciseSource.USER,
            variants = input.variants.mapIndexed { i, v ->
                ExerciseVariant(
                    "$id.${v.equipment.name.lowercase()}",
                    id,
                    v.equipment,
                    v.loadType,
                    position = i,
                    source = ExerciseSource.USER
                )
            },
        )
        exercises.update { it + exercise }
        return exercise
    }

    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        history.map { it[variantId].orEmpty().sortedByDescending { e -> e.sessionStartedAt }.take(limit) }

    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        history.map { all ->
            all.values.flatten().filter { it.exercise.variantId.startsWith("$exerciseId.") }
                .sortedByDescending { it.sessionStartedAt }
                .take(limit)
        }

    private fun Exercise.matches(filter: ExerciseFilter): Boolean {
        val q = filter.query.trim().lowercase()
        return (q.isEmpty() || name.en.lowercase().contains(q) || name.es.lowercase().contains(q)) &&
            (filter.category == null || category == filter.category) &&
            (filter.muscle == null || filter.muscle in primaryMuscles) &&
            (filter.equipment == null || variants.any { !it.deprecated && it.equipment == filter.equipment })
    }
}

class FakeSettingsRepository(unit: WeightUnit = WeightUnit.KG) : SettingsRepository {
    private val state = MutableStateFlow(UserSettings.defaultsFor(unit))
    override val settings: StateFlow<UserSettings> = state

    override fun setUnit(unit: WeightUnit, resetEquipment: Boolean) = state.update { it.copy(unit = unit) }
    override fun setBarWeight(grams: Long) = state.update { it.copy(barWeightGrams = grams) }
    override fun setAvailablePlates(grams: List<Long>) = state.update { it.copy(availablePlatesGrams = grams) }
    override fun setKeepScreenOn(enabled: Boolean) = state.update { it.copy(keepScreenOn = enabled) }
    override fun setRestSound(enabled: Boolean) = state.update { it.copy(restSound = enabled) }
    override fun setRestVibration(enabled: Boolean) = state.update { it.copy(restVibration = enabled) }
}

// Test data.

fun variant(
    exerciseId: String,
    equipment: Equipment,
    loadType: LoadType = LoadType.BARBELL,
    frames: List<String> = emptyList(),
    deprecated: Boolean = false,
    instructions: LocalizedSteps? = null,
    position: Int = 0,
) = ExerciseVariant(
    id = "$exerciseId.${equipment.name.lowercase()}",
    exerciseId = exerciseId,
    equipment = equipment,
    loadType = loadType,
    frames = frames,
    deprecated = deprecated,
    instructions = instructions,
    position = position,
)

@Suppress("LongParameterList")
fun exercise(
    id: String,
    en: String,
    es: String = en,
    category: ExerciseCategory = ExerciseCategory.STRENGTH,
    primary: List<Muscle> = listOf(Muscle.CHEST),
    secondary: List<Muscle> = emptyList(),
    tracking: TrackingType = TrackingType.WEIGHT_REPS,
    source: ExerciseSource = ExerciseSource.CATALOG,
    steps: LocalizedSteps = LocalizedSteps.EMPTY,
    variants: List<ExerciseVariant> = listOf(variant(id, Equipment.BARBELL)),
) = Exercise(
    id = id,
    name = LocalizedText(en = en, es = es),
    category = category,
    level = Level.INTERMEDIATE,
    primaryMuscles = primary,
    secondaryMuscles = secondary,
    instructions = steps,
    trackingType = tracking,
    source = source,
    variants = variants,
)

fun entry(sessionId: String, at: Long, variantId: String, vararg sets: Pair<Long?, Int?>) = ExerciseHistoryEntry(
    sessionId = sessionId,
    sessionStartedAt = at,
    exercise = SessionExercise(
        id = "se-$sessionId",
        sessionId = sessionId,
        position = 0,
        variantId = variantId,
        sets = sets.mapIndexed { i, (load, reps) ->
            SetLog(
                id = "set-$sessionId-$i",
                sessionExerciseId = "se-$sessionId",
                position = i,
                kind = SetKind.WORKING,
                reps = reps,
                loadGrams = load,
                completed = true,
            )
        },
    ),
)
