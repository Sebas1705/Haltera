package es.sebas1705.haltera.data.repository

import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.CatalogImportResult
import es.sebas1705.haltera.data.models.CatalogSummary
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.DeleteOutcome
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseEdit
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.ExerciseUsage
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineTemplate
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

// Repository contracts used by features and use cases. Implementations are internal to this module
// and bound in `repositoryModule`. Times are epoch millis; loads are grams.

interface CatalogRepository {
    /** Imports [json] (the bundled catalogue) if it is newer than the installed one. */
    suspend fun importBundled(json: String): CatalogImportResult

    fun observeSummary(): Flow<CatalogSummary>
}

@Suppress("TooManyFunctions") // Catalogue reads plus the custom exercise lifecycle.
interface ExerciseRepository {
    /**
     * Active (non-deprecated) exercises matching [filter], with their active variants, in [language] ("en" or "es").
     *
     * Without a text query: every exercise passing the filters, the most logged first, then by name. With one:
     * a fuzzy, ranked search (docs/designs/2026-10-16-fuzzy-search-design.md) over the names in both languages,
     * variant names, muscles, equipment and category, tolerant of word order, accents, plurals and typos, with
     * Spanish/English gym synonyms; best match first. The filters apply in both cases. Every exercise search
     * of the app (Exercises tab, routine exercise picker, session "add exercise") goes through here.
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

    /**
     * Edits the USER exercise [id] (core design §3.1.1); ids stay, so sessions and routines keep pointing at it.
     * Throws [IllegalArgumentException] for an unknown or catalogue exercise, a blank name, no variants or a variant
     * of another exercise, and [IllegalStateException] when the edit changes what history depends on: the tracking
     * type of a logged exercise, or the equipment or load type of a logged variant (add a new variant instead).
     * Variants left out are deleted, or deprecated while a session or routine references them. The defaults
     * (here and below) serve test fakes that do not edit exercises.
     */
    suspend fun updateCustom(id: String, edit: ExerciseEdit): Exercise =
        throw UnsupportedOperationException("updateCustom")

    /** Where the USER exercise [id] is used (routines, sessions, sets); empty for an unknown id. */
    suspend fun usage(id: String): ExerciseUsage = ExerciseUsage()

    /**
     * Deletes the USER exercise [id] with the safe policy of core design §3.1.1: when routines use it, nothing
     * changes unless [removeFromRoutines] (then those slots are removed first); with history it is soft-deleted
     * (hidden, history keeps it); otherwise its rows are deleted and a tombstone syncs.
     */
    suspend fun deleteCustom(id: String, removeFromRoutines: Boolean): DeleteOutcome =
        throw UnsupportedOperationException("deleteCustom")

    /**
     * The user's soft-deleted (hidden) custom exercises with their variants, live: what the Exercises tab lists
     * under "Hidden" so any of them can be restored at any time (core design §3.1.1). Catalogue rows never appear.
     */
    fun observeHiddenCustom(): Flow<List<Exercise>> = flowOf(emptyList())

    /** Undoes a soft delete of the USER exercise [id]: it syncs as an update and is searchable again at once. */
    suspend fun restoreCustom(id: String): Unit = throw UnsupportedOperationException("restoreCustom")

    /**
     * The USER exercise a shared exercise with origin [originId] was imported as, or the user's own exercise with
     * that id (a routine they shared themselves); soft-deleted ones included. Null when there is none.
     */
    suspend fun findByOrigin(originId: String): Exercise? = null

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

    /**
     * Manual override of the next day (Today's day picker), clamped to the routine's days and stamped
     * with now. It applies until a session of the routine starts after it (spec §3.4, `Cycle`).
     */
    suspend fun setNextDayIndex(routineId: String, index: Int)

    /**
     * Sets how the routine rests ([policy] normalised; docs/designs/2026-10-16-flexible-rest-days.md). Changing
     * the mode converts the days in one transaction and never removes a training day:
     * - to FLEXIBLE, REST days without slots are removed and REST days with slots become training days;
     * - to FIXED, [appendRestDays] REST days named [restDayName] are added at the end of the cycle.
     *
     * The day indexes of the routine's sessions and the manual override are remapped to the same days, so the
     * history and the next day stay put. Within the same mode only the policy changes.
     */
    suspend fun setRestPolicy(
        routineId: String,
        policy: RestPolicy,
        appendRestDays: Int = 0,
        restDayName: String = "Rest",
    )

    /** Appends a day and returns its id. */
    suspend fun addDay(routineId: String, name: String, kind: DayKind = DayKind.TRAINING): String

    suspend fun updateDay(dayId: String, name: String, kind: DayKind)

    /** Deletes a day with its slots, closes the gap and clamps the cycle pointer. */
    suspend fun deleteDay(dayId: String)

    /** Moves a day to [toPosition] (clamped), shifting the others. */
    suspend fun moveDay(dayId: String, toPosition: Int)

    /**
     * Copies a day with all its slots (target, prescription, rest, superset, bilateral, notes, progression) under
     * new ids, inserts the copy right after the original and returns its id. The copy is named
     * "<name> (copy)", or "<name> (copia)" when [language] is Spanish (as in [copyTemplate]).
     *
     * This default composes the other operations so simple implementations get it for free; the SQL
     * repository overrides it with a single transaction.
     */
    suspend fun duplicateDay(dayId: String, language: String = "en"): String {
        val routine = requireNotNull(observeRoutines().first().firstOrNull { r -> r.days.any { it.id == dayId } }) {
            "Unknown routine day $dayId"
        }
        val day = routine.days.first { it.id == dayId }
        val copyId = addDay(routine.id, copyName(day.name, language), day.kind)
        day.slots.forEach { addSlot(copyId, it.toSpec()) }
        moveDay(copyId, day.position + 1)
        return copyId
    }

    /** Appends a slot to a day and returns its id. */
    suspend fun addSlot(dayId: String, spec: SlotSpec): String

    suspend fun updateSlot(slotId: String, spec: SlotSpec)

    suspend fun deleteSlot(slotId: String)

    suspend fun moveSlot(slotId: String, toPosition: Int)

    /** The bundled famous routines (spec §6.3). */
    fun templates(): List<RoutineTemplate>

    /** Copies a famous routine into "My routines" with names in [language]; returns the new routine id. */
    suspend fun copyTemplate(templateId: String, language: String): String

    companion object {
        /** The name of a duplicated day: "<name> (copy)" or, in Spanish, "<name> (copia)". */
        fun copyName(name: String, language: String): String =
            if (language.lowercase().startsWith("es")) "$name (copia)" else "$name (copy)"
    }
}

/** Workout sessions (spec §3.3, §5). Every write is persisted immediately. */
@Suppress("TooManyFunctions") // One method per session operation.
interface SessionRepository {
    /** The session in progress (endedAt == null) with exercises and sets, or null. */
    fun observeInProgress(): Flow<WorkoutSession?>

    suspend fun getInProgress(): WorkoutSession?

    fun observeSession(id: String): Flow<WorkoutSession?>

    suspend fun getSession(id: String): WorkoutSession?

    /**
     * The routine's most recent session by `startedAt`, finished or not, or null. It drives the cycle
     * (spec §3.4): the next day is the one after it.
     */
    fun observeLatestForRoutine(routineId: String): Flow<WorkoutSession?>

    /**
     * Starts a session for day [dayIndex] of a routine, with one exercise per slot and no sets. A flexible
     * slot uses the variant last used in that slot (before the start), else its first allowed variant,
     * else the exercise's first variant.
     *
     * A live start ([startedAt] null) starts now; if a session is already in progress, nothing is created
     * and its id is returned. A start at a given time (a past session being logged) always creates a new
     * session, whatever is in progress.
     */
    suspend fun startFromRoutineDay(routineId: String, dayIndex: Int, startedAt: Long? = null): String

    /** Starts an empty free session; same [startedAt] rules as [startFromRoutineDay]. */
    suspend fun startFree(startedAt: Long? = null): String

    /** Appends an exercise to a session and returns its id. */
    suspend fun addExercise(sessionId: String, variantId: String, slotId: String? = null): String

    suspend fun removeExercise(sessionExerciseId: String)

    /** Changes the variant of a session exercise (e.g. dumbbells instead of barbell). Its sets are kept. */
    suspend fun substituteExercise(sessionExerciseId: String, variantId: String)

    suspend fun moveExercise(sessionExerciseId: String, toPosition: Int)

    suspend fun updateExerciseNotes(sessionExerciseId: String, notes: String)

    /**
     * Appends a set and returns its id; `completedAt` is set now when the input is completed, kept inside the session's
     * start and end when it is finished (a past session logged later). Works on finished sessions too (editing a saved
     * session never reopens it).
     */
    suspend fun logSet(sessionExerciseId: String, input: SetInput): String

    /** Replaces a set's values; `completedAt` is kept, set (as in [logSet]), or cleared to match `completed`. */
    suspend fun updateSet(setId: String, input: SetInput)

    suspend fun deleteSet(setId: String)

    suspend fun updateSessionNotes(sessionId: String, notes: String)

    /**
     * Ends the session at [endedAt] (now if null). Finishing twice does nothing: the first end time is
     * kept (use [updateTimes] to change it). The cycle needs no update: it is derived from the sessions.
     */
    suspend fun finish(sessionId: String, endedAt: Long? = null)

    /**
     * Deletes the session's uncompleted sets and the exercises left without a completed set, and renumbers
     * what remains (finishing or saving a session). The database does it in one transaction; this default
     * goes through the single-row operations (fakes).
     */
    suspend fun dropIncomplete(sessionId: String) {
        getSession(sessionId)?.exercises.orEmpty().forEach { exercise ->
            if (exercise.sets.none { it.completed }) {
                removeExercise(exercise.id)
            } else {
                exercise.sets.filterNot { it.completed }.forEach { deleteSet(it.id) }
            }
        }
    }

    /**
     * Changes when a session started and ended ([endedAt] null makes it in progress again). An end
     * before the start is moved to the start. Completion times of its sets move by the same offset as the start
     * and are kept inside the new start and end.
     */
    suspend fun updateTimes(sessionId: String, startedAt: Long, endedAt: Long?)

    /** Deletes a session with its exercises and sets (e.g. a discarded session). */
    suspend fun deleteSession(sessionId: String)

    /** Finished sessions with exercises and sets, latest `startedAt` first. */
    fun observeHistory(limit: Int = DEFAULT_LIMIT): Flow<List<WorkoutSession>>

    /** Sessions (any state) with exercises and sets started in [fromMillis, toMillis), oldest first. */
    fun observeSessionsBetween(fromMillis: Long, toMillis: Long): Flow<List<WorkoutSession>>

    /**
     * Last finished performances of a routine slot with one variant, latest `startedAt` first
     * (progression input). [before] keeps only sessions started strictly before it (a past session's
     * prefill must not see what came after it); null means no bound.
     */
    suspend fun lastForSlotAndVariant(
        slotId: String,
        variantId: String,
        limit: Int = 10,
        before: Long? = null,
    ): List<ExerciseHistoryEntry>

    /** Last finished performances of a variant in any context, latest first (set prefill); [before] as above. */
    suspend fun lastForVariant(variantId: String, limit: Int = 10, before: Long? = null): List<ExerciseHistoryEntry>

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
