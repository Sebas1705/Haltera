package es.sebas1705.ironlog.data.repository

import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.models.CatalogImportResult
import es.sebas1705.ironlog.data.models.CatalogSummary
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.NewExercise
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineTemplate
import es.sebas1705.ironlog.data.models.SetInput
import es.sebas1705.ironlog.data.models.SlotSpec
import es.sebas1705.ironlog.data.models.WorkoutSession
import kotlinx.coroutines.flow.Flow

// Repository contracts used by features and use cases. Implementations are internal to this module
// and bound in `repositoryModule`. Times are epoch millis; loads are grams.

interface CatalogRepository {
    /** Imports [json] (the bundled catalogue) if it is newer than the installed one. */
    suspend fun importBundled(json: String): CatalogImportResult

    fun observeSummary(): Flow<CatalogSummary>
}

interface ExerciseRepository {
    /**
     * Active (non-deprecated) exercises matching [filter], with their active variants, sorted by name in
     * [language] ("en" or "es"). The query is accent and case insensitive and matches both languages.
     */
    fun search(filter: ExerciseFilter, language: String, limit: Int = DEFAULT_LIMIT): Flow<List<Exercise>>

    /** An exercise with all its variants (deprecated ones included, so history still resolves). */
    fun observeExercise(id: String): Flow<Exercise?>

    suspend fun getExercise(id: String): Exercise?

    suspend fun getVariant(id: String): ExerciseVariant?

    /** Variants by id, in no particular order; unknown ids are skipped. */
    suspend fun getVariants(ids: Collection<String>): List<ExerciseVariant>

    /** Creates a USER exercise with id `user-<uuid>` and its variants (`<exerciseId>.<equipment>`). */
    suspend fun createCustom(input: NewExercise): Exercise

    /** Finished-session history of one variant, latest first. */
    fun observeVariantHistory(variantId: String, limit: Int = DEFAULT_LIMIT): Flow<List<ExerciseHistoryEntry>>

    /** Finished-session history of every variant of an exercise, latest first. */
    fun observeExerciseHistory(exerciseId: String, limit: Int = DEFAULT_LIMIT): Flow<List<ExerciseHistoryEntry>>

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}

/**
 * Routines with their days and slots. Day and slot positions are 0-based and kept contiguous.
 * Every change to a day or slot bumps the routine's `updatedAt`.
 */
@Suppress("TooManyFunctions") // The routine editor needs every operation.
interface RoutineRepository {
    /** All routines, active first, then most recently updated. */
    fun observeRoutines(): Flow<List<Routine>>

    fun observeRoutine(id: String): Flow<Routine?>

    /** The active routine (drives the Today tab), or null. */
    fun observeActive(): Flow<Routine?>

    suspend fun getRoutine(id: String): Routine?

    /** Creates an empty routine (no days) and returns its id. */
    suspend fun createRoutine(name: String, description: String = ""): String

    suspend fun updateRoutine(id: String, name: String, description: String)

    /** Deletes the routine with its days and slots; its past sessions are kept and detached. */
    suspend fun deleteRoutine(id: String)

    /** Makes [id] the only active routine; null deactivates all. */
    suspend fun setActive(id: String?)

    /** Sets the cycle pointer; clamped to the routine's days. */
    suspend fun setNextDayIndex(routineId: String, index: Int)

    /** Appends a day and returns its id. */
    suspend fun addDay(routineId: String, name: String, kind: DayKind = DayKind.TRAINING): String

    suspend fun updateDay(dayId: String, name: String, kind: DayKind)

    /** Deletes a day with its slots, closes the gap and clamps the cycle pointer. */
    suspend fun deleteDay(dayId: String)

    /** Moves a day to [toPosition] (clamped), shifting the others. */
    suspend fun moveDay(dayId: String, toPosition: Int)

    /** Appends a slot to a day and returns its id. */
    suspend fun addSlot(dayId: String, spec: SlotSpec): String

    suspend fun updateSlot(slotId: String, spec: SlotSpec)

    suspend fun deleteSlot(slotId: String)

    suspend fun moveSlot(slotId: String, toPosition: Int)

    /** The bundled famous routines (spec §6.3). */
    fun templates(): List<RoutineTemplate>

    /** Copies a famous routine into "My routines" with names in [language]; returns the new routine id. */
    suspend fun copyTemplate(templateId: String, language: String): String
}

/** Workout sessions (spec §3.3, §5). Every write is persisted immediately. */
@Suppress("TooManyFunctions") // One method per live-session operation.
interface SessionRepository {
    /** The session in progress (endedAt == null) with exercises and sets, or null. */
    fun observeInProgress(): Flow<WorkoutSession?>

    suspend fun getInProgress(): WorkoutSession?

    fun observeSession(id: String): Flow<WorkoutSession?>

    suspend fun getSession(id: String): WorkoutSession?

    /**
     * Starts a session for day [dayIndex] of a routine, with one exercise per slot and no sets. A flexible
     * slot uses the variant last used in that slot, else its first allowed variant, else the exercise's
     * first variant. If a session is already in progress, nothing is created and its id is returned.
     */
    suspend fun startFromRoutineDay(routineId: String, dayIndex: Int): String

    /** Starts an empty free session, or returns the id of the one in progress. */
    suspend fun startFree(): String

    /** Appends an exercise to a session and returns its id. */
    suspend fun addExercise(sessionId: String, variantId: String, slotId: String? = null): String

    suspend fun removeExercise(sessionExerciseId: String)

    /** Changes the variant of a session exercise (e.g. dumbbells instead of barbell). Its sets are kept. */
    suspend fun substituteExercise(sessionExerciseId: String, variantId: String)

    suspend fun moveExercise(sessionExerciseId: String, toPosition: Int)

    suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String)

    /** Appends a set and returns its id; `completedAt` is set now when the input is completed. */
    suspend fun logSet(sessionExerciseId: String, input: SetInput): String

    /** Replaces a set's values; `completedAt` is kept, set now, or cleared to match `completed`. */
    suspend fun updateSet(setId: String, input: SetInput)

    suspend fun deleteSet(setId: String)

    suspend fun updateSessionNotes(sessionId: String, notes: String)

    /**
     * Ends the session. For a routine session, the routine's cycle pointer moves to the day after the
     * one trained (mod the number of days, spec §3.4). Finishing twice does nothing.
     */
    suspend fun finish(sessionId: String)

    /** Deletes a session with its exercises and sets (e.g. a discarded session). */
    suspend fun deleteSession(sessionId: String)

    /** Finished sessions with exercises and sets, latest first. */
    fun observeHistory(limit: Int = DEFAULT_LIMIT): Flow<List<WorkoutSession>>

    /** Sessions (any state) with exercises and sets started in [fromMillis, toMillis), oldest first. */
    fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>>

    /** Last finished performances of a routine slot with one variant, latest first (progression input). */
    suspend fun lastForSlotAndVariant(slotId: String, variantId: String, limit: Int = 10): List<ExerciseHistoryEntry>

    /** Last finished performances of a variant in any context, latest first (set prefill). */
    suspend fun lastForVariant(variantId: String, limit: Int = 10): List<ExerciseHistoryEntry>

    companion object {
        const val DEFAULT_LIMIT = 200
    }
}

interface BodyWeightRepository {
    /** All entries, latest first. */
    fun observeAll(): Flow<List<BodyWeightEntry>>

    fun observeLatest(): Flow<BodyWeightEntry?>

    /** Entries measured in [fromMillis, toMillis), oldest first. */
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<BodyWeightEntry>>

    /** Adds an entry and returns its id; [measuredAt] defaults to now. */
    suspend fun add(weightGrams: Long, measuredAt: Long? = null, notes: String = ""): String

    suspend fun update(entry: BodyWeightEntry)

    suspend fun delete(id: String)
}
