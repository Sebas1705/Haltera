package es.sebas1705.haltera.data.repository.importer

import es.sebas1705.haltera.data.repository.export.ExportFormat
import es.sebas1705.haltera.data.repository.share.SharedExercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val ZIP_MAGIC_0 = 'P'.code.toByte()
private const val ZIP_MAGIC_1 = 'K'.code.toByte()
private const val ZIP_LOCAL = 3.toByte()
private const val ZIP_EMPTY = 5.toByte()
private const val REPLACEMENT = '�'
private const val BYTE_MASK = 0xFF
private const val ZIP_SIGNATURE_SIZE = 4

@Serializable
private data class ManifestHead(val format: String = "", val version: Int = 0)

@Serializable
private data class ExercisesFile(val exercises: List<SharedExercise> = emptyList())

/**
 * Reads a history file picked by the user and decides which source it comes from by its header row:
 * Hevy, Strong, FitNotes, or a Haltera export (the zip of "Export my data" or its `sessions.csv`).
 * Pure: bytes in, [ParsedImport] out. Failures throw [ImportFormatException] and nothing else is touched.
 */
object HistoryFileParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("TooGenericExceptionCaught") // A truncated zip reads past its end: that is damage, not a bug.
    fun parse(bytes: ByteArray, options: ParseOptions = ParseOptions()): ParsedImport {
        if (bytes.isEmpty()) throw ImportFormatException(ImportError.UNRECOGNISED, "empty file")
        if (!isZip(bytes)) return parseText(decode(bytes), options)
        return try {
            parseZip(bytes)
        } catch (e: IndexOutOfBoundsException) {
            throw ImportFormatException(ImportError.CORRUPT, "damaged zip", e)
        }
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size >= ZIP_SIGNATURE_SIZE && bytes[0] == ZIP_MAGIC_0 && bytes[1] == ZIP_MAGIC_1 &&
            (bytes[2] == ZIP_LOCAL || bytes[2] == ZIP_EMPTY)

    /** UTF-8, or Latin-1 when the bytes are not valid UTF-8 (spreadsheets in Western Europe save that way). */
    private fun decode(bytes: ByteArray): String {
        val utf8 = bytes.decodeToString()
        if (REPLACEMENT !in utf8) return utf8
        return buildString(bytes.size) { bytes.forEach { append((it.toInt() and BYTE_MASK).toChar()) } }
    }

    private fun parseText(text: String, options: ParseOptions): ParsedImport {
        val table = CsvTable.open(text) ?: throw ImportFormatException(ImportError.UNRECOGNISED, "no header row")
        val parsed = when (HistoryFileDetector.detect(table.columns.keys)) {
            HistoryFileKind.HEVY -> ThirdPartyParsers.hevy(table, options.timeZone)
            HistoryFileKind.STRONG -> ThirdPartyParsers.strong(table, options.timeZone)
            HistoryFileKind.FITNOTES -> ThirdPartyParsers.fitnotes(table, options.timeZone)
            HistoryFileKind.HALTERA_SESSIONS -> HalteraParser.sessions(table, emptyMap())
            HistoryFileKind.HALTERA_BODY_WEIGHT ->
                ParsedImport(ImportSource.HALTERA, emptyList(), HalteraParser.bodyWeights(table))
            HistoryFileKind.HALTERA_BODY_MEASUREMENTS ->
                ParsedImport(
                    ImportSource.HALTERA,
                    emptyList(),
                    bodyMeasurements = HalteraParser.bodyMeasurements(table)
                )
            null -> throw ImportFormatException(ImportError.UNRECOGNISED, "unknown header")
        }
        return requireData(parsed)
    }

    private fun requireData(parsed: ParsedImport): ParsedImport {
        if (parsed.isEmpty) throw ImportFormatException(ImportError.NO_DATA)
        return parsed
    }

    private fun parseZip(bytes: ByteArray): ParsedImport {
        val zip = ZipArchive.open(bytes)
        zip.find(ExportFormat.MANIFEST_FILE)?.let { checkManifest(it.decodeToString()) }
        val sessionsFile = zip.find(ExportFormat.SESSIONS_FILE)
        val weightFile = zip.find(ExportFormat.BODY_WEIGHT_FILE)
        // Version 3; older exports have no such file.
        val measurementsFile = zip.find(ExportFormat.BODY_MEASUREMENTS_FILE)
        if (sessionsFile == null && weightFile == null && measurementsFile == null) {
            throw ImportFormatException(ImportError.NOT_AN_EXPORT)
        }
        val definitions = zip.find(ExportFormat.EXERCISES_FILE)?.let(::customExercises).orEmpty()
        val sessions = sessionsFile?.let { file ->
            val table = CsvTable.open(file.decodeToString())
            table?.takeIf { HistoryFileDetector.detect(it.columns.keys) == HistoryFileKind.HALTERA_SESSIONS }
                ?.let { HalteraParser.sessions(it, definitions) }
                ?: throw ImportFormatException(ImportError.UNRECOGNISED, "sessions.csv header")
        }
        val weights = weightFile?.let { file ->
            CsvTable.open(file.decodeToString())?.let(HalteraParser::bodyWeights)
        }.orEmpty()
        val measurements = measurementsFile?.let { file ->
            CsvTable.open(file.decodeToString())?.let(HalteraParser::bodyMeasurements)
        }.orEmpty()
        return requireData(
            ParsedImport(
                ImportSource.HALTERA,
                sessions?.sessions.orEmpty(),
                weights,
                sessions?.skippedRows ?: 0,
                bodyMeasurements = measurements,
            ),
        )
    }

    private fun checkManifest(text: String) {
        val head = try {
            json.decodeFromString(ManifestHead.serializer(), text)
        } catch (e: SerializationException) {
            throw ImportFormatException(ImportError.CORRUPT, "manifest.json", e)
        }
        val problem = when {
            head.format != ExportFormat.FORMAT -> ImportError.NOT_AN_EXPORT
            head.version > ExportFormat.VERSION -> ImportError.NEWER_FORMAT
            else -> null
        }
        if (problem != null) throw ImportFormatException(problem)
    }

    @Suppress("SwallowedException") // The definitions only improve custom exercises created from names.
    private fun customExercises(bytes: ByteArray): Map<String, SharedExercise> = try {
        json.decodeFromString(ExercisesFile.serializer(), bytes.decodeToString()).exercises.associateBy { it.id }
    } catch (_: SerializationException) {
        emptyMap() // The definitions only improve a custom exercise created from a name; history imports without.
    }
}
