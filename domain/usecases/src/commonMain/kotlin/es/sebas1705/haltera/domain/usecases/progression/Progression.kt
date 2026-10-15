package es.sebas1705.ironlog.domain.usecases.progression

import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog

/** What the next session of a slot should load (spec §3.5). Loads are grams (or steps for bands/stacks). */
sealed interface Suggestion {
    /** No previous session of this slot with this variant. */
    data object FirstTime : Suggestion

    /** Repeat [loadGrams] (null when the last session logged no load, e.g. pure bodyweight). */
    data class Keep(val loadGrams: Long?) : Suggestion

    data class Increase(val fromGrams: Long, val toGrams: Long, val reason: IncreaseReason) : Suggestion
}

enum class IncreaseReason {
    /** Double progression: top of the rep range for the required sessions in a row. */
    TOP_OF_RANGE,

    /** Linear progression: the target was hit in the required sessions. */
    TARGET_HIT,
}

/**
 * The progression engine (spec §3.5). Pure: give it the slot, the variant the user will do and the
 * history of that slot, latest first. Entries with another variant or slot are ignored, so a flexible
 * slot keeps one streak per variant. Only completed WORKING sets count; warm-up, drop and failure sets
 * are ignored.
 */
object ProgressionEngine {

    fun suggest(slot: RoutineSlot, variantId: String, history: List<SessionExercise>): Suggestion {
        val sessions = history.filter { it.variantId == variantId && it.slotId == slot.id }
            .filter { workingSets(it).isNotEmpty() }
        val last = sessions.firstOrNull() ?: return Suggestion.FirstTime
        val lastLoad = workingLoad(last)
        return when (val rule = slot.progression) {
            ProgressionRule.None -> Suggestion.Keep(lastLoad)
            is ProgressionRule.DoubleProgression ->
                streakSuggestion(
                    slot,
                    sessions,
                    rule.sessionsAtTop,
                    rule.increment,
                    useTop = true,
                    IncreaseReason.TOP_OF_RANGE
                )
            is ProgressionRule.Linear ->
                streakSuggestion(
                    slot,
                    sessions,
                    rule.everyNSessions,
                    rule.increment,
                    useTop = false,
                    IncreaseReason.TARGET_HIT
                )
        }
    }

    /** Completed working sets of a performed exercise. */
    fun workingSets(exercise: SessionExercise): List<SetLog> =
        exercise.sets.filter { it.kind == SetKind.WORKING && it.completed }

    /** The load of the working sets: the lowest when they differ (conservative), null without load. */
    fun workingLoad(exercise: SessionExercise): Long? = workingSets(exercise).mapNotNull { it.loadGrams }.minOrNull()

    /**
     * Whether every working set reached the goal and at least [requiredSets] were done. With [useTop], a
     * rep range needs its max (double progression); otherwise its min (linear).
     */
    fun hitTarget(exercise: SessionExercise, prescription: Prescription, requiredSets: Int, useTop: Boolean): Boolean {
        val sets = workingSets(exercise)
        if (sets.isEmpty() || sets.size < requiredSets) return false
        return sets.all { set ->
            when (prescription) {
                is Prescription.RepRange -> (set.reps ?: 0) >= if (useTop) prescription.max else prescription.min
                is Prescription.Reps -> (set.reps ?: 0) >= prescription.reps
                is Prescription.Duration -> (set.durationS ?: 0) >= prescription.seconds
                is Prescription.Distance -> (set.distanceM ?: 0) >= prescription.meters
            }
        }
    }

    fun applyIncrement(loadGrams: Long, increment: LoadIncrement): Long = when (increment) {
        is LoadIncrement.Weight -> loadGrams + increment.grams
        is LoadIncrement.Steps -> loadGrams + increment.steps
    }

    @Suppress("LongParameterList")
    private fun streakSuggestion(
        slot: RoutineSlot,
        sessions: List<SessionExercise>,
        required: Int,
        increment: LoadIncrement,
        useTop: Boolean,
        reason: IncreaseReason,
    ): Suggestion {
        val lastLoad = workingLoad(sessions.first())
        val needed = required.coerceAtLeast(1)
        val window = sessions.take(needed)
        val eligible = window.size == needed &&
            lastLoad != null &&
            window.all { workingLoad(it) == lastLoad && hitTarget(it, slot.prescription, slot.sets, useTop) }
        return if (eligible && lastLoad != null) {
            Suggestion.Increase(lastLoad, applyIncrement(lastLoad, increment), reason)
        } else {
            Suggestion.Keep(lastLoad)
        }
    }
}
