package es.sebas1705.haltera.data.repository.sql

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import es.sebas1705.haltera.data.local.db.ExerciseEntity
import es.sebas1705.haltera.data.local.db.ExerciseVariantEntity
import es.sebas1705.haltera.data.local.db.RoutineDayEntity
import es.sebas1705.haltera.data.local.db.RoutineEntity
import es.sebas1705.haltera.data.local.db.RoutineSlotEntity
import es.sebas1705.haltera.data.local.db.SetLogEntity
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.ModelJson
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineOrigin
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.RestMode
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

internal fun Boolean.toDb(): Long = if (this) 1L else 0L

/**
 * Emits once now and again after any write to the tables of [queries]. SQLDelight notifies per table,
 * so any query on a table works as a change trigger for the whole table.
 */
internal fun tablesChanged(vararg queries: Query<*>): Flow<Unit> =
    combine(queries.map { it.asFlow() }) { }

private val StepsSerializer = ListSerializer(String.serializer())

internal fun stepsFromDb(value: String?): List<String> = if (value.isNullOrBlank()) {
    emptyList()
} else {
    try {
        ModelJson.json.decodeFromString(StepsSerializer, value)
    } catch (_: SerializationException) {
        emptyList()
    }
}

internal fun stepsToDb(steps: List<String>): String = ModelJson.json.encodeToString(StepsSerializer, steps)

internal fun ExerciseEntity.toModel(variants: List<ExerciseVariant> = emptyList()) = Exercise(
    id = id,
    name = LocalizedText(en = nameEn, es = nameEs),
    category = ExerciseCategory.fromDb(category),
    level = Level.fromDb(level),
    primaryMuscles = Muscle.listFromDb(primaryMuscles),
    secondaryMuscles = Muscle.listFromDb(secondaryMuscles),
    instructions = LocalizedSteps(stepsFromDb(instructionsEn), stepsFromDb(instructionsEs)),
    trackingType = TrackingType.fromDb(trackingType),
    source = ExerciseSource.fromDb(source),
    deprecated = deprecated != 0L,
    variants = variants,
)

internal fun ExerciseVariantEntity.toModel() = ExerciseVariant(
    id = id,
    exerciseId = exerciseId,
    equipment = Equipment.fromDb(equipment),
    loadType = LoadType.fromDb(loadType),
    name = nameEn?.let { en -> nameEs?.let { es -> LocalizedText(en = en, es = es) } },
    instructions = if (instructionsEn != null || instructionsEs != null) {
        LocalizedSteps(stepsFromDb(instructionsEn), stepsFromDb(instructionsEs))
    } else {
        null
    },
    frames = stepsFromDb(frames),
    source = ExerciseSource.fromDb(source),
    position = position.toInt(),
    deprecated = deprecated != 0L,
)

internal fun RoutineEntity.toModel(days: List<RoutineDay>) = Routine(
    id = id,
    name = name,
    description = description,
    days = days,
    origin = originTemplateId?.let { RoutineOrigin(it, originVersion?.toInt() ?: 0) },
    isActive = isActive != 0L,
    nextDayIndex = if (days.isEmpty()) 0 else nextDayIndex.toInt().coerceIn(0, days.lastIndex),
    nextDayIndexSetAt = nextDayIndexSetAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    restPolicy = RestPolicy(RestMode.fromDb(restMode), restDaysPerWeek.toInt(), restWeekdays.toInt()).normalized(),
)

internal fun RoutineDayEntity.toModel(slots: List<RoutineSlot>) = RoutineDay(
    id = id,
    routineId = routineId,
    position = position.toInt(),
    name = name,
    kind = DayKind.fromDb(kind),
    slots = slots,
)

/** Null when the stored target cannot be read (e.g. written by a newer version). */
internal fun RoutineSlotEntity.toModel(): RoutineSlot? {
    val decodedTarget = ModelJson.decodeTarget(target) ?: return null
    return RoutineSlot(
        id = id,
        dayId = dayId,
        position = position.toInt(),
        target = decodedTarget,
        sets = sets.toInt(),
        warmupSets = warmupSets.toInt(),
        prescription = ModelJson.decodePrescription(prescription),
        restBetweenSetsS = restBetweenSetsS.toInt(),
        restAfterS = restAfterS.toInt(),
        supersetGroup = supersetGroup?.toInt(),
        bilateral = bilateral != 0L,
        notes = notes,
        progression = ModelJson.decodeProgression(progression),
    )
}

internal fun SetLogEntity.toModel() = SetLog(
    id = id,
    sessionExerciseId = sessionExerciseId,
    position = position.toInt(),
    kind = SetKind.fromDb(kind),
    reps = reps?.toInt(),
    loadGrams = loadGrams,
    loadDetail = loadDetail,
    durationS = durationS?.toInt(),
    distanceM = distanceM?.toInt(),
    rpe = rpe,
    completed = completed != 0L,
    completedAt = completedAt,
)
