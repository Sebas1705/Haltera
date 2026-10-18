package es.sebas1705.haltera.data.repository.importer

/** A CSV file with a header row; columns are found by their lower-cased, trimmed names. */
internal class CsvTable private constructor(private val reader: CsvReader, val columns: Map<String, Int>) {

    /** One row; a missing or short cell reads as an empty string. */
    inner class Row(private val values: List<String>) {
        /** The first non-blank cell among the columns [names] (trimmed), or "". */
        operator fun get(vararg names: String): String {
            for (name in names) {
                val index = columns[name] ?: continue
                val value = values.getOrNull(index)?.trim().orEmpty()
                if (value.isNotEmpty()) return value
            }
            return ""
        }
    }

    fun has(name: String): Boolean = name in columns

    /** The remaining rows, read lazily; call once. */
    fun rows(): Sequence<Row> = sequence {
        while (true) {
            val values = reader.next() ?: break
            yield(Row(values))
        }
    }

    companion object {
        /** Reads the header of [text]; null when there is none. */
        fun open(text: String): CsvTable? {
            val reader = CsvReader(text, CsvReader.detectDelimiter(text))
            val header = reader.next() ?: return null
            val columns = LinkedHashMap<String, Int>()
            header.forEachIndexed { index, name ->
                val key = name.trim().trimStart(CsvReader.BOM).lowercase()
                if (key.isNotEmpty() && key !in columns) columns[key] = index
            }
            return CsvTable(reader, columns)
        }
    }
}

/** The file kinds this importer knows, told apart by their header row (docs/IMPORT-FORMATS.md). */
internal enum class HistoryFileKind { HEVY, STRONG, FITNOTES, HALTERA_SESSIONS, HALTERA_BODY_WEIGHT, HALTERA_BODY_MEASUREMENTS }

internal object HistoryFileDetector {
    fun detect(columns: Set<String>): HistoryFileKind? = when {
        "session_id" in columns && "exercise_id" in columns && "set_type" in columns -> HistoryFileKind.HALTERA_SESSIONS
        "measured_at" in columns && "weight_grams" in columns -> HistoryFileKind.HALTERA_BODY_WEIGHT
        "measured_at" in columns && "raw_value" in columns && "type" in columns -> HistoryFileKind.HALTERA_BODY_MEASUREMENTS
        "exercise_title" in columns && "start_time" in columns -> HistoryFileKind.HEVY
        "exercise name" in columns && "set order" in columns && "date" in columns -> HistoryFileKind.STRONG
        "exercise" in columns && "date" in columns && "reps" in columns &&
            columns.any { it == "category" || it.startsWith("weight") } -> HistoryFileKind.FITNOTES
        else -> null
    }
}
