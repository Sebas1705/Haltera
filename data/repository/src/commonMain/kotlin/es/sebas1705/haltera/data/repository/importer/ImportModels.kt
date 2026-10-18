package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.MeasurementType
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.TrackingType
import kotlinx.datetime.TimeZone

/** Where a history file comes from; detected from its header row (docs/IMPORT-FORMATS.md). */
enum class ImportSource { HEVY, STRONG, FITNOTES, HALTERA }

/** The unit of a load that the file does not state itself (Strong writes the unit the user had selected). */
enum class ImportWeightUnit { KG, LB }

/** What to do with a session that is already in the logbook (same deterministic id). */
enum class ExistingPolicy { SKIP, REPLACE }

/** Why a file could not be read. Nothing is written in any of these cases. */
enum class ImportError {
    /** Empty file, or a header row with no recognised columns. */
    UNRECOGNISED,

    /** A known format but without a single usable set. */
    NO_DATA,

    /** A zip with compressed entries, which this reader does not inflate. */
    COMPRESSED_ZIP,

    /** A zip that is not a Haltera export (no sessions file). */
    NOT_AN_EXPORT,

    /** A Haltera export written by a newer version of the format. */
    NEWER_FORMAT,

    /** Damaged zip or text that is not valid. */
    CORRUPT,
}

class ImportFormatException(
    val error: ImportError,
    detail: String = error.name,
    cause: Throwable? = null,
) : Exception(detail, cause)

data class ParseOptions(val timeZone: TimeZone = TimeZone.currentSystemDefault())

/** One set read from a file. A load is either an exact [loadGrams] or a [weight] in [weightUnit] (null: ask). */
data class ImportedSet(
    /** Haltera exports: the set id of the file, kept on import so a replaced session updates its sets in place. */
    val id: String? = null,
    val kind: SetKind = SetKind.WORKING,
    val reps: Int? = null,
    val weight: Double? = null,
    val weightUnit: ImportWeightUnit? = null,
    val loadGrams: Long? = null,
    val loadDetail: String? = null,
    val durationS: Int? = null,
    val distanceM: Int? = null,
    val rpe: Double? = null,
    val completedAt: Long? = null,
)

/** An exercise as the file names it, with what it says about it (used to create a custom exercise). */
data class ImportedExercise(
    val name: String,
    val sets: List<ImportedSet>,
    val notes: String = "",
    /** Haltera files only: the stable ids written by the exporter. */
    val exerciseId: String? = null,
    val variantId: String? = null,
    val category: ExerciseCategory? = null,
    val trackingType: TrackingType? = null,
    val muscles: List<Muscle> = emptyList(),
    val equipment: Equipment? = null,
    /** Haltera export v2: the routine slot it was logged from (linked only if it exists here). */
    val slotId: String? = null,
    /** Haltera export v2: the "Both hands" snapshot; null when the file does not say (one implement). */
    val bilateral: Boolean? = null,
)

data class ImportedSession(
    /** Deterministic: the same session in the same file always has the same id. */
    val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val title: String?,
    val notes: String,
    val bodyWeightGrams: Long?,
    val exercises: List<ImportedExercise>,
    /** Haltera export v2: the routine and day the session was logged from (linked only if they exist here). */
    val routineId: String? = null,
    val routineName: String? = null,
    val routineDayIndex: Int? = null,
) {
    val setCount: Int get() = exercises.sumOf { it.sets.size }
}

data class ImportedBodyWeight(val id: String, val measuredAt: Long, val weightGrams: Long, val notes: String)

/** A row of `body_measurements.csv` (Haltera export v3). [value] is mm, or tenths of a percent for body fat. */
data class ImportedBodyMeasurement(
    val id: String,
    val measuredAt: Long,
    val type: MeasurementType,
    val value: Long,
    val customName: String = "",
    val notes: String = "",
)

/** The content of a file, parsed but not yet written. */
data class ParsedImport(
    val source: ImportSource,
    val sessions: List<ImportedSession>,
    val bodyWeights: List<ImportedBodyWeight> = emptyList(),
    /** Rows that were read but are not a set (empty sets, rest timers, rows without date or exercise). */
    val skippedRows: Int = 0,
    /** True when loads have no unit in the file, so the user must say which unit they were logged in. */
    val needsUnitChoice: Boolean = false,
    /** Haltera export v3: `body_measurements.csv`. */
    val bodyMeasurements: List<ImportedBodyMeasurement> = emptyList(),
) {
    val setCount: Int get() = sessions.sumOf { it.setCount }

    /** Nothing at all to write. */
    val isEmpty: Boolean get() = sessions.isEmpty() && bodyWeights.isEmpty() && bodyMeasurements.isEmpty()
}

/** An exercise name of the file that matches nothing in the logbook. */
data class UnmatchedExercise(
    /** Normalised name; the key of the user's decision. */
    val key: String,
    val name: String,
    val sets: Int,
    val sessions: Int,
    /** An existing exercise the preview proposes (fuzzy search, ExerciseSuggester); null when nothing is close. */
    val suggestion: ExerciseSuggestion? = null,
)

data class ImportPreview(
    val source: ImportSource,
    val sessions: Int,
    val sets: Int,
    val firstStartedAt: Long?,
    val lastStartedAt: Long?,
    val bodyWeights: Int,
    val existingSessions: Int,
    val existingBodyWeights: Int,
    val matchedExercises: Int,
    val unmatched: List<UnmatchedExercise>,
    val skippedRows: Int,
    val needsUnitChoice: Boolean,
    val bodyMeasurements: Int = 0,
    val existingBodyMeasurements: Int = 0,
)

/** What to do with an unmatched exercise name. The default is [CreateCustom]. */
sealed interface ExerciseDecision {
    data object CreateCustom : ExerciseDecision

    /** Use an existing exercise; the variant is chosen from the equipment named in the file, else the first. */
    data class MapTo(val exerciseId: String) : ExerciseDecision
}

data class ImportOptions(
    val weightUnit: ImportWeightUnit = ImportWeightUnit.KG,
    val existing: ExistingPolicy = ExistingPolicy.SKIP,
    val decisions: Map<String, ExerciseDecision> = emptyMap(),
)

data class ImportSummary(
    val sessionsWritten: Int,
    val sessionsSkipped: Int,
    val sessionsReplaced: Int,
    val sets: Int,
    val bodyWeights: Int,
    val exercisesCreated: Int,
    val bodyMeasurements: Int = 0,
)
