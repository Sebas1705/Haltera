package es.sebas1705.haltera.dataimport

/** A file the user picked: its display [name] and its whole content. */
class PickedImportFile(val name: String, val bytes: ByteArray)

/**
 * Platform side of "Import": lets the user pick a history file. Android opens the system document picker
 * (ACTION_OPEN_DOCUMENT); iOS shows the document picker. The file is read completely before it is returned.
 */
interface ImportFileBridge {
    /** The picked file, or null when the user cancelled. Throws when the file cannot be read. */
    suspend fun pick(): PickedImportFile?

    companion object {
        /** Files larger than this are refused (a 50 000 row CSV is about 10 MB). */
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
