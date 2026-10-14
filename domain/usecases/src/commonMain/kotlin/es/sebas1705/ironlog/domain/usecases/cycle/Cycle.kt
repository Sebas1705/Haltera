package es.sebas1705.ironlog.domain.usecases.cycle

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay

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

/** Cycle position logic (spec §3.4): sequential, never date-anchored. Pure functions. */
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

    fun nextPendingDay(routine: Routine): NextDay? = nextPendingDay(routine.days, routine.nextDayIndex)

    /** Pointer after completing day [dayIndex] of a cycle of [dayCount] days: k + 1 (mod N). */
    fun pointerAfter(dayIndex: Int, dayCount: Int): Int = if (dayCount <= 0) 0 else (dayIndex + 1).mod(dayCount)
}
