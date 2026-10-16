package es.sebas1705.haltera.export

import es.sebas1705.haltera.data.repository.export.ByteSink

/**
 * Platform side of "Export my data": hands the user a file. Android asks where to save it (system file
 * picker, ACTION_CREATE_DOCUMENT); iOS writes it to a temporary file and shows the share sheet.
 */
interface ExportFileBridge {
    /**
     * Lets the user choose the destination of [fileName], then runs [write] with a sink that fills it.
     * Returns what [write] returned, or null when the user cancelled (nothing is written then).
     */
    suspend fun <T : Any> save(fileName: String, mimeType: String, write: suspend (ByteSink) -> T): T?
}
