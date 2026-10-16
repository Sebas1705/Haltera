package es.sebas1705.haltera.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynonymTableTest {
    private val table = GymSynonyms.table

    private fun expandName(name: String) = table.expand(TextNormalizer.terms(name)).toSet()

    @Test
    fun `the owner's gym terms are equivalent in both directions`() {
        // Pairs from the feature request: each side must expand to the other.
        val pairs = listOf(
            "press banca" to "bench press",
            "jalón" to "pulldown",
            "jalón al pecho" to "lat pulldown",
            "remo" to "row",
            "sentadilla" to "squat",
            "aperturas" to "fly",
            "aperturas" to "flyes",
            "dominadas" to "pull-up",
            "dominadas" to "chin-up",
            "zancadas" to "lunge",
            "peso muerto" to "deadlift",
            "mancuerna" to "dumbbell",
            "barra" to "barbell",
            "polea" to "cable",
        )
        pairs.forEach { (es, en) ->
            val esTerms = TextNormalizer.terms(es)
            val enTerms = TextNormalizer.terms(en) - esTerms.toSet()
            assertTrue(expandName(es).containsAll(enTerms), "$es should expand to $en, got ${expandName(es)}")
            assertTrue(
                expandName(en).containsAll(esTerms - TextNormalizer.terms(en).toSet()),
                "$en should expand to $es, got ${expandName(en)}"
            )
        }
    }

    private fun assertExpands(name: String, vararg terms: String) {
        val got = expandName(name)
        val expected = terms.map(TextNormalizer::stem)
        assertTrue(got.containsAll(expected), "$name should expand to $expected, got $got")
    }

    @Test
    fun `phrases are found inside longer names, after normalisation`() {
        assertExpands("Press de Banca con Mancuernas", "bench", "dumbbell")
        assertExpands("Peso muerto rumano", "deadlift", "romanian", "rdl")
        assertExpands("Incline Bench Press", "inclinado", "banca")
        assertExpands("Jalón al pecho en polea", "lat", "pulldown", "cable")
    }

    @Test
    fun `expansion never repeats the name's own terms and ignores unknown words`() {
        val terms = TextNormalizer.terms("Remo con barra")
        assertTrue(table.expand(terms).none { it in terms })
        assertEquals(emptyList(), table.expand(TextNormalizer.terms("xyzzy plugh")))
        assertEquals(emptyList(), table.expand(emptyList()))
        assertEquals(emptyList(), SynonymTable.EMPTY.expand(listOf("remo")))
    }

    @Test
    fun `every curated group has at least two distinct phrases`() {
        assertTrue(table.size >= 80, "table has ${table.size} groups")
        table.groups().forEach { group ->
            assertTrue(
                group.size >= 2,
                "group $group has one phrase after normalisation"
            )
        }
    }
}
