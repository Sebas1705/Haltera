package es.sebas1705.haltera.dataimport

import es.sebas1705.haltera.data.repository.importer.ExistingPolicy
import es.sebas1705.haltera.data.repository.importer.ImportPreview
import es.sebas1705.haltera.data.repository.importer.ImportSource
import es.sebas1705.haltera.data.repository.importer.ImportWeightUnit
import es.sebas1705.haltera.data.repository.importer.SuggestionConfidence
import es.sebas1705.haltera.data.repository.importer.UnmatchedExercise
import es.sebas1705.haltera.resources.Res
import es.sebas1705.haltera.resources.import_suggestion_high
import es.sebas1705.haltera.resources.import_suggestion_high_unselected
import es.sebas1705.haltera.resources.import_suggestion_medium
import es.sebas1705.haltera.resources.import_suggestion_medium_selected
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** QA alpha.4: "Exercises matched automatically: 0" when the whole file was already imported. */
class ImportReviewWordingTest {
    private fun review(
        sessions: Int = 3,
        existing: Int = 0,
        matched: Int = 2,
        unmatched: Int = 1,
        policy: ExistingPolicy = ExistingPolicy.SKIP,
    ) = ImportReview(
        fileName = "strong.csv",
        preview = ImportPreview(
            source = ImportSource.STRONG,
            sessions = sessions,
            sets = 10,
            firstStartedAt = 0,
            lastStartedAt = 1,
            bodyWeights = 0,
            existingSessions = existing,
            existingBodyWeights = 0,
            matchedExercises = matched,
            unmatched = List(unmatched) { UnmatchedExercise("k$it", "Name $it", 1, 1) },
            skippedRows = 0,
            needsUnitChoice = false,
        ),
        unit = ImportWeightUnit.KG,
        existing = policy,
    )

    @Test
    fun `new sessions show the matched count and the names not found`() {
        val r = review()
        assertFalse(r.nothingNew)
        assertEquals(2, r.matchedLine)
        assertTrue(r.unmatchedShown)
    }

    @Test
    fun `zero matched is not a line of its own`() {
        assertNull(review(matched = 0).matchedLine)
    }

    @Test
    fun `a file already imported says only that`() {
        // The importer resolves only the sessions it would write: none, so 0 matched and 0 unmatched.
        val r = review(existing = 3, matched = 0, unmatched = 0)
        assertTrue(r.nothingNew)
        assertNull(r.matchedLine)
        assertFalse(r.unmatchedShown)
    }

    @Test
    fun `replacing existing sessions writes them, so matching is shown again`() {
        val r = review(existing = 3, policy = ExistingPolicy.REPLACE)
        assertFalse(r.nothingNew)
        assertEquals(2, r.matchedLine)
    }

    @Test
    fun `the suggestion wording follows what is selected now`() {
        assertEquals(Res.string.import_suggestion_high, suggestionWording(SuggestionConfidence.HIGH, chosen = true))
        assertEquals(
            Res.string.import_suggestion_high_unselected,
            suggestionWording(SuggestionConfidence.HIGH, chosen = false)
        )
        assertEquals(
            Res.string.import_suggestion_medium,
            suggestionWording(SuggestionConfidence.MEDIUM, chosen = false)
        )
        assertEquals(
            Res.string.import_suggestion_medium_selected,
            suggestionWording(SuggestionConfidence.MEDIUM, chosen = true)
        )
    }
}
