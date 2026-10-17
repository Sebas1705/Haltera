package es.sebas1705.haltera.domain.usecases.cycle

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.RestPolicy
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.WorkoutSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flexible routines: next day by training order, and the weekly rest status (docs/designs/2026-10-16-flexible-rest-days.md). */
class FlexibleCycleTest {
    private val utc = TimeZone.UTC
    private val madrid = TimeZone.of("Europe/Madrid")
    private val tokyo = TimeZone.of("Asia/Tokyo")

    private fun days(vararg names: String) =
        names.mapIndexed { i, name ->
            RoutineDay(
                "d$i",
                "r",
                i,
                name,
                if (name == "Rest") DayKind.REST else DayKind.TRAINING
            )
        }

    private val ppl = Routine(
        id = "r",
        name = "PPL+U",
        days = days("Push", "Pull", "Legs", "Upper"),
        restPolicy = RestPolicy.flexible(3),
    )

    /** Epoch millis of [date] at [hour]:[minute] local time in [zone]. */
    private fun at(date: String, hour: Int, zone: TimeZone = utc, minute: Int = 0): Long {
        val d = LocalDate.parse(date)
        return LocalDateTime(d.year, d.month, d.day, hour, minute).toInstant(zone).toEpochMilliseconds()
    }

    private fun session(id: String, dayIndex: Int?, startedAt: Long, routineId: String? = "r") =
        WorkoutSession(
            id,
            startedAt,
            endedAt = startedAt + 3_600_000,
            routineId = routineId,
            routineDayIndex = dayIndex
        )

    private fun next(routine: Routine, logged: List<WorkoutSession>): NextDay? =
        Cycle.nextPendingDay(routine, Cycle.latestOf(routine.id, logged))

    // Next day

    @Test
    fun `a new flexible routine starts at its first training day without a rest hint`() {
        val next = next(ppl, emptyList())!!
        assertEquals(0, next.dayIndex)
        assertFalse(next.planSaysRest)
    }

    @Test
    fun `the next day follows the order whatever the gap between sessions`() {
        // Monday Push, then nothing until Saturday: still Pull.
        val logged = mutableListOf(session("a", 0, at("2026-10-12", 18)))
        assertEquals(1, next(ppl, logged)!!.dayIndex)
        logged += session("b", 1, at("2026-10-17", 10))
        assertEquals(2, next(ppl, logged)!!.dayIndex)
        // Three weeks later: Upper, never a rest day.
        logged += session("c", 2, at("2026-11-07", 10))
        val upper = next(ppl, logged)!!
        assertEquals(3, upper.dayIndex)
        assertFalse(upper.planSaysRest)
        logged += session("d", 3, at("2026-11-08", 10))
        assertEquals(0, next(ppl, logged)!!.dayIndex)
    }

    @Test
    fun `back-to-back days and several days of rest give the same sequence`() {
        val dense = listOf(session("a", 0, at("2026-10-12", 9)), session("b", 1, at("2026-10-13", 9)))
        val sparse = listOf(session("a", 0, at("2026-10-12", 9)), session("b", 1, at("2026-10-16", 9)))
        assertEquals(next(ppl, dense)!!.dayIndex, next(ppl, sparse)!!.dayIndex)
    }

    @Test
    fun `a past session logged later counts by its date, not by when it was logged`() {
        // Thursday Legs is logged; then the user adds Tuesday's forgotten Pull afterwards.
        val logged = mutableListOf(session("thu", 2, at("2026-10-15", 19)))
        assertEquals(3, next(ppl, logged)!!.dayIndex)
        logged += session("tue", 1, at("2026-10-13", 19))
        assertEquals(3, next(ppl, logged)!!.dayIndex)
        // A past session newer than every other one moves the pointer.
        logged += session("fri", 3, at("2026-10-16", 7))
        assertEquals(0, next(ppl, logged)!!.dayIndex)
    }

    @Test
    fun `free sessions and other routines never move the flexible pointer`() {
        val logged = listOf(
            session("push", 0, at("2026-10-12", 9)),
            session("free", null, at("2026-10-13", 9), routineId = null),
            session("other", 3, at("2026-10-14", 9), routineId = "x"),
        )
        assertEquals(1, next(ppl, logged)!!.dayIndex)
    }

    @Test
    fun `a session of the routine without a day keeps the previous pointer source`() {
        // latestOf ignores sessions without a day index; the override (0) applies when nothing else does.
        val logged = listOf(session("dayless", null, at("2026-10-14", 9)))
        assertEquals(0, next(ppl, logged)!!.dayIndex)
    }

    @Test
    fun `out of order logs on the same day use the latest start time`() {
        val logged = listOf(session("evening", 1, at("2026-10-14", 20)), session("morning", 0, at("2026-10-14", 7)))
        assertEquals(2, next(ppl, logged)!!.dayIndex)
    }

    @Test
    fun `the manual override works for flexible routines too`() {
        val overridden = ppl.copy(nextDayIndex = 3, nextDayIndexSetAt = at("2026-10-14", 12))
        val logged = mutableListOf(session("a", 0, at("2026-10-14", 9)))
        assertEquals(3, next(overridden, logged)!!.dayIndex)
        logged += session("b", 3, at("2026-10-15", 9))
        assertEquals(0, next(overridden, logged)!!.dayIndex)
    }

    @Test
    fun `a stray rest day in a flexible routine is skipped silently`() {
        val stray = ppl.copy(days = days("Push", "Rest", "Pull"))
        val next = next(stray, listOf(session("a", 0, at("2026-10-12", 9))))!!
        assertEquals(2, next.dayIndex)
        assertTrue(next.restDaysSkipped.isEmpty())
        assertFalse(next.planSaysRest)
        // The same days as a fixed routine report the rest day.
        assertTrue(
            next(stray.copy(restPolicy = RestPolicy.FIXED), listOf(session("a", 0, at("2026-10-12", 9))))!!.planSaysRest
        )
    }

    @Test
    fun `a flexible routine without days or without training days has no next day`() {
        assertNull(next(ppl.copy(days = emptyList()), emptyList()))
        assertNull(next(ppl.copy(days = days("Rest", "Rest")), emptyList()))
    }

    @Test
    fun `a single-day flexible routine repeats that day`() {
        val one = ppl.copy(days = days("Full body"))
        assertEquals(0, next(one, listOf(session("a", 0, at("2026-10-12", 9))))!!.dayIndex)
    }

    // Week status: number of rest days

    @Test
    fun `a fixed routine has no flexible week status`() {
        assertNull(FlexibleRest.week(ppl.copy(restPolicy = RestPolicy.FIXED), emptyList(), at("2026-10-15", 12), utc))
    }

    @Test
    fun `rest days used are the past days of the week without a session of the routine`() {
        // Thursday; trained Monday and Tuesday: Wednesday was rest.
        val logged = listOf(session("mon", 0, at("2026-10-12", 18)), session("tue", 1, at("2026-10-13", 18)))
        val week = FlexibleRest.week(ppl, logged, at("2026-10-15", 12), utc)!!
        assertEquals(RestWeek(used = 1, planned = 3, restToday = false, knowsWeekdays = false), week)
    }

    @Test
    fun `today is never counted as a rest day yet`() {
        // Monday morning: nothing to count.
        assertEquals(0, FlexibleRest.week(ppl, emptyList(), at("2026-10-12", 8), utc)!!.used)
        // Sunday evening without any session this week: six days so far.
        assertEquals(6, FlexibleRest.week(ppl, emptyList(), at("2026-10-18", 22), utc)!!.used)
    }

    @Test
    fun `used rest days can exceed the plan and are still reported`() {
        val week = FlexibleRest.week(ppl, emptyList(), at("2026-10-17", 12), utc)!!
        assertEquals(5, week.used)
        assertEquals(3, week.planned)
    }

    @Test
    fun `two sessions on the same day count once and last week is ignored`() {
        val logged = listOf(
            session("lastSun", 3, at("2026-10-11", 18)),
            session("monA", 0, at("2026-10-12", 7)),
            session("monB", 1, at("2026-10-12", 19)),
        )
        // Wednesday: Monday trained, Tuesday rest.
        assertEquals(1, FlexibleRest.week(ppl, logged, at("2026-10-14", 12), utc)!!.used)
    }

    @Test
    fun `days with only a free session or another routine count as rest for this routine`() {
        val logged = listOf(
            session("free", null, at("2026-10-12", 9), routineId = null),
            session("other", 0, at("2026-10-13", 9), routineId = "x"),
        )
        val week = FlexibleRest.week(ppl, logged, at("2026-10-14", 12), utc)!!
        assertEquals(2, week.used)
        assertEquals(2, week.usedWithOtherTraining, "both rest days had other training")
    }

    @Test
    fun `rest days with other training are counted apart, once per day and only before today`() {
        // Sunday: PPL on Mon, Wed, Fri; free sessions on Tue (two) and Thu; another routine on Fri and today.
        val logged = listOf(
            session("mon", 0, at("2026-10-12", 18)),
            session("tueA", null, at("2026-10-13", 8), routineId = null),
            session("tueB", 0, at("2026-10-13", 19), routineId = "x"),
            session("wed", 1, at("2026-10-14", 18)),
            session("thu", null, at("2026-10-15", 18), routineId = null),
            session("fri", 2, at("2026-10-16", 18)),
            session("friOther", 0, at("2026-10-16", 20), routineId = "x"),
            session("lastWeek", null, at("2026-10-10", 18), routineId = null),
            session("sun", 0, at("2026-10-18", 9), routineId = "x"),
        )
        val week = FlexibleRest.week(ppl, logged, at("2026-10-18", 12), utc)!!
        // Tue, Thu and Sat were rest days for PPL; Tue and Thu had other training (Fri was a PPL day anyway).
        assertEquals(3, week.used)
        assertEquals(2, week.usedWithOtherTraining)
        assertEquals(0, FlexibleRest.week(ppl, emptyList(), at("2026-10-18", 12), utc)!!.usedWithOtherTraining)
    }

    @Test
    fun `a past session logged for an earlier day of the week fills that day`() {
        val before = FlexibleRest.week(ppl, emptyList(), at("2026-10-15", 12), utc)!!
        val after = FlexibleRest.week(ppl, listOf(session("tue", 1, at("2026-10-13", 18))), at("2026-10-15", 12), utc)!!
        assertEquals(before.used - 1, after.used)
    }

    // Week status: rest weekdays

    private val wedSun = ppl.copy(restPolicy = RestPolicy.onWeekdays(listOf(3, 7)))

    @Test
    fun `on a chosen rest weekday the plan says rest`() {
        val week = FlexibleRest.week(wedSun, emptyList(), at("2026-10-14", 9), utc)!!
        assertTrue(week.restToday)
        assertTrue(week.knowsWeekdays)
        assertEquals(2, week.planned)
    }

    @Test
    fun `on the other weekdays it does not`() {
        listOf("2026-10-12", "2026-10-13", "2026-10-15", "2026-10-16", "2026-10-17").forEach { day ->
            assertFalse(FlexibleRest.week(wedSun, emptyList(), at(day, 9), utc)!!.restToday, day)
        }
        assertTrue(FlexibleRest.week(wedSun, emptyList(), at("2026-10-18", 9), utc)!!.restToday)
    }

    @Test
    fun `training anyway on a rest weekday clears the hint for the rest of that day`() {
        val logged = listOf(session("wed", 0, at("2026-10-14", 8)))
        assertFalse(FlexibleRest.week(wedSun, logged, at("2026-10-14", 12), utc)!!.restToday)
    }

    @Test
    fun `the weekday rest hint does not change the next day`() {
        val logged = listOf(session("tue", 1, at("2026-10-13", 18)))
        assertEquals(2, next(wedSun, logged)!!.dayIndex)
        assertFalse(next(wedSun, logged)!!.planSaysRest)
    }

    // Time zones and date boundaries

    @Test
    fun `sessions are dated in the device time zone`() {
        // 00:30 Tuesday in Tokyo is Monday 15:30 UTC.
        val tuesdayInTokyo = at("2026-10-13", 0, tokyo, minute = 30)
        val logged = listOf(session("t", 0, tuesdayInTokyo))
        // Wednesday noon in Tokyo: Monday rest, Tuesday trained.
        assertEquals(1, FlexibleRest.week(ppl, logged, at("2026-10-14", 12, tokyo), tokyo)!!.used)
        // Read in UTC the same session falls on Monday: Tuesday is the rest day instead.
        assertEquals(1, FlexibleRest.week(ppl, logged, at("2026-10-14", 12), utc)!!.used)
        assertEquals(LocalDate.parse("2026-10-13"), FlexibleRest.dateOf(tuesdayInTokyo, tokyo))
        assertEquals(LocalDate.parse("2026-10-12"), FlexibleRest.dateOf(tuesdayInTokyo, utc))
    }

    @Test
    fun `a session just before midnight belongs to that day`() {
        val lateMonday = at("2026-10-12", 23, madrid, minute = 59)
        assertEquals(
            0,
            FlexibleRest.week(ppl, listOf(session("m", 0, lateMonday)), at("2026-10-13", 0, madrid, 1), madrid)!!.used
        )
    }

    @Test
    fun `the week starts on Monday at local midnight`() {
        // Sunday 23:59 in Madrid is still the old week; Monday 00:01 starts a new one.
        val sundayNight = at("2026-10-18", 23, madrid, minute = 59)
        val mondayMorning = at("2026-10-19", 0, madrid, minute = 1)
        assertEquals(6, FlexibleRest.week(ppl, emptyList(), sundayNight, madrid)!!.used)
        assertEquals(0, FlexibleRest.week(ppl, emptyList(), mondayMorning, madrid)!!.used)
        assertEquals(LocalDate.parse("2026-10-19"), FlexibleRest.mondayOf(LocalDate.parse("2026-10-25")))
    }

    @Test
    fun `the daylight saving change keeps every day of the week`() {
        // Europe/Madrid leaves summer time on Sunday 2026-10-25 (a 25-hour day).
        val logged = listOf(
            session("mon", 0, at("2026-10-19", 9, madrid)),
            session("sunLate", 1, at("2026-10-25", 23, madrid, minute = 30)),
        )
        val sundayLate = at("2026-10-25", 23, madrid, minute = 45)
        // Tuesday to Saturday without sessions: 5 rest days; Sunday is today and was trained.
        assertEquals(5, FlexibleRest.week(ppl, logged, sundayLate, madrid)!!.used)
        val sundayRest = wedSun.copy(restPolicy = RestPolicy.onWeekdays(listOf(7)))
        assertFalse(FlexibleRest.week(sundayRest, logged, sundayLate, madrid)!!.restToday)
        assertTrue(
            FlexibleRest.week(sundayRest, emptyList(), at("2026-10-25", 2, madrid, minute = 30), madrid)!!.restToday
        )
        // Spring forward (2026-03-29, a 23-hour Sunday) is still a Sunday rest day.
        assertTrue(FlexibleRest.week(sundayRest, emptyList(), at("2026-03-29", 12, madrid), madrid)!!.restToday)
    }

    // Converting

    @Test
    fun `the starting policy of a converted routine scales the rest positions to a week`() {
        fun fixed(vararg names: String) = Routine("f", "F", days = days(*names))
        assertEquals(
            1,
            FlexibleRest.policyFromCycle(fixed("Push", "Pull", "Legs", "Push", "Pull", "Legs", "Rest")).restDaysCount
        )
        assertEquals(2, FlexibleRest.policyFromCycle(fixed("Upper", "Lower", "Rest")).restDaysCount)
        assertEquals(0, FlexibleRest.policyFromCycle(fixed("A", "B")).restDaysCount)
        // One rest in two days is 3.5 a week, rounded to 4; one training day in seven leaves the maximum, 6.
        assertEquals(4, FlexibleRest.policyFromCycle(fixed("A", "Rest")).restDaysCount)
        assertEquals(
            6,
            FlexibleRest.policyFromCycle(fixed("A", "Rest", "Rest", "Rest", "Rest", "Rest", "Rest")).restDaysCount
        )
        assertEquals(0, FlexibleRest.policyFromCycle(Routine("e", "E")).restDaysCount)
        assertTrue(FlexibleRest.policyFromCycle(fixed("A", "Rest")).isFlexible)
    }

    @Test
    fun `a rest day holding exercises does not count as a rest position`() {
        val slot = RoutineSlot("s", "d1", 0, SlotTarget.Fixed("v"), prescription = Prescription.Reps(5))
        val days = days("A", "Rest", "Rest").mapIndexed { i, d -> if (i == 1) d.copy(slots = listOf(slot)) else d }
        assertEquals(2, FlexibleRest.policyFromCycle(Routine("f", "F", days = days)).restDaysCount)
    }
}
