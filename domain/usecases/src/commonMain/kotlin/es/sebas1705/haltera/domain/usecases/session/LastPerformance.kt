package es.sebas1705.haltera.domain.usecases.session

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.domain.usecases.stats.TrainingMath

/**
 * What a variant's first set of a new session starts from (agreed with the owner on 2026-10-15): the best
 * set of the **most recent calendar day** the same variant (exercise + equipment) was done, in any context
 * (a routine slot, a free session, a session logged afterwards).
 *
 * - [latest] is the most recent performance (warm-ups are prefilled from it).
 * - [best] is the heaviest counted set (completed, not a warm-up: sets to failure and drop sets count, as in
 *   the records) over every performance of that day; without loads (time, reps only) the latest counted set.
 * - [day] is that calendar day (an epoch day in the user's time zone).
 */
data class LastPerformance(val latest: SessionExercise, val best: SetLog?, val day: Int) {
    companion object {
        /**
         * The last performance in [history] (one variant, latest first, only sessions before the session being
         * filled). Performances without a counted set (left empty, warm-ups only) say nothing and are skipped.
         * [dayOf] maps a session start (epoch millis) to its local calendar day.
         */
        fun from(history: List<ExerciseHistoryEntry>, dayOf: (Long) -> Int): LastPerformance? {
            val withWork = history.sortedByDescending { it.sessionStartedAt }
                .filter { entry -> entry.exercise.sets.any(TrainingMath::isCounted) }
            val latest = withWork.firstOrNull() ?: return null
            val day = dayOf(latest.sessionStartedAt)
            val sameDay = withWork.takeWhile { dayOf(it.sessionStartedAt) == day }
            // Oldest first, so "the latest counted set" (no loads) is the last one of the day.
            val sets = sameDay.reversed().flatMap { entry -> entry.exercise.sets.sortedBy { it.position } }
            return LastPerformance(latest.exercise, bestSet(sets), day)
        }

        /**
         * The heaviest counted set of [sets] (the most reps among equally heavy ones, then the latest); without
         * any load, the last counted set. Higher is heavier for every load kind: added weight, a less negative
         * assistance, a higher band level or stack position.
         */
        fun bestSet(sets: List<SetLog>): SetLog? {
            val counted = sets.filter(TrainingMath::isCounted)
            val loaded = counted.filter { it.loadGrams != null }
            if (loaded.isEmpty()) return counted.lastOrNull()
            return loaded.withIndex()
                .maxWith(
                    compareBy<IndexedValue<SetLog>> { requireNotNull(it.value.loadGrams) }
                        .thenBy { it.value.reps ?: 0 }
                        .thenBy { it.index },
                ).value
        }

        /** Warm-ups of a performance that were done, in order. */
        fun warmups(exercise: SessionExercise?): List<SetLog> =
            exercise?.sets.orEmpty().filter { it.kind == SetKind.WARMUP && it.completed }.sortedBy { it.position }
    }
}
