package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.WorkoutSession

/** The kinds of personal record of Metric.PERSONAL_RECORDS. */
enum class PrKind { HEAVIEST_LOAD, ESTIMATED_ONE_RM, REPS_AT_LOAD, SESSION_VOLUME }

/**
 * A record set in a session. [value] is grams (load, 1RM), reps (reps at a load) or gram-reps (volume);
 * [loadGrams] is the load of a reps record.
 */
data class PrEvent(
    val sessionId: String,
    val at: Long,
    val variantId: String,
    val kind: PrKind,
    val value: Long,
    val loadGrams: Long? = null,
)

/**
 * Finds when records were set (Metric.PERSONAL_RECORDS). History is read oldest first (by start time);
 * the first session of a variant only sets the baseline, so it is never a record. A record must be
 * strictly better than everything before it:
 *
 * - heaviest load: the heaviest weight of a counted set with at least one rep;
 * - estimated 1RM: the best Epley estimate of a counted set;
 * - reps at a load: more reps than ever done at that load or heavier (one event per session, at the
 *   heaviest load that qualifies). A brand-new heaviest load is a load record, not a reps record;
 * - session volume: the variant's volume in that session.
 *
 * Bands and machines logged by plate number are left out (their numbers are not weights).
 */
object PrDetector {

    private class Performance(val sessionId: String, val at: Long, val sets: List<Pair<Long, Int>>, val volume: Long)

    /** Records of one variant's [history] (any order), oldest first. */
    fun events(history: List<ExerciseHistoryEntry>, bilateralSlots: Set<String> = emptySet()): List<PrEvent> {
        val variantId = history.firstOrNull()?.exercise?.variantId ?: return emptyList()
        val performances = history.groupBy { it.sessionId }.values
            .map { entries ->
                val first = entries.first()
                Performance(
                    sessionId = first.sessionId,
                    at = first.sessionStartedAt,
                    sets = entries.flatMap { e ->
                        e.exercise.sets.filter { TrainingMath.isCounted(it) && (it.reps ?: 0) > 0 }
                            .mapNotNull { set -> TrainingMath.weightGrams(set)?.let { it to (set.reps ?: 0) } }
                    },
                    volume = entries.sumOf { TrainingMath.volume(it.exercise, bilateralSlots) },
                )
            }
            .filter { it.sets.isNotEmpty() }
            .sortedWith(compareBy<Performance> { it.at }.thenBy { it.sessionId })
        return detect(variantId, performances)
    }

    private fun detect(variantId: String, performances: List<Performance>): List<PrEvent> {
        val events = mutableListOf<PrEvent>()
        var heaviest: Long? = null
        var bestOneRm = 0L
        var bestVolume = 0L
        val maxRepsAtLoad = mutableMapOf<Long, Int>()
        performances.forEachIndexed { index, p ->
            val load = p.sets.maxOf { it.first }
            val oneRm = p.sets.maxOf { (l, r) -> Stats.epleyOneRm(l, r) }
            if (index > 0) {
                fun add(kind: PrKind, value: Long, at: Long? = null) =
                    events.add(PrEvent(p.sessionId, p.at, variantId, kind, value, at))
                val previousHeaviest = heaviest
                if (previousHeaviest != null && load > previousHeaviest) add(PrKind.HEAVIEST_LOAD, load)
                if (oneRm > bestOneRm && bestOneRm > 0) add(PrKind.ESTIMATED_ONE_RM, oneRm)
                repsRecord(p.sets, maxRepsAtLoad)?.let { (l, r) -> add(PrKind.REPS_AT_LOAD, r.toLong(), l) }
                if (p.volume > bestVolume && bestVolume > 0) add(PrKind.SESSION_VOLUME, p.volume)
            }
            heaviest = maxOf(heaviest ?: load, load)
            bestOneRm = maxOf(bestOneRm, oneRm)
            bestVolume = maxOf(bestVolume, p.volume)
            p.sets.forEach { (l, r) -> maxRepsAtLoad[l] = maxOf(maxRepsAtLoad[l] ?: 0, r) }
        }
        return events
    }

    /** The heaviest (load, reps) beating the most reps done before at that load or heavier, if any. */
    private fun repsRecord(sets: List<Pair<Long, Int>>, before: Map<Long, Int>): Pair<Long, Int>? =
        sets.filter { (load, reps) ->
            val best = before.filterKeys { it >= load }.values.maxOrNull()
            best != null && reps > best
        }.maxWithOrNull(compareBy<Pair<Long, Int>> { it.first }.thenBy { it.second })

    /** Records of every variant in [sessions] (finished ones), oldest first. */
    fun eventsForSessions(sessions: List<WorkoutSession>, bilateralSlots: Set<String> = emptySet()): List<PrEvent> =
        sessions.filter { !it.inProgress }
            .flatMap { s -> s.exercises.map { ExerciseHistoryEntry(s.id, s.startedAt, it) } }
            .groupBy { it.exercise.variantId }
            .values
            .flatMap { events(it, bilateralSlots) }
            .sortedBy { it.at }
}
