package es.sebas1705.ironlog.domain.usecases.cycle

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
import es.sebas1705.ironlog.data.models.WorkoutSession

/**
 * The next day to train. [restDaysSkipped] are the REST days between the pointer and [dayIndex] (in
 * cycle order): when not empty, the plan says today is a rest day (spec §3.4, informational only).
 */
data class NextDay(
    val dayIndex: Int,
    val day: RoutineDay,
    val restDaysSkipped: List<Int>,
) {
    val planSaysRest: Boolean get() = restDaysSkipped.isNotEmpty()
}

/**
 * Cycle position logic (spec §3.4): sequential, never date-anchored. Pure functions.
 *
 * The pointer is derived, not stored: it is the day after the routine's most recent session by
 * `startedAt` (finished or not), so logging past days in any order, editing a session's date or deleting
 * one keeps it right. The stored `Routine.nextDayIndex` is only a manual override (Today's day picker),
 * stamped with `nextDayIndexSetAt`: it wins until a session of the routine starts after that instant.
 */
object Cycle {
    /** [pointer] clamped to a valid day index; 0 for an empty cycle. */
    fun clamp(pointer: Int, dayCount: Int): Int = if (dayCount <= 0) 0 else pointer.coerceIn(0, dayCount - 1)

    /**
     * The first TRAINING day at or after [pointer] (wrapping around), or null if the cycle has no
     * training day. An out-of-range pointer is clamped first.
     */
    fun nextPendingDay(days: List<RoutineDay>, pointer: Int): NextDay? {
        if (days.isEmpty()) return null
        val start = clamp(pointer, days.size)
        val skipped = mutableListOf<Int>()
        for (offset in days.indices) {
            val index = (start + offset) % days.size
            if (days[index].kind == DayKind.TRAINING) return NextDay(index, days[index], skipped)
            skipped += index
        }
        return null
    }

    /** Next pending day of [routine] given its most recent session ([latest], by `startedAt`). */
    fun nextPendingDay(routine: Routine, latest: WorkoutSession? = null): NextDay? =
        nextPendingDay(routine.days, pointer(routine, latest))

    /**
     * The cycle pointer: the day after [latest] (the routine's most recent session by `startedAt`), unless
     * the manual override was set after that session started (or there is no session yet).
     */
    fun pointer(routine: Routine, latest: WorkoutSession?): Int {
        val trainedIndex = latest?.takeIf { it.routineId == routine.id }?.routineDayIndex
        if (latest == null || trainedIndex == null) return routine.nextDayIndex
        val overrideAt = routine.nextDayIndexSetAt
        val sessionWins = overrideAt == null || latest.startedAt > overrideAt
        return if (sessionWins) pointerAfter(trainedIndex, routine.days.size) else routine.nextDayIndex
    }

    /** The routine's most recent session by `startedAt` among [sessions] (any order), or null. */
    fun latestOf(routineId: String, sessions: List<WorkoutSession>): WorkoutSession? =
        sessions.filter { it.routineId == routineId && it.routineDayIndex != null }.maxByOrNull { it.startedAt }

    /** Pointer after training day [dayIndex] of a cycle of [dayCount] days: k + 1 (mod N). */
    fun pointerAfter(dayIndex: Int, dayCount: Int): Int = if (dayCount <= 0) 0 else (dayIndex + 1).mod(dayCount)
}
