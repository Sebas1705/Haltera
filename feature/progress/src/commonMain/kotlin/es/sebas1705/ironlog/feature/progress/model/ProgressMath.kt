package es.sebas1705.ironlog.feature.progress.model

import es.sebas1705.ironlog.data.models.BodyWeightEntry
import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.domain.usecases.stats.Stats
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

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

/** Volume of one muscle per week; [weeks] is aligned with the week starts it was computed for. */
internal data class MuscleVolume(val muscle: Muscle, val weeks: List<Long>) {
    val total: Long get() = weeks.sum()
}

/** Pure calculations behind the progress screens. */
internal object ProgressMath {
    const val MOVING_AVERAGE_WINDOW = 7
    const val TREND_DAYS = 30
    const val WEEKS_SHOWN = 4
    private const val DAYS_PER_WEEK = 7
    private const val MILLIS_PER_DAY = 86_400_000L

    fun summarize(session: WorkoutSession, routineNames: Map<String, String>): SessionSummary = SessionSummary(
        id = session.id,
        startedAt = session.startedAt,
        durationMillis = session.endedAt?.let { (it - session.startedAt).coerceAtLeast(0) },
        routineName = session.routineId?.let { routineNames[it] },
        dayName = session.dayName,
        exerciseCount = session.exercises.size,
        volumeGrams = session.exercises.sumOf { Stats.volume(it) },
    )

    /** Sessions grouped by the local day they started on, each day ordered by start time. */
    fun groupByDay(
        sessions: List<WorkoutSession>,
        routineNames: Map<String, String>,
        timeZone: TimeZone,
    ): Map<LocalDate, List<SessionSummary>> = sessions
        .sortedBy { it.startedAt }
        .groupBy({ it.startedAt.toLocalDate(timeZone) }, { summarize(it, routineNames) })

    /**
     * Per-session points of an exercise history (any order): heaviest counted load, best estimated 1RM
     * and volume. Several entries of the same session are merged; sessions without a value are skipped.
     */
    fun exerciseCharts(history: List<ExerciseHistoryEntry>): ExerciseCharts {
        val sessions = history.groupBy { it.sessionId }.values
            .map { entries -> entries.first().sessionStartedAt to entries }
            .sortedBy { it.first }
        fun series(value: (List<ExerciseHistoryEntry>) -> Long): List<ChartPoint> =
            sessions.mapNotNull { (at, entries) ->
                val v = value(entries)
                if (v > 0) ChartPoint(at, v.toDouble()) else null
            }
        return ExerciseCharts(
            topSet = series { entries ->
                entries.flatMap { Stats.countedSets(it.exercise.sets) }.maxOfOrNull { it.loadGrams ?: 0 } ?: 0
            },
            oneRm = series { entries -> entries.maxOf { Stats.bestOneRm(it.exercise) } },
            volume = series { entries -> entries.sumOf { Stats.volume(it.exercise) } },
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

    /** The Monday of each of the last [count] weeks ending with the week of [today], oldest first. */
    fun lastWeekStarts(today: LocalDate, count: Int = WEEKS_SHOWN): List<LocalDate> {
        val current = today.weekStart()
        return (count - 1 downTo 0).map { current.plus(-it * DAYS_PER_WEEK, DateTimeUnit.DAY) }
    }

    /**
     * Volume per primary muscle and week. Each exercise's volume counts in full for every primary
     * muscle of its exercise. Sessions in progress or outside the weeks are ignored. Muscles with no
     * volume are dropped; the rest are sorted by total volume, highest first.
     */
    fun weeklyMuscleVolume(
        sessions: List<WorkoutSession>,
        musclesByVariant: Map<String, List<Muscle>>,
        weekStarts: List<LocalDate>,
        timeZone: TimeZone,
    ): List<MuscleVolume> {
        val totals = mutableMapOf<Muscle, LongArray>()
        val performed = sessions.filter { !it.inProgress }.flatMap { session ->
            val week = weekStarts.indexOf(session.startedAt.toLocalDate(timeZone).weekStart())
            if (week < 0) emptyList() else session.exercises.map { week to it }
        }
        performed.forEach { (week, exercise) ->
            val volume = Stats.volume(exercise)
            val muscles = if (volume > 0) musclesByVariant[exercise.variantId].orEmpty().distinct() else emptyList()
            muscles.forEach { muscle -> totals.getOrPut(muscle) { LongArray(weekStarts.size) }[week] += volume }
        }
        return totals.map { (muscle, weeks) -> MuscleVolume(muscle, weeks.toList()) }
            .sortedWith(compareByDescending<MuscleVolume> { it.total }.thenBy { it.muscle.ordinal })
    }
}
