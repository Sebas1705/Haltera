package es.sebas1705.haltera.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIndexTest {

    private fun doc(key: String, name: String, vararg keywords: Pair<String, Double>) =
        SearchDocument(
            key,
            listOf(SearchField(name, 1.0, FieldKind.NAME)) + keywords.map { (text, w) -> SearchField(text, w) }
        )

    private fun SearchIndex<String>.keys(query: String, limit: Int = 50) = search(query, limit).map { it.key }

    private val index = SearchIndex.build(
        listOf(
            doc("bench", "Bench Press"),
            doc("incline", "Incline Bench Press"),
            doc("close", "Close-Grip Bench Press"),
            doc("squat", "Squat"),
            doc("front", "Front Squat"),
            doc("pullup", "Pull-Up"),
            doc("pulldown", "Lat Pulldown"),
            doc("deadlift", "Deadlift"),
            doc("curl", "Biceps Curl", "dumbbell" to 0.65),
            doc("hammer", "Hammer Curl", "dumbbell" to 0.65),
        ),
    )

    @Test
    fun `word order does not matter`() {
        assertEquals("bench", index.keys("press bench").first())
        assertEquals("incline", index.keys("press incline bench").first())
        assertEquals("curl", index.keys("curl biceps").first())
    }

    @Test
    fun `an exact name ranks first, then names with every word, then partial matches`() {
        assertEquals(listOf("bench", "incline", "close"), index.keys("bench press"))
        assertEquals(listOf("squat", "front"), index.keys("squat"))
    }

    @Test
    fun `typos are tolerated within the allowance`() {
        assertEquals("deadlift", index.keys("deadlfit").first())
        assertEquals("squat", index.keys("sqaut").first())
        assertEquals("curl", index.keys("bicpes curl").first())
        // Three letters get no typo allowance.
        assertTrue(index.keys("sqt").isEmpty())
    }

    @Test
    fun `prefixes match while typing`() {
        assertEquals("bench", index.keys("ben").first())
        assertEquals(listOf("bench", "incline", "close"), index.keys("bench pr"))
        assertEquals("deadlift", index.keys("d").first())
    }

    @Test
    fun `joined and split words meet`() {
        assertEquals("pullup", index.keys("pullup").first())
        assertEquals("pulldown", index.keys("lat pull down").first())
        assertEquals("pulldown", index.keys("latpulldown").first())
    }

    @Test
    fun `scores follow the documented tiers`() {
        fun score(query: String, key: String) = index.search(query).first { it.key == key }.score
        val exact = score("bench press", "bench")
        val allWords = score("bench press", "incline")
        val prefix = score("bench pre", "incline")
        val fuzzy = score("bnech press", "incline")
        assertTrue(exact > allWords, "exact $exact > all words $allWords")
        assertTrue(allWords > prefix, "all words $allWords > prefix $prefix")
        assertTrue(prefix > fuzzy, "prefix $prefix > fuzzy $fuzzy")
    }

    @Test
    fun `field weights rank a name match above a keyword match`() {
        val weighted = SearchIndex.build(
            listOf(
                SearchDocument(
                    "category",
                    listOf(SearchField("Plank", 1.0, FieldKind.NAME), SearchField("strength", 0.45))
                ),
                SearchDocument(
                    "equipment",
                    listOf(SearchField("Row", 1.0, FieldKind.NAME), SearchField("strength", 0.65))
                ),
                SearchDocument(
                    "alias",
                    listOf(SearchField("Lift", 1.0, FieldKind.NAME), SearchField("strength", 0.8, FieldKind.ALIAS))
                ),
                SearchDocument("name", listOf(SearchField("Strength Circuit", 1.0, FieldKind.NAME))),
            ),
        )
        assertEquals(listOf("name", "alias", "equipment", "category"), weighted.keys("strength"))
    }

    @Test
    fun `junk and blank queries return nothing`() {
        listOf("", "   ", "xyzzy", "qwerty asdf", "--").forEach { assertTrue(index.keys(it).isEmpty(), "'$it'") }
    }

    @Test
    fun `two-word queries need both words, longer ones may miss one`() {
        assertTrue(index.keys("bench xyzzy").isEmpty())
        assertEquals("incline", index.keys("incline bench press xyzzy").first())
    }

    @Test
    fun `ties keep the build order, so results are stable`() {
        val twins = SearchIndex.build(
            listOf("b", "a", "c").map { SearchDocument(it, listOf(SearchField("Row", 1.0, FieldKind.NAME))) }
        )
        repeat(3) { assertEquals(listOf("b", "a", "c"), twins.keys("row")) }
        assertEquals(index.search("press"), index.search("press"))
    }

    @Test
    fun `accept filters and boost reorders`() {
        assertEquals(listOf("hammer"), index.search("curl", accept = { it == "hammer" }).map { it.key })
        assertEquals("hammer", index.search("curl", boost = { if (it == "hammer") 0.5 else 0.0 }).first().key)
        assertEquals(1, index.search("press", limit = 1).size)
        assertTrue(index.search("press", limit = 0).isEmpty())
    }

    @Test
    fun `synonyms expand names at build time`() {
        val bilingual = SearchIndex.build(
            listOf(SearchDocument("dl", listOf(SearchField("Deadlift", 1.0, FieldKind.NAME)))),
            SynonymTable(listOf(listOf("peso muerto", "deadlift"))),
        )
        assertEquals(listOf("dl"), bilingual.keys("peso muerto"))
        assertEquals(listOf("dl"), bilingual.keys("muerto peso"))
        // A synonym match scores below the same words in the name itself.
        val direct = SearchIndex.build(
            listOf(SearchDocument("dl", listOf(SearchField("Peso muerto", 1.0, FieldKind.NAME))))
        )
        assertTrue(direct.search("peso muerto").single().score > bilingual.search("peso muerto").single().score)
    }

    @Test
    fun `keyword fields are not expanded with synonyms`() {
        val keywordOnly = SearchIndex.build(
            listOf(
                SearchDocument("x", listOf(SearchField("Plank", 1.0, FieldKind.NAME), SearchField("deadlift", 0.5)))
            ),
            SynonymTable(listOf(listOf("peso muerto", "deadlift"))),
        )
        assertTrue(keywordOnly.keys("peso muerto").isEmpty())
    }

    @Test
    fun `a query split across a name and a variant ranks it like a name, without adding term weight`() {
        val variants = SearchIndex.build(
            listOf(
                SearchDocument(
                    "fly",
                    listOf(
                        SearchField("Aperturas", 1.0, FieldKind.NAME),
                        SearchField("dumbbell mancuerna", 0.65),
                        SearchField("Aperturas mancuerna", 0.95, FieldKind.VARIANT),
                    ),
                ),
                SearchDocument(
                    "incline",
                    listOf(SearchField("Apertura con mancuernas en banco inclinado", 1.0, FieldKind.NAME))
                ),
                SearchDocument(
                    "variantOnly",
                    listOf(
                        SearchField("Plank", 1.0, FieldKind.NAME),
                        SearchField("Remo mancuerna", 0.95, FieldKind.VARIANT)
                    )
                ),
            ),
        )
        assertEquals("fly", variants.keys("aperturas mancuerna").first())
        // A variant name alone never makes a document match.
        assertTrue(variants.keys("remo").isEmpty())
    }
}
