package es.sebas1705.haltera.data.repository.search

import es.sebas1705.haltera.data.repository.TestClock
import es.sebas1705.haltera.data.repository.fixtureBytes
import es.sebas1705.haltera.data.repository.importer.ExerciseDecision
import es.sebas1705.haltera.data.repository.importer.HistoryFileParser
import es.sebas1705.haltera.data.repository.importer.ImportOptions
import es.sebas1705.haltera.data.repository.importer.ParseOptions
import es.sebas1705.haltera.data.repository.importer.SqlHistoryImporter
import es.sebas1705.haltera.data.repository.importer.SuggestionConfidence
import es.sebas1705.haltera.data.repository.sql.HistoryLoader
import es.sebas1705.haltera.data.repository.sql.SqlExerciseRepository
import es.sebas1705.haltera.data.repository.sql.SqlSessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A Strong file with common default names, previewed and imported against the bundled catalogue. */
class ImportCommonNamesTest {

    @Test
    fun `aliases match, close names are suggested and nothing is applied without the user`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val db = BundledData.database()
        val history = HistoryLoader(db)
        val exercises = SqlExerciseRepository(db, history, dispatcher, TestClock(0L))
        val importer = SqlHistoryImporter(db, exercises, dispatcher)
        val parsed = HistoryFileParser.parse(fixtureBytes("strong-common-names.csv"), ParseOptions(TimeZone.UTC))

        val preview = importer.preview(parsed, ImportOptions())
        // Lat Pulldown (Cable), Seated Cable Row, Remo con barra, Romanian Deadlift (Barbell), Incline Dumbbell Press,
        // Tricep Pushdown.
        assertEquals(6, preview.matchedExercises)
        val unmatched = preview.unmatched.associateBy { it.name }
        assertEquals(setOf("Bench Pres", "Seated Leg Press", "Zumba"), unmatched.keys)
        assertEquals("bench-press", unmatched.getValue("Bench Pres").suggestion?.exerciseId)
        assertEquals(SuggestionConfidence.HIGH, unmatched.getValue("Bench Pres").suggestion?.confidence)
        assertEquals("leg-press", unmatched.getValue("Seated Leg Press").suggestion?.exerciseId)
        assertEquals(SuggestionConfidence.MEDIUM, unmatched.getValue("Seated Leg Press").suggestion?.confidence)
        assertNull(unmatched.getValue("Zumba").suggestion)

        // The importer applies only the user's decisions: here the high suggestion was kept, the rest created.
        val decisions = mapOf(unmatched.getValue("Bench Pres").key to ExerciseDecision.MapTo("bench-press"))
        val summary = importer.import(parsed, ImportOptions(decisions = decisions))
        assertEquals(2, summary.exercisesCreated)
        val sessions = SqlSessionRepository(db, history, dispatcher, TestClock(0L)).observeHistory(10).first()
        val variants = sessions.flatMap { s -> s.exercises.map { it.variantId } }.toSet()
        listOf(
            "lat-pulldown.wide-grip",
            "seated-row.cable",
            "bent-over-row.barbell",
            "romanian-deadlift.barbell",
            "incline-bench-press.dumbbell",
            "triceps-pushdown.bar",
            "bench-press.barbell",
        ).forEach { assertEquals(true, it in variants, it) }
    }
}
