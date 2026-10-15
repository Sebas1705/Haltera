package es.sebas1705.haltera.feature.nutrition.scan

/**
 * Platform barcode scanner, implemented by the app shell (Android: the Play services code scanner, no camera
 * permission; iOS: not available yet).
 */
interface BarcodeScanner {
    val isAvailable: Boolean

    /** Scans a product barcode (EAN-13/8, UPC-A/E) and returns its digits, or null when cancelled or unavailable. */
    suspend fun scan(): String?
}

/** No scanner (iOS for now, previews, tests). */
object NoBarcodeScanner : BarcodeScanner {
    override val isAvailable: Boolean = false

    override suspend fun scan(): String? = null
}
