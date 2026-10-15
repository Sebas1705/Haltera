package es.sebas1705.ironlog.domain.usecases.stats

import es.sebas1705.ironlog.data.models.ExerciseHistoryEntry
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StatsTest {
    private fun set(reps: Int?, load: Long?, kind: SetKind = SetKind.WORKING, completed: Boolean = true) =
        SetLog(
            id = "x",
            sessionExerciseId = "e",
            position = 0,
            kind = kind,
            reps = reps,
            loadGrams = load,
            completed = completed
        )

    private fun entry(session: String, at: Long, vararg sets: SetLog) =
        ExerciseHistoryEntry(
            session,
            at,
            SessionExercise("e$session", session, 0, "squat.barbell", sets = sets.toList())
        )

    @Test
    fun `epley one rep max`() {
        assertEquals(116_667L, Stats.epleyOneRm(100_000, 5))
        assertEquals(100_000L, Stats.epleyOneRm(100_000, 1))
        assertEquals(133_333L, Stats.epleyOneRm(100_000, 10))
        assertEquals(0L, Stats.epleyOneRm(100_000, 0))
        assertEquals(0L, Stats.epleyOneRm(-10_000, 5))
    }

    @Test
    fun `volume counts completed non warm-up sets`() {
        val sets = listOf(
            set(5, 60_000, SetKind.WARMUP),
            set(5, 100_000),
            set(5, 100_000),
            set(8, 70_000, SetKind.DROP),
            set(5, 100_000, completed = false),
            set(10, null),
            set(5, -20_000),
        )
        assertEquals(1_560_000L, Stats.volume(sets))
        assertEquals(3_120_000L, Stats.volume(sets, bilateral = true))
    }

    @Test
    fun `personal records over the history`() {
        val history = listOf(
            entry("a", 1, set(5, 100_000), set(5, 100_000)),
            entry("b", 2, set(1, 120_000)),
            entry("c", 3, set(12, 60_000), set(12, 60_000), set(12, 60_000)),
        )
        val prs = Stats.personalRecords(history)
        assertEquals(Record(120_000, "b", 2), prs.heaviestLoad)
        assertEquals(Record(120_000, "b", 2), prs.bestEstimatedOneRm)
        assertEquals(Record(12, "c", 3), prs.mostReps)
        assertEquals(Record(2_160_000, "c", 3), prs.bestSessionVolume)
    }

    @Test
    fun `no qualifying sets gives no records`() {
        val prs = Stats.personalRecords(listOf(entry("a", 1, set(5, 60_000, SetKind.WARMUP))))
        assertNull(prs.heaviestLoad)
        assertNull(prs.bestSessionVolume)
    }

    @Test
    fun `new records compare against the previous history`() {
        val previous = listOf(entry("a", 1, set(5, 100_000), set(5, 100_000)))
        val current = entry("b", 2, set(3, 105_000))
        assertEquals(setOf(RecordKind.HEAVIEST_LOAD), Stats.newRecords(previous, current))
        assertEquals(RecordKind.entries.toSet(), Stats.newRecords(emptyList(), current))
    }
}
