package es.sebas1705.haltera.feature.progress.model

import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.domain.usecases.stats.ProgressionRow
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.stats.TrainingMath
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** One row of a day's session list. [durationMillis] is null while the session is in progress. */
internal data class SessionSummary(
    val id: String,
    val startedAt: Long,
    val durationMillis: Long?,
    val routineName: String?,
    val dayName: String?,
    val exerciseCount: Int,
    val volumeGrams: Long,
) {
    val inProgress: Boolean get() = durationMillis == null
}

/** A point of a time chart: [x] is epoch millis, [y] a value in grams (or gram-reps). */
internal data class ChartPoint(val x: Long, val y: Double)

/** The three per-exercise charts, oldest first. */
internal data class ExerciseCharts(
    val topSet: List<ChartPoint> = emptyList(),
    val oneRm: List<ChartPoint> = emptyList(),
    val volume: List<ChartPoint> = emptyList(),
)

/** Body-weight chart: the raw entries and their moving average, oldest first. */
internal data class BodyWeightChart(
    val raw: List<ChartPoint> = emptyList(),
    val average: List<ChartPoint> = emptyList(),
)

/** Pure calculations behind the progress screens. */
internal object ProgressMath {
    const val MOVING_AVERAGE_WINDOW = 7
    const val TREND_DAYS = 30
    private const val MILLIS_PER_DAY = 86_400_000L

    /** The volume is the one of the overview (TrainingMath.volume). */
    fun summarize(
        session: WorkoutSession,
        routineNames: Map<String, String>,
    ): SessionSummary = SessionSummary(
        id = session.id,
        startedAt = session.startedAt,
        durationMillis = session.endedAt?.let { (it - session.startedAt).coerceAtLeast(0) },
        routineName = session.routineId?.let { routineNames[it] },
        dayName = session.dayName,
        // Only exercises with at least one completed set: empty ones are left over from the plan.
        exerciseCount = session.exercises.count { exercise -> exercise.sets.any { it.completed } },
        volumeGrams = TrainingMath.volume(session),
    )

    /** Sessions grouped by the local day they started on, each day ordered by start time. */
    fun groupByDay(
        sessions: List<WorkoutSession>,
        routineNames: Map<String, String>,
        timeZone: TimeZone,
    ): Map<LocalDate, List<SessionSummary>> = sessions
        .sortedBy { it.startedAt }
        .groupBy({ it.startedAt.toLocalDate(timeZone) }, { summarize(it, routineNames) })

    /** Points of the three exercise charts from progression [rows] (any order), oldest first; zero values are skipped. */
    fun exerciseCharts(rows: List<ProgressionRow>): ExerciseCharts {
        val ordered = rows.sortedBy { it.at }
        fun series(value: (ProgressionRow) -> Long?): List<ChartPoint> =
            ordered.mapNotNull { row -> value(row)?.takeIf { it > 0 }?.let { ChartPoint(row.at, it.toDouble()) } }
        return ExerciseCharts(
            topSet = series { it.topLoad },
            oneRm = series { it.oneRm },
            volume = series { it.volume },
        )
    }

    /** Trailing moving average: each value averages itself and up to [window] - 1 previous values. */
    fun movingAverage(values: List<Double>, window: Int = MOVING_AVERAGE_WINDOW): List<Double> {
        require(window > 0) { "window must be positive" }
        return values.indices.map { i ->
            val from = (i - window + 1).coerceAtLeast(0)
            values.subList(from, i + 1).average()
        }
    }

    fun bodyWeightChart(entries: List<BodyWeightEntry>): BodyWeightChart {
        val ordered = entries.sortedBy { it.measuredAt }
        val raw = ordered.map { ChartPoint(it.measuredAt, it.weightGrams.toDouble()) }
        val average = movingAverage(raw.map { it.y }).mapIndexed { i, y -> ChartPoint(raw[i].x, y) }
        return BodyWeightChart(raw, average)
    }

    /**
     * Change over the last [TREND_DAYS] days in grams: the latest entry minus the oldest entry measured
     * within the [TREND_DAYS] days before it. Null when there is no earlier entry in that window.
     */
    fun change(entries: List<BodyWeightEntry>, days: Int = TREND_DAYS): Long? {
        val latest = entries.maxByOrNull { it.measuredAt } ?: return null
        val windowStart = latest.measuredAt - days * MILLIS_PER_DAY
        val baseline = entries
            .filter { it.id != latest.id && it.measuredAt >= windowStart && it.measuredAt <= latest.measuredAt }
            .minByOrNull { it.measuredAt }
            ?: return null
        return latest.weightGrams - baseline.weightGrams
    }
}
