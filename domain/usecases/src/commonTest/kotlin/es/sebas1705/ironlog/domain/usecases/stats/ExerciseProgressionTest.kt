package es.sebas1705.ironlog.domain.usecases.stats

import es.sebas1705.ironlog.data.models.SetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExerciseProgressionTest {

    @Test
    fun `one row per session with best set, deltas and records`() {
        val history = listOf(
            historyEntry("a", 1, lift(60.0, 5, SetKind.WARMUP), lift(100.0, 5), lift(100.0, 4)),
            historyEntry("b", 8, lift(102.5, 5), lift(102.5, 5)),
            historyEntry("c", 15, lift(100.0, 3)),
            historyEntry("skip", 20, lift(100.0, 5, completed = false)),
        )
        val rows = ExerciseProgression.table(history)
        assertEquals(listOf("c", "b", "a"), rows.map { it.sessionId })

        val a = rows[2]
        assertEquals(BestSet(100_000, 5), a.bestSet)
        assertEquals(116_667L, a.oneRm)
        assertEquals(100_000L, a.topLoad)
        assertEquals(900_000L, a.volume)
        assertEquals(2, a.effectiveSets)
        assertNull(a.oneRmDelta)
        assertEquals(emptySet(), a.prs)

        val b = rows[1]
        assertEquals(119_583L, b.oneRm)
        assertEquals(119_583L - 116_667L, b.oneRmDelta)
        assertEquals(setOf(PrKind.HEAVIEST_LOAD, PrKind.ESTIMATED_ONE_RM, PrKind.SESSION_VOLUME), b.prs)

        val c = rows[0]
        assertEquals(110_000L - 119_583L, c.oneRmDelta)
        assertEquals(emptySet(), c.prs)
    }

    @Test
    fun `entries of the same session are merged and unloaded sets keep their reps`() {
        val history = listOf(
            historyEntry("a", 1, lift(0.0, 12)),
            historyEntry("a", 1, lift(0.0, 15)),
            historyEntry("b", 2, lift(null, null, seconds = 60)),
        )
        val rows = ExerciseProgression.table(history)
        assertEquals(2, rows.size)
        val a = rows.single { it.sessionId == "a" }
        assertEquals(BestSet(0, 15), a.bestSet)
        assertEquals(0L, a.oneRm)
        assertEquals(2, a.effectiveSets)
        val timed = rows.single { it.sessionId == "b" }
        assertNull(timed.bestSet)
        assertNull(timed.topLoad)
        assertNull(timed.oneRmDelta)
    }

    @Test
    fun `rep ranges count effective sets`() {
        val history = listOf(
            historyEntry("a", 1, lift(100.0, 3), lift(100.0, 5), lift(80.0, 8), lift(60.0, 12)),
            historyEntry("b", 2, lift(50.0, 15), lift(20.0, 25), lift(60.0, 10, SetKind.WARMUP), lift(1.0, 0)),
        )
        val ranges = ExerciseProgression.repRanges(history)
        assertEquals(RepRange.entries.toList(), ranges.keys.toList())
        assertEquals(listOf(2, 1, 1, 1, 1), ranges.values.toList())
        assertNull(RepRange.of(0))
        assertEquals(RepRange.R21_PLUS, RepRange.of(100))
        assertEquals(RepRange.R9_12, RepRange.of(9))
    }

    @Test
    fun `e1rm trend is the least-squares slope per week`() {
        // +1 kg of estimated 1RM per day → +7 kg per week.
        val history = (0 until 4).map { i -> historyEntry("s$i", 100 + i * 7, lift(100.0 + 7 * i, 1)) }
        val trend = assertNotNull(ExerciseProgression.oneRmTrend(ExerciseProgression.table(history)))
        assertEquals(7_000.0, trend.gramsPerWeek, 1e-6)
        assertEquals(1.0, trend.trend.r2, 1e-9)
        assertEquals(100 * DAY_MS, trend.firstAt)
        assertEquals(121 * DAY_MS, trend.lastAt)
        // Too few sessions.
        assertNull(ExerciseProgression.oneRmTrend(ExerciseProgression.table(history.take(2))))
    }
}
