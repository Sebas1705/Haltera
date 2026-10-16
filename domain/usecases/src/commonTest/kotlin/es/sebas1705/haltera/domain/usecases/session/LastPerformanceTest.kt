package es.sebas1705.haltera.domain.usecases.session

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastPerformanceTest {
    private val hour = 60 * 60_000L
    private val day = 24 * hour

    /** Days in UTC, as the session feature passes them for a UTC user. */
    private val utcDay: (Long) -> Int = { millis -> millis.floorDiv(day).toInt() }

    private var nextSet = 0

    private fun set(load: Long?, reps: Int? = 10, kind: SetKind = SetKind.WORKING, completed: Boolean = true) =
        SetLog("set-${nextSet++}", "e", 0, kind, reps, load, completed = completed)

    private fun entry(id: String, at: Long, vararg sets: SetLog) = ExerciseHistoryEntry(
        id,
        at,
        SessionExercise(
            "$id-e",
            id,
            0,
            VARIANT,
            null,
            sets = sets.mapIndexed { i, s -> s.copy(sessionExerciseId = "$id-e", position = i) },
        ),
    )

    @Test
    fun noHistoryMeansNoLastPerformance() {
        assertNull(LastPerformance.from(emptyList(), utcDay))
    }

    @Test
    fun performancesWithoutACountedSetAreSkipped() {
        val history = listOf(
            entry("empty", 10 * day, set(20_000, completed = false)),
            entry("warmups", 9 * day, set(10_000, kind = SetKind.WARMUP)),
            entry("real", 5 * day, set(12_000)),
        )
        val last = LastPerformance.from(history, utcDay)!!
        assertEquals("real", last.latest.sessionId)
        assertEquals(12_000, last.best?.loadGrams)
        assertEquals(5, last.day)
    }

    @Test
    fun theBestSetOfTheMostRecentDayWinsNotTheAllTimeBest() {
        val history = listOf(
            entry("today-ish", 3 * day, set(10_000), set(12_000), set(12_000)),
            entry("older", 1 * day, set(20_000)),
        )
        assertEquals(12_000, LastPerformance.from(history, utcDay)!!.best?.loadGrams)
    }

    @Test
    fun everySessionOfThatDayCounts() {
        val history = listOf(
            entry("evening", 3 * day + 20 * hour, set(10_000)),
            entry("morning", 3 * day + 8 * hour, set(14_000)),
            entry("day-before", 2 * day + 20 * hour, set(30_000)),
        )
        val last = LastPerformance.from(history, utcDay)!!
        assertEquals("evening", last.latest.sessionId, "warm-ups come from the latest performance")
        assertEquals(14_000, last.best?.loadGrams, "the morning session's 14 kg is the day's best")
    }

    @Test
    fun theCalendarDayFollowsTheUsersTimeZone() {
        // 23:30 and 00:30 UTC are two days in UTC but one day in UTC+2 (01:30 and 02:30 of the same date).
        val late = 4 * day - 30 * 60_000L
        val history = listOf(
            entry("after-midnight", 4 * day + 30 * 60_000L, set(10_000)),
            entry("before", late, set(16_000))
        )
        assertEquals(10_000, LastPerformance.from(history, utcDay)!!.best?.loadGrams)
        val utcPlus2: (Long) -> Int = { millis -> (millis + 2 * hour).floorDiv(day).toInt() }
        assertEquals(16_000, LastPerformance.from(history, utcPlus2)!!.best?.loadGrams)
    }

    @Test
    fun historyIsSortedByStartWhateverTheInputOrder() {
        val history = listOf(entry("old", 1 * day, set(30_000)), entry("new", 6 * day, set(8_000)))
        assertEquals("new", LastPerformance.from(history, utcDay)!!.latest.sessionId)
    }

    @Test
    fun warmUpsNeverCountAndFailureAndDropSetsDo() {
        val sets = listOf(
            set(40_000, kind = SetKind.WARMUP),
            set(12_000),
            set(14_000, reps = 6, kind = SetKind.FAILURE),
            set(8_000, kind = SetKind.DROP),
            set(50_000, completed = false),
        )
        val best = LastPerformance.bestSet(sets)!!
        assertEquals(14_000, best.loadGrams)
        assertEquals(SetKind.FAILURE, best.kind)
        assertEquals(8_000, LastPerformance.bestSet(listOf(set(8_000, kind = SetKind.DROP)))?.loadGrams)
        assertNull(LastPerformance.bestSet(listOf(set(40_000, kind = SetKind.WARMUP))))
    }

    @Test
    fun equallyHeavySetsPreferMoreRepsThenTheLatest() {
        val fewer = set(12_000, reps = 8)
        val more = set(12_000, reps = 10)
        val laterSame = set(12_000, reps = 10)
        assertEquals(more.id, LastPerformance.bestSet(listOf(fewer, more))?.id)
        assertEquals(laterSame.id, LastPerformance.bestSet(listOf(more, laterSame, fewer))?.id)
    }

    @Test
    fun withoutLoadsTheLastCountedSetOfTheDayIsUsed() {
        val history = listOf(
            entry("later", 2 * day + 18 * hour, set(null, reps = 12), set(null, reps = 9)),
            entry("earlier", 2 * day + 8 * hour, set(null, reps = 15)),
        )
        assertEquals(9, LastPerformance.from(history, utcDay)!!.best?.reps)
    }

    @Test
    fun assistedLoadsPreferLessAssistanceAndWeightedMoreWeight() {
        assertEquals(-10_000, LastPerformance.bestSet(listOf(set(-20_000), set(-10_000)))?.loadGrams)
        assertEquals(5_000, LastPerformance.bestSet(listOf(set(-10_000), set(5_000), set(0)))?.loadGrams)
    }

    @Test
    fun loadedSetsWinOverSetsWithoutALoad() {
        assertEquals(2_000, LastPerformance.bestSet(listOf(set(2_000, reps = 5), set(null, reps = 20)))?.loadGrams)
    }

    @Test
    fun warmUpsAreTheCompletedOnesInOrder() {
        val exercise = entry(
            "s",
            0,
            set(20_000, kind = SetKind.WARMUP),
            set(30_000, kind = SetKind.WARMUP, completed = false),
            set(25_000, kind = SetKind.WARMUP),
            set(60_000),
        ).exercise
        assertEquals(listOf(20_000L, 25_000L), LastPerformance.warmups(exercise).map { it.loadGrams })
        assertEquals(emptyList(), LastPerformance.warmups(null))
    }

    private companion object {
        const val VARIANT = "chest-fly.dumbbell"
    }
}
