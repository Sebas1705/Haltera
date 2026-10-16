@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.sebas1705.haltera.dataimport

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

/** iOS: the document picker (copies the chosen file into the app sandbox, then it is read completely). */
class IosImportFileBridge : ImportFileBridge {
    // The picker holds its delegate weakly: keep it alive while the picker is shown.
    private var delegate: PickerDelegate? = null

    override suspend fun pick(): PickedImportFile? = suspendCancellableCoroutine { cont ->
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
        val handler = PickerDelegate { url ->
            delegate = null
            if (cont.isActive) cont.resume(url?.let(::read))
        }
        delegate = handler
        picker.delegate = handler
        val presenter = topController()
        if (presenter == null) {
            delegate = null
            cont.resume(null)
        } else {
            presenter.presentViewController(picker, animated = true, completion = null)
        }
        cont.invokeOnCancellation {
            delegate = null
            picker.dismissViewControllerAnimated(true, completion = null)
        }
    }

    private fun read(url: NSURL): PickedImportFile {
        val data = checkNotNull(NSData.dataWithContentsOfURL(url)) { "cannot read $url" }
        check(data.length.toLong() <= ImportFileBridge.MAX_BYTES) { "file too large" }
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        }
        return PickedImportFile(url.lastPathComponent.orEmpty(), bytes)
    }

    private fun topController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }

    private class PickerDelegate(private val done: (NSURL?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            done(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            done(null)
        }
    }
}
