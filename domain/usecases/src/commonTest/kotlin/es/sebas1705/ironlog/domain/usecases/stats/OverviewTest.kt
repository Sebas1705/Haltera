package es.sebas1705.ironlog.domain.usecases.stats

import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.data.models.RoutineDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OverviewTest {

    private fun routine(vararg kinds: DayKind) = Routine(
        id = "r",
        name = "R",
        days = kinds.mapIndexed { i, kind -> RoutineDay("d$i", "r", i, "Day $i", kind) },
    )

    private val sessions = listOf(
        // Previous period (TODAY-55 … TODAY-28).
        dated("p1", TODAY - 40, move("squat", lift(100.0, 5), lift(100.0, 5)), minutes = 50),
        // Current period (TODAY-27 … TODAY).
        dated("c1", TODAY - 20, move("squat", lift(105.0, 5), lift(105.0, 5)), minutes = 60),
        dated("c2", TODAY - 3, move("squat", lift(100.0, 5)), move("row", lift(60.0, 10)), minutes = 90),
        dated("c3", TODAY, move("squat", lift(100.0, 5)), minutes = null), // in progress
        // Too old for both windows.
        dated("old", TODAY - 100, move("squat", lift(80.0, 5)), minutes = 40),
    )

    @Test
    fun `this period against the previous equal period`() {
        val stats = Overview.build(StatsPeriod.FOUR_WEEKS, TODAY, sessions)
        assertEquals(DayWindow(TODAY - 27, TODAY), stats.current)
        assertEquals(DayWindow(TODAY - 55, TODAY - 28), stats.previous)
        assertEquals(Comparison(2.0, 1.0), stats.sessions)
        assertEquals(Comparison(1_050_000.0 + 500_000.0 + 600_000.0, 1_000_000.0), stats.volume)
        assertEquals(Comparison(4.0, 2.0), stats.effectiveSets)
        assertEquals(Comparison(150 * 60_000.0, 50 * 60_000.0), stats.trainingTimeMillis)
        assertEquals(Comparison(75 * 60_000.0, 50 * 60_000.0), stats.averageDurationMillis)
        // p1 beat "old" (load, e1RM, volume); c1 beat p1 (load, e1RM, volume).
        assertEquals(Comparison(3.0, 3.0), stats.newPrs)
    }

    @Test
    fun `buckets are aligned 7-day blocks of both periods`() {
        val stats = Overview.build(StatsPeriod.FOUR_WEEKS, TODAY, sessions)
        assertEquals(4, stats.buckets.size)
        assertEquals(4, stats.previousBuckets.size)
        assertEquals(listOf(0, 1, 0, 1), stats.buckets.map { it.sessions })
        assertEquals(listOf(0, 0, 1, 0), stats.previousBuckets.map { it.sessions })
        assertEquals(DayWindow(TODAY - 6, TODAY), stats.buckets.last().window)
        assertEquals(90 * 60_000.0, stats.buckets.last().averageDurationMillis)
        assertNull(stats.buckets.first().averageDurationMillis)
    }

    @Test
    fun `all time has no comparison`() {
        val stats = Overview.build(StatsPeriod.ALL, TODAY, sessions)
        assertEquals(DayWindow(TODAY - 100, TODAY), stats.current)
        assertNull(stats.previous)
        assertNull(stats.sessions.previous)
        assertEquals(4.0, stats.sessions.current)
        assertEquals(emptyList(), stats.previousBuckets)
        assertEquals(15, stats.buckets.size) // 101 days: 14 full weeks and 3 days
        assertEquals(3, stats.buckets.first().window.length)
    }

    @Test
    fun `an empty period has no average duration`() {
        val stats = Overview.build(StatsPeriod.FOUR_WEEKS, TODAY, emptyList())
        assertEquals(Comparison(0.0, 0.0), stats.sessions)
        assertNull(stats.averageDurationMillis)
        assertNull(stats.sessions.percent)
    }

    @Test
    fun `per week scales a bucket to 7 days`() {
        val bucket = StatsBucket(DayWindow(0, 27), sessions = 8)
        assertEquals(2.0, bucket.perWeek(bucket.sessions.toDouble()))
    }

    @Test
    fun `planned sessions per week come from the cycle's training and rest days`() {
        // Full body A, rest, B, rest, C, rest, rest: 3 per week.
        val fullBody = routine(
            DayKind.TRAINING, DayKind.REST, DayKind.TRAINING, DayKind.REST,
            DayKind.TRAINING, DayKind.REST, DayKind.REST,
        )
        assertEquals(3.0, Overview.plannedPerWeek(fullBody))
        // Push, pull, legs, rest: 3 in 4 days = 5.25 per week.
        assertEquals(5.25, Overview.plannedPerWeek(routine(DayKind.TRAINING, DayKind.TRAINING, DayKind.TRAINING, DayKind.REST)))
        // No rest day: the cycle is not a week.
        assertNull(Overview.plannedPerWeek(routine(DayKind.TRAINING, DayKind.TRAINING)))
        assertNull(Overview.plannedPerWeek(routine(DayKind.REST)))
        assertNull(Overview.plannedPerWeek(routine()))
    }

    @Test
    fun `adherence compares logged and planned sessions per week`() {
        val plan = routine(
            DayKind.TRAINING, DayKind.REST, DayKind.TRAINING, DayKind.REST,
            DayKind.TRAINING, DayKind.REST, DayKind.REST,
        )
        val adherence = assertNotNull(Overview.build(StatsPeriod.FOUR_WEEKS, TODAY, sessions, activeRoutine = plan).adherence)
        assertEquals(3.0, adherence.plannedPerWeek)
        assertEquals(0.5, adherence.loggedPerWeek)
        assertEquals(16.666, adherence.percent, 0.001)
        assertNull(Overview.build(StatsPeriod.FOUR_WEEKS, TODAY, sessions).adherence)
        assertEquals(150.0, Adherence(2.0, 3.0).percent)
    }
}
