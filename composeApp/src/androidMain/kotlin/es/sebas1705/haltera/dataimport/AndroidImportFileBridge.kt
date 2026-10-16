package es.sebas1705.haltera.dataimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import es.sebas1705.haltera.share.CurrentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** Android: the system document picker (ACTION_OPEN_DOCUMENT); the chosen file is read completely. */
class AndroidImportFileBridge(private val context: Context) : ImportFileBridge {

    override suspend fun pick(): PickedImportFile? {
        val activity = checkNotNull(CurrentActivity.activity) { "no activity to pick a file" }
        val uri = suspendCancellableCoroutine<Uri?> { cont ->
            var launcher: ActivityResultLauncher<Array<String>>? = null
            launcher = activity.activityResultRegistry.register(PICK_KEY, ActivityResultContracts.OpenDocument()) {
                launcher?.unregister()
                if (cont.isActive) cont.resume(it)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(MIME_TYPES)
        } ?: return null
        return withContext(Dispatchers.IO) { read(uri) }
    }

    private fun read(uri: Uri): PickedImportFile {
        val stream = checkNotNull(context.contentResolver.openInputStream(uri)) { "cannot open $uri" }
        val bytes = stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                check(total <= ImportFileBridge.MAX_BYTES) { "file too large" }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        return PickedImportFile(displayName(uri), bytes)
    }

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private companion object {
        const val PICK_KEY = "haltera-import-data"
        const val BUFFER = 64 * 1024

        // CSV files are reported under many types (text/csv, text/comma-separated-values, octet-stream from
        // Drive and file managers), so everything is offered and the content decides.
        val MIME_TYPES = arrayOf("*/*")
    }
}
