package es.sebas1705.haltera.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import es.sebas1705.haltera.feature.routines.share.RoutineShareBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/** The activity in front, for the pickers and the scanner. Set by [es.sebas1705.haltera.MainActivity]. */
object CurrentActivity {
    private var ref: WeakReference<ComponentActivity>? = null

    var activity: ComponentActivity?
        get() = ref?.get()
        set(value) {
            ref = value?.let(::WeakReference)
        }
}

/** Android sharing: FileProvider + ACTION_SEND, OpenDocument and the Play services code scanner. */
class AndroidRoutineShareBridge(
    private val context: Context,
    private val channel: IncomingRoutineChannel,
) : RoutineShareBridge {
    override val incoming: StateFlow<String?> = channel.value

    override fun consumeIncoming() = channel.consume()

    override suspend fun shareFile(fileName: String, content: String) {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, SHARED_DIR).apply { mkdirs() }.resolve(fileName).apply { writeText(content) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, fileName.removeSuffix(".haltera"))
        val activity = CurrentActivity.activity
        if (activity != null) {
            activity.startActivity(chooser)
        } else {
            context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override suspend fun pickFile(): String? {
        val activity = CurrentActivity.activity ?: return null
        val uri = suspendCancellableCoroutine<Uri?> { cont ->
            var launcher: ActivityResultLauncher<Array<String>>? = null
            launcher = activity.activityResultRegistry.register(PICK_KEY, ActivityResultContracts.OpenDocument()) {
                launcher?.unregister()
                if (cont.isActive) cont.resume(it)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(arrayOf("*/*"))
        } ?: return null
        return readText(context, uri)
    }

    override val canScanQr: Boolean = true

    override suspend fun scanQr(): String? {
        val activity = CurrentActivity.activity ?: return null
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        return suspendCancellableCoroutine { cont ->
            GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.rawValue) }
                .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }

    companion object {
        private const val SHARED_DIR = "shared"
        private const val PICK_KEY = "haltera-import-routine"
        const val MIME_TYPE = "application/json"

        /** The text of [uri], or null when it cannot be read or is too large to be a routine. */
        suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytesCompat(IncomingRoutineChannel.MAX_BYTES + 1)
                    bytes.takeIf { it.size <= IncomingRoutineChannel.MAX_BYTES }?.decodeToString()
                }
            }.getOrNull()
        }

        private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            while (out.size() < limit) {
                val n = read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }

        private const val BUFFER = 8_192
    }
}
