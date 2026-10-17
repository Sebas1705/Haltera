package es.sebas1705.haltera.domain.usecases.cycle

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.WorkoutSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * The current week of a flexible routine, information only (never a block): [used] rest days so far this week
 * out of [planned]; [restToday] when today is a chosen rest weekday and the routine has not been trained today.
 * [usedWithOtherTraining] of the [used] days had a free session or a session of another routine: they are rest
 * days for this routine all the same, and Today says so (the week strip shows them as trained).
 */
data class RestWeek(
    val used: Int,
    val planned: Int,
    val restToday: Boolean,
    val knowsWeekdays: Boolean,
    val usedWithOtherTraining: Int = 0,
)

/** Rest days of flexible routines (docs/designs/2026-10-16-flexible-rest-days.md). Pure functions. */
object FlexibleRest {
    private const val DAYS_IN_WEEK = 7

    /** The local date of [millis] in [timeZone]. */
    fun dateOf(millis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date

    /** The Monday of [date]'s week. */
    fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    /** Whether [date] is one of the chosen rest weekdays of [policy] (false when weekdays are not known). */
    fun isRestDay(policy: RestPolicy, date: LocalDate): Boolean = policy.isRestWeekday(date.dayOfWeek.isoDayNumber)

    /**
     * The week status of [routine] at [nowMillis] in [timeZone], or null for a fixed routine. A rest day used is a
     * day of this week (Monday first) before today without a session of the routine, by the local date of its
     * start; today is not counted (it can still be trained). [sessions] may hold anything: other routines, free
     * sessions and other weeks are ignored.
     */
    fun week(routine: Routine, sessions: List<WorkoutSession>, nowMillis: Long, timeZone: TimeZone): RestWeek? {
        val policy = routine.restPolicy.normalized()
        if (!policy.isFlexible) return null
        val today = dateOf(nowMillis, timeZone)
        val monday = mondayOf(today)
        val (mine, others) = sessions.partition { it.routineId == routine.id }
        val trained = mine.map { dateOf(it.startedAt, timeZone) }.toSet()
        val otherTraining = others.map { dateOf(it.startedAt, timeZone) }.toSet()
        val pastDays = today.dayOfWeek.isoDayNumber - 1
        val restDays = (0 until pastDays).map { monday.plus(it, DateTimeUnit.DAY) }.filter { it !in trained }
        return RestWeek(
            used = restDays.size,
            planned = policy.restDaysCount,
            restToday = isRestDay(policy, today) && today !in trained,
            knowsWeekdays = policy.knowsWeekdays,
            usedWithOtherTraining = restDays.count { it in otherTraining },
        )
    }

    /**
     * The starting policy when a fixed routine becomes flexible: its rest positions scaled to a week
     * (rest × 7 / cycle length, rounded, 0–6). A 7-day cycle with one rest day gives 1, "Upper · Lower · Rest" 2.
     */
    fun policyFromCycle(routine: Routine): RestPolicy {
        val days = routine.days.size
        if (days == 0) return RestPolicy.flexible(0)
        val rest = routine.days.count { it.kind == DayKind.REST && it.slots.isEmpty() }
        return RestPolicy.flexible((rest * DAYS_IN_WEEK.toDouble() / days).roundToInt())
    }
}
