package es.sebas1705.ironlog.feature.session

import es.sebas1705.ironlog.core.units.PlateCalculator
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.feature.session.load.LoadDetail
import es.sebas1705.ironlog.feature.session.load.LoadSteps
import es.sebas1705.ironlog.feature.session.load.isMachinePlate
import es.sebas1705.ironlog.feature.session.timer.TimerMath
import es.sebas1705.ironlog.feature.session.timer.TimerPhase
import es.sebas1705.ironlog.feature.session.timer.TimerSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimerAndLoadTest {

    @Test
    fun remainingSecondsRoundUpAndPhasesFollowAbsoluteInstants() {
        val s = TimerSnapshot(sessionId = "s", restStartsAt = 0, restEndsAt = 90_000)
        assertEquals(TimerPhase.REST, TimerMath.phase(s, 0))
        assertEquals(90, TimerMath.remainingSeconds(s, 0))
        assertEquals(1, TimerMath.remainingSeconds(s, 89_001))
        assertEquals(TimerPhase.IDLE, TimerMath.phase(s, 90_000))
        assertEquals(0, TimerMath.remainingMillis(s, 120_000))
        assertNull(TimerMath.phaseEndsAt(s, 120_000))
        assertEquals(0.5f, TimerMath.restFraction(s, 45_000))
    }

    @Test
    fun alertInstantsOnlyIncludeTheFuture() {
        val s = TimerSnapshot(countdownEndsAt = 3_000, workEndsAt = 33_000, restEndsAt = 93_000)
        assertEquals(listOf(33_000L, 93_000L), s.alertInstants(0))
        assertEquals(listOf(93_000L), s.alertInstants(40_000))
        assertTrue(s.alertInstants(100_000).isEmpty())
    }

    @Test
    fun formatsDurations() {
        assertEquals("1:05", TimerMath.formatSeconds(65))
        assertEquals("0:00", TimerMath.formatSeconds(-3))
        assertEquals("12:03", TimerMath.formatElapsed(723_000))
        assertEquals("1:02:03", TimerMath.formatElapsed(3_723_000))
    }

    @Test
    fun loadStepsFollowLoadTypeAndUnit() {
        val kgPlates = PlateCalculator.KG_PLATES
        assertEquals(2_500, LoadSteps.stepGrams(LoadType.BARBELL, WeightUnit.KG, kgPlates))
        assertEquals(2_000, LoadSteps.stepGrams(LoadType.DUMBBELL, WeightUnit.KG, kgPlates))
        assertEquals(Weights.lbToGrams(5.0), LoadSteps.stepGrams(LoadType.DUMBBELL, WeightUnit.LB, kgPlates))
        assertEquals(1, LoadSteps.stepGrams(LoadType.MACHINE, WeightUnit.KG, kgPlates, machinePlate = true))
        // A barbell never goes below the empty bar; bands stay within their levels.
        assertEquals(20_000, LoadSteps.step(20_000, -1, LoadType.BARBELL, WeightUnit.KG, kgPlates, 20_000))
        assertEquals(22_500, LoadSteps.step(null, 1, LoadType.BARBELL, WeightUnit.KG, kgPlates, 20_000))
        assertEquals(1, LoadSteps.step(1, -1, LoadType.BAND, WeightUnit.KG, kgPlates, 20_000))
        assertEquals(-2_500, LoadSteps.step(0, -1, LoadType.BODYWEIGHT, WeightUnit.KG, kgPlates, 20_000))
    }

    @Test
    fun loadDetailRoundTripsAndIsLenient() {
        val json = LoadDetail.encode(LoadDetail.MachinePlate)
        assertTrue(isMachinePlate(json))
        assertEquals(
            LoadDetail.Bodyweight(assisted = true),
            LoadDetail.decode(LoadDetail.encode(LoadDetail.Bodyweight(true)))
        )
        assertNull(LoadDetail.decode("{not json"))
        assertNull(LoadDetail.decode(null))
    }
}
