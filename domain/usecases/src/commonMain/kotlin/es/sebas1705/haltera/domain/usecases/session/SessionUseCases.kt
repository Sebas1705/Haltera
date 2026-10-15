package es.sebas1705.haltera.domain.usecases.session

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.ProgressionRule
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.repository.RoutineRepository
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.domain.usecases.cycle.Cycle
import es.sebas1705.haltera.domain.usecases.cycle.NextDay
import es.sebas1705.haltera.domain.usecases.progression.ProgressionEngine
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Load suggestion for a slot with the variant chosen, from its own history (spec §3.5). [before] limits
 * the history to sessions started before it (a session's own start), so a past session never looks at
 * what came after it; null uses the whole history.
 */
class SuggestNextLoad(private val sessions: SessionRepository) {
    suspend operator fun invoke(slot: RoutineSlot, variantId: String, before: Long? = null): Suggestion {
        val needed = when (val rule = slot.progression) {
            ProgressionRule.None -> 1
            is ProgressionRule.DoubleProgression -> rule.sessionsAtTop
            is ProgressionRule.Linear -> rule.everyNSessions
        }
        val history = sessions.lastForSlotAndVariant(
            slot.id,
            variantId,
            limit = needed.coerceAtLeast(1) + HISTORY_MARGIN,
            before = before,
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

/**
 * The active routine with its next day, derived from the routine's most recent session (spec §3.4,
 * [Cycle.pointer]). Updates when the routine or any session changes.
 */
class ObserveTodayPlan(private val routines: RoutineRepository, private val sessions: SessionRepository) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<TodayPlan?> = routines.observeActive().flatMapLatest { routine ->
        if (routine == null) {
            flowOf(null)
        } else {
            sessions.observeLatestForRoutine(routine.id).map { latest ->
                TodayPlan(routine, Cycle.nextPendingDay(routine, latest))
            }
        }
    }
}

/** What a logged past session trained: a day of a routine, or nothing planned. */
sealed interface PastSessionTarget {
    data class RoutineDay(val routineId: String, val dayIndex: Int) : PastSessionTarget

    data object Free : PastSessionTarget
}

/** One choice of the "what was trained" list: a training day of a routine. */
data class PastSessionOption(
    val target: PastSessionTarget.RoutineDay,
    val routineName: String,
    val dayName: String,
    val activeRoutine: Boolean,
)

/**
 * Logs a session that already happened (any date, any routine day or free): it is created with its start
 * and end and saved straight away, so leaving the editor never loses it. Its sets are then logged on the
 * saved session, which stays editable. Nothing is restricted: the user logs the truth.
 */
class LogPastSession(private val sessions: SessionRepository) {
    /** Creates the session started at [startedAt] lasting [durationMillis] and returns its id. */
    suspend operator fun invoke(target: PastSessionTarget, startedAt: Long, durationMillis: Long): String {
        val id = when (target) {
            is PastSessionTarget.RoutineDay -> sessions.startFromRoutineDay(
                target.routineId,
                target.dayIndex,
                startedAt
            )
            PastSessionTarget.Free -> sessions.startFree(startedAt)
        }
        sessions.finish(id, endedAt = startedAt + durationMillis.coerceAtLeast(0))
        return id
    }

    companion object {
        const val DEFAULT_DURATION_MINUTES = 60

        /**
         * The training days that can be logged: the active routine's first, then the others (in [routines]
         * order). Rest days are left out.
         */
        fun options(routines: List<Routine>): List<PastSessionOption> =
            routines.sortedByDescending { it.isActive }.flatMap { routine ->
                routine.days.sortedBy { it.position }.mapIndexedNotNull { index, day ->
                    if (day.kind == DayKind.REST) {
                        null
                    } else {
                        PastSessionOption(
                            PastSessionTarget.RoutineDay(routine.id, index),
                            routine.name,
                            day.name,
                            routine.isActive,
                        )
                    }
                }
            }
    }
}
