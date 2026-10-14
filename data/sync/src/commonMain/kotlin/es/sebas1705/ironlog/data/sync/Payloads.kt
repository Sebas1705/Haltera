package es.sebas1705.ironlog.data.sync

import es.sebas1705.ironlog.data.local.db.BodyWeightEntity
import es.sebas1705.ironlog.data.local.db.ExerciseEntity
import es.sebas1705.ironlog.data.local.db.ExerciseVariantEntity
import es.sebas1705.ironlog.data.local.db.RoutineDayEntity
import es.sebas1705.ironlog.data.local.db.RoutineEntity
import es.sebas1705.ironlog.data.local.db.RoutineSlotEntity
import es.sebas1705.ironlog.data.local.db.SessionExerciseEntity
import es.sebas1705.ironlog.data.local.db.SetLogEntity
import es.sebas1705.ironlog.data.local.db.WorkoutSessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// JSON payloads of the synced aggregates (design §3, `schema: 1`). They mirror the table columns; parent ids
// are implied by the nesting. Booleans stay 0/1 like the columns. New optional fields need a default.

internal val SyncJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class RoutinePayload(
    val id: String,
    val name: String,
    val description: String = "",
    val originTemplateId: String? = null,
    val originVersion: Long? = null,
    val nextDayIndex: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val days: List<DayPayload> = emptyList(),
)

@Serializable
internal data class DayPayload(
    val id: String,
    val position: Long,
    val name: String,
    val kind: String,
    val slots: List<SlotPayload> = emptyList(),
)

@Serializable
internal data class SlotPayload(
    val id: String,
    val position: Long,
    val target: String,
    val sets: Long,
    val warmupSets: Long = 0,
    val prescription: String,
    val restBetweenSetsS: Long,
    val restAfterS: Long,
    val supersetGroup: Long? = null,
    val bilateral: Long = 0,
    val notes: String = "",
    val progression: String,
)

@Serializable
internal data class SessionPayload(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val routineId: String? = null,
    val routineDayIndex: Long? = null,
    val dayName: String? = null,
    val notes: String = "",
    val bodyWeightGrams: Long? = null,
    val exercises: List<SessionExercisePayload> = emptyList(),
)

@Serializable
internal data class SessionExercisePayload(
    val id: String,
    val position: Long,
    val variantId: String,
    val slotId: String? = null,
    val notes: String = "",
    val sets: List<SetPayload> = emptyList(),
)

@Serializable
internal data class SetPayload(
    val id: String,
    val position: Long,
    val kind: String,
    val reps: Long? = null,
    val loadGrams: Long? = null,
    val loadDetail: String? = null,
    val durationS: Long? = null,
    val distanceM: Long? = null,
    val rpe: Double? = null,
    val completed: Long = 0,
    val completedAt: Long? = null,
)

@Serializable
internal data class BodyWeightPayload(
    val id: String,
    val measuredAt: Long,
    val weightGrams: Long,
    val notes: String = "",
)

@Serializable
internal data class ExercisePayload(
    val id: String,
    val nameEn: String,
    val nameEs: String,
    val nameEnSearch: String = "",
    val nameEsSearch: String = "",
    val category: String,
    val trackingType: String,
    val level: String,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val instructionsEn: String,
    val instructionsEs: String,
    val deprecated: Long = 0,
    val updatedAt: Long,
    val variants: List<VariantPayload> = emptyList(),
)

@Serializable
internal data class VariantPayload(
    val id: String,
    val position: Long,
    val equipment: String,
    val loadType: String,
    val nameEn: String? = null,
    val nameEs: String? = null,
    val instructionsEn: String? = null,
    val instructionsEs: String? = null,
    val frames: String = "[]",
    val deprecated: Long = 0,
    val updatedAt: Long,
)

/** `settings/user`: the user settings plus the active routine id (design §3). */
@Serializable
internal data class SettingsPayload(
    val prefs: SettingsPrefs,
    val activeRoutineId: String? = null,
)

/** The SettingsRepository part of [SettingsPayload]. */
@Serializable
internal data class SettingsPrefs(
    val unit: String,
    val barWeightGrams: Long,
    val availablePlatesGrams: List<Long>,
    val keepScreenOn: Boolean,
    val restSound: Boolean,
    val restVibration: Boolean,
)

internal fun RoutineEntity.toPayload(days: List<DayPayload>) = RoutinePayload(
    id = id, name = name, description = description, originTemplateId = originTemplateId,
    originVersion = originVersion, nextDayIndex = nextDayIndex, createdAt = createdAt, updatedAt = updatedAt, days = days,
)

internal fun RoutineDayEntity.toPayload(slots: List<SlotPayload>) =
    DayPayload(id = id, position = position, name = name, kind = kind, slots = slots)

internal fun RoutineSlotEntity.toPayload() = SlotPayload(
    id = id, position = position, target = target, sets = sets, warmupSets = warmupSets, prescription = prescription,
    restBetweenSetsS = restBetweenSetsS, restAfterS = restAfterS, supersetGroup = supersetGroup,
    bilateral = bilateral, notes = notes, progression = progression,
)

internal fun WorkoutSessionEntity.toPayload(exercises: List<SessionExercisePayload>) = SessionPayload(
    id = id, startedAt = startedAt, endedAt = endedAt, routineId = routineId, routineDayIndex = routineDayIndex,
    dayName = dayName, notes = notes, bodyWeightGrams = bodyWeightGrams, exercises = exercises,
)

internal fun SessionExerciseEntity.toPayload(sets: List<SetPayload>) =
    SessionExercisePayload(
        id = id,
        position = position,
        variantId = variantId,
        slotId = slotId,
        notes = notes,
        sets = sets
    )

internal fun SetLogEntity.toPayload() = SetPayload(
    id = id, position = position, kind = kind, reps = reps, loadGrams = loadGrams, loadDetail = loadDetail,
    durationS = durationS, distanceM = distanceM, rpe = rpe, completed = completed, completedAt = completedAt,
)

internal fun BodyWeightEntity.toPayload() =
    BodyWeightPayload(id = id, measuredAt = measuredAt, weightGrams = weightGrams, notes = notes)

internal fun ExerciseEntity.toPayload(variants: List<VariantPayload>) = ExercisePayload(
    id = id, nameEn = nameEn, nameEs = nameEs, nameEnSearch = nameEnSearch, nameEsSearch = nameEsSearch,
    category = category, trackingType = trackingType, level = level, primaryMuscles = primaryMuscles,
    secondaryMuscles = secondaryMuscles, instructionsEn = instructionsEn, instructionsEs = instructionsEs,
    deprecated = deprecated, updatedAt = updatedAt, variants = variants,
)

internal fun ExerciseVariantEntity.toPayload() = VariantPayload(
    id = id, position = position, equipment = equipment, loadType = loadType, nameEn = nameEn, nameEs = nameEs,
    instructionsEn = instructionsEn, instructionsEs = instructionsEs, frames = frames, deprecated = deprecated,
    updatedAt = updatedAt,
)
