package es.sebas1705.haltera.domain.usecases.session

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.domain.usecases.stats.TrainingMath

/** How an exercise went compared with the last time the same variant was done. */
enum class Trend {
    /** No earlier performance of this variant: a baseline. */
    FIRST_TIME,
    BETTER,
    SAME,
    LOWER,

    /** Not comparable (e.g. a weight then, a machine stack position now): both sets are shown, no verdict. */
    UNCOMPARABLE,
}

/** [current] (the best set today) against [previous] (the best set of the last day), with the [trend]. */
data class LastTimeComparison(val trend: Trend, val current: SetLog, val previous: SetLog?)

/**
 * "Compared with last time" of the session summary: an exercise's best set today against the best set of the last
 * calendar day the same variant was done (as [LastPerformance] picks the day). Plain sets the user recognises
 * instead of a volume: what counts as "best" follows what the exercise tracks, so no weight is invented for
 * bodyweight, timed or distance work.
 *
 * - Weight and reps: the heavier set, then more reps at that weight. Bodyweight without added load is 0, an
 *   assisted set is negative (less assistance is better); band levels and machine stack positions compare as
 *   levels, but never against a weight.
 * - Reps only: more reps. Time: longer. Weight and time: heavier, then longer. Distance and time: farther, then
 *   faster.
 * - Only counted sets (completed, not warm-ups) count; other variants are another history (the caller passes
 *   this variant's history only).
 */
object LastTimeComparisons {

    /** Null when [exercise] has no counted set (nothing was done). */
    fun compare(
        exercise: SessionExercise,
        history: List<ExerciseHistoryEntry>,
        trackingType: TrackingType,
        loadType: LoadType,
        dayOf: (Long) -> Int,
    ): LastTimeComparison? {
        val current = best(exercise.sets, trackingType, loadType) ?: return null
        val previousDay = LastPerformance.from(history, dayOf)
            ?: return LastTimeComparison(Trend.FIRST_TIME, current, null)
        val day = previousDay.day
        val daySets = history.filter { dayOf(it.sessionStartedAt) == day }.flatMap { it.exercise.sets }
        val previous = best(daySets, trackingType, loadType)
            ?: return LastTimeComparison(Trend.FIRST_TIME, current, null)
        return LastTimeComparison(trend(current, previous, trackingType, loadType), current, previous)
    }

    /** The best counted set of [sets] for what the exercise tracks; null without counted sets. */
    fun best(sets: List<SetLog>, trackingType: TrackingType, loadType: LoadType): SetLog? {
        val counted = sets.filter(TrainingMath::isCounted).sortedBy { it.position }
        if (counted.isEmpty()) return null
        // Prefer sets whose load is comparable with each other: weights over levels when both were logged.
        val pool = counted.filter { !TrainingMath.isIndexLoad(it) }.ifEmpty { counted }
        val byKey = Comparator<IndexedValue<SetLog>> { a, b ->
            compareKeys(key(a.value, trackingType, loadType), key(b.value, trackingType, loadType))
        }
        // Equal sets: the latest one.
        return pool.withIndex().maxWith(byKey.thenBy { it.index }).value
    }

    private fun trend(current: SetLog, previous: SetLog, trackingType: TrackingType, loadType: LoadType): Trend {
        val usesLoad = usesLoad(trackingType, loadType)
        if (usesLoad && !loadsComparable(current, previous, loadType)) return Trend.UNCOMPARABLE
        val order = compareKeys(key(current, trackingType, loadType), key(previous, trackingType, loadType))
        return when {
            order > 0 -> Trend.BETTER
            order < 0 -> Trend.LOWER
            else -> Trend.SAME
        }
    }

    private fun usesLoad(trackingType: TrackingType, loadType: LoadType) =
        trackingType.tracksLoad && loadType != LoadType.NONE

    /** Both weights, or both levels of the same kind; a missing load only matches bodyweight (0). */
    private fun loadsComparable(a: SetLog, b: SetLog, loadType: LoadType): Boolean {
        if (TrainingMath.isIndexLoad(a) != TrainingMath.isIndexLoad(b)) return false
        if (loadType == LoadType.BODYWEIGHT) return true
        return (a.loadGrams == null) == (b.loadGrams == null)
    }

    /** What "better" means for the exercise, most important first. */
    private fun key(set: SetLog, trackingType: TrackingType, loadType: LoadType): List<Long> {
        val load = if (usesLoad(trackingType, loadType)) {
            set.loadGrams ?: if (loadType == LoadType.BODYWEIGHT) 0L else Long.MIN_VALUE
        } else {
            null
        }
        val reps = (set.reps ?: 0).toLong()
        val duration = (set.durationS ?: 0).toLong()
        val distance = (set.distanceM ?: 0).toLong()
        return when {
            trackingType.tracksDistance -> listOf(distance, -duration)
            trackingType.tracksDuration -> listOfNotNull(load, duration)
            else -> listOfNotNull(load, reps)
        }
    }

    private fun compareKeys(a: List<Long>, b: List<Long>): Int {
        a.zip(b).forEach { (x, y) -> if (x != y) return x.compareTo(y) }
        return 0
    }
}
