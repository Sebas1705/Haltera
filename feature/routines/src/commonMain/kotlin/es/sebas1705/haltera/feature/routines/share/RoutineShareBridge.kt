package es.sebas1705.haltera.feature.routines.share

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform side of routine sharing, implemented by the app shell (Android: FileProvider, OpenDocument,
 * Play services code scanner; iOS: UIActivityViewController, UIDocumentPicker).
 */
interface RoutineShareBridge {
    /** A routine (file text or QR payload) opened from outside the app, waiting to be imported. */
    val incoming: StateFlow<String?>

    /** Clears [incoming] once the import screen has taken it. */
    fun consumeIncoming()

    /** Writes [content] to a cache file called [fileName] and opens the system share sheet. */
    suspend fun shareFile(fileName: String, content: String)

    /** Lets the user pick a file and returns its text, or null when cancelled. */
    suspend fun pickFile(): String?

    val canScanQr: Boolean

    /** Scans a QR code and returns its text, or null when cancelled or unavailable. */
    suspend fun scanQr(): String?
}
