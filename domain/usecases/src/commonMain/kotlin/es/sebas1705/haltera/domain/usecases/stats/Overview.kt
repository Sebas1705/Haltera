package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Routine

/** Training totals of a range of days. Durations are milliseconds; volume gram-reps. */
data class StatsBucket(
    val window: DayWindow,
    val sessions: Int = 0,
    val volume: Long = 0,
    val effectiveSets: Int = 0,
    val trainingTimeMillis: Long = 0,
    /** Sessions with a duration (all finished sessions). */
    val timedSessions: Int = 0,
) {
    /** [value] scaled to 7 days (Metric.FREQUENCY): a 28-day bucket reports its weekly average. */
    fun perWeek(value: Double): Double = value * EpochDays.DAYS_PER_WEEK / window.length

    /** Mean session length (Metric.SESSION_DURATION), or null without sessions. */
    val averageDurationMillis: Double?
        get() = if (timedSessions == 0) null else trainingTimeMillis.toDouble() / timedSessions
}

/**
 * Sessions logged against the active routine's plan (Metric.ADHERENCE), both per 7 days. [percent] can
 * be above 100: it describes, it does not grade.
 */
data class Adherence(val plannedPerWeek: Double, val loggedPerWeek: Double) {
    val percent: Double get() = loggedPerWeek / plannedPerWeek * PERCENT

    private companion object {
        const val PERCENT = 100.0
    }
}

/** Everything the Overview tab shows for one period. */
data class OverviewStats(
    val period: StatsPeriod,
    val current: DayWindow,
    val previous: DayWindow?,
    val sessions: Comparison,
    val volume: Comparison,
    val effectiveSets: Comparison,
    val trainingTimeMillis: Comparison,
    val newPrs: Comparison,
    /** Null when the period has no finished session. */
    val averageDurationMillis: Comparison?,
    /** The period split in buckets (7 days, or 28 for a long "all"), oldest first. */
    val buckets: List<StatsBucket>,
    /** The comparison period split the same way (aligned by index), or empty. */
    val previousBuckets: List<StatsBucket>,
    /** Null without an active routine or when its cycle has no weekly shape (see [Overview.plannedPerWeek]). */
    val adherence: Adherence?,
)

/** The Overview tab's maths: this period against the previous equal period. */
object Overview {

    /**
     * [sessions] are every logged session with its local day (in-progress ones are ignored); [today] is
     * the local epoch day. Records are detected over the whole history, then counted in each window.
     */
    fun build(
        period: StatsPeriod,
        today: Int,
        sessions: List<DatedSession>,
        bilateralSlots: Set<String> = emptySet(),
        activeRoutine: Routine? = null,
    ): OverviewStats {
        val finished = sessions.filter { !it.session.inProgress }
        val current = Periods.current(period, today, finished.minOfOrNull { it.day })
        val previous = Periods.previous(period, current)
        val dayOfSession = finished.associate { it.session.id to it.day }
        val prDays = PrDetector.eventsForSessions(finished.map { it.session }, bilateralSlots)
            .mapNotNull { dayOfSession[it.sessionId] }
        val now = totals(current, finished, bilateralSlots)
        val before = previous?.let { totals(it, finished, bilateralSlots) }
        fun compare(value: (StatsBucket) -> Double) = Comparison(value(now), before?.let(value))
        val size = Periods.bucketDays(current)
        return OverviewStats(
            period = period,
            current = current,
            previous = previous,
            sessions = compare { it.sessions.toDouble() },
            volume = compare { it.volume.toDouble() },
            effectiveSets = compare { it.effectiveSets.toDouble() },
            trainingTimeMillis = compare { it.trainingTimeMillis.toDouble() },
            newPrs = Comparison(
                prDays.count { it in current }.toDouble(),
                previous?.let { window -> prDays.count { it in window }.toDouble() },
            ),
            averageDurationMillis = now.averageDurationMillis?.let { Comparison(it, before?.averageDurationMillis) },
            buckets = Periods.buckets(current, size).map { totals(it, finished, bilateralSlots) },
            previousBuckets = previous
                ?.let { window -> Periods.buckets(window, size).map { totals(it, finished, bilateralSlots) } }
                .orEmpty(),
            adherence = activeRoutine?.let(::plannedPerWeek)?.let { planned ->
                Adherence(planned, now.sessions / current.weeks)
            },
        )
    }

    /** Totals of the finished [sessions] whose day falls in [window]. */
    fun totals(window: DayWindow, sessions: List<DatedSession>, bilateralSlots: Set<String> = emptySet()): StatsBucket {
        val inside = sessions.filter { it.day in window && !it.session.inProgress }.map { it.session }
        val durations = inside.mapNotNull(TrainingMath::durationMillis)
        return StatsBucket(
            window = window,
            sessions = inside.size,
            volume = inside.sumOf { TrainingMath.volume(it, bilateralSlots) },
            effectiveSets = inside.sumOf { TrainingMath.effectiveSets(it) },
            trainingTimeMillis = durations.sum(),
            timedSessions = durations.size,
        )
    }

    /**
     * Planned sessions per 7 days of a routine (Metric.ADHERENCE): training days × 7 / cycle length,
     * reading the cycle as one routine day per calendar day. Null when the routine has no training day
     * or no rest day: a cycle without rest days does not describe a week (it is followed in order,
     * whenever you train), so no weekly plan can be derived from it.
     */
    fun plannedPerWeek(routine: Routine): Double? {
        val days = routine.days
        val training = days.count { it.kind == DayKind.TRAINING }
        if (training == 0 || training == days.size) return null
        return training.toDouble() * EpochDays.DAYS_PER_WEEK / days.size
    }
}
