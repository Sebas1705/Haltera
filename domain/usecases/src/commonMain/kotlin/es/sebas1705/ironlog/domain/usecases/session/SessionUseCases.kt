package es.sebas1705.ironlog.domain.usecases.session

import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.domain.usecases.cycle.Cycle
import es.sebas1705.ironlog.domain.usecases.cycle.NextDay
import es.sebas1705.ironlog.domain.usecases.progression.ProgressionEngine
import es.sebas1705.ironlog.domain.usecases.progression.Suggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Load suggestion for a slot with the variant chosen for today, from its own history (spec §3.5). */
class SuggestNextLoad(private val sessions: SessionRepository) {
    suspend operator fun invoke(slot: RoutineSlot, variantId: String): Suggestion {
        val needed = when (val rule = slot.progression) {
            ProgressionRule.None -> 1
            is ProgressionRule.DoubleProgression -> rule.sessionsAtTop
            is ProgressionRule.Linear -> rule.everyNSessions
        }
        val history = sessions.lastForSlotAndVariant(
            slot.id,
            variantId,
            limit = needed.coerceAtLeast(1) + HISTORY_MARGIN
        )
        return ProgressionEngine.suggest(slot, variantId, history.map { it.exercise })
    }

    private companion object {
        /** Extra sessions loaded in case the latest ones have no completed working sets. */
        const val HISTORY_MARGIN = 5
    }
}

/** The active routine and its next pending training day, for the Today tab. */
data class TodayPlan(val routine: Routine, val next: NextDay?)

class ObserveTodayPlan(private val routines: RoutineRepository) {
    operator fun invoke(): Flow<TodayPlan?> = routines.observeActive().map { routine ->
        routine?.let { TodayPlan(it, Cycle.nextPendingDay(it)) }
    }
}
