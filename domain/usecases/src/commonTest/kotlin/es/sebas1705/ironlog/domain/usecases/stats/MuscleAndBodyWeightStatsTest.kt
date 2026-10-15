package es.sebas1705.ironlog.domain.usecases.stats

import es.sebas1705.ironlog.data.models.Muscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MuscleAndBodyWeightStatsTest {

    private val muscles = mapOf(
        "bench" to listOf(Muscle.CHEST, Muscle.TRICEPS),
        "row" to listOf(Muscle.MIDDLE_BACK, Muscle.LATS),
        "squat" to listOf(Muscle.QUADRICEPS, Muscle.GLUTES),
        "curl-leg" to listOf(Muscle.HAMSTRINGS),
        "plank" to listOf(Muscle.ABDOMINALS),
    )
    private val current = DayWindow(TODAY - 13, TODAY) // 2 weeks
    private val previous = DayWindow(TODAY - 27, TODAY - 14)

    private val sessions = listOf(
        dated("p", TODAY - 20, move("bench", lift(80.0, 5), lift(80.0, 5))),
        dated(
            "c1",
            TODAY - 5,
            move("bench", lift(80.0, 5), lift(80.0, 5), lift(80.0, 5), lift(80.0, 5)),
            move("row", lift(60.0, 10), lift(60.0, 10)),
            move("squat", lift(100.0, 5), lift(100.0, 5), lift(100.0, 5)),
        ),
        dated("c2", TODAY - 1, move("curl-leg", lift(40.0, 10)), move("plank", lift(null, null, seconds = 60))),
    )

    @Test
    fun `weekly sets and volume per primary muscle against the previous period`() {
        val result = MuscleStats.compare(sessions, muscles, current, previous)
        val chest = result.first { it.muscle == Muscle.CHEST }
        assertEquals(Comparison(2.0, 1.0), chest.setsPerWeek)
        assertEquals(Comparison(800_000.0, 400_000.0), chest.volumePerWeek)
        // Both primary muscles get the full set count.
        assertEquals(chest.setsPerWeek, result.first { it.muscle == Muscle.TRICEPS }.setsPerWeek)
        assertEquals(Comparison(0.5, 0.0), result.first { it.muscle == Muscle.ABDOMINALS }.setsPerWeek)
        assertEquals(Comparison(0.0, 0.0), result.first { it.muscle == Muscle.ABDOMINALS }.volumePerWeek)
        // Sorted by this period's sets: chest and triceps (2) before glutes and quads (1.5; ties by muscle order).
        assertEquals(
            listOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.GLUTES, Muscle.QUADRICEPS),
            result.take(4).map { it.muscle }
        )
        // Without a previous window there is no comparison.
        assertNull(MuscleStats.compare(sessions, muscles, current, null).first().setsPerWeek.previous)
    }

    @Test
    fun `balance counts a set once per side`() {
        val balance = MuscleStats.balance(sessions, muscles, current).associateBy { it.kind }
        val pushPull = balance.getValue(BalanceKind.PUSH_PULL)
        assertEquals(2.0, pushPull.first) // 4 bench sets / 2 weeks, chest and triceps count once
        assertEquals(1.0, pushPull.second)
        assertEquals(2.0, pushPull.ratio)
        assertEquals(2.0 / 3, pushPull.firstShare!!, 1e-9)
        val upperLower = balance.getValue(BalanceKind.UPPER_LOWER)
        assertEquals(3.0, upperLower.first)
        assertEquals(2.0, upperLower.second) // 3 squat + 1 leg curl
        val quadsHams = balance.getValue(BalanceKind.QUADS_HAMSTRINGS)
        assertEquals(1.5, quadsHams.first)
        assertEquals(0.5, quadsHams.second)
        assertEquals(3.0, quadsHams.ratio)
        assertNull(Balance(BalanceKind.PUSH_PULL, 2.0, 0.0).ratio)
        assertNull(Balance(BalanceKind.PUSH_PULL, 0.0, 0.0).firstShare)
    }

    private fun weight(day: Int, kg: Double, hour: Int = 8) =
        DatedWeight(day, day * DAY_MS + hour * 3_600_000L, (kg * 1_000).toLong())

    @Test
    fun `weekly body weight averages with the change against the previous week with entries`() {
        val monday = EpochDays.weekStart(TODAY)
        val entries = listOf(
            weight(monday - 14, 80.0),
            weight(monday - 13, 81.0),
            weight(monday, 79.0),
            weight(monday + 1, 80.0),
            weight(monday + 2, 78.0),
        )
        val weeks = BodyWeightStats.weeklyAverages(entries)
        assertEquals(listOf(monday, monday - 14), weeks.map { it.weekStart })
        assertEquals(79_000.0, weeks[0].averageGrams)
        assertEquals(3, weeks[0].entries)
        assertEquals(78_000L, weeks[0].minGrams)
        assertEquals(80_000L, weeks[0].maxGrams)
        assertEquals(-1_500.0, weeks[0].change)
        assertNull(weeks[1].change)
    }

    @Test
    fun `rate of change per week from the last 28 days`() {
        // -0.1 kg per day over two weeks → -0.7 kg per week; an old entry is ignored.
        val entries = (0..14).map { weight(TODAY - 14 + it, 80.0 - 0.1 * it) } + weight(TODAY - 60, 95.0)
        val rate = assertNotNull(BodyWeightStats.ratePerWeek(entries, TODAY))
        assertEquals(-700.0, rate.gramsPerWeek, 1.0)
        assertEquals(15, rate.entries)
        assertEquals(28, rate.days)
        // Too few entries or too short a span.
        assertNull(BodyWeightStats.ratePerWeek(entries.take(2), TODAY))
        assertNull(
            BodyWeightStats.ratePerWeek(
                listOf(weight(TODAY, 80.0), weight(TODAY - 1, 80.5), weight(TODAY - 2, 81.0)),
                TODAY
            )
        )
    }
}
