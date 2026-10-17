package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.ExerciseHistoryEntry
import es.sebas1705.haltera.data.models.Routine
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.SlotTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the one volume rule (TrainingMath.volume, core design §3.3 "Volume") for every way a load is entered,
 * and checks that every statistic built on it (overview, muscles, progression, records) gives the same number.
 */
class VolumeRuleTest {
    private val routines = listOf(
        Routine(
            id = "r",
            name = "Upper",
            days = listOf(
                RoutineDay(
                    id = "d",
                    routineId = "r",
                    position = 0,
                    name = "A",
                    slots = listOf(
                        slot("slot-bench", "bench-press.barbell"),
                        slot("slot-db-press", "bench-press.dumbbell", bilateral = true),
                        slot("slot-one-arm-row", "one-arm-row.dumbbell"),
                    ),
                ),
            ),
        ),
    )
    private fun slot(id: String, variantId: String, bilateral: Boolean = false) =
        RoutineSlot(id = id, dayId = "d", position = 0, target = SlotTarget.Fixed(variantId), bilateral = bilateral)

    /** The slot's "Both hands" flag when the exercise is logged: what the repository snapshots. */
    private fun flagOf(slotId: String?): Boolean =
        routines.flatMap { r -> r.days.flatMap { it.slots } }.any { it.id == slotId && it.bilateral }

    /** An exercise logged now from [slotId] (null: a free session), with the snapshot taken from the routines. */
    private fun logged(variantId: String, vararg sets: SetLog, slotId: String? = null) =
        move(variantId, *sets, slotId = slotId, bilateral = flagOf(slotId))

    private fun volume(vararg sets: SetLog, slotId: String? = null) = TrainingMath.volume(
        logged("v", *sets, slotId = slotId)
    )

    @Test
    fun `the snapshot is the slot flag at log time`() {
        assertTrue(flagOf("slot-db-press"))
        assertFalse(flagOf("slot-bench"))
        assertFalse(flagOf(null))
    }

    @Test
    fun `past volume ignores later changes of the slot`() {
        val press = logged("bench-press.dumbbell", lift(24.0, 10), lift(24.0, 10), slotId = "slot-db-press")
        assertEquals(960_000L, TrainingMath.volume(press))
        // Slot removed, routine deleted or custom exercise deleted: the link is cleared, the snapshot stays.
        assertEquals(960_000L, TrainingMath.volume(press.copy(slotId = null)))
        // "Both hands" switched off afterwards: only exercises logged from now on count one implement.
        val after = move("bench-press.dumbbell", lift(24.0, 10), lift(24.0, 10), slotId = "slot-db-press")
        assertEquals(960_000L, TrainingMath.volume(press))
        assertEquals(480_000L, TrainingMath.volume(after))
    }

    @Test
    fun `barbell counts the stored total once`() {
        // 100 kg = bar + plates, as stored; 3 × 5 plus a warm-up that does not count.
        assertEquals(1_500_000L, volume(lift(100.0, 5), lift(100.0, 5), lift(100.0, 5), lift(40.0, 10, SetKind.WARMUP)))
        assertEquals(1_500_000L, volume(lift(100.0, 5), lift(100.0, 5), lift(100.0, 5), slotId = "slot-bench"))
    }

    @Test
    fun `a single dumbbell counts once`() {
        // One-arm row from a slot that is not bilateral, and a free-session dumbbell exercise (no slot).
        assertEquals(300_000L, volume(lift(30.0, 10), slotId = "slot-one-arm-row"))
        assertEquals(300_000L, volume(lift(30.0, 10)))
    }

    @Test
    fun `a pair of dumbbells from a bilateral slot counts both`() {
        // 2 × 10 reps with 24 kg per hand: 24 × 10 × 2 per set.
        assertEquals(960_000L, volume(lift(24.0, 10), lift(24.0, 10), slotId = "slot-db-press"))
        // The same sets logged in a free session (no slot) count one dumbbell: the slot flag decides.
        assertEquals(480_000L, volume(lift(24.0, 10), lift(24.0, 10)))
    }

    @Test
    fun `machine weight counts, stack positions do not`() {
        assertEquals(500_000L, volume(lift(50.0, 10, detail = """{"type":"machineWeight"}""")))
        assertEquals(0L, volume(lift(0.007, 10, detail = """{"type":"machinePlate"}""")))
    }

    @Test
    fun `bodyweight counts only an added load`() {
        assertEquals(0L, volume(lift(null, 12)))
        assertEquals(0L, volume(lift(0.0, 12)))
        assertEquals(0L, volume(lift(-20.0, 8, detail = """{"type":"bodyweight","assisted":true}""")))
        assertEquals(200_000L, volume(lift(20.0, 10, detail = """{"type":"bodyweight","assisted":false}""")))
    }

    @Test
    fun `band levels and timed or distance sets add nothing`() {
        assertEquals(0L, volume(lift(0.003, 15, detail = """{"type":"band"}""")))
        assertEquals(0L, volume(lift(null, null, seconds = 60)))
        // Weighted carry: a load and a time, no reps.
        assertEquals(0L, volume(lift(32.0, null, seconds = 40)))
    }

    @Test
    fun `every statistic agrees on the same sessions`() {
        val bench = logged("bench-press.barbell", lift(100.0, 5), lift(100.0, 5), slotId = "slot-bench")
        val press = logged("bench-press.dumbbell", lift(30.0, 10), lift(30.0, 8), slotId = "slot-db-press")
        val curl = logged("biceps-curl.dumbbell", lift(12.0, 12))
        val day = dated("s1", TODAY, bench, press, curl)
        val expected = 1_000_000L + (300_000L + 240_000L) * 2 + 144_000L
        assertEquals(expected, TrainingMath.volume(day.session))

        val window = DayWindow(TODAY, TODAY)
        assertEquals(expected, Overview.totals(window, listOf(day)).volume)

        val pressHistory = listOf(ExerciseHistoryEntry("s1", day.session.startedAt, press))
        val pressVolume = TrainingMath.volume(press)
        assertEquals(1_080_000L, pressVolume)
        assertEquals(pressVolume, Stats.personalRecords(pressHistory).bestSessionVolume?.value)
        assertEquals(pressVolume, ExerciseProgression.table(pressHistory).single().volume)
    }

    @Test
    fun `the finish summary's session volume record uses the same rule`() {
        val before = listOf(
            ExerciseHistoryEntry(
                "s0",
                0,
                // A free session: one dumbbell, 30 × 10 × 2 = 600 kg.
                move("bench-press.dumbbell", lift(30.0, 10), lift(30.0, 10)),
            ),
        )
        // From the bilateral slot: 20 × 10 × 2 = 400 kg per hand, 800 kg with both, so a volume record.
        val now = ExerciseHistoryEntry(
            "s1",
            1,
            logged("bench-press.dumbbell", lift(20.0, 10), lift(20.0, 10), slotId = "slot-db-press"),
        )
        assertTrue(RecordKind.SESSION_VOLUME in Stats.newRecords(before, now))
        val oneHand = now.copy(exercise = now.exercise.copy(bilateral = false))
        assertFalse(RecordKind.SESSION_VOLUME in Stats.newRecords(before, oneHand), "one implement is no record")
    }
}
