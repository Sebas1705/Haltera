package es.sebas1705.haltera.core.search

/**
 * Opposite gym words that are only one or two letters apart (docs/designs/2026-10-16-fuzzy-search-design.md §5).
 * The typo step never links them: "inclinado" is not a typo of "declinado", "abductor" is not a typo of
 * "adductor", "pulldown" is not a typo of "pushdown". Exact and prefix matches are unaffected.
 *
 * Each entry is a pair of sides; a side lists the forms of one word (Spanish and English, singular and plural).
 * Forms go through [TextNormalizer], so accents and case do not matter.
 */
object Antonyms {

    val pairs: List<Pair<List<String>, List<String>>> = listOf(
        listOf("inclinado", "inclinada", "inclinados", "inclinadas", "incline", "inclined") to
            listOf("declinado", "declinada", "declinados", "declinadas", "decline", "declined"),
        listOf("supino", "supina", "supinos", "supinas", "supinated", "supination", "supine") to
            listOf("prono", "prona", "pronos", "pronas", "pronated", "pronation", "prone"),
        listOf("abductor", "abductores", "abductors", "abduccion", "abduction") to
            listOf("aductor", "aductores", "adductor", "adductors", "aduccion", "adduction"),
        listOf("interno", "interna", "internal") to listOf("externo", "externa", "external"),
        listOf("pulldown", "pulldowns") to listOf("pushdown", "pushdowns"),
        listOf("pullup", "pullups") to listOf("pushup", "pushups"),
    )

    /** Forms of each pair: [left forms, right forms]. */
    private val forms: List<Pair<List<String>, List<String>>> = pairs.map { (left, right) -> normalised(left) to normalised(right) }

    private fun normalised(words: List<String>) =
        words.flatMap { TextNormalizer.words(it) + TextNormalizer.terms(it) }.distinct().sortedByDescending { it.length }

    /**
     * True when [a] and [b] are forms of opposite words of the table, also inside joined words and word starts
     * ("pressinclinado" / "pressdeclinado", "abductor" / "adductorgroin"): opposite forms at the same position.
     */
    fun opposite(a: String, b: String): Boolean =
        forms.any { (left, right) -> swaps(a, b, left, right) || swaps(b, a, left, right) }

    private fun swaps(a: String, b: String, left: List<String>, right: List<String>): Boolean =
        left.any { l ->
            val at = a.indexOf(l)
            at >= 0 && right.any { r -> b.indexOf(r) == at }
        }
}
