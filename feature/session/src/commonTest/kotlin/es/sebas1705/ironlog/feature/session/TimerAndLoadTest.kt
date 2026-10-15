package es.sebas1705.ironlog.feature.session

import es.sebas1705.ironlog.core.units.PlateCalculator
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.feature.session.load.LoadDetail
import es.sebas1705.ironlog.feature.session.load.LoadSteps
import es.sebas1705.ironlog.feature.session.load.Warmups
import es.sebas1705.ironlog.feature.session.load.WarmupRounding
import es.sebas1705.ironlog.feature.session.ui.LoadStepOptions
import es.sebas1705.ironlog.data.models.SetInput
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
        // A step chosen for the variant replaces the default, except for band levels.
        assertEquals(
            21_250,
            LoadSteps.step(20_000, 1, LoadType.BARBELL, WeightUnit.KG, kgPlates, 20_000, override = 1_250)
        )
        assertEquals(
            3,
            LoadSteps.step(1, 1, LoadType.MACHINE, WeightUnit.KG, kgPlates, 20_000, machinePlate = true, override = 2)
        )
        assertEquals(2, LoadSteps.step(1, 1, LoadType.BAND, WeightUnit.KG, kgPlates, 20_000, override = 5))
        assertEquals(2_500, LoadSteps.stepGrams(LoadType.BARBELL, WeightUnit.KG, kgPlates, override = 0))
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

    @Test
    fun warmupsWithoutHistoryStartAtPercentagesOfTheWorkingLoad() {
        val bar = WarmupRounding(stepGrams = 2_500, barGrams = 20_000, plates = PlateCalculator.KG_PLATES)
        val working = SetInput(loadGrams = 80_000)
        assertEquals(listOf(60L), Warmups.percents(1))
        assertEquals(listOf(50L, 70L), Warmups.percents(2))
        assertEquals(listOf(40L, 60L, 80L), Warmups.percents(3))
        // 48 kg rounds down to the 2.5 kg step: 47.5 kg.
        assertEquals(listOf(47_500L), Warmups.generated(1, working, LoadType.BARBELL, bar))
        // 40 and 56 kg: 56 rounds down to 55.
        assertEquals(listOf(40_000L, 55_000L), Warmups.generated(2, working, LoadType.BARBELL, bar))
        // Never below the bar, never above the working load.
        assertEquals(
            listOf(20_000L, 20_000L),
            Warmups.generated(2, SetInput(loadGrams = 20_000), LoadType.BARBELL, bar)
        )
        // Plates that cannot build the step: the closest load below.
        val bigPlates = WarmupRounding(stepGrams = 2_500, barGrams = 20_000, plates = listOf(20_000, 10_000))
        assertEquals(
            listOf(40_000L),
            Warmups.generated(1, SetInput(loadGrams = 90_000), LoadType.BARBELL, bigPlates)
        )
        // Dumbbells: the 2 kg step.
        assertEquals(
            listOf(12_000L, 16_000L),
            Warmups.generated(2, SetInput(loadGrams = 24_000), LoadType.DUMBBELL, WarmupRounding(2_000)),
        )
    }

    @Test
    fun warmupsAreNotGeneratedWithoutAWeight() {
        val rounding = WarmupRounding(2_500)
        assertNull(Warmups.generated(2, SetInput(loadGrams = null), LoadType.BARBELL, rounding))
        assertNull(Warmups.generated(2, SetInput(loadGrams = 5), LoadType.BAND, rounding))
        assertNull(Warmups.generated(2, SetInput(loadGrams = 10_000), LoadType.BODYWEIGHT, rounding))
        val stack = SetInput(loadGrams = 7, loadDetail = LoadDetail.encode(LoadDetail.MachinePlate))
        assertNull(Warmups.generated(1, stack, LoadType.MACHINE, rounding))
    }

    @Test
    fun theStepDialogDoesNotRepeatTheDefault() {
        val presets = LoadStepOptions.presets(plateMode = false, unit = WeightUnit.KG, default = 2_500)
        assertTrue(2_500L !in presets)
        assertEquals(5, presets.size)
        assertEquals(
            listOf(2L, 3L, 4L, 5L),
            LoadStepOptions.presets(plateMode = true, unit = WeightUnit.KG, default = 1)
        )
    }
}
