package es.sebas1705.haltera.feature.routines

import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineOrigin
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.ExerciseRepository
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.feature.routines.model.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [RoutineRepository] that keeps positions contiguous like the SQL one and records calls. */
@Suppress("TooManyFunctions")
class FakeRoutineRepository(
    private val templates: List<RoutineTemplate> = emptyList(),
) : RoutineRepository {
    private val state = MutableStateFlow<List<Routine>>(emptyList())
    private var nextId = 0

    /** Every write, as "method(args)", in call order. */
    val calls = mutableListOf<String>()

    /** When set, every write throws it. */
    var failure: Exception? = null

    val routines: List<Routine> get() = state.value

    fun routine(id: String): Routine = state.value.first { it.id == id }

    private fun id(prefix: String) = "$prefix-${++nextId}"

    private fun write(call: String) {
        failure?.let { throw it }
        calls += call
    }

    private fun edit(routineId: String, change: (Routine) -> Routine) = state.update { list ->
        list.map { if (it.id == routineId) change(it) else it }
    }

    private fun editDays(routineId: String, change: (List<RoutineDay>) -> List<RoutineDay>) = edit(routineId) { r ->
        r.copy(days = change(r.days).mapIndexed { i, d -> d.copy(position = i) })
    }

    private fun routineOfDay(dayId: String) = state.value.first { r -> r.days.any { it.id == dayId } }

    private fun dayOfSlot(slotId: String) = state.value.flatMap { it.days }.first { d -> d.slots.any { it.id == slotId } }

    private fun editSlots(dayId: String, change: (List<RoutineSlot>) -> List<RoutineSlot>) {
        val routine = routineOfDay(dayId)
        editDays(routine.id) { days ->
            days.map { d ->
                if (d.id == dayId) d.copy(slots = change(d.slots).mapIndexed { i, s -> s.copy(position = i) }) else d
            }
        }
    }

    /** Adds a routine directly (test setup), returning it. */
    fun seed(routine: Routine): Routine {
        state.update { it + routine }
        return routine
    }

    override fun observeRoutines(): Flow<List<Routine>> =
        state.map { list -> list.sortedByDescending { it.isActive } }

    override fun observeRoutine(id: String): Flow<Routine?> = state.map { list -> list.firstOrNull { it.id == id } }

    override fun observeActive(): Flow<Routine?> = state.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun getRoutine(id: String): Routine? = state.value.firstOrNull { it.id == id }

    override suspend fun createRoutine(name: String, description: String): String {
        write("createRoutine($name)")
        val id = id("routine")
        state.update { it + Routine(id = id, name = name, description = description) }
        return id
    }

    override suspend fun updateRoutine(id: String, name: String, description: String) {
        write("updateRoutine($name, $description)")
        edit(id) { it.copy(name = name, description = description) }
    }

    override suspend fun deleteRoutine(id: String) {
        write("deleteRoutine($id)")
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setActive(id: String?) {
        write("setActive($id)")
        state.update { list -> list.map { it.copy(isActive = it.id == id) } }
    }

    override suspend fun setNextDayIndex(routineId: String, index: Int) {
        write("setNextDayIndex($index)")
        edit(routineId) { it.copy(nextDayIndex = index) }
    }

    override suspend fun setRestPolicy(routineId: String, policy: RestPolicy, appendRestDays: Int, restDayName: String) {
        val target = policy.normalized()
        write("setRestPolicy(${target.mode}, ${target.restDaysPerWeek}, ${target.restWeekdays}, $appendRestDays)")
        val wasFlexible = routine(routineId).restPolicy.isFlexible
        if (target.isFlexible && !wasFlexible) {
            editDays(routineId) { days ->
                days.filter { it.kind == DayKind.TRAINING || it.slots.isNotEmpty() }.map {
                    it.copy(
                        kind = DayKind.TRAINING
                    )
                }
            }
        } else if (!target.isFlexible && wasFlexible) {
            editDays(routineId) { days ->
                days + List(appendRestDays) {
                    RoutineDay(id = id("day"), routineId = routineId, position = 0, name = restDayName, kind = DayKind.REST)
                }
            }
        }
        edit(routineId) { it.copy(restPolicy = target) }
    }

    override suspend fun addDay(routineId: String, name: String, kind: DayKind): String {
        write("addDay($name, $kind)")
        val id = id("day")
        editDays(routineId) { it + RoutineDay(id = id, routineId = routineId, position = 0, name = name, kind = kind) }
        return id
    }

    override suspend fun updateDay(dayId: String, name: String, kind: DayKind) {
        write("updateDay($name, $kind)")
        editDays(
            routineOfDay(dayId).id
        ) { days -> days.map { if (it.id == dayId) it.copy(name = name, kind = kind) else it } }
    }

    override suspend fun deleteDay(dayId: String) {
        write("deleteDay($dayId)")
        editDays(routineOfDay(dayId).id) { days -> days.filterNot { it.id == dayId } }
    }

    override suspend fun moveDay(dayId: String, toPosition: Int) {
        write("moveDay($dayId, $toPosition)")
        editDays(routineOfDay(dayId).id) { days -> moved(days, days.indexOfFirst { it.id == dayId }, toPosition) }
    }

    override suspend fun addSlot(dayId: String, spec: SlotSpec): String {
        write("addSlot($dayId)")
        val id = id("slot")
        editSlots(dayId) { it + slotOf(id, dayId, spec) }
        return id
    }

    override suspend fun updateSlot(slotId: String, spec: SlotSpec) {
        write("updateSlot($slotId)")
        val day = dayOfSlot(slotId)
        editSlots(day.id) { slots -> slots.map { if (it.id == slotId) slotOf(slotId, day.id, spec) else it } }
    }

    override suspend fun deleteSlot(slotId: String) {
        write("deleteSlot($slotId)")
        editSlots(dayOfSlot(slotId).id) { slots -> slots.filterNot { it.id == slotId } }
    }

    override suspend fun moveSlot(slotId: String, toPosition: Int) {
        write("moveSlot($slotId, $toPosition)")
        editSlots(dayOfSlot(slotId).id) { slots -> moved(slots, slots.indexOfFirst { it.id == slotId }, toPosition) }
    }

    override fun templates(): List<RoutineTemplate> = templates

    override suspend fun copyTemplate(templateId: String, language: String): String {
        write("copyTemplate($templateId, $language)")
        val template = templates.first { it.id == templateId }
        val routineId = id("routine")
        val days = template.days.mapIndexed { index, day ->
            val dayId = id("day")
            RoutineDay(
                id = dayId,
                routineId = routineId,
                position = index,
                name = day.name.get(language),
                kind = day.kind,
                slots = day.slots.mapIndexed { i, spec -> slotOf(id("slot"), dayId, spec).copy(position = i) },
            )
        }
        state.update {
            it + Routine(
                id = routineId,
                name = template.name.get(language),
                description = template.description.get(language),
                days = days,
                origin = RoutineOrigin(template.id, template.version),
            )
        }
        return routineId
    }

    private fun <T> moved(items: List<T>, from: Int, to: Int): List<T> {
        if (from < 0) return items
        val item = items[from]
        return (items - item).toMutableList().apply { add(to.coerceIn(0, size), item) }
    }

    private fun slotOf(id: String, dayId: String, spec: SlotSpec) = RoutineSlot(
        id = id,
        dayId = dayId,
        position = 0,
        target = spec.target,
        sets = spec.sets,
        warmupSets = spec.warmupSets,
        prescription = spec.prescription,
        restBetweenSetsS = spec.restBetweenSetsS,
        restAfterS = spec.restAfterS,
        supersetGroup = spec.supersetGroup,
        bilateral = spec.bilateral,
        notes = spec.notes,
        progression = spec.progression,
    )
}

/** [ExerciseRepository] over a fixed list; search matches the English name. */
class FakeExerciseRepository(private val exercises: List<Exercise>) : ExerciseRepository {
    val searches = mutableListOf<String>()
    val filters = mutableListOf<ExerciseFilter>()

    override fun search(filter: ExerciseFilter, language: String, limit: Int): Flow<List<Exercise>> {
        searches += filter.query
        filters += filter
        return flowOf(
            exercises.filter { e ->
                e.name.en.contains(filter.query, ignoreCase = true) &&
                    (filter.category == null || e.category == filter.category) &&
                    (filter.muscle == null || filter.muscle in e.primaryMuscles) &&
                    (filter.equipment == null || e.variants.any { it.equipment == filter.equipment })
            }.take(limit),
        )
    }

    override fun observeExercise(id: String): Flow<Exercise?> = flowOf(exercises.firstOrNull { it.id == id })

    override suspend fun getExercise(id: String): Exercise? = exercises.firstOrNull { it.id == id }

    override suspend fun getVariant(id: String): ExerciseVariant? =
        exercises.flatMap { it.variants }.firstOrNull { it.id == id }

    override suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant> =
        exercises.flatMap { it.variants }.filter { it.id in ids }

    override suspend fun createCustom(input: NewExercise): Exercise = error("Not used")

    override fun observeVariantHistory(variantId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        flowOf(emptyList())

    override fun observeExerciseHistory(exerciseId: String, limit: Int): Flow<List<ExerciseHistoryEntry>> =
        flowOf(emptyList())
}

class FakeSettingsRepository(unit: WeightUnit = WeightUnit.KG) : SettingsRepository {
    private val state = MutableStateFlow(UserSettings.defaultsFor(unit))
    override val settings: StateFlow<UserSettings> = state.asStateFlow()

    override fun setUnit(unit: WeightUnit, resetEquipment: Boolean) = state.update { it.copy(unit = unit) }

    override fun setBarWeight(grams: Long) = Unit

    override fun setAvailablePlates(grams: List<Long>) = Unit

    override fun setKeepScreenOn(enabled: Boolean) = Unit

    override fun setRestSound(enabled: Boolean) = Unit

    override fun setRestVibration(enabled: Boolean) = Unit

    override fun setShareName(name: String) = state.update { it.copy(shareName = name) }
}

internal val english = AppLanguage { "en" }

internal val spanish = AppLanguage { "es" }

/** Test catalogue: bench press (barbell, dumbbell), plank (time) and pull-up (bodyweight). */
object TestCatalog {
    private fun variant(exerciseId: String, equipment: Equipment, loadType: LoadType) =
        ExerciseVariant(
            id = "$exerciseId.${equipment.name.lowercase()}",
            exerciseId = exerciseId,
            equipment = equipment,
            loadType = loadType
        )

    private fun exercise(
        id: String,
        en: String,
        es: String,
        tracking: TrackingType,
        vararg variants: ExerciseVariant,
        primary: List<Muscle> = emptyList(),
        secondary: List<Muscle> = emptyList(),
    ) =
        Exercise(
            id = id,
            name = LocalizedText(en = en, es = es),
            category = ExerciseCategory.STRENGTH,
            level = Level.BEGINNER,
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            instructions = LocalizedSteps.EMPTY,
            trackingType = tracking,
            source = ExerciseSource.CATALOG,
            variants = variants.toList(),
        )

    val bench = exercise(
        "bench-press",
        "Bench press",
        "Press de banca",
        TrackingType.WEIGHT_REPS,
        variant("bench-press", Equipment.BARBELL, LoadType.BARBELL),
        variant("bench-press", Equipment.DUMBBELL, LoadType.DUMBBELL),
        primary = listOf(Muscle.CHEST),
        secondary = listOf(Muscle.SHOULDERS, Muscle.TRICEPS),
    )
    val plank =
        exercise(
            "plank",
            "Plank",
            "Plancha",
            TrackingType.TIME,
            variant("plank", Equipment.NONE, LoadType.NONE),
            primary = listOf(Muscle.ABDOMINALS),
        )
    val pullUp = exercise(
        "pull-up",
        "Pull-up",
        "Dominada",
        TrackingType.BODYWEIGHT_REPS,
        variant("pull-up", Equipment.BODYWEIGHT, LoadType.BODYWEIGHT),
        primary = listOf(Muscle.LATS),
        secondary = listOf(Muscle.BICEPS, Muscle.MIDDLE_BACK),
    )

    val all = listOf(bench, plank, pullUp)

    const val BENCH_BARBELL = "bench-press.barbell"
    const val BENCH_DUMBBELL = "bench-press.dumbbell"
}
