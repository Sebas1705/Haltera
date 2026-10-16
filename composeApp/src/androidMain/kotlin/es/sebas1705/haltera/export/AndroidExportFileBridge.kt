package es.sebas1705.haltera.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import es.sebas1705.haltera.data.repository.export.ByteSink
import es.sebas1705.haltera.share.CurrentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import kotlin.coroutines.resume

/** Android: the system "save as" picker (ACTION_CREATE_DOCUMENT), then the file is streamed into the chosen document. */
class AndroidExportFileBridge(private val context: Context) : ExportFileBridge {

    override suspend fun <T : Any> save(fileName: String, mimeType: String, write: suspend (ByteSink) -> T): T? {
        val activity = checkNotNull(CurrentActivity.activity) { "no activity to ask where to save" }
        val uri = suspendCancellableCoroutine<Uri?> { cont ->
            var launcher: ActivityResultLauncher<String>? = null
            launcher = activity.activityResultRegistry.register(
                PICK_KEY,
                ActivityResultContracts.CreateDocument(mimeType),
            ) {
                launcher?.unregister()
                if (cont.isActive) cont.resume(it)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(fileName)
        } ?: return null
        return withContext(Dispatchers.IO) {
            val stream = checkNotNull(context.contentResolver.openOutputStream(uri, "w")) { "cannot open $uri" }
            try {
                BufferedOutputStream(stream).use { out ->
                    val result = write(
                        object : ByteSink {
                            override fun write(bytes: ByteArray, offset: Int, length: Int) =
                                out.write(bytes, offset, length)
                        },
                    )
                    out.flush()
                    result
                }
            } catch (e: IOException) {
                discard(uri)
                throw e
            } catch (e: IllegalStateException) {
                discard(uri)
                throw e
            }
        }
    }

    /** Removes the half-written document so a failed export does not leave a broken zip behind. */
    private fun discard(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private companion object {
        const val PICK_KEY = "haltera-export-data"
    }
}
