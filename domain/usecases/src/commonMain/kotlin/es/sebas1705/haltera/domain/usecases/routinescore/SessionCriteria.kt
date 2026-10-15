package es.sebas1705.haltera.domain.usecases.routinescore

import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.stats.Stats
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The criteria that need logged sessions (design §3.9–3.10): adherence over the last four weeks and load progress
 * over the last eight. Both are left out when the routine has no finished session in the last four weeks.
 */
internal class SessionCriteria(
    private val routine: Routine,
    private val plan: RoutinePlan,
    private val catalogue: ScoringCatalogue,
    sessions: List<WorkoutSession>,
    private val now: Long,
) {
    private val finished = sessions
        .filter { it.routineId == routine.id && it.endedAt != null && it.startedAt <= now }
        .sortedBy { it.startedAt }

    fun all(): List<Criterion> {
        if (finished.none { it.startedAt >= now - ADHERENCE_WINDOW_MS }) return emptyList()
        return listOfNotNull(adherence(), loadProgress())
    }

    // 3.9 Adherence: sessions done vs planned, and working sets done vs planned.
    private fun adherence(): Criterion? {
        val first = finished.first().startedAt
        if (now - first < MIN_HISTORY_MS || plan.sessionsPerWeek <= 0.0) return null
        val start = max(now - ADHERENCE_WINDOW_MS, first)
        val weeks = (now - start).toDouble() / WEEK_MS
        val planned = max(1, (plan.sessionsPerWeek * weeks).roundToInt())
        val recent = finished.filter { it.startedAt >= start }
        val completion = recent.mapNotNull(::setCompletion)
        val setRatio = if (completion.isEmpty()) 1.0 else completion.average()
        val sessionRatio = (recent.size.toDouble() / planned).coerceAtMost(1.0)
        val score = SESSION_SHARE * sessionRatio + (1 - SESSION_SHARE) * setRatio
        val advice = if (sessionRatio < GOOD_ADHERENCE) {
            listOf(
                Advice.FitTheSchedule(recent.size, planned)
            )
        } else {
            emptyList()
        }
        return Criterion(
            CriterionId.ADHERENCE,
            percent(score),
            RoutineScorer.WEIGHTS.getValue(CriterionId.ADHERENCE),
            Finding.Adherence(recent.size, planned, round1(setRatio), max(1, weeks.roundToInt())),
            advice,
            listOf(Reference.SCHOENFELD_2017_VOLUME, Reference.RATAMESS_2009_ACSM),
        )
    }

    /** Working sets done over the sets the routine day plans, capped at 1; null for a session without a known day. */
    private fun setCompletion(session: WorkoutSession): Double? {
        val day = session.routineDayIndex?.let { routine.days.getOrNull(it) } ?: return null
        val planned = day.slots.sumOf { it.sets }
        if (planned == 0) return null
        val done = session.exercises.sumOf { Stats.countedSets(it.sets).size }
        return (done.toDouble() / planned).coerceAtMost(1.0)
    }

    // 3.10 Load progress: best estimated 1RM (or reps, for bodyweight work) early vs late in the last 8 weeks.
    private fun loadProgress(): Criterion? {
        val window = finished.filter { it.startedAt >= now - PROGRESS_WINDOW_MS }
        val byVariant = window.flatMap { session -> session.exercises.map { session.startedAt to it } }
            .groupBy { (_, exercise) -> exercise.variantId }
        val tracked = byVariant.mapNotNull { (variantId, entries) -> trend(entries)?.let { variantId to it } }
        if (tracked.isEmpty()) return null
        val improved = tracked.count { (_, up) -> up }
        val stalled = tracked.filterNot { (_, up) -> up }
            .mapNotNull { (variantId, _) -> catalogue.exerciseOfVariant(variantId)?.id ?: variantId.substringBefore('.') }
            .distinct()
        val share = improved.toDouble() / tracked.size
        val weeks = max(1, ((now - window.first().startedAt).toDouble() / WEEK_MS).roundToInt())
        return Criterion(
            CriterionId.LOAD_PROGRESS,
            percent(share / FULL_PROGRESS_SHARE),
            RoutineScorer.WEIGHTS.getValue(CriterionId.LOAD_PROGRESS),
            Finding.LoadProgress(improved, tracked.size, weeks),
            if (stalled.isEmpty()) emptyList() else listOf(Advice.BreakPlateau(stalled.take(MAX_STALLED))),
            listOf(Reference.KRAEMER_2004, Reference.RATAMESS_2009_ACSM),
        )
    }

    /** Whether the best performance rose from the first third to the last third; null without enough data. */
    private fun trend(entries: List<Pair<Long, SessionExercise>>): Boolean? {
        val points = entries.mapNotNull { (at, exercise) ->
            performance(
                exercise
            )?.let { at to it }
        }.sortedBy { it.first }
        if (points.size < MIN_SESSIONS || points.last().first - points.first().first < MIN_SPAN_MS) return null
        val third = max(1, points.size / THIRDS)
        val early = points.take(third).map { it.second }.average()
        val late = points.takeLast(third).map { it.second }.average()
        return late > early * IMPROVEMENT
    }

    /** Best estimated 1RM in grams, or the most reps for unloaded work; null when nothing counted was logged. */
    private fun performance(exercise: SessionExercise): Double? {
        val counted = Stats.countedSets(exercise.sets)
        if (counted.isEmpty()) return null
        val oneRm = Stats.bestOneRm(exercise)
        if (oneRm > 0) return oneRm.toDouble()
        return counted.maxOf { it.reps ?: 0 }.takeIf { it > 0 }?.toDouble()
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        const val WEEK_MS = 7 * DAY_MS
        const val ADHERENCE_WINDOW_MS = 28 * DAY_MS
        const val PROGRESS_WINDOW_MS = 56 * DAY_MS
        private const val MIN_HISTORY_MS = 7 * DAY_MS
        private const val MIN_SPAN_MS = 14 * DAY_MS
        private const val MIN_SESSIONS = 3
        private const val THIRDS = 3
        private const val SESSION_SHARE = 0.6
        private const val GOOD_ADHERENCE = 0.75
        private const val IMPROVEMENT = 1.01
        private const val FULL_PROGRESS_SHARE = 0.7
        private const val MAX_STALLED = 3
    }
}
