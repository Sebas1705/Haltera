package es.sebas1705.haltera.data.models

/**
 * A workout (spec §3.3). Times are epoch milliseconds. [endedAt] is null while in progress.
 * [routineId] and [routineDayIndex] are null for a free session. [exercises] is empty when not loaded.
 */
data class WorkoutSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val routineId: String? = null,
    val routineDayIndex: Int? = null,
    /** Snapshot of the routine day name when the session started; null for a free session. */
    val dayName: String? = null,
    val notes: String = "",
    val bodyWeightGrams: Long? = null,
    val exercises: List<SessionExercise> = emptyList(),
) {
    val inProgress: Boolean get() = endedAt == null
}

/**
 * An exercise performed in a session, with the variant actually used. [slotId] is null if added ad hoc.
 * [bilateral]: its dumbbell volume counts both hands, a snapshot of the slot's "Both hands" flag taken when it was
 * logged (core design §3.3); editing or removing the slot later never changes it.
 */
data class SessionExercise(
    val id: String,
    val sessionId: String,
    val position: Int,
    val variantId: String,
    val slotId: String? = null,
    val notes: String = "",
    val sets: List<SetLog> = emptyList(),
    val bilateral: Boolean = false,
)

/**
 * One set. [loadGrams] is grams (negative for assisted bodyweight), or a plate/level index for machines
 * by stack position and for bands. [loadDetail] is a JSON string with what was entered (bar and plates
 * per side, plate index, band level, sign) so it is shown back exactly as typed.
 */
data class SetLog(
    val id: String,
    val sessionExerciseId: String,
    val position: Int,
    val kind: SetKind = SetKind.WORKING,
    val reps: Int? = null,
    val loadGrams: Long? = null,
    val loadDetail: String? = null,
    val durationS: Int? = null,
    val distanceM: Int? = null,
    val rpe: Double? = null,
    val completed: Boolean = false,
    val completedAt: Long? = null,
)

/** Values to write into a set; the repository owns ids and positions. */
data class SetInput(
    val kind: SetKind = SetKind.WORKING,
    val reps: Int? = null,
    val loadGrams: Long? = null,
    val loadDetail: String? = null,
    val durationS: Int? = null,
    val distanceM: Int? = null,
    val rpe: Double? = null,
    val completed: Boolean = true,
)

/** A performed exercise with the session it belongs to, for history views and progression. */
data class ExerciseHistoryEntry(
    val sessionId: String,
    val sessionStartedAt: Long,
    val exercise: SessionExercise,
)

data class BodyWeightEntry(
    val id: String,
    val measuredAt: Long,
    val weightGrams: Long,
    val notes: String = "",
)
