package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SetLog

/** The best set of a session: its load (null for an unloaded set) and reps. */
data class BestSet(val loadGrams: Long?, val reps: Int)

/** One session of an exercise (Metric.PROGRESSION_TABLE). Loads and 1RM in grams, volume in gram-reps. */
data class ProgressionRow(
    val sessionId: String,
    val at: Long,
    val bestSet: BestSet?,
    /** Best Epley estimate of the session, 0 without a weighted set. */
    val oneRm: Long,
    /** Heaviest weight lifted for at least one rep, or null. */
    val topLoad: Long?,
    val volume: Long,
    val effectiveSets: Int,
    /** Estimated 1RM change against the previous session that had one; null for the first. */
    val oneRmDelta: Long?,
    val prs: Set<PrKind>,
)

/** Repetition buckets of Metric.REP_RANGES; [max] is null for the open last one. */
enum class RepRange(val min: Int, val max: Int?) {
    R1_5(1, 5),
    R6_8(6, 8),
    R9_12(9, 12),
    R13_20(13, 20),
    R21_PLUS(21, null),
    ;

    operator fun contains(reps: Int): Boolean = reps >= min && (max == null || reps <= max)

    companion object {
        fun of(reps: Int): RepRange? = entries.firstOrNull { reps in it }
    }
}

/** The e1RM trend of Metric.ONE_RM_TREND: [trend] has x in days since the epoch and y in grams. */
data class OneRmTrend(val trend: Trend, val firstAt: Long, val lastAt: Long) {
    /** Grams gained (or lost, negative) per 7 days according to the fitted line. */
    val gramsPerWeek: Double get() = trend.slope * EpochDays.DAYS_PER_WEEK
}

/** Per-exercise progress: the progression table, rep ranges and the e1RM trend. */
object ExerciseProgression {
    const val MIN_TREND_SESSIONS = 3
    const val MILLIS_PER_DAY = 86_400_000.0

    /** One row per session of [history] (one variant, any order), latest first. */
    fun table(history: List<ExerciseHistoryEntry>): List<ProgressionRow> {
        val prs = PrDetector.events(history).groupBy({ it.sessionId }, { it.kind })
        val sessions = history.groupBy { it.sessionId }.values
            .map { entries -> entries.first() to entries }
            .sortedWith(compareBy({ it.first.sessionStartedAt }, { it.first.sessionId }))
        var previousOneRm: Long? = null
        val rows = sessions.mapNotNull { (first, entries) ->
            val sets = entries.flatMap { e -> e.exercise.sets.filter { TrainingMath.isCounted(it) } }
            val withReps = sets.filter { (it.reps ?: 0) > 0 }
            val effective = entries.sumOf { TrainingMath.effectiveSets(it.exercise) }
            if (effective == 0) return@mapNotNull null
            val oneRm = withReps.maxOfOrNull { TrainingMath.setOneRm(it) } ?: 0
            val row = ProgressionRow(
                sessionId = first.sessionId,
                at = first.sessionStartedAt,
                bestSet = bestSet(withReps),
                oneRm = oneRm,
                topLoad = withReps.mapNotNull { TrainingMath.weightGrams(it) }.maxOrNull(),
                volume = entries.sumOf { TrainingMath.volume(it.exercise) },
                effectiveSets = effective,
                oneRmDelta = if (oneRm > 0) previousOneRm?.let { oneRm - it } else null,
                prs = prs[first.sessionId].orEmpty().toSet(),
            )
            if (oneRm > 0) previousOneRm = oneRm
            row
        }
        return rows.reversed()
    }

    /** The set with the best estimated 1RM; ties (and unloaded sets) go to the heavier, then more reps. */
    private fun bestSet(sets: List<SetLog>): BestSet? = sets
        .maxWithOrNull(
            compareBy<SetLog> { TrainingMath.setOneRm(it) }
                .thenBy { TrainingMath.weightGrams(it) ?: Long.MIN_VALUE }
                .thenBy { it.reps ?: 0 },
        )
        ?.let { BestSet(TrainingMath.weightGrams(it), it.reps ?: 0) }

    /** Effective sets with reps of [history] per [RepRange], every range present, in order. */
    fun repRanges(history: List<ExerciseHistoryEntry>): Map<RepRange, Int> {
        val counts = history.flatMap { it.exercise.sets }
            .filter { TrainingMath.isEffective(it) }
            .mapNotNull { set -> set.reps?.let { RepRange.of(it) } }
            .groupingBy { it }
            .eachCount()
        return RepRange.entries.associateWith { counts[it] ?: 0 }
    }

    /**
     * Least-squares line through the sessions' estimated 1RM against time ([rows] in any order). Null
     * with fewer than [MIN_TREND_SESSIONS] sessions with an estimate, or when they share one instant.
     */
    fun oneRmTrend(rows: List<ProgressionRow>): OneRmTrend? {
        val points = rows.filter { it.oneRm > 0 }.sortedBy { it.at }
        if (points.size < MIN_TREND_SESSIONS) return null
        val trend = LinearRegression.fit(points.map { it.at / MILLIS_PER_DAY to it.oneRm.toDouble() }) ?: return null
        return OneRmTrend(trend, points.first().at, points.last().at)
    }
}

/** The most reps done at [loadGrams] (first time), part of the reps-at-a-load records. */
data class RepRecord(val loadGrams: Long, val reps: Int, val sessionId: String, val at: Long)

/** Records of one variant as Metric.PERSONAL_RECORDS defines them. */
data class ExerciseRecords(
    val heaviestLoad: Record?,
    val bestOneRm: Record?,
    val bestVolume: Record?,
    /** Heaviest first; each entry has more reps than every heavier load (so none is dominated). */
    val repsAtLoad: List<RepRecord>,
)

/** All-time records of one variant's [history]; ties keep the earliest. */
fun ExerciseProgression.records(
    history: List<ExerciseHistoryEntry>
): ExerciseRecords {
    val rows = table(history).sortedBy { it.at }
    fun best(value: (ProgressionRow) -> Long?): Record? = rows
        .mapNotNull { row -> value(row)?.takeIf { it > 0 }?.let { Record(it, row.sessionId, row.at) } }
        .fold(null as Record?) { acc, r -> if (acc == null || r.value > acc.value) r else acc }
    val bestRepsPerLoad = history.sortedBy { it.sessionStartedAt }
        .flatMap { e ->
            e.exercise.sets.filter { TrainingMath.isCounted(it) && (it.reps ?: 0) > 0 }
                .mapNotNull { set ->
                    TrainingMath.weightGrams(
                        set
                    )?.let { RepRecord(it, set.reps ?: 0, e.sessionId, e.sessionStartedAt) }
                }
        }
        .groupBy { it.loadGrams }
        .map { (_, records) -> records.reduce { acc, r -> if (r.reps > acc.reps) r else acc } }
        .sortedByDescending { it.loadGrams }
    val frontier = mutableListOf<RepRecord>()
    bestRepsPerLoad.forEach { record -> if (frontier.isEmpty() || record.reps > frontier.last().reps) frontier += record }
    return ExerciseRecords(
        heaviestLoad = best { it.topLoad },
        bestOneRm = best { it.oneRm },
        bestVolume = best { it.volume },
        repsAtLoad = frontier,
    )
}
