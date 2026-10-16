package es.sebas1705.haltera.domain.usecases.session

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastTimeComparisonTest {
    private val day = 24 * 60 * 60_000L
    private val dayOf: (Long) -> Int = { millis -> millis.floorDiv(day).toInt() }
    private var next = 0

    private fun set(
        load: Long? = null,
        reps: Int? = null,
        kind: SetKind = SetKind.WORKING,
        completed: Boolean = true,
        duration: Int? = null,
        distance: Int? = null,
        detail: String? = null,
    ) = SetLog("s${next++}", "e", next, kind, reps, load, detail, duration, distance, completed = completed)

    private fun today(vararg sets: SetLog) = SessionExercise("today-e", "today", 0, VARIANT, null, sets = sets.toList())

    private fun past(id: String, at: Long, vararg sets: SetLog) =
        ExerciseHistoryEntry(id, at, SessionExercise("$id-e", id, 0, VARIANT, null, sets = sets.toList()))

    private fun compare(
        exercise: SessionExercise,
        history: List<ExerciseHistoryEntry>,
        tracking: TrackingType = TrackingType.WEIGHT_REPS,
        load: LoadType = LoadType.DUMBBELL,
    ) = LastTimeComparisons.compare(exercise, history, tracking, load, dayOf)

    @Test
    fun aFirstTimeIsABaseline() {
        val result = compare(today(set(12_000, 10)), emptyList())!!
        assertEquals(Trend.FIRST_TIME, result.trend)
        assertEquals(12_000, result.current.loadGrams)
        assertNull(result.previous)
    }

    @Test
    fun historyWithOnlyWarmUpsOrUnfinishedSetsIsAFirstTime() {
        val history = listOf(past("p", 5 * day, set(20_000, 5, SetKind.WARMUP), set(30_000, 5, completed = false)))
        assertEquals(Trend.FIRST_TIME, compare(today(set(12_000, 10)), history)!!.trend)
    }

    @Test
    fun nothingDoneTodayIsNotCompared() {
        val history = listOf(past("p", 5 * day, set(12_000, 10)))
        assertNull(compare(today(set(14_000, 10, completed = false), set(16_000, 5, SetKind.WARMUP)), history))
    }

    @Test
    fun aHeavierBestSetIsBetterAndALighterOneLower() {
        val history = listOf(past("p", 5 * day, set(10_000, 12), set(12_000, 10)))
        val better = compare(today(set(12_000, 10), set(14_000, 8)), history)!!
        assertEquals(Trend.BETTER, better.trend)
        assertEquals(12_000, better.previous?.loadGrams)
        assertEquals(14_000, better.current.loadGrams)
        assertEquals(Trend.LOWER, compare(today(set(10_000, 15)), history)!!.trend, "lighter, even with more reps")
    }

    @Test
    fun atTheSameLoadRepsDecideAndATieIsTheSame() {
        val history = listOf(past("p", 5 * day, set(12_000, 10)))
        assertEquals(Trend.BETTER, compare(today(set(12_000, 11)), history)!!.trend)
        assertEquals(Trend.LOWER, compare(today(set(12_000, 9)), history)!!.trend)
        assertEquals(Trend.SAME, compare(today(set(12_000, 10), set(10_000, 12)), history)!!.trend)
    }

    @Test
    fun warmUpsNeverCountOnEitherSide() {
        val history = listOf(past("p", 5 * day, set(30_000, 5, SetKind.WARMUP), set(12_000, 10)))
        val result = compare(today(set(40_000, 3, SetKind.WARMUP), set(12_000, 10)), history)!!
        assertEquals(Trend.SAME, result.trend)
        assertEquals(12_000, result.current.loadGrams)
    }

    @Test
    fun failureAndDropSetsCountLikeInTheRecords() {
        val history = listOf(past("p", 5 * day, set(12_000, 10)))
        assertEquals(Trend.BETTER, compare(today(set(12_000, 10), set(14_000, 6, SetKind.FAILURE)), history)!!.trend)
    }

    @Test
    fun theLastDayIsComparedNotTheAllTimeBest() {
        val history = listOf(past("old", 2 * day, set(20_000, 8)), past("last", 6 * day, set(12_000, 10)))
        val result = compare(today(set(14_000, 10)), history)!!
        assertEquals(Trend.BETTER, result.trend)
        assertEquals(12_000, result.previous?.loadGrams)
    }

    @Test
    fun everySessionOfTheLastDayCounts() {
        val history = listOf(
            past("evening", 6 * day + 20 * 3_600_000L, set(10_000, 10)),
            past("morning", 6 * day + 8 * 3_600_000L, set(16_000, 6)),
        )
        val result = compare(today(set(14_000, 10)), history)!!
        assertEquals(16_000, result.previous?.loadGrams)
        assertEquals(Trend.LOWER, result.trend)
    }

    @Test
    fun bodyweightWithoutLoadIsZeroAndAddedWeightIsBetter() {
        val history = listOf(past("p", 5 * day, set(null, 10)))
        val bodyweight = TrackingType.BODYWEIGHT_REPS
        assertEquals(Trend.BETTER, compare(today(set(5_000, 6)), history, bodyweight, LoadType.BODYWEIGHT)!!.trend)
        assertEquals(Trend.BETTER, compare(today(set(null, 12)), history, bodyweight, LoadType.BODYWEIGHT)!!.trend)
        assertEquals(Trend.SAME, compare(today(set(0, 10)), history, bodyweight, LoadType.BODYWEIGHT)!!.trend)
    }

    @Test
    fun lessAssistanceIsBetter() {
        val history = listOf(past("p", 5 * day, set(-20_000, 8)))
        val tracking = TrackingType.BODYWEIGHT_REPS
        assertEquals(Trend.BETTER, compare(today(set(-10_000, 8)), history, tracking, LoadType.BODYWEIGHT)!!.trend)
        assertEquals(Trend.LOWER, compare(today(set(-30_000, 12)), history, tracking, LoadType.BODYWEIGHT)!!.trend)
        assertEquals(
            Trend.BETTER,
            compare(today(set(null, 3)), history, tracking, LoadType.BODYWEIGHT)!!.trend,
            "unassisted"
        )
    }

    @Test
    fun repsOnlyComparesReps() {
        val history = listOf(past("p", 5 * day, set(null, 20), set(null, 15)))
        assertEquals(Trend.BETTER, compare(today(set(null, 22)), history, TrackingType.REPS, LoadType.NONE)!!.trend)
        assertEquals(Trend.SAME, compare(today(set(null, 20)), history, TrackingType.REPS, LoadType.NONE)!!.trend)
    }

    @Test
    fun timedWorkComparesTheLongestSetAndNeverALoad() {
        val history = listOf(past("p", 5 * day, set(duration = 45), set(duration = 60)))
        val result = compare(today(set(duration = 50), set(duration = 70)), history, TrackingType.TIME, LoadType.NONE)!!
        assertEquals(Trend.BETTER, result.trend)
        assertEquals(70, result.current.durationS)
        assertEquals(60, result.previous?.durationS)
        assertNull(result.current.loadGrams)
    }

    @Test
    fun weightAndTimeComparesTheWeightThenTheTime() {
        val history = listOf(past("p", 5 * day, set(20_000, duration = 40)))
        val tracking = TrackingType.WEIGHT_TIME
        assertEquals(
            Trend.BETTER,
            compare(today(set(20_000, duration = 45)), history, tracking, LoadType.DUMBBELL)!!.trend
        )
        assertEquals(
            Trend.LOWER,
            compare(today(set(16_000, duration = 90)), history, tracking, LoadType.DUMBBELL)!!.trend
        )
    }

    @Test
    fun distanceComparesTheDistanceThenTheTime() {
        val history = listOf(past("p", 5 * day, set(distance = 1_000, duration = 300)))
        val tracking = TrackingType.DISTANCE_TIME
        assertEquals(
            Trend.BETTER,
            compare(today(set(distance = 1_200, duration = 400)), history, tracking, LoadType.NONE)!!.trend
        )
        assertEquals(
            Trend.BETTER,
            compare(today(set(distance = 1_000, duration = 280)), history, tracking, LoadType.NONE)!!.trend
        )
        assertEquals(
            Trend.LOWER,
            compare(today(set(distance = 1_000, duration = 320)), history, tracking, LoadType.NONE)!!.trend
        )
    }

    @Test
    fun bandLevelsCompareAsLevels() {
        val band = """{"type":"band"}"""
        val history = listOf(past("p", 5 * day, set(2, 12, detail = band)))
        assertEquals(Trend.BETTER, compare(today(set(3, 10, detail = band)), history, load = LoadType.BAND)!!.trend)
    }

    @Test
    fun aStackPositionIsNeverComparedWithAWeight() {
        val plate = """{"type":"machinePlate"}"""
        val history = listOf(past("p", 5 * day, set(40_000, 10)))
        val result = compare(today(set(7, 10, detail = plate)), history, load = LoadType.MACHINE)!!
        assertEquals(Trend.UNCOMPARABLE, result.trend)
        assertEquals(40_000, result.previous?.loadGrams)
    }

    @Test
    fun aMissingWeightIsNotComparedWithAWeight() {
        val history = listOf(past("p", 5 * day, set(12_000, 10)))
        assertEquals(Trend.UNCOMPARABLE, compare(today(set(null, 12)), history)!!.trend)
    }

    @Test
    fun weightsArePreferredOverLevelsForTheBestSet() {
        val plate = """{"type":"machinePlate"}"""
        val best = LastTimeComparisons.best(
            listOf(set(9, 10, detail = plate), set(30_000, 8)),
            TrackingType.WEIGHT_REPS,
            LoadType.MACHINE,
        )
        assertEquals(30_000, best?.loadGrams)
    }

    private companion object {
        const val VARIANT = "chest-fly.dumbbell"
    }
}
