package es.sebas1705.haltera.data.repository.search

import es.sebas1705.haltera.data.local.db.AppDatabase
import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.Exercise
import es.sebas1705.haltera.data.models.ExerciseCategory
import es.sebas1705.haltera.data.models.ExerciseFilter
import es.sebas1705.haltera.data.models.ExerciseSource
import es.sebas1705.haltera.data.models.Level
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedSteps
import es.sebas1705.haltera.data.models.LocalizedText
import es.sebas1705.haltera.data.models.Muscle
import es.sebas1705.haltera.data.models.NewExercise
import es.sebas1705.haltera.data.models.NewVariant
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.repository.TestClock
import es.sebas1705.haltera.data.repository.sql.ExerciseSearch
import es.sebas1705.haltera.data.repository.sql.ExerciseUsage
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Exercise search against the real bundled catalogue (801 exercises), through `ExerciseRepository.search`
 * as every search entry point calls it (Exercises tab, routine exercise picker, session "add exercise").
 */
class ExerciseSearchCatalogTest {

    /** [expected] must be within the first [within] results of [query] in [lang]; null = no results at all. */
    private data class Case(val lang: String, val query: String, val expected: String?, val within: Int = 1)

    private fun top(lang: String, query: String, id: String) = Case(lang, query, id)
    private fun inTop(lang: String, query: String, id: String, k: Int) = Case(lang, query, id, k)
    private fun none(lang: String, query: String) = Case(lang, query, null)

    private val cases = listOf(
        // The owner's examples.
        top("es", "press banca inclinado mancuerna", "incline-bench-press"),
        top("es", "banca press", "bench-press"),
        top("es", "sentadila", "squat"),
        top("es", "dominadass", "pull-up"),
        inTop("es", "aperturas mancuerna", "chest-fly", 5),
        top("es", "curl biceps", "biceps-curl"),
        top("en", "lat pull down", "lat-pulldown"),
        top("en", "deadlfit", "deadlift"),
        // Reordered words.
        top("es", "inclinado press", "incline-bench-press"),
        top("en", "press bench", "bench-press"),
        top("es", "biceps curl", "biceps-curl"),
        top("es", "muerto peso", "deadlift"),
        top("es", "rumano peso muerto", "romanian-deadlift"),
        top("en", "curl hammer", "hammer-curl"),
        top("es", "pecho jalon", "lat-pulldown"),
        top("es", "cerrado agarre press banca", "close-grip-bench-press"),
        // Exact names, both languages.
        top("es", "press banca", "bench-press"),
        top("en", "bench press", "bench-press"),
        top("es", "peso muerto", "deadlift"),
        top("es", "peso muerto rumano", "romanian-deadlift"),
        top("es", "press militar", "overhead-press"),
        top("en", "overhead press", "overhead-press"),
        top("es", "elevacion lateral", "lateral-raise"),
        top("es", "flexiones", "pushups"),
        top("es", "plancha", "plank"),
        top("en", "plank", "plank"),
        top("es", "hip thrust", "barbell-hip-thrust"),
        top("es", "extension cuadriceps", "leg-extensions"),
        top("en", "leg extension", "leg-extensions"),
        top("es", "curl femoral", "leg-curl"),
        top("en", "leg curl", "leg-curl"),
        top("en", "triceps pushdown", "triceps-pushdown"),
        top("es", "press frances", "skull-crusher"),
        top("en", "skull crusher", "skull-crusher"),
        top("es", "face pull", "face-pull"),
        top("es", "rueda abdominal", "ab-roller"),
        top("es", "sentadilla goblet", "goblet-squat"),
        top("en", "goblet squat", "goblet-squat"),
        top("es", "sentadilla frontal", "front-squat"),
        top("en", "front squat", "front-squat"),
        top("es", "remo invertido", "inverted-row"),
        top("es", "curl martillo", "hammer-curl"),
        top("en", "hammer curl", "hammer-curl"),
        top("es", "curl concentrado", "concentration-curls"),
        top("en", "preacher curl", "preacher-curl"),
        top("en", "good morning", "good-morning"),
        top("es", "cruce poleas", "cable-crossover"),
        top("en", "cable crossover", "cable-crossover"),
        top("en", "leg press", "leg-press"),
        top("es", "press banca agarre cerrado", "close-grip-bench-press"),
        top("en", "split squat", "split-squats"),
        top("es", "dominadas supinas", "chin-up"),
        top("es", "pajaros", "rear-delt-fly"),
        top("es", "aperturas", "chest-fly"),
        top("es", "zancada", "lunge"),
        // Typos.
        top("es", "sentadiya", "squat"),
        top("en", "sqaut", "squat"),
        top("en", "bicpes curl", "biceps-curl"),
        top("en", "tricep pushdwon", "triceps-pushdown"),
        top("es", "press milittar", "overhead-press"),
        top("es", "dominda", "pull-up"),
        top("en", "kettlebel swing", "one-arm-kettlebell-swings"),
        inTop("en", "curl bicep dumbell", "biceps-curl", 2),
        top("es", "peso muetro", "deadlift"),
        top("en", "romanain deadlift", "romanian-deadlift"),
        // Plurals and singulars.
        top("en", "squats", "squat"),
        top("es", "dominada", "pull-up"),
        top("en", "pullups", "pull-up"),
        top("en", "pull ups", "pull-up"),
        top("en", "chinups", "chin-up"),
        top("en", "chin up", "chin-up"),
        top("en", "bicep curls", "biceps-curl"),
        top("en", "deadlifts", "deadlift"),
        top("es", "zancadas", "lunge"),
        top("en", "lunges", "lunge"),
        top("en", "lateral raises", "lateral-raise"),
        top("es", "elevaciones laterales", "lateral-raise"),
        top("en", "shrugs", "shrug"),
        top("en", "crunch", "crunches"),
        // Joined and split words.
        top("en", "latpulldown", "lat-pulldown"),
        top("en", "pullup", "pull-up"),
        top("en", "push up", "pushups"),
        // Accents and case.
        top("es", "jalón al pecho", "lat-pulldown"),
        top("es", "PRÉSS DE BANCA", "bench-press"),
        top("es", "Sentadílla", "squat"),
        top("es", "pájaros", "rear-delt-fly"),
        // The other language than the app's.
        top("es", "bench press", "bench-press"),
        top("en", "press de banca", "bench-press"),
        top("en", "sentadilla", "squat"),
        top("es", "deadlift", "deadlift"),
        // Synonyms and gym slang.
        top("es", "jalon", "lat-pulldown"),
        top("es", "jalon pecho", "lat-pulldown"),
        top("en", "rdl", "romanian-deadlift"),
        inTop("en", "ohp", "overhead-press", 10),
        inTop("es", "kb swing", "one-arm-kettlebell-swings", 3),
        inTop("es", "pesa rusa", "kettlebell-one-legged-deadlift", 10),
        top("en", "ab wheel", "ab-roller"),
        top("en", "bulgarian split squat", "smith-single-leg-split-squat"),
        top("es", "sentadilla bulgara", "smith-single-leg-split-squat"),
        inTop("es", "prensa", "leg-press", 2),
        // Prefixes while typing.
        top("es", "press b", "bench-press"),
        top("es", "press ba", "bench-press"),
        top("es", "sent", "squat"),
        top("es", "domi", "pull-up"),
        top("es", "zanc", "lunge"),
        top("en", "lat pu", "lat-pulldown"),
        inTop("en", "dead", "deadlift", 3),
        inTop("en", "ben", "bench-press", 5),
        // Nothing to find.
        none("es", "xyzzy"),
        none("en", "qwerty asdf"),
        none("es", "zzz"),
        none("en", "bench xyzzy"),
        none("es", "kjhgfd"),
        none("en", "the"),
    )

    private suspend fun TestScope.repository(db: AppDatabase = BundledData.database()) =
        SqlExerciseRepository(db, HistoryLoader(db), UnconfinedTestDispatcher(testScheduler), now = { NOW })

    private fun Exercise.label(lang: String) = "$id (${if (lang == "es") name.es else name.en})"

    @Test
    fun `table of real queries finds the expected exercise`() = runTest {
        val repository = repository()
        val failures = cases.mapNotNull { case ->
            val results = repository.search(ExerciseFilter(query = case.query), case.lang).first()
            val ids = results.map { it.id }
            val ok = if (case.expected == null) ids.isEmpty() else case.expected in ids.take(case.within)
            if (ok) {
                null
            } else {
                "[${case.lang}] '${case.query}': expected ${case.expected ?: "nothing"} in top ${case.within}, got " +
                    results.take(5).joinToString { it.label(case.lang) }
            }
        }
        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of ${cases.size} cases failed:\n" + failures.joinToString("\n")
            )
        }
    }

    @Test
    fun `prefix typing of a name keeps the exercise near the top on every keystroke`() = runTest {
        val repository = repository()
        val typed = "press de banca"
        for (end in 5..typed.length) {
            val query = typed.substring(0, end)
            val ids = repository.search(ExerciseFilter(query = query), "es").first().map { it.id }
            assertTrue("bench-press" in ids.take(10), "'$query' → ${ids.take(10)}")
        }
        // Every keystroke of a one-word query starting a name.
        for (end in 1.."sentadilla".length) {
            val query = "sentadilla".substring(0, end)
            val names = repository.search(ExerciseFilter(query = query), "es").first().map { it.name.es.lowercase() }
            assertTrue(names.isNotEmpty(), "'$query' found nothing")
            if (end >= 3) assertTrue(names.first().startsWith("sentadilla"), "'$query' → ${names.take(3)}")
        }
    }

    @Test
    fun `typing the first letters lists only names with words starting with them`() = runTest {
        val results = repository().search(ExerciseFilter(query = "pre"), "es").first()
        assertTrue(results.size > 20)
        results.take(10).forEach { e -> assertTrue(e.name.es.lowercase().startsWith("pre"), e.label("es")) }
    }

    @Test
    fun `blank or punctuation-only queries list the catalogue as before`() = runTest {
        val repository = repository()
        val all = repository.search(ExerciseFilter(), "es").first()
        assertEquals(all, repository.search(ExerciseFilter(query = "   "), "es").first())
        assertEquals(all, repository.search(ExerciseFilter(query = " - "), "es").first())
    }

    @Test
    fun `filters work together with the text query`() = runTest {
        val repository = repository()
        val dumbbellPress = repository.search(
            ExerciseFilter(query = "press", equipment = Equipment.DUMBBELL),
            "es"
        ).first()
        assertTrue(dumbbellPress.size >= 10)
        dumbbellPress.forEach { e -> assertTrue(e.variants.any { it.equipment == Equipment.DUMBBELL }, e.label("es")) }
        assertTrue("incline-bench-press" in dumbbellPress.map { it.id })

        val hamstringCurl = repository.search(ExerciseFilter(query = "curl", muscle = Muscle.HAMSTRINGS), "en").first()
        assertEquals("leg-curl", hamstringCurl.first().id)
        hamstringCurl.forEach { e -> assertTrue(Muscle.HAMSTRINGS in e.primaryMuscles, e.label("en")) }

        val machineBench = repository.search(
            ExerciseFilter(query = "press banca", equipment = Equipment.MACHINE),
            "es"
        ).first()
        assertEquals("bench-press", machineBench.first().id)

        val cableFly = repository.search(ExerciseFilter(query = "aperturas", equipment = Equipment.CABLE), "es").first()
        cableFly.forEach { e -> assertTrue(e.variants.any { it.equipment == Equipment.CABLE }, e.label("es")) }
        assertEquals("chest-fly", cableFly.first().id)

        val stretches = repository.search(
            ExerciseFilter(query = "isquios", category = ExerciseCategory.MOBILITY),
            "es"
        ).first()
        assertTrue(stretches.isNotEmpty())
        stretches.forEach { assertEquals(ExerciseCategory.MOBILITY, it.category, it.label("es")) }

        assertTrue(
            repository.search(
                ExerciseFilter(query = "sentadilla", equipment = Equipment.SWISS_BALL, muscle = Muscle.NECK),
                "es"
            )
                .first().isEmpty(),
        )
    }

    @Test
    fun `muscle, equipment and category words find exercises without the word in the name`() = runTest {
        val repository = repository()
        val chest = repository.search(ExerciseFilter(query = "chest"), "en").first().map { it.id }
        assertTrue("bench-press" in chest, "chest → bench press")
        val dumbbell = repository.search(ExerciseFilter(query = "mancuerna"), "es").first().map { it.id }
        assertTrue("incline-bench-press" in dumbbell, "mancuerna → press inclinado (dumbbell variant)")
        val strength = repository.search(ExerciseFilter(query = "fuerza"), "es", limit = 1_000).first()
        assertTrue(strength.size > 300)
        // A name match outranks a muscle-only match.
        assertEquals("chest-fly", chest.first())
    }

    @Test
    fun `results are limited and ranking is stable`() = runTest {
        val repository = repository()
        assertEquals(5, repository.search(ExerciseFilter(query = "press"), "es", limit = 5).first().size)
        val first = repository.search(ExerciseFilter(query = "curl"), "es").first().map { it.id }
        repeat(3) { assertEquals(first, repository.search(ExerciseFilter(query = "curl"), "es").first().map { it.id }) }

        // The order does not depend on the order rows come from the database.
        val all = repository.search(ExerciseFilter(), "en", limit = 5_000).first()
        val forward = ExerciseSearch(all, "es")
        val shuffled = ExerciseSearch(all.shuffled(kotlin.random.Random(7)), "es")
        listOf("press", "curl", "sentadila", "p", "remo mancuerna", "dominadas").forEach { q ->
            assertEquals(
                forward.search(ExerciseFilter(query = q), 200, emptyMap(), NOW).map { it.id },
                shuffled.search(ExerciseFilter(query = q), 200, emptyMap(), NOW).map { it.id },
                q,
            )
        }
    }

    @Test
    fun `custom exercises are found, typo tolerant and in both languages, after the index was built`() = runTest {
        val repository = repository()
        // Build the index first: creating an exercise must invalidate it.
        assertTrue(
            repository.search(ExerciseFilter(query = "landmine"), "en").first().none { it.id.startsWith("user-") }
        )
        val landmine = repository.createCustom(
            NewExercise(
                name = LocalizedText(en = "Landmine Press", es = "Press con mina"),
                category = ExerciseCategory.STRENGTH,
                trackingType = TrackingType.WEIGHT_REPS,
                primaryMuscles = listOf(Muscle.SHOULDERS),
                variants = listOf(NewVariant(Equipment.BARBELL, LoadType.BARBELL)),
            ),
        )
        val pendlay = repository.createCustom(
            NewExercise(LocalizedText.same("Remo Pendlay"), ExerciseCategory.STRENGTH, TrackingType.WEIGHT_REPS),
        )
        listOf(
            "en" to "landmine press", "en" to "landmine pr", "en" to "lanmine press", "en" to "press landmine",
            "es" to "mina", "es" to "press mina", "es" to "mina press", "es" to "pres con mina", "es" to "landmine press",
        ).forEach { (lang, q) ->
            assertEquals(
                landmine.id,
                repository.search(ExerciseFilter(query = q), lang).first().firstOrNull()?.id,
                q
            )
        }
        // Synonyms reach custom names: "row" ↔ "remo".
        assertEquals(pendlay.id, repository.search(ExerciseFilter(query = "pendlay row"), "en").first().first().id)
        assertEquals(pendlay.id, repository.search(ExerciseFilter(query = "pendley"), "es").first().first().id)
    }

    @Test
    fun `a custom exercise named like a catalogue one ranks first`() = runTest {
        val repository = repository()
        val mine = repository.createCustom(
            NewExercise(
                LocalizedText(en = "Bench Press", es = "Press de banca"),
                ExerciseCategory.STRENGTH,
                TrackingType.WEIGHT_REPS
            ),
        )
        assertEquals(
            listOf(
                mine.id,
                "bench-press"
            ),
            repository.search(ExerciseFilter(query = "press banca"), "es").first().take(2).map {
                it.id
            }
        )
    }

    @Test
    fun `exercises the user logs often and recently move up among equal matches`() = runTest {
        val db = BundledData.database()
        val clock = TestClock(NOW)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val exercises = SqlExerciseRepository(db, HistoryLoader(db), dispatcher, clock)
        val sessions = SqlSessionRepository(db, HistoryLoader(db), dispatcher, clock)
        val before = exercises.search(ExerciseFilter(query = "press banca"), "es").first().map { it.id }
        assertEquals("bench-press", before.first())
        val target = "pin-presses"
        assertTrue(before.indexOf(target) > 1, "$target starts below second: $before")

        repeat(3) {
            val session = sessions.startFree()
            sessions.logSet(sessions.addExercise(session, "$target.barbell"), SetInput(reps = 5, loadGrams = 80_000L))
            sessions.finish(session)
        }
        val after = exercises.search(ExerciseFilter(query = "press banca"), "es").first().map { it.id }
        // The exact name still wins; the logged exercise is next.
        assertEquals(listOf("bench-press", target), after.take(2))
        // The boost is small: it never lifts an exercise that does not match.
        assertTrue(exercises.search(ExerciseFilter(query = "sentadilla"), "es").first().none { it.id == target })
    }

    @Test
    fun `boost grows with usage and recency and stays small`() {
        val exercise = Exercise(
            id = "x", name = LocalizedText.same("X"), category = ExerciseCategory.STRENGTH, level = Level.BEGINNER,
            primaryMuscles = emptyList(), secondaryMuscles = emptyList(),
            instructions = LocalizedSteps(
                emptyList(),
                emptyList()
            ),
            trackingType = TrackingType.WEIGHT_REPS, source = ExerciseSource.CATALOG,
        )
        val day = 86_400_000L
        fun boost(
            sessions: Long,
            daysAgo: Long
        ) = ExerciseSearch.boost(exercise, ExerciseUsage(sessions, NOW - daysAgo * day), NOW)
        assertEquals(0.0, ExerciseSearch.boost(exercise, null, NOW))
        assertTrue(boost(1, 100) < boost(5, 100))
        assertTrue(boost(5, 100) < boost(5, 1))
        assertTrue(boost(1_000, 0) <= ExerciseSearch.USAGE_BOOST + ExerciseSearch.RECENT_BOOST + 1e-9)
        assertEquals(
            ExerciseSearch.CUSTOM_BOOST,
            ExerciseSearch.boost(exercise.copy(source = ExerciseSource.USER), null, NOW)
        )
    }

    @Test
    fun `benchmark - ranking the whole catalogue stays far below a frame`() = runTest {
        val all = repository().search(ExerciseFilter(), "en", limit = 5_000).first()
        assertTrue(all.size >= 800)
        val buildStart = System.nanoTime()
        val search = ExerciseSearch(all, "es")
        val buildMs = (System.nanoTime() - buildStart) / 1e6
        val queries = cases.map { it.query } + "press de banca".indices.map { "press de banca".substring(0, it + 1) }
        repeat(
            50
        ) { search.search(ExerciseFilter(query = queries[it % queries.size]), 200, emptyMap(), NOW) } // warm-up
        val runs = 1_000
        val start = System.nanoTime()
        repeat(runs) { search.search(ExerciseFilter(query = queries[it % queries.size]), 200, emptyMap(), NOW) }
        val totalMs = (System.nanoTime() - start) / 1e6
        println(
            "ExerciseSearch benchmark: index of ${all.size} built in ${"%.1f".format(
                buildMs
            )} ms; $runs queries in ${"%.0f".format(totalMs)} ms (${"%.3f".format(totalMs / runs)} ms/query)"
        )
        // Generous bounds for slow CI machines; a frame is 16 ms.
        assertTrue(totalMs < 4_000, "$runs queries took $totalMs ms")
        assertTrue(buildMs < 2_000, "index build took $buildMs ms")
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
