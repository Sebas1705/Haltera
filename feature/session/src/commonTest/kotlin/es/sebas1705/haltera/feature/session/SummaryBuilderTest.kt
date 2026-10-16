package es.sebas1705.haltera.feature.session

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.domain.usecases.session.Trend
import es.sebas1705.haltera.feature.session.ui.ExerciseName
import es.sebas1705.haltera.feature.session.ui.SummaryBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Compared with last time" part of the finish summary, which replaced the volume headline. */
class SummaryBuilderTest {
    private val day = 24 * 60 * 60_000L
    private val start = 10 * day + 18 * 3_600_000L
    private var next = 0

    private fun done(load: Long?, reps: Int?, kind: SetKind = SetKind.WORKING, duration: Int? = null) =
        SetLog("s${next++}", "", next, kind, reps, load, durationS = duration, completed = true)

    private fun exercise(id: String, variant: String, position: Int, vararg sets: SetLog) =
        SessionExercise(id, "today", position, variant, null, sets = sets.toList())

    private fun past(id: String, at: Long, variant: String, vararg sets: SetLog) =
        ExerciseHistoryEntry(id, at, SessionExercise("$id-$variant", id, 0, variant, null, sets = sets.toList()))

    private val history = mutableMapOf<String, List<ExerciseHistoryEntry>>()
    private val asked = mutableListOf<String>()

    private suspend fun build(vararg exercises: Pair<SessionExercise, SummaryBuilder.Card>) = SummaryBuilder.build(
        session = WorkoutSession("today", start, endedAt = start + 3_000_000, exercises = exercises.map { it.first }),
        slots = emptyMap(),
        cards = exercises.associate { (e, card) -> e.id to card },
        dayOf = { millis -> millis.floorDiv(day).toInt() },
        previous = { variant ->
            asked += variant
            history[variant].orEmpty()
        },
        increase = { _, _ -> null },
    )

    private fun card(tracking: TrackingType = TrackingType.WEIGHT_REPS, load: LoadType = LoadType.DUMBBELL) =
        SummaryBuilder.Card(ExerciseName(null, null, null), tracking, load)

    @Test
    fun eachExerciseIsComparedWithItsOwnVariantOnly() = runTest {
        history[FLY_DUMBBELL] = listOf(past("p", start - day, FLY_DUMBBELL, done(12_000, 10)))
        history[FLY_CABLE] = listOf(past("p", start - day, FLY_CABLE, done(40_000, 12)))
        val summary = build(
            exercise("a", FLY_DUMBBELL, 0, done(14_000, 10)) to card(),
            exercise("b", FLY_CABLE, 1, done(35_000, 12)) to card(load = LoadType.MACHINE),
        )
        assertEquals(listOf(FLY_DUMBBELL, FLY_CABLE), asked)
        assertEquals(listOf(Trend.BETTER, Trend.LOWER), summary.comparisons.map { it.comparison.trend })
        assertEquals(12_000, summary.comparisons[0].comparison.previous?.loadGrams, "not the cable's 40 kg")
        assertEquals(1, summary.improved)
        assertEquals(2, summary.compared)
    }

    @Test
    fun aFirstTimeIsShownButNotCountedAsCompared() = runTest {
        val summary = build(exercise("a", FLY_DUMBBELL, 0, done(12_000, 10)) to card())
        val line = summary.comparisons.single()
        assertEquals(Trend.FIRST_TIME, line.comparison.trend)
        assertNull(line.comparison.previous)
        assertEquals(0, summary.compared)
        assertEquals(0, summary.improved)
        assertTrue(summary.records.isEmpty(), "a first time is a baseline, not a record")
    }

    @Test
    fun aTieIsTheSameAndWarmUpsDoNotChangeIt() = runTest {
        history[FLY_DUMBBELL] = listOf(past("p", start - day, FLY_DUMBBELL, done(12_000, 10)))
        val summary = build(
            exercise("a", FLY_DUMBBELL, 0, done(20_000, 5, SetKind.WARMUP), done(12_000, 10)) to card(),
        )
        assertEquals(Trend.SAME, summary.comparisons.single().comparison.trend)
        assertEquals(1, summary.compared)
        assertEquals(0, summary.improved)
    }

    @Test
    fun timedAndBodyweightExercisesCompareWithoutAnyWeight() = runTest {
        history[PLANK] = listOf(past("p", start - day, PLANK, done(null, null, duration = 60)))
        history[PULL_UP] = listOf(past("p", start - day, PULL_UP, done(null, 8)))
        val summary = build(
            exercise("a", PLANK, 0, done(null, null, duration = 75)) to card(TrackingType.TIME, LoadType.NONE),
            exercise("b", PULL_UP, 1, done(null, 10)) to card(TrackingType.BODYWEIGHT_REPS, LoadType.BODYWEIGHT),
        )
        val (plank, pullUp) = summary.comparisons
        assertEquals(Trend.BETTER, plank.comparison.trend)
        assertEquals(TrackingType.TIME, plank.trackingType)
        assertNull(plank.comparison.current.loadGrams)
        assertEquals(Trend.BETTER, pullUp.comparison.trend)
        assertEquals(2, summary.improved)
    }

    @Test
    fun anUncomparableExerciseIsListedWithoutCounting() = runTest {
        history[FLY_DUMBBELL] = listOf(past("p", start - day, FLY_DUMBBELL, done(12_000, 10)))
        val summary = build(exercise("a", FLY_DUMBBELL, 0, done(null, 12)) to card())
        assertEquals(Trend.UNCOMPARABLE, summary.comparisons.single().comparison.trend)
        assertEquals(0, summary.compared)
    }

    @Test
    fun anExerciseWithoutCompletedSetsIsLeftOut() = runTest {
        val pending = SetLog("x", "", 0, SetKind.WORKING, 10, 12_000, completed = false)
        val summary = build(exercise("a", FLY_DUMBBELL, 0, pending) to card())
        assertTrue(summary.comparisons.isEmpty())
        assertTrue(asked.isEmpty(), "no history read for an exercise left empty")
        assertEquals(0, summary.completedSets)
    }

    @Test
    fun durationAndSetsStayInTheHeadline() = runTest {
        val summary = build(exercise("a", FLY_DUMBBELL, 0, done(10_000, 5, SetKind.WARMUP), done(12_000, 10)) to card())
        assertEquals(3_000_000L, summary.durationMs)
        assertEquals(2, summary.completedSets)
    }

    private companion object {
        const val FLY_DUMBBELL = "chest-fly.dumbbell"
        const val FLY_CABLE = "chest-fly.cable"
        const val PLANK = "plank.none"
        const val PULL_UP = "pull-up.bodyweight"
    }
}
