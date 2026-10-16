package es.sebas1705.haltera.data.repository.importer

/**
 * Writes a parsed history file into the logbook (docs/IMPORT-FORMATS.md). Imported data goes through the same
 * tables as logged data, so the sync triggers fire exactly as for a session the user logged by hand.
 */
interface HistoryImporter {
    /** What importing [parsed] with [options] would do. Reads only; nothing is written. */
    suspend fun preview(parsed: ParsedImport, options: ImportOptions): ImportPreview

    /**
     * Writes the file in one transaction (a failure leaves the logbook as it was), after creating the custom
     * exercises the user chose to create. Sessions that already exist are skipped or replaced per [options].
     */
    suspend fun import(parsed: ParsedImport, options: ImportOptions): ImportSummary
}
