package es.sebas1705.haltera.data.repository.search

import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.repository.TestClock
import es.sebas1705.haltera.data.repository.importer.ExerciseMatcher
import es.sebas1705.haltera.data.repository.importer.ExerciseSuggester
import es.sebas1705.haltera.data.repository.importer.ImportAliases
import es.sebas1705.haltera.data.repository.importer.ImportValues
import es.sebas1705.haltera.data.repository.importer.SuggestionConfidence
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The importer's alias table and suggestions against the real bundled catalogue (docs/IMPORT-FORMATS.md, "Aliases"
 * and "Suggestions").
 */
class ImportAliasesTest {
    private val catalogue: List<Exercise> by lazy {
        runBlocking {
            val db = BundledData.database()
            SqlExerciseRepository(db, HistoryLoader(db), UnconfinedTestDispatcher(), TestClock(0L))
                .search(ExerciseFilter(), "en", Int.MAX_VALUE).first()
        }
    }
    private val matcher by lazy { ExerciseMatcher(catalogue) }
    private val suggester by lazy { ExerciseSuggester(catalogue) }

    @Test
    fun `every alias target is an active exercise or variant of the bundled catalogue`() {
        val exercises = catalogue.associateBy { it.id }
        val variants = catalogue.flatMap { e -> e.variants.filter { !it.deprecated } }.associateBy { it.id }
        val broken = ImportAliases.entries.filter { (_, target) ->
            if ('.' in target) target !in variants else target !in exercises
        }
        assertTrue(broken.isEmpty(), "targets missing from catalog.json: $broken")
    }

    @Test
    fun `aliases are unique and never shadow a catalogue name`() {
        val keys = ImportAliases.entries.map { ImportValues.matchKey(it.first) }
        assertEquals(
            keys.size,
            keys.toSet().size,
            "duplicate alias: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}"
        )
        val names = catalogue.flatMap { listOf(it.name.en, it.name.es) }.map(ImportValues::matchKey).toSet()
        val shadowed = ImportAliases.entries.filter { ImportValues.matchKey(it.first) in names }
        assertTrue(shadowed.isEmpty(), "aliases that are catalogue names (never used): $shadowed")
    }

    @Test
    fun `every alias resolves to its target through the matcher`() {
        ImportAliases.entries.forEach { (name, target) ->
            val match = matcher.match(name) ?: fail("$name did not match")
            if ('.' in target) {
                assertEquals(target, match.variantId, name)
            } else {
                assertEquals(target, match.exerciseId, name)
            }
        }
    }

    @Test
    fun `common third party names match the expected variant`() {
        val expected = mapOf(
            // The examples of the QA pass.
            "Remo con barra" to "bent-over-row.barbell",
            "Incline Dumbbell Press" to "incline-bench-press.dumbbell",
            "Lat Pulldown (Cable)" to "lat-pulldown.wide-grip",
            "Barbell Squat" to "squat.barbell",
            "Romanian Deadlift (Barbell)" to "romanian-deadlift.barbell",
            "Seated Cable Row" to "seated-row.cable",
            "Tricep Pushdown" to "triceps-pushdown.bar",
            "Flat Barbell Bench Press" to "bench-press.barbell",
            "Bench Press (Barbell)" to "bench-press.barbell",
            // An exercise alias keeps picking the variant from the equipment.
            "Bicep Curl (Dumbbell)" to "biceps-curl.dumbbell",
            "Bicep Curl (Cable)" to "biceps-curl.cable",
            "Rear Delt Reverse Fly (Machine)" to "rear-delt-fly.machine",
            "Skullcrusher (Cable)" to "skull-crusher.cable",
            // Spanish "con <equipment>".
            "Curl de bíceps con mancuernas" to "biceps-curl.dumbbell",
            "Sentadilla con barra" to "squat.barbell",
            "Press de banca con mancuernas" to "bench-press.dumbbell",
            "Press militar con mancuernas" to "overhead-press.dumbbell",
            "Hip thrust con barra" to "barbell-hip-thrust.barbell",
            "Curl with dumbbells" to null,
            // Hevy / Strong / FitNotes defaults that were already catalogue names.
            "Leg Press" to "leg-press.machine",
            "Deadlift (Barbell)" to "deadlift.barbell",
            "Deadlift (Trap bar)" to "deadlift.trap-bar",
            "Lateral Raise (Dumbbell)" to "lateral-raise.dumbbell",
            "Hammer Curl (Dumbbell)" to "hammer-curl.dumbbell",
            "Overhead Press (Barbell)" to "overhead-press.barbell",
            "Pull Up" to "pull-up.bodyweight",
            "Chin Up" to "chin-up.bodyweight",
            "Lying Leg Curl (Machine)" to "leg-curl.lying",
            "Seated Leg Curl (Machine)" to "leg-curl.seated",
            "Leg Extension (Machine)" to "leg-extensions.machine",
            "Hip Thrust (Barbell)" to "barbell-hip-thrust.barbell",
        )
        expected.forEach { (name, variant) -> assertEquals(variant, matcher.match(name)?.variantId, name) }
    }

    @Test
    fun `the user's own exercise with an alias name wins over the alias`() {
        val template = catalogue.first { it.id == "lat-pulldown" }
        val own = template.copy(
            id = "user-1",
            name = LocalizedText.same("Lat Pulldown (Cable)"),
            source = ExerciseSource.USER,
            variants = template.variants.map {
                it.copy(
                    id = "user-1.${it.id.substringAfter('.')}",
                    exerciseId = "user-1"
                )
            },
        )
        assertEquals("user-1", ExerciseMatcher(catalogue + own).match("Lat Pulldown (Cable)")?.exerciseId)
    }

    @Test
    fun `aliases never guess equipment the target lacks`() {
        // Romanian deadlift has no dumbbell variant; leg curl variants are lying/seated/standing machines.
        assertNull(matcher.match("Romanian Deadlift (Dumbbell)"))
        assertNull(matcher.match("Lying Leg Curl (Dumbbell)"))
        assertNull(matcher.match("Remo con mancuerna (Cable)"))
    }

    @Test
    fun `most default exercises of FitNotes and Hevy match without a mapping`() {
        val matched = (FITNOTES_DEFAULTS + HEVY_COMMON).filter { matcher.match(it) != null }
        val missing = (FITNOTES_DEFAULTS + HEVY_COMMON) - matched.toSet()
        // Not 100 %: names that could be two exercises, or lifts the catalogue lacks, are left to the user.
        assertTrue(matched.size * 100 >= (FITNOTES_DEFAULTS.size + HEVY_COMMON.size) * 75, "unmatched: $missing")
    }

    // ----- suggestions -----

    private fun suggestion(name: String) = suggester.suggest(name)

    @Test
    fun `a typo or a joined word of a name is a high confidence suggestion`() {
        listOf(
            "Bench Pres" to "bench-press",
            "Benchpress" to "bench-press",
            "Romanian Dead lift" to "romanian-deadlift",
            "Hammer Curls" to "hammer-curl",
            "Sentadila" to "squat",
        ).forEach { (name, id) ->
            val s = suggestion(name) ?: fail("no suggestion for $name")
            assertEquals(id, s.exerciseId, name)
            assertEquals(SuggestionConfidence.HIGH, s.confidence, name)
        }
    }

    @Test
    fun `an extra or missing word is only a medium suggestion`() {
        val s = suggestion("Seated Leg Press") ?: fail("no suggestion")
        assertEquals("leg-press", s.exerciseId)
        assertEquals(SuggestionConfidence.MEDIUM, s.confidence)
    }

    @Test
    fun `equipment the exercise lacks lowers a suggestion to medium`() {
        val s = suggestion("Romanian Deadlif (Dumbbell)") ?: fail("no suggestion")
        assertEquals("romanian-deadlift", s.exerciseId)
        assertEquals(SuggestionConfidence.MEDIUM, s.confidence)
    }

    @Test
    fun `opposite words are never suggested for each other`() {
        // Incline vs decline, pulldown vs pushdown, seated vs lying, front vs rear: no wrong suggestion at any level.
        val tricky = mapOf(
            "Decline Dumbbell Fly Machine" to setOf("incline-dumbbell-flyes", "chest-fly"),
            "Incline Barbel Bench" to setOf("decline-bench-press", "bench-press"),
            "Decline Benchpress" to setOf("incline-bench-press", "bench-press"),
            "Triceps Pulldown" to setOf("triceps-pushdown"),
            "Lat Pushdown" to setOf("lat-pulldown"),
            "Seated Calf Raises Machine Heavy" to setOf("standing-calf-raise"),
            "Front Delt Fly" to setOf("rear-delt-fly"),
            "Leg Curl Extension" to setOf("leg-extensions"),
        )
        tricky.forEach { (name, wrong) ->
            val s = suggestion(name)
            assertTrue(s == null || s.exerciseId !in wrong, "$name suggested ${s?.exerciseId}")
        }
    }

    @Test
    fun `unrelated names get no suggestion`() {
        listOf("Zumba", "My weird thing", "Heavy thing", "x").forEach { assertNull(suggestion(it), it) }
    }

    private companion object {
        /** FitNotes' default exercises (FitNotes2Hevy, data/exercises/fitnotes_default.txt), the weight-training ones. */
        val FITNOTES_DEFAULTS = listOf(
            "Ab-Wheel Rollout", "Arnold Dumbbell Press", "Barbell Curl", "Barbell Front Squat", "Barbell Glute Bridge",
            "Barbell Row", "Barbell Shrug", "Barbell Squat", "Behind The Neck Barbell Press", "Cable Crossover",
            "Cable Crunch", "Cable Curl", "Cable Face Pull", "Chin Up", "Close Grip Barbell Bench Press", "Crunch",
            "Crunch Machine", "Deadlift", "Decline Barbell Bench Press", "Decline Crunch", "Donkey Calf Raise",
            "Dumbbell Concentration Curl", "Dumbbell Hammer Curl", "Dumbbell Preacher Curl", "Dumbbell Row",
            "EZ-Bar Curl", "EZ-Bar Preacher Curl", "EZ-Bar Skullcrusher", "Elliptical Trainer",
            "Flat Barbell Bench Press", "Flat Dumbbell Bench Press", "Flat Dumbbell Fly", "Front Dumbbell Raise",
            "Glute-Ham Raise", "Good Morning", "Hanging Leg Raise", "Incline Barbell Bench Press",
            "Incline Dumbbell Bench Press", "Incline Dumbbell Fly", "Lat Pulldown", "Lateral Dumbbell Raise",
            "Leg Extension Machine", "Leg Press", "Lying Leg Curl Machine", "Lying Triceps Extension", "Machine Shrug",
            "Overhead Press", "Parallel Bar Triceps Dip", "Plank", "Pull Up", "Push Press", "Rack Pull",
            "Rear Delt Dumbbell Raise", "Rear Delt Machine Fly", "Ring Dip", "Romanian Deadlift", "Rope Push Down",
            "Rowing Machine", "Running (Outdoor)", "Running (Treadmill)", "Seated Cable Row",
            "Seated Calf Raise Machine", "Seated Dumbbell Lateral Raise", "Seated Dumbbell Press",
            "Seated Incline Dumbbell Curl", "Seated Leg Curl Machine", "Seated Machine Curl", "Seated Machine Fly",
            "Side Plank", "Smith Machine Close Grip Bench Press", "Smith Machine Overhead Press",
            "Standing Calf Raise Machine", "Stationary Bike", "Stiff-Legged Deadlift", "Straight-Arm Cable Pushdown",
            "Sumo Deadlift", "Swimming", "T-Bar Row", "V-Bar Push Down", "Walking",
        )

        /** Common lifts of the Hevy library (FitNotes2Hevy, data/exercises/hevy.txt; gymruntohevy mappings.py). */
        val HEVY_COMMON = listOf(
            "Arnold Press (Dumbbell)", "Bench Press (Barbell)", "Bench Press (Dumbbell)", "Bench Press (Smith Machine)",
            "Bench Press - Close Grip (Barbell)", "Bent Over Row (Barbell)", "Bent Over Row (Dumbbell)",
            "Bicep Curl (Barbell)", "Bicep Curl (Cable)", "Bicep Curl (Dumbbell)", "Bicep Curl (Machine)",
            "Butterfly (Pec Deck)", "Cable Crunch", "Cable Fly Crossovers", "Chest Dip", "Chest Fly (Dumbbell)",
            "Chest Fly (Machine)", "Chest Press (Machine)", "Chin Up", "Crunch", "Deadlift (Barbell)",
            "Deadlift (Trap bar)", "Decline Bench Press (Barbell)", "EZ Bar Biceps Curl", "Face Pull", "Front Squat",
            "Goblet Squat", "Hack Squat (Machine)", "Hammer Curl (Dumbbell)", "Hanging Leg Raise",
            "Hip Thrust (Barbell)", "Incline Bench Press (Barbell)", "Incline Bench Press (Dumbbell)",
            "Incline Chest Press (Machine)", "Lat Pulldown (Cable)", "Lat Pulldown (Machine)",
            "Lat Pulldown - Close Grip (Cable)", "Lateral Raise (Cable)", "Lateral Raise (Dumbbell)",
            "Leg Extension (Machine)", "Leg Press (Machine)", "Lunge (Dumbbell)", "Lying Leg Curl (Machine)",
            "Overhead Press (Barbell)", "Overhead Press (Dumbbell)", "Plank", "Preacher Curl (Barbell)", "Pull Up",
            "Push Up", "Rear Delt Reverse Fly (Dumbbell)", "Romanian Deadlift (Barbell)", "Rowing Machine",
            "Seated Cable Row - V Grip (Cable)", "Seated Leg Curl (Machine)", "Seated Row (Machine)",
            "Shoulder Press (Dumbbell)", "Shrug (Barbell)", "Shrug (Dumbbell)", "Skullcrusher (Barbell)",
            "Squat (Barbell)", "Squat (Smith Machine)", "Standing Calf Raise (Machine)",
            "Straight Arm Lat Pulldown (Cable)", "T Bar Row", "Triceps Dip", "Triceps Pushdown",
            "Triceps Rope Pushdown", "Upright Row (Barbell)",
        )
    }
}
