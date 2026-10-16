package es.sebas1705.haltera.core.search

/**
 * Turns names and queries into comparable search terms (Spanish and English):
 *
 * 1. lower case, Latin accents and diacritics folded ("Jalón" → "jalon", "ñ" → "n");
 * 2. every character that is not a letter or a digit splits words ("Pull-Up" → "pull up", "3/4" → "3 4");
 * 3. stopwords ("de", "con", "the", "with"…) are dropped, unless the text has nothing else;
 * 4. light plural stemming ([stem]) so "curls"/"curl", "mancuernas"/"mancuerna", "flyes"/"fly" meet.
 *
 * The same function runs on the indexed text and on the query, so the stems only need to agree with
 * each other, not to be real words.
 */
object TextNormalizer {
    private val FOLD: Map<Char, String> = buildMap {
        fun map(chars: String, to: String) = chars.forEach { put(it, to) }
        map("áàâäãåā", "a")
        map("éèêëē", "e")
        map("íìîïī", "i")
        map("óòôöõøō", "o")
        map("úùûüū", "u")
        map("ýÿ", "y")
        map("ñ", "n")
        map("ç", "c")
        map("ß", "ss")
        map("æ", "ae")
        map("œ", "oe")
    }

    /** Words that carry no meaning in an exercise or food name. */
    val STOPWORDS: Set<String> = setOf(
        // Spanish
        "de", "del", "la", "las", "el", "los", "lo", "con", "en", "a", "al", "y", "e", "o", "u", "por", "para",
        "sobre", "un", "una", "unos", "unas", "sin",
        // English
        "the", "an", "with", "on", "to", "of", "and", "or", "in", "for", "from", "at", "into", "onto", "without",
    )

    /** Lower case, accents folded, punctuation turned into single spaces, trimmed. */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        var lastWasSpace = true
        for (c in text.lowercase()) {
            val folded = FOLD[c]
            when {
                folded != null -> {
                    out.append(folded)
                    lastWasSpace = false
                }
                c.isLetterOrDigit() -> {
                    out.append(c)
                    lastWasSpace = false
                }
                !lastWasSpace -> {
                    out.append(' ')
                    lastWasSpace = true
                }
            }
        }
        return out.toString().trimEnd()
    }

    /** Folded words of [text] without stopwords (kept when the text has only stopwords), each stemmed. */
    fun terms(text: String): List<String> = words(text).map(::stem)

    /** Folded words of [text] without stopwords (kept when the text has only stopwords), not stemmed. */
    fun words(text: String): List<String> {
        val words = fold(text).split(' ').filter { it.isNotEmpty() }
        return words.filter { it !in STOPWORDS }.ifEmpty { words }
    }

    /**
     * Light plural/singular stemming for Spanish and English. Plural forms reduce to the stem of their
     * singular: "presses" → "press", "flies"/"flyes"/"flye"/"fly" → "fly", "crunches" → "crunch",
     * "elevaciones" → "elevacion", "dorsales" → "dorsal", "mancuernas" → "mancuerna", "cables"/"cable" →
     * "cabl", "lunges"/"lunge" → "lung". Words of 2 letters or less are left alone.
     */
    @Suppress("CyclomaticComplexMethod", "ReturnCount") // A flat list of suffix rules reads best.
    fun stem(word: String): String {
        if (word.length < MIN_STEM_LENGTH || word.any { it.isDigit() }) return word
        val singular = when {
            word.endsWith("sses") -> word.dropLast(2)
            word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
            word.length > SHORT && word.endsWith("ies") -> word.removeSuffix("ies") + "y"
            word.endsWith("yes") -> word.dropLast(2)
            word.endsWith("ye") -> word.dropLast(1)
            word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") -> word.dropLast(2)
            word.length > SHORT && word.endsWith("ones") -> word.dropLast(2)
            word.length > SHORT + 1 && word.endsWith("es") && word.dropLast(2).last() in "rldn" -> word.dropLast(2)
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }
        // A final "e" goes so English singulars meet the "-es" plurals above ("cable" and "cables" → "cabl"); short
        // words keep it so a query like "core" does not turn into the prefix "cor".
        return if (singular.length > SHORT && singular.endsWith("e")) singular.dropLast(1) else singular
    }

    private const val MIN_STEM_LENGTH = 3
    private const val SHORT = 4
}
