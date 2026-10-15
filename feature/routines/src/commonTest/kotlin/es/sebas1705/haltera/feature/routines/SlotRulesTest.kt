package es.sebas1705.ironlog.feature.routines

import es.sebas1705.ironlog.data.models.LoadIncrement
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.ProgressionRule
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.feature.routines.model.PrescriptionKind
import es.sebas1705.ironlog.feature.routines.model.PrescriptionRules
import es.sebas1705.ironlog.feature.routines.model.SlotLimits
import es.sebas1705.ironlog.feature.routines.model.defaultSpec
import es.sebas1705.ironlog.feature.routines.model.movedIndex
import es.sebas1705.ironlog.feature.routines.ui.components.formatSeconds
import es.sebas1705.ironlog.feature.routines.ui.components.supersetLetter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlotRulesTest {

    @Test
    fun `allowed prescriptions follow the tracking type`() {
        assertEquals(
            listOf(PrescriptionKind.REP_RANGE, PrescriptionKind.REPS),
            PrescriptionRules.allowedKinds(TrackingType.BODYWEIGHT_REPS),
        )
        assertEquals(listOf(PrescriptionKind.DURATION), PrescriptionRules.allowedKinds(TrackingType.WEIGHT_TIME))
        assertEquals(
            listOf(PrescriptionKind.DISTANCE, PrescriptionKind.DURATION),
            PrescriptionRules.allowedKinds(TrackingType.DISTANCE_TIME),
        )
        TrackingType.entries.forEach { type ->
            assertTrue(PrescriptionRules.isCompatible(PrescriptionRules.defaultFor(type), type), "default for $type")
        }
        assertFalse(PrescriptionRules.isCompatible(Prescription.Reps(5), TrackingType.TIME))
        assertFalse(PrescriptionRules.isCompatible(Prescription.Duration(30), TrackingType.WEIGHT_REPS))
    }

    @Test
    fun `normalize clamps and orders a rep range`() {
        assertEquals(Prescription.RepRange(10, 10), PrescriptionRules.normalize(Prescription.RepRange(10, 6)))
        assertEquals(Prescription.RepRange(1, 100), PrescriptionRules.normalize(Prescription.RepRange(0, 500)))
        assertEquals(Prescription.Reps(1), PrescriptionRules.normalize(Prescription.Reps(-2)))
        assertEquals(
            Prescription.Duration(SlotLimits.DURATION_S.first),
            PrescriptionRules.normalize(Prescription.Duration(0)),
        )
        assertEquals(
            Prescription.Distance(SlotLimits.DISTANCE_M.last),
            PrescriptionRules.normalize(Prescription.Distance(Int.MAX_VALUE)),
        )
    }

    @Test
    fun `convert falls back to a sensible default when nothing carries over`() {
        assertEquals(
            Prescription.Distance(1_000),
            PrescriptionRules.convert(Prescription.Duration(60), PrescriptionKind.DISTANCE)
        )
        assertEquals(
            Prescription.Duration(60),
            PrescriptionRules.convert(Prescription.Duration(60), PrescriptionKind.DURATION)
        )
    }

    @Test
    fun `a new slot fits its exercise`() {
        val barbell = defaultSpec(TestCatalog.bench, SlotTarget.Fixed(TestCatalog.BENCH_BARBELL))
        assertEquals(Prescription.RepRange(8, 12), barbell.prescription)
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)), barbell.progression)
        assertFalse(barbell.bilateral)

        val dumbbell = defaultSpec(TestCatalog.bench, SlotTarget.Fixed(TestCatalog.BENCH_DUMBBELL))
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_000)), dumbbell.progression)
        assertTrue(dumbbell.bilateral)

        val plank = defaultSpec(TestCatalog.plank, SlotTarget.Fixed("plank.none"))
        assertEquals(Prescription.Duration(30), plank.prescription)
        assertEquals(ProgressionRule.None, plank.progression)

        val flexible = defaultSpec(TestCatalog.bench, SlotTarget.Flexible("bench-press"))
        assertEquals(ProgressionRule.DoubleProgression(1, LoadIncrement.Weight(2_500)), flexible.progression)
    }

    @Test
    fun `helpers`() {
        assertEquals(1, movedIndex(0, 1, 3))
        assertNull(movedIndex(0, -1, 3))
        assertNull(movedIndex(2, 1, 3))
        assertEquals("1:30", formatSeconds(90))
        assertEquals("0:05", formatSeconds(5))
        assertEquals("A", supersetLetter(1))
        assertEquals("C", supersetLetter(3))
    }
}
