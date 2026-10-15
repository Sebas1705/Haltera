package es.sebas1705.haltera.data.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsTest {
    @Test
    fun `known enum names map to their values`() {
        assertEquals(ExerciseCategory.CARDIO, ExerciseCategory.fromDb("CARDIO"))
        assertEquals(TrackingType.DISTANCE_TIME, TrackingType.fromDb("distance_time"))
        assertEquals(Equipment.EZ_BAR, Equipment.fromDb(" EZ_BAR "))
        assertEquals(SetKind.DROP, SetKind.fromDb("DROP"))
    }

    @Test
    fun `unknown or missing enum values fall back to a safe default`() {
        assertEquals(ExerciseCategory.STRENGTH, ExerciseCategory.fromDb("YOGA"))
        assertEquals(TrackingType.WEIGHT_REPS, TrackingType.fromDb(null))
        assertEquals(Level.BEGINNER, Level.fromDb(""))
        assertEquals(Equipment.OTHER, Equipment.fromDb("SANDBAG"))
        assertEquals(LoadType.NONE, LoadType.fromDb("CHAINS"))
        assertEquals(ExerciseSource.CATALOG, ExerciseSource.fromDb("SHARED"))
        assertEquals(DayKind.TRAINING, DayKind.fromDb("DELOAD"))
        assertEquals(SetKind.WORKING, SetKind.fromDb("AMRAP"))
    }

    @Test
    fun `muscle lists drop unknown names and round trip`() {
        assertEquals(listOf(Muscle.CHEST, Muscle.TRICEPS), Muscle.listFromDb("CHEST,PECS,,TRICEPS"))
        assertEquals(emptyList(), Muscle.listFromDb(""))
        assertEquals("CHEST,TRICEPS", Muscle.listToDb(listOf(Muscle.CHEST, Muscle.TRICEPS)))
    }

    @Test
    fun `localized text picks the language with English as fallback`() {
        val text = LocalizedText(en = "Squat", es = "Sentadilla")
        assertEquals("Sentadilla", text.get("es"))
        assertEquals("Sentadilla", text.get("es-ES"))
        assertEquals("Squat", text.get("fr"))
    }

    @Test
    fun `slot columns round trip through JSON`() {
        val targets =
            listOf(
                SlotTarget.Fixed("squat.barbell"),
                SlotTarget.Flexible("bench-press", listOf("bench-press.dumbbell"))
            )
        targets.forEach { assertEquals(it, ModelJson.decodeTarget(ModelJson.encodeTarget(it))) }
        val prescriptions = listOf(
            Prescription.RepRange(8, 12),
            Prescription.Reps(5),
            Prescription.Duration(60),
            Prescription.Distance(5000),
        )
        prescriptions.forEach { assertEquals(it, ModelJson.decodePrescription(ModelJson.encodePrescription(it))) }
        val rules = listOf(
            ProgressionRule.None,
            ProgressionRule.DoubleProgression(2, LoadIncrement.Weight(2_500)),
            ProgressionRule.Linear(LoadIncrement.Steps(1), everyNSessions = 3),
        )
        rules.forEach { assertEquals(it, ModelJson.decodeProgression(ModelJson.encodeProgression(it))) }
    }

    @Test
    fun `unreadable slot columns decode leniently`() {
        assertNull(ModelJson.decodeTarget("{\"type\":\"circuit\"}"))
        assertNull(ModelJson.decodeTarget("not json"))
        assertEquals(Prescription.RepRange(8, 12), ModelJson.decodePrescription("{\"type\":\"amrap\"}"))
        assertEquals(
            ProgressionRule.None,
            ModelJson.decodeProgression("{\"type\":\"percentOfTrainingMax\",\"tm\":100}")
        )
    }

    @Test
    fun `default increments follow the load type`() {
        assertEquals(LoadIncrement.Weight(2_500), LoadIncrement.defaultFor(LoadType.BARBELL))
        assertEquals(LoadIncrement.Weight(2_000), LoadIncrement.defaultFor(LoadType.DUMBBELL))
        assertEquals(LoadIncrement.Steps(1), LoadIncrement.defaultFor(LoadType.BAND))
    }
}
