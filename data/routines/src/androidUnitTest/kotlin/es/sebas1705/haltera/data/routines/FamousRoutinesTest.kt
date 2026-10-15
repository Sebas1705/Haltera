package es.sebas1705.haltera.data.routines

import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.SlotSpec
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FamousRoutinesTest {
    // Unit tests run with the module directory as working directory.
    private val catalog = File("../../composeApp/src/commonMain/composeResources/files/catalog.json")

    private val exercises: List<JsonObject> by lazy {
        Json.parseToJsonElement(catalog.readText()).jsonObject.getValue("exercises").jsonArray.map { it.jsonObject }
    }

    private val trackingByExercise: Map<String, TrackingType> by lazy {
        exercises.associate { it.string("id") to TrackingType.valueOf(it.string("trackingType")) }
    }

    private val variantsByExercise: Map<String, Set<String>> by lazy {
        exercises.associate { exercise ->
            exercise.string("id") to exercise.getValue("variants").jsonArray.map { it.jsonObject.string("id") }.toSet()
        }
    }

    private val exerciseByVariant: Map<String, String> by lazy {
        variantsByExercise.flatMap { (exerciseId, variantIds) -> variantIds.map { it to exerciseId } }.toMap()
    }

    /** Every slot of every template, labelled for failure messages. */
    private val slots: List<Pair<String, SlotSpec>> =
        FamousRoutines.all.flatMap { template ->
            template.days.flatMapIndexed { index, day -> day.slots.map { "${template.id} day ${index + 1}" to it } }
        }

    @Test
    fun `every required template is present`() {
        val required = listOf(
            "beginner-full-body-3", "linear-5x5", "starting-strength", "gzclp", "ppl-reddit", "upper-lower-4",
            "phul", "arnold-split", "bro-split-5", "recommended-routine", "couch-to-5k", "hiit-tabata",
        )
        required.forEach { id -> assertNotNull(FamousRoutines.byId(id), "missing template $id") }
        assertEquals(null, FamousRoutines.byId("does-not-exist"))
    }

    @Test
    fun `every fixed variant exists in the catalogue`() {
        slots.forEach { (label, slot) ->
            val target = slot.target
            if (target is SlotTarget.Fixed) {
                assertTrue(target.variantId in exerciseByVariant, "$label: unknown variant ${target.variantId}")
            }
        }
    }

    @Test
    fun `every flexible exercise exists and allows only its own variants`() {
        slots.forEach { (label, slot) ->
            val target = slot.target
            if (target is SlotTarget.Flexible) {
                val variants = variantsByExercise[target.exerciseId] ?: fail("$label: unknown exercise ${target.exerciseId}")
                target.allowedVariantIds.forEach { variantId ->
                    assertTrue(variantId in variants, "$label: $variantId is not a variant of ${target.exerciseId}")
                }
            }
        }
    }

    @Test
    fun `prescriptions match the exercise tracking type`() {
        slots.forEach { (label, slot) ->
            val exerciseId = when (val target = slot.target) {
                is SlotTarget.Fixed -> exerciseByVariant[target.variantId]
                is SlotTarget.Flexible -> target.exerciseId
            } ?: fail("$label: unresolved target ${slot.target}")
            val tracking = trackingByExercise.getValue(exerciseId)
            assertTrue(
                isConsistent(tracking, slot.prescription),
                "$label: ${slot.prescription} does not fit $tracking ($exerciseId)"
            )
        }
    }

    @Test
    fun `slot values are sane`() {
        slots.forEach { (label, slot) ->
            assertTrue(slot.sets >= 1, "$label: sets must be at least 1")
            assertTrue(slot.warmupSets >= 0, "$label: negative warm-up sets")
            assertTrue(slot.restBetweenSetsS >= 0 && slot.restAfterS >= 0, "$label: negative rest")
            when (val prescription = slot.prescription) {
                is Prescription.RepRange -> assertTrue(
                    prescription.min in 1..prescription.max,
                    "$label: bad range $prescription"
                )
                is Prescription.Reps -> assertTrue(prescription.reps >= 1, "$label: bad reps $prescription")
                is Prescription.Duration -> assertTrue(prescription.seconds >= 1, "$label: bad duration $prescription")
                is Prescription.Distance -> assertTrue(prescription.meters >= 1, "$label: bad distance $prescription")
            }
        }
    }

    @Test
    fun `templates are well formed`() {
        val ids = FamousRoutines.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate template ids: $ids")
        val kebab = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        FamousRoutines.all.forEach { template ->
            val id = template.id
            assertTrue(kebab.matches(id), "$id is not kebab-case")
            assertEquals(1, template.version, "$id: version")
            assertTrue(template.name.en.isNotBlank() && template.name.es.isNotBlank(), "$id: blank name")
            assertTrue(
                template.description.en.isNotBlank() && template.description.es.isNotBlank(),
                "$id: blank description"
            )
            assertTrue(template.credit.isNotBlank(), "$id: blank credit")
            assertTrue(template.days.isNotEmpty(), "$id: no days")
            assertTrue(template.days.any { it.kind == DayKind.TRAINING }, "$id: no training day")
            template.days.forEachIndexed { index, day ->
                val label = "$id day ${index + 1}"
                assertTrue(day.name.en.isNotBlank() && day.name.es.isNotBlank(), "$label: blank name")
                when (day.kind) {
                    DayKind.TRAINING -> assertTrue(day.slots.isNotEmpty(), "$label: training day without slots")
                    DayKind.REST -> assertTrue(day.slots.isEmpty(), "$label: rest day with slots")
                }
            }
        }
    }

    private fun isConsistent(tracking: TrackingType, prescription: Prescription): Boolean = when {
        tracking.tracksReps -> prescription is Prescription.Reps || prescription is Prescription.RepRange
        tracking.tracksDistance -> prescription is Prescription.Duration || prescription is Prescription.Distance
        tracking.tracksDuration -> prescription is Prescription.Duration
        else -> false
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
}
