package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import kotlin.math.roundToLong

/** A record value with where it happened. */
data class Record(val value: Long, val sessionId: String, val at: Long)

/**
 * Personal records of one variant (spec §7). Loads and 1RM in grams; volume in gram-reps.
 * Null when there is no qualifying set.
 */
data class PersonalRecords(
    val heaviestLoad: Record?,
    val bestEstimatedOneRm: Record?,
    val mostReps: Record?,
    val bestSessionVolume: Record?,
)

/** Training statistics (spec §4 `stats`). Pure functions. */
object Stats {
    private const val EPLEY_DIVISOR = 30.0

    /**
     * Epley estimated one-rep max: load × (1 + reps / 30). One rep returns the load itself; zero or
     * negative reps or load return 0.
     */
    fun epleyOneRm(loadGrams: Long, reps: Int): Long = when {
        reps <= 0 || loadGrams <= 0 -> 0
        reps == 1 -> loadGrams
        else -> (loadGrams * (1 + reps / EPLEY_DIVISOR)).roundToLong()
    }

    /** Sets that count for stats: completed and not warm-ups. */
    fun countedSets(sets: List<SetLog>): List<SetLog> = sets.filter { it.completed && it.kind != SetKind.WARMUP }

    /** Best estimated 1RM among the counted sets of a performed exercise, or 0. */
    fun bestOneRm(exercise: SessionExercise): Long =
        countedSets(exercise.sets).maxOfOrNull { epleyOneRm(it.loadGrams ?: 0, it.reps ?: 0) } ?: 0

    /**
     * Records over a variant's history (any order). Ties keep the earliest. The session volume follows the one
     * volume rule ([TrainingMath.volume]), so it agrees with Progress.
     */
    fun personalRecords(
        history: List<ExerciseHistoryEntry>
    ): PersonalRecords {
        val ordered = history.sortedBy { it.sessionStartedAt }
        fun best(value: (ExerciseHistoryEntry) -> Long?): Record? {
            var record: Record? = null
            for (entry in ordered) {
                val v = value(entry) ?: continue
                if (v > 0 && (record == null || v > record.value)) record = Record(v, entry.sessionId, entry.sessionStartedAt)
            }
            return record
        }
        return PersonalRecords(
            heaviestLoad = best { e -> countedSets(e.exercise.sets).mapNotNull { it.loadGrams }.maxOrNull() },
            bestEstimatedOneRm = best { e -> bestOneRm(e.exercise) },
            mostReps = best { e -> countedSets(e.exercise.sets).mapNotNull { it.reps?.toLong() }.maxOrNull() },
            bestSessionVolume = best { e -> TrainingMath.volume(e.exercise) },
        )
    }

    /**
     * Records that [current] beats compared to [previous] (the history before it): the record kinds
     * where the new value is strictly greater, for the finish summary.
     */
    fun newRecords(
        previous: List<ExerciseHistoryEntry>,
        current: ExerciseHistoryEntry
    ): Set<RecordKind> {
        val before = personalRecords(previous)
        val now = personalRecords(listOf(current))
        return buildSet {
            if (beats(now.heaviestLoad, before.heaviestLoad)) add(RecordKind.HEAVIEST_LOAD)
            if (beats(now.bestEstimatedOneRm, before.bestEstimatedOneRm)) add(RecordKind.ESTIMATED_ONE_RM)
            if (beats(now.mostReps, before.mostReps)) add(RecordKind.MOST_REPS)
            if (beats(now.bestSessionVolume, before.bestSessionVolume)) add(RecordKind.SESSION_VOLUME)
        }
    }

    private fun beats(candidate: Record?, best: Record?): Boolean =
        candidate != null && (best == null || candidate.value > best.value)
}

enum class RecordKind { HEAVIEST_LOAD, ESTIMATED_ONE_RM, MOST_REPS, SESSION_VOLUME }
