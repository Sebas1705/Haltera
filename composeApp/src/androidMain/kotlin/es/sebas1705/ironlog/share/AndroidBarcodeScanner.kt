package es.sebas1705.ironlog.share

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import es.sebas1705.ironlog.feature.nutrition.scan.BarcodeScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Product barcodes for nutrition with the Play services code scanner (Google's scanner UI, no camera
 * permission), as for routine QR codes.
 */
class AndroidBarcodeScanner : BarcodeScanner {
    override val isAvailable: Boolean = true

    override suspend fun scan(): String? {
        val activity = CurrentActivity.activity ?: return null
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        return suspendCancellableCoroutine { cont ->
            GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.rawValue) }
                .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }
}
