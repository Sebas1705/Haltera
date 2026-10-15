package es.sebas1705.haltera.feature.progress.model

import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.domain.usecases.stats.ExerciseProgression
import es.sebas1705.haltera.feature.progress.performed
import es.sebas1705.haltera.feature.progress.session
import es.sebas1705.haltera.feature.progress.set
import es.sebas1705.haltera.feature.progress.utc
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressMathTest {

    @Test
    fun `moving average uses up to seven previous values`() {
        val values = (1..8).map { it.toDouble() }
        assertEquals(listOf(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0), ProgressMath.movingAverage(values))
        assertEquals(listOf(1.0, 1.5, 2.5), ProgressMath.movingAverage(listOf(1.0, 2.0, 3.0), window = 2))
        assertEquals(emptyList(), ProgressMath.movingAverage(emptyList()))
    }

    @Test
    fun `body weight chart is oldest first with its moving average`() {
        val entries = listOf(
            BodyWeightEntry("b", measuredAt = 2, weightGrams = 82_000),
            BodyWeightEntry("a", measuredAt = 1, weightGrams = 80_000),
        )
        val chart = ProgressMath.bodyWeightChart(entries)
        assertEquals(listOf(ChartPoint(1, 80_000.0), ChartPoint(2, 82_000.0)), chart.raw)
        assertEquals(listOf(ChartPoint(1, 80_000.0), ChartPoint(2, 81_000.0)), chart.average)
    }

    @Test
    fun `exercise charts merge a session, skip warm-ups and sort by time`() {
        val later = utc(2026, 10, 8)
        val earlier = utc(2026, 10, 1)
        val history = listOf(
            entry("s2", later, performed("bench.barbell", set(100.0, 5), set(120.0, 1, SetKind.WARMUP), set(90.0, 8))),
            entry("s2", later, performed("bench.barbell", set(80.0, 10))),
            entry("s1", earlier, performed("bench.barbell", set(60.0, 5))),
            entry("s0", utc(2026, 9, 1), performed("bench.barbell", set(40.0, 10, SetKind.WARMUP))),
        )

        val charts = ProgressMath.exerciseCharts(ExerciseProgression.table(history))

        assertEquals(listOf(ChartPoint(earlier, 60_000.0), ChartPoint(later, 100_000.0)), charts.topSet)
        // Epley: 60 × (1 + 5/30) = 70 kg; best of 100×5 (116.667), 90×8 (114) and 80×10 (106.667).
        assertEquals(listOf(ChartPoint(earlier, 70_000.0), ChartPoint(later, 116_667.0)), charts.oneRm)
        // 100×5 + 90×8 + 80×10 = 2020 kg·reps.
        assertEquals(listOf(ChartPoint(earlier, 300_000.0), ChartPoint(later, 2_020_000.0)), charts.volume)
    }

    @Test
    fun `change compares the latest entry with the oldest one of the last 30 days`() {
        val day = 86_400_000L
        val now = utc(2026, 10, 14)
        val entries = listOf(
            BodyWeightEntry("old", now - 40 * day, 90_000),
            BodyWeightEntry("start", now - 20 * day, 85_000),
            BodyWeightEntry("mid", now - 10 * day, 86_000),
            BodyWeightEntry("latest", now, 84_000),
        )
        assertEquals(-1_000L, ProgressMath.change(entries))
        assertNull(ProgressMath.change(entries.take(1)))
        assertNull(ProgressMath.change(listOf(entries[0], entries[3])))
    }

    @Test
    fun `sessions are grouped by local start day with their summary`() {
        val sessions = listOf(
            session(
                "late",
                utc(2026, 10, 14, hour = 19),
                performed("bench.barbell", set(100.0, 5)),
                routineId = "r1",
                dayName = "Push"
            ),
            session("early", utc(2026, 10, 14, hour = 7), durationMinutes = null),
            session("other", utc(2026, 10, 3)),
        )

        val grouped = ProgressMath.groupByDay(sessions, mapOf("r1" to "PPL"), TimeZone.UTC)

        assertEquals(setOf(LocalDate(2026, 10, 3), LocalDate(2026, 10, 14)), grouped.keys)
        val day = grouped.getValue(LocalDate(2026, 10, 14))
        assertEquals(listOf("early", "late"), day.map { it.id })
        assertTrue(day[0].inProgress)
        assertEquals(SessionSummary("late", utc(2026, 10, 14, hour = 19), 3_600_000, "PPL", "Push", 1, 500_000), day[1])
    }

    @Test
    fun `the exercise count leaves out exercises without completed sets`() {
        val finished = session(
            "s",
            utc(2026, 10, 15),
            performed("squat.barbell", set(100.0, 5)),
            performed("bench.barbell", set(80.0, 5, completed = false)),
            performed("row.barbell"),
            performed("curl.dumbbell", set(12.0, 10, SetKind.WARMUP), set(14.0, 8, completed = false)),
        )

        assertEquals(2, ProgressMath.summarize(finished, emptyMap()).exerciseCount)
    }

    @Test
    fun `month key navigates across years`() {
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).plus(1))
        assertEquals(MonthKey(2025, 11), MonthKey(2026, 1).plus(-2))
        assertEquals(31, MonthKey(2026, 10).length)
        assertEquals(28, MonthKey(2026, 2).length)
        // 1 October 2026 is a Thursday: three blanks in a Monday-first grid.
        assertEquals(3, MonthKey(2026, 10).leadingBlanks)
    }

    private fun entry(sessionId: String, at: Long, exercise: SessionExercise) =
        ExerciseHistoryEntry(sessionId, at, exercise)
}
