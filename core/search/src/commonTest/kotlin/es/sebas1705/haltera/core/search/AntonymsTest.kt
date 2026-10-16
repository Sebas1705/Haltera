package es.sebas1705.haltera.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntonymsTest {

    private fun stem(word: String) = TextNormalizer.terms(word).single()

    @Test
    fun `opposites are recognised in both directions, any form`() {
        val pairs = listOf(
            "inclinado" to "declinado",
            "inclinada" to "declinadas",
            "incline" to "decline",
            "inclined" to "declined",
            "supino" to "prono",
            "supinated" to "pronated",
            "abductores" to "aductores",
            "abduction" to "adduction",
            "internal" to "external",
            "pulldown" to "pushdown",
            "pullups" to "pushups",
        )
        pairs.forEach { (a, b) ->
            assertTrue(Antonyms.opposite(stem(a), stem(b)), "$a / $b")
            assertTrue(Antonyms.opposite(stem(b), stem(a)), "$b / $a")
            assertTrue(Antonyms.opposite(TextNormalizer.fold(a), TextNormalizer.fold(b)), "unstemmed $a / $b")
        }
    }

    @Test
    fun `same side and unknown words are not opposites`() {
        assertFalse(Antonyms.opposite("inclinado", "incline"))
        assertFalse(Antonyms.opposite("inclinado", "inclinado"))
        assertFalse(Antonyms.opposite("sentadilla", "sentadila"))
        assertFalse(Antonyms.opposite("xyzzy", "declinado"))
        // Inside joined words and word starts too.
        assertTrue(Antonyms.opposite("pressinclinado", "pressdeclinado"))
        assertTrue(Antonyms.opposite("abductor", "adductorgroin"))
        assertFalse(Antonyms.opposite("pressinclinado", "pressinclinada"))
    }

    @Test
    fun `the typo step never links opposites, exact and prefix still work`() {
        // Without the guard these are within the typo allowance.
        assertEquals(2, EditDistance.distance("inclinado", "declinado", 2))
        assertEquals(0.0, SearchWeights.quality("inclinado", "declinado", prefix = true, typos = 2))
        assertEquals(0.0, SearchWeights.quality("pulldown", "pushdown", prefix = true, typos = 2))
        assertEquals(0.0, SearchWeights.quality("abductor", "aductor", prefix = true, typos = 2))
        assertEquals(SearchWeights.EXACT, SearchWeights.quality("declinado", "declinado", prefix = true, typos = 2))
        assertTrue(SearchWeights.quality("declin", "declinado", prefix = true, typos = 1) > 0.0)
        assertTrue(SearchWeights.quality("sentadila", "sentadilla", prefix = true, typos = 2) > 0.0)
    }

    @Test
    fun `an incline query does not find the decline exercise by typo`() {
        val index = SearchIndex.build(
            listOf(
                SearchDocument("incline", listOf(SearchField("Press inclinado", 1.0, FieldKind.NAME))),
                SearchDocument("decline", listOf(SearchField("Press declinado", 1.0, FieldKind.NAME))),
            ),
        )
        assertEquals(listOf("incline"), index.search("press inclinado").map { it.key })
        assertEquals(listOf("decline"), index.search("declinado").map { it.key })
    }
}
