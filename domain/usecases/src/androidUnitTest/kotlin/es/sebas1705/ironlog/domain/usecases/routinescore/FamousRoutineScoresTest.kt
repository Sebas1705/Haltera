package es.sebas1705.ironlog.domain.usecases.routinescore

import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.data.models.ExerciseCategory
import es.sebas1705.ironlog.data.models.ExerciseSource
import es.sebas1705.ironlog.data.models.ExerciseVariant
import es.sebas1705.ironlog.data.models.Level
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.LocalizedSteps
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.Muscle
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.routines.FamousRoutines
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scores every bundled famous routine with the bundled catalogue and checks the results make sense. */
class FamousRoutineScoresTest {
    // Unit tests run with the module directory as working directory.
    private val catalogFile = File("../../composeApp/src/commonMain/composeResources/files/catalog.json")

    private val exercises: List<Exercise> by lazy {
        Json.parseToJsonElement(catalogFile.readText()).jsonObject.getValue("exercises").jsonArray
            .map { it.jsonObject.toExercise() }
    }

    private val catalogue by lazy { ScoringCatalogue(exercises) }

    private val scores: Map<String, RoutineScore> by lazy {
        FamousRoutines.all.associate { it.id to RoutineScorer.score(it.toScoringRoutine("en"), catalogue) }
    }

    private fun score(id: String) = scores.getValue(id)

    private fun RoutineScore.criterion(id: CriterionId) = criteria.first { it.id == id }

    @Test
    fun `print the famous routine scores`() {
        scores.forEach { (id, score) ->
            println(
                "$id: ${score.total} ${score.verdict} ${score.focus} spw=${score.sessionsPerWeek} " +
                    score.criteria.joinToString { "${it.id}=${it.score}" },
            )
            score.criteria.forEach { c -> println("    ${c.id}: ${c.finding} -> ${c.advice}") }
            println("    weekly=${score.weeklySets}")
        }
    }

    @Test
    fun `every suggested exercise exists in the catalogue`() {
        val ids = exercises.map { it.id }.toSet()
        val suggested = RoutineScorer.SUGGESTED_EXERCISES.values.flatten() +
            RoutineScorer.BALANCE_EXERCISES.values.flatten() + RoutineScorer.HEAVY_COMPOUNDS
        suggested.forEach { assertTrue(it in ids, "unknown suggested exercise $it") }
    }

    @Test
    fun `every resistance routine is scored and every slot resolves`() {
        FamousRoutines.all.forEach { template ->
            template.days.flatMap { it.slots }.forEach { slot ->
                assertTrue(catalogue.exerciseOf(slot.target) != null, "${template.id}: unresolved ${slot.target}")
            }
        }
        val resistance = scores.filterKeys { it !in setOf("couch-to-5k", "hiit-tabata") }
        resistance.forEach { (id, score) ->
            assertTrue(score.scored, "$id should be scored")
            assertTrue(score.total in 1..100, "$id total ${score.total}")
            assertEquals(100, score.criteria.sumOf { it.weight }, "$id: plan weights")
        }
    }

    @Test
    fun `cardio programmes are not scored`() {
        listOf("couch-to-5k", "hiit-tabata").forEach { id ->
            assertEquals(RoutineFocus.CONDITIONING, score(id).focus)
            assertEquals(Verdict.NOT_SCORED, score(id).verdict)
        }
    }

    @Test
    fun `the bro split trains each muscle once a week`() {
        val frequency = score("bro-split-5").criterion(CriterionId.FREQUENCY)
        assertTrue(frequency.score <= 60, "bro split frequency ${frequency.score}")
        assertTrue(frequency.advice.isNotEmpty())
        assertTrue(score("bro-split-5").total < score("ppl-reddit").total)
    }

    @Test
    fun `push pull legs has good volume and frequency`() {
        val ppl = score("ppl-reddit")
        assertTrue(
            ppl.criterion(CriterionId.VOLUME).score >= 70,
            "PPL volume ${ppl.criterion(CriterionId.VOLUME).score}"
        )
        assertEquals(100, ppl.criterion(CriterionId.FREQUENCY).score)
        assertTrue(ppl.total >= 70, "PPL total ${ppl.total}")
    }

    @Test
    fun `barbell novice programmes are strength focused and light on volume`() {
        listOf("linear-5x5", "starting-strength").forEach { id ->
            val score = score(id)
            assertEquals(RoutineFocus.STRENGTH, score.focus, id)
            assertEquals(3.0, score.sessionsPerWeek, id)
            assertTrue(
                score.criterion(CriterionId.REP_RANGES).finding.let { it is Finding.RepRanges && it.heavyCompound }
            )
        }
        // Starting Strength has almost no direct back or arm work.
        val coverage = score("starting-strength").criterion(CriterionId.COVERAGE).finding as Finding.Coverage
        assertTrue(MuscleGroup.BICEPS in coverage.missing)
    }

    @Test
    fun `upper lower splits score well`() {
        listOf("upper-lower-4", "phul").forEach { id ->
            assertTrue(score(id).total >= 70, "$id total ${score(id).total}")
            assertEquals(100, score(id).criterion(CriterionId.FREQUENCY).score, id)
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.muscles(key: String) =
        Muscle.listFromDb(getValue(key).jsonArray.joinToString(",") { it.jsonPrimitive.content })

    private fun JsonObject.toExercise(): Exercise {
        val id = string("id")
        val name = getValue("name").jsonObject
        return Exercise(
            id = id,
            name = LocalizedText(name.string("en"), name.string("es")),
            category = ExerciseCategory.fromDb(string("category")),
            level = Level.fromDb(string("level")),
            primaryMuscles = muscles("primaryMuscles"),
            secondaryMuscles = muscles("secondaryMuscles"),
            instructions = LocalizedSteps.EMPTY,
            trackingType = TrackingType.fromDb(string("trackingType")),
            source = ExerciseSource.CATALOG,
            variants = getValue("variants").jsonArray.map { v ->
                val variant = v.jsonObject
                ExerciseVariant(
                    id = variant.string("id"),
                    exerciseId = id,
                    equipment = Equipment.fromDb(variant.string("equipment")),
                    loadType = LoadType.fromDb(variant.string("loadType")),
                )
            },
        )
    }
}
