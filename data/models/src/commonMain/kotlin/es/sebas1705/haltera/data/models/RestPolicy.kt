package es.sebas1705.haltera.data.models

/**
 * How a routine rests (docs/designs/2026-10-16-flexible-rest-days.md).
 *
 * - [RestMode.FIXED]: rest days are REST positions of the cycle; the other fields are unused (0).
 * - [RestMode.FLEXIBLE]: the cycle holds training days only. Either the user knows the rest weekdays
 *   ([restWeekdays], a Monday-first bitmask: bit 0 = Monday … bit 6 = Sunday) or only how many days a week they
 *   rest ([restDaysPerWeek], 0–6, with [restWeekdays] = 0). When weekdays are known, [restDaysPerWeek] is their
 *   count ([normalized]).
 */
data class RestPolicy(
    val mode: RestMode = RestMode.FIXED,
    val restDaysPerWeek: Int = 0,
    val restWeekdays: Int = 0,
) {
    val isFlexible: Boolean get() = mode == RestMode.FLEXIBLE

    /** Flexible with at least one rest weekday chosen. */
    val knowsWeekdays: Boolean get() = isFlexible && (restWeekdays and ALL_WEEKDAYS) != 0

    /** Rest days a week of a flexible routine: the weekdays chosen, else the number; 0 when fixed. */
    val restDaysCount: Int
        get() = when {
            !isFlexible -> 0
            knowsWeekdays -> (restWeekdays and ALL_WEEKDAYS).countOneBits()
            else -> restDaysPerWeek.coerceIn(0, MAX_REST_DAYS)
        }

    /** Training days a week of a flexible routine (7 − rest days); null for a fixed routine. */
    val trainingDaysPerWeek: Int? get() = if (isFlexible) DAYS_IN_WEEK - restDaysCount else null

    /** Whether [isoDayOfWeek] (1 = Monday … 7 = Sunday) is a chosen rest weekday. */
    fun isRestWeekday(isoDayOfWeek: Int): Boolean =
        knowsWeekdays && isoDayOfWeek in 1..DAYS_IN_WEEK && (restWeekdays and bit(isoDayOfWeek)) != 0

    /** The chosen rest weekdays, ISO numbers (1 = Monday) in order; empty when not known. */
    val weekdays: List<Int> get() = if (knowsWeekdays) (1..DAYS_IN_WEEK).filter(::isRestWeekday) else emptyList()

    /**
     * The policy kept in range: fixed clears the flexible fields; weekdays are limited to seven bits and at most
     * [MAX_REST_DAYS] of them (the first ones in week order win), and set [restDaysPerWeek] to their count.
     */
    fun normalized(): RestPolicy {
        if (!isFlexible) return FIXED
        val mask = maskOf(weekdaysOf(restWeekdays).take(MAX_REST_DAYS))
        return if (mask != 0) {
            RestPolicy(RestMode.FLEXIBLE, mask.countOneBits(), mask)
        } else {
            RestPolicy(RestMode.FLEXIBLE, restDaysPerWeek.coerceIn(0, MAX_REST_DAYS), 0)
        }
    }

    companion object {
        const val DAYS_IN_WEEK = 7

        /** At least one training day a week. */
        const val MAX_REST_DAYS = 6
        const val ALL_WEEKDAYS = 0b111_1111

        val FIXED = RestPolicy()

        /** Flexible with [restDaysPerWeek] rest days a week, weekdays not known. */
        fun flexible(restDaysPerWeek: Int): RestPolicy =
            RestPolicy(RestMode.FLEXIBLE, restDaysPerWeek, 0).normalized()

        /** Flexible resting on [isoDays] (1 = Monday … 7 = Sunday). */
        fun onWeekdays(isoDays: Collection<Int>): RestPolicy =
            RestPolicy(RestMode.FLEXIBLE, 0, maskOf(isoDays)).normalized()

        /** Bitmask of ISO weekdays; values outside 1..7 are ignored. */
        fun maskOf(isoDays: Collection<Int>): Int =
            isoDays.filter { it in 1..DAYS_IN_WEEK }.fold(0) { mask, day -> mask or bit(day) }

        /** ISO weekdays of [mask], in order. */
        fun weekdaysOf(mask: Int): List<Int> = (1..DAYS_IN_WEEK).filter { (mask and bit(it)) != 0 }

        private fun bit(isoDayOfWeek: Int) = 1 shl (isoDayOfWeek - 1)
    }
}
