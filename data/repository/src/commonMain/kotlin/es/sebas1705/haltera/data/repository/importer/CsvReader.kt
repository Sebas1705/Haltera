package es.sebas1705.haltera.data.repository.importer

/**
 * A pull parser for RFC 4180 style CSV: quoted fields (with doubled quotes, delimiters and line breaks inside),
 * CRLF, LF or CR line ends, and a leading byte order mark. It never builds the list of rows, so a file with
 * tens of thousands of rows is read with constant extra memory. Lines without any text are skipped.
 */
internal class CsvReader(private val text: String, val delimiter: Char) {
    private var pos = if (text.startsWith(BOM)) 1 else 0

    /** The next non-empty row, or null at the end of the text. */
    fun next(): List<String>? {
        while (pos < text.length) {
            val row = readRow()
            if (row.size > 1 || row[0].isNotEmpty()) return row
        }
        return null
    }

    private fun readRow(): List<String> {
        val fields = ArrayList<String>(INITIAL_FIELDS)
        val field = StringBuilder()
        var quoted = false
        var wasQuoted = false
        while (pos < text.length) {
            val c = text[pos++]
            when {
                quoted -> when {
                    c != '"' -> field.append(c)
                    pos < text.length && text[pos] == '"' -> {
                        field.append('"')
                        pos++
                    }
                    else -> quoted = false
                }
                c == '"' && field.isEmpty() && !wasQuoted -> {
                    quoted = true
                    wasQuoted = true
                }
                c == delimiter -> {
                    fields += field.toString()
                    field.clear()
                    wasQuoted = false
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && pos < text.length && text[pos] == '\n') pos++
                    fields += field.toString()
                    return fields
                }
                else -> field.append(c)
            }
        }
        fields += field.toString()
        return fields
    }

    companion object {
        const val BOM = '\uFEFF'
        private const val INITIAL_FIELDS = 16

        /**
         * The delimiter of a file from its first line: whichever of comma, semicolon and tab is most frequent
         * outside quotes. A header holds no numbers, so a semicolon there is always a separator (Strong on
         * Android writes semicolons).
         */
        fun detectDelimiter(text: String): Char {
            val start = if (text.startsWith(BOM)) 1 else 0
            var end = start
            var quoted = false
            val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
            while (end < text.length) {
                val c = text[end]
                if (c == '"') quoted = !quoted
                if (!quoted && (c == '\n' || c == '\r')) break
                if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
                end++
            }
            return counts.entries.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
        }
    }
}
