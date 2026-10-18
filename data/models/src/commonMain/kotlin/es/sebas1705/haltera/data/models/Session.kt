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
    /**
     * Name of the routine the session was logged from: it follows renames while the routine exists and is kept when
     * the routine is deleted ([routineId] is then null), so the title survives. Null for a free session.
     */
    val routineName: String? = null,
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

/** What a body measurement measures (roadmap P6). Lengths are millimetres; [BODY_FAT] is tenths of a percent. */
enum class MeasurementType {
    WAIST, CHEST, HIPS, ARM, THIGH, NECK, BODY_FAT,

    /** A length the user names ("Calf", "Left arm"); the name is [BodyMeasurement.customName]. */
    CUSTOM;

    /** True for the lengths (millimetres); false for [BODY_FAT] (tenths of a percent). */
    val isLength: Boolean get() = this != BODY_FAT

    companion object {
        /** A type written by a newer version becomes a custom length (its row keeps the stored name). */
        fun fromDb(value: String?): MeasurementType = lenientEnum(value, CUSTOM)
    }
}

/**
 * One body measurement. [value] is whole millimetres for a length and tenths of a percent for body fat
 * ([MeasurementType.isLength]); [customName] is set only for [MeasurementType.CUSTOM] (empty otherwise).
 */
data class BodyMeasurement(
    val id: String,
    val measuredAt: Long,
    val type: MeasurementType,
    val value: Long,
    val customName: String = "",
    val notes: String = "",
) {
    /** The chart a measurement belongs to: its type, or the custom name (case and spacing do not matter). */
    val seriesKey: String
        get() = if (type == MeasurementType.CUSTOM) "CUSTOM:${BodyMeasurements.nameKey(customName)}" else type.name
}

/** Rules shared by the editor, the repository, sync and import. */
object BodyMeasurements {
    /** Longest custom name kept; longer names are cut. */
    const val MAX_NAME_LENGTH = 40

    /** 5 m: anything longer is a typo, not a body measurement. */
    const val MAX_LENGTH_MM = 5_000L

    /** 100 %. */
    const val MAX_BODY_FAT_TENTHS = 1_000L

    private val SPACES = Regex("\\s+")

    /** The name as stored: trimmed, inner runs of spaces collapsed, at most [MAX_NAME_LENGTH] characters. */
    fun normalizeName(name: String): String = name.trim().replace(SPACES, " ").take(MAX_NAME_LENGTH).trimEnd()

    /** Two custom names are the same series when they only differ in case or spacing (accents do count). */
    fun nameKey(name: String): String = normalizeName(name).lowercase()

    /** Largest value accepted for [type]. */
    fun maxValue(type: MeasurementType): Long = if (type.isLength) MAX_LENGTH_MM else MAX_BODY_FAT_TENTHS

    /** A value is valid when it is above zero and at most [maxValue]. */
    fun isValidValue(type: MeasurementType, value: Long): Boolean = value in 1..maxValue(type)
}

/** A progress photo kept on this device only (never synced or exported). [takenAt] is the date the user gave it. */
data class ProgressPhoto(
    val id: String,
    val takenAt: Long,
    val notes: String = "",
    /** When it was added to Haltera, epoch millis (orders photos of the same day). */
    val addedAt: Long = takenAt,
)
