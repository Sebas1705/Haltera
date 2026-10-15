package es.sebas1705.ironlog.domain.usecases.stats

import es.sebas1705.ironlog.data.models.SetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrDetectorTest {

    private fun kinds(events: List<PrEvent>, session: String) = events.filter {
        it.sessionId == session
    }.map { it.kind }.toSet()

    @Test
    fun `the first session is only the baseline`() {
        assertTrue(PrDetector.events(listOf(historyEntry("a", 1, lift(100.0, 5)))).isEmpty())
        assertTrue(PrDetector.events(emptyList()).isEmpty())
    }

    @Test
    fun `each kind is detected when strictly beaten`() {
        val history = listOf(
            historyEntry("a", 1, lift(100.0, 5), lift(100.0, 5)),
            // Heavier single: load and e1RM? 110 × 1 = 110 kg < 116.7 kg, so only the load.
            historyEntry("b", 2, lift(110.0, 1)),
            // 100 × 6: more reps at 100 kg, better e1RM (120 kg), but less volume.
            historyEntry("c", 3, lift(100.0, 6)),
            // Same as a: nothing new (ties are not records).
            historyEntry("d", 4, lift(100.0, 5), lift(100.0, 5)),
            // More volume only.
            historyEntry("e", 5, lift(90.0, 5), lift(90.0, 5), lift(90.0, 5)),
        )
        val events = PrDetector.events(history)
        assertEquals(setOf(PrKind.HEAVIEST_LOAD), kinds(events, "b"))
        assertEquals(setOf(PrKind.ESTIMATED_ONE_RM, PrKind.REPS_AT_LOAD), kinds(events, "c"))
        assertEquals(emptySet(), kinds(events, "d"))
        assertEquals(setOf(PrKind.SESSION_VOLUME), kinds(events, "e"))
        val reps = events.single { it.kind == PrKind.REPS_AT_LOAD }
        assertEquals(6L, reps.value)
        assertEquals(100_000L, reps.loadGrams)
    }

    @Test
    fun `reps at a load compares with that load or heavier`() {
        val history = listOf(
            historyEntry("a", 1, lift(100.0, 3), lift(80.0, 8)),
            // 90 × 6: no set at 90 before, but 100 × 3 was heavier with fewer reps → reps record.
            historyEntry("b", 2, lift(90.0, 6)),
            // 70 × 8: 80 × 8 was heavier with as many reps → not a record.
            historyEntry("c", 3, lift(70.0, 8)),
        )
        val events = PrDetector.events(history)
        assertEquals(setOf(PrKind.REPS_AT_LOAD), kinds(events, "b"))
        assertEquals(emptySet(), kinds(events, "c"))
    }

    @Test
    fun `warm-ups, index loads and order of input are ignored`() {
        val history = listOf(
            historyEntry("b", 2, lift(150.0, 5, SetKind.WARMUP), lift(100.0, 5)),
            historyEntry("a", 1, lift(100.0, 5)),
        )
        assertTrue(PrDetector.events(history).isEmpty())
        val bands = listOf(
            historyEntry("a", 1, lift(0.003, 10, detail = """{"type":"band"}""")),
            historyEntry("b", 2, lift(0.005, 12, detail = """{"type":"band"}""")),
        )
        assertTrue(PrDetector.events(bands).isEmpty())
    }

    @Test
    fun `records per variant across sessions`() {
        val sessions = listOf(
            dated("a", 1, move("squat", lift(100.0, 5)), move("bench", lift(80.0, 5))),
            dated("b", 2, move("squat", lift(105.0, 5)), move("bench", lift(80.0, 5))),
            dated("c", 3, move("bench", lift(82.5, 5)), minutes = null), // in progress: ignored
        ).map { it.session }
        val events = PrDetector.eventsForSessions(sessions)
        assertTrue(events.all { it.variantId == "squat" && it.sessionId == "b" })
        assertEquals(
            setOf(PrKind.HEAVIEST_LOAD, PrKind.ESTIMATED_ONE_RM, PrKind.SESSION_VOLUME),
            events.map { it.kind }.toSet()
        )
    }
}
