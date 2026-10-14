package es.sebas1705.ironlog.feature.session.ui

import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseFilter
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.progression.ProgressionEngine
import es.sebas1705.ironlog.domain.usecases.progression.Suggestion
import es.sebas1705.ironlog.domain.usecases.session.SuggestNextLoad
import kotlinx.coroutines.flow.Flow

/**
 * Read side of the session screen: exercises, variants, routine slots, suggestions and last loads, cached
 * per session. History is always read as of the session's start ([sessionStartedAt]): a past session
 * being logged only sees what came before it, and the session itself never counts.
 */
class SessionCatalog(
    private val exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val suggestNextLoad: SuggestNextLoad,
) {
    private val variantCache = mutableMapOf<String, ExerciseVariant?>()
    private val exerciseCache = mutableMapOf<String, Exercise?>()
    private val slotCache = mutableMapOf<String, Map<String, RoutineSlot>>()
    private val suggestionCache = mutableMapOf<Pair<String, String>, Suggestion>()
    private val lastCache = mutableMapOf<Pair<String?, String>, SessionExercise?>()

    /** Start of the session shown: the upper bound (exclusive) of every history read. */
    var sessionStartedAt: Long? = null
        set(value) {
            if (field != value) {
                suggestionCache.clear()
                lastCache.clear()
            }
            field = value
        }

    suspend fun variant(id: String): ExerciseVariant? =
        if (id in variantCache) variantCache[id] else exercises.getVariant(id).also { variantCache[id] = it }

    suspend fun exercise(id: String): Exercise? =
        if (id in exerciseCache) exerciseCache[id] else exercises.getExercise(id).also { exerciseCache[id] = it }

    /** The slots of a routine by id (empty for a free session or a deleted routine). */
    suspend fun slots(routineId: String?): Map<String, RoutineSlot> {
        routineId ?: return emptyMap()
        return slotCache[routineId] ?: (
            routines.getRoutine(routineId)?.days.orEmpty().flatMap { it.slots }.associateBy { it.id }
            ).also { slotCache[routineId] = it }
    }

    suspend fun suggestion(slot: RoutineSlot, variantId: String): Suggestion =
        suggestionCache.getOrPut(slot.id to variantId) { suggestNextLoad(slot, variantId, sessionStartedAt) }

    /** Last finished performance (before this session) of the slot with this variant, else of the variant. */
    suspend fun lastPerformance(slotId: String?, variantId: String): SessionExercise? {
        val key = slotId to variantId
        if (key in lastCache) return lastCache[key]
        val before = sessionStartedAt
        val fromSlot = slotId?.let {
            sessions.lastForSlotAndVariant(it, variantId, limit = 1, before = before).firstOrNull()
        }
        val last = fromSlot?.exercise
            ?: sessions.lastForVariant(variantId, limit = 1, before = before).firstOrNull()?.exercise
        lastCache[key] = last
        return last
    }

    /** Last working load of a variant in any context, for the flexible-slot picker. */
    suspend fun lastLoad(variantId: String): Long? =
        lastPerformance(null, variantId)?.let { ProgressionEngine.workingLoad(it) }

    /** The variants a flexible slot allows, in catalogue order. */
    suspend fun allowedVariants(target: SlotTarget.Flexible): List<ExerciseVariant> {
        val all = exercise(target.exerciseId)?.variants.orEmpty().filterNot { it.deprecated }
        return if (target.allowedVariantIds.isEmpty()) all else all.filter { it.id in target.allowedVariantIds }
    }

    /** Finished history of a variant before this session (and without it), for PR comparison. */
    suspend fun previousHistory(variantId: String, excludeSessionId: String): List<ExerciseHistoryEntry> =
        sessions.lastForVariant(variantId, limit = HISTORY_LIMIT, before = sessionStartedAt)
            .filter { it.sessionId != excludeSessionId }

    /**
     * The increase a finished session earned for its slot (spec §5 summary), when that session is the
     * latest of the slot with this variant; null otherwise. Bypasses the suggestion cache.
     */
    suspend fun earnedIncrease(slot: RoutineSlot, variantId: String, sessionId: String): Suggestion.Increase? {
        val latest = sessions.lastForSlotAndVariant(slot.id, variantId, limit = 1).firstOrNull()
        if (latest?.sessionId != sessionId) return null
        return suggestNextLoad(slot, variantId) as? Suggestion.Increase
    }

    fun search(query: String, language: String): Flow<List<Exercise>> =
        exercises.search(ExerciseFilter(query = query), language, limit = SEARCH_LIMIT)

    private companion object {
        const val SEARCH_LIMIT = 60
        const val HISTORY_LIMIT = 500
    }
}
