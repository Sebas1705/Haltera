package es.sebas1705.ironlog.data.local

/**
 * Normalisation for the `name*Search` columns and search queries: lower case, Latin accents and
 * diacritics removed, whitespace collapsed. "Press de Banca Inclinado" and "PRÉSS  de banca" both
 * normalise to text that matches with LIKE.
 */
object SearchText {
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

    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        var lastWasSpace = true
        for (c in text.lowercase()) {
            if (c.isWhitespace()) {
                if (!lastWasSpace) out.append(' ')
                lastWasSpace = true
            } else {
                out.append(FOLD[c] ?: c)
                lastWasSpace = false
            }
        }
        return out.toString().trimEnd()
    }

    /** A user query ready for the LIKE search: normalised, with the LIKE wildcards `%` and `_` removed. */
    fun query(text: String): String = normalize(text).replace("%", "").replace("_", "")
}
