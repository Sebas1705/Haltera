package es.sebas1705.haltera.domain.usecases.stats

import es.sebas1705.haltera.data.models.SetKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingMathTest {

    @Test
    fun `volume is load times reps of counted sets`() {
        assertEquals(500_000L, TrainingMath.setVolume(lift(100.0, 5)))
        assertEquals(0L, TrainingMath.setVolume(lift(60.0, 5, SetKind.WARMUP)))
        assertEquals(0L, TrainingMath.setVolume(lift(100.0, 5, completed = false)))
        assertEquals(560_000L, TrainingMath.setVolume(lift(70.0, 8, SetKind.DROP)))
        assertEquals(1_000_000L, TrainingMath.setVolume(lift(100.0, 5), bilateral = true))
    }

    @Test
    fun `bodyweight sets add only the added load`() {
        assertEquals(0L, TrainingMath.setVolume(lift(0.0, 12)))
        assertEquals(0L, TrainingMath.setVolume(lift(-20.0, 8, detail = """{"type":"bodyweight","assisted":true}""")))
        assertEquals(
            100_000L,
            TrainingMath.setVolume(lift(10.0, 10, detail = """{"type":"bodyweight","assisted":false}"""))
        )
    }

    @Test
    fun `band levels and machine plate numbers are not weights`() {
        val band = lift(0.005, 12, detail = """{"type":"band"}""")
        val plate = lift(0.007, 10, detail = """{"type":"machinePlate"}""")
        val machineKg = lift(50.0, 10, detail = """{"type":"machineWeight"}""")
        assertTrue(TrainingMath.isIndexLoad(band))
        assertTrue(TrainingMath.isIndexLoad(plate))
        assertFalse(TrainingMath.isIndexLoad(machineKg))
        assertEquals(0L, TrainingMath.setVolume(band))
        assertEquals(0L, TrainingMath.setOneRm(plate))
        assertNull(TrainingMath.weightGrams(plate))
        assertEquals(500_000L, TrainingMath.setVolume(machineKg))
    }

    @Test
    fun `bilateral slots double the exercise volume`() {
        val curls = move("curl.dumbbell", lift(12.0, 10), lift(12.0, 10), slotId = "slot-1")
        assertEquals(240_000L, TrainingMath.volume(curls))
        assertEquals(480_000L, TrainingMath.volume(curls, bilateralSlots = setOf("slot-1")))
        assertEquals(240_000L, TrainingMath.volume(curls.copy(slotId = null), bilateralSlots = setOf("slot-1")))
    }

    @Test
    fun `effective sets need something done`() {
        val exercise = move(
            "x",
            lift(60.0, 5, SetKind.WARMUP),
            lift(100.0, 5),
            lift(null, null, seconds = 45),
            lift(100.0, 0),
            lift(100.0, 5, completed = false),
            lift(80.0, 6, SetKind.FAILURE),
        )
        assertEquals(3, TrainingMath.effectiveSets(exercise))
    }

    @Test
    fun `one rm and duration`() {
        assertEquals(116_667L, TrainingMath.setOneRm(lift(100.0, 5)))
        assertEquals(0L, TrainingMath.setOneRm(lift(100.0, 5, SetKind.WARMUP)))
        val session = dated("a", TODAY, minutes = 75).session
        assertEquals(75 * 60_000L, TrainingMath.durationMillis(session))
        assertNull(TrainingMath.durationMillis(dated("b", TODAY, minutes = null).session))
    }
}
