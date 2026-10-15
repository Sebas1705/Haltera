@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.sebas1705.haltera.share

import es.sebas1705.haltera.feature.routines.share.RoutineShareBridge
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS sharing: UIActivityViewController and UIDocumentPicker. QR scanning is not supported yet
 * (an AVFoundation scanner is future work), so the import screen says so.
 */
class IosRoutineShareBridge(private val channel: IncomingRoutineChannel) : RoutineShareBridge {
    override val incoming: StateFlow<String?> = channel.value

    // Kept alive while the picker is shown: UIKit holds its delegate weakly.
    private var pickerDelegate: NSObject? = null

    override fun consumeIncoming() = channel.consume()

    override suspend fun shareFile(fileName: String, content: String) {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        val text = NSString.create(string = content)
        text.writeToURL(url, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        topController()?.presentViewController(controller, animated = true, completion = null)
    }

    override suspend fun pickFile(): String? {
        val presenter = topController() ?: return null
        val url = suspendCancellableCoroutine<NSURL?> { cont ->
            val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol {
                override fun documentPicker(
                    controller: UIDocumentPickerViewController,
                    didPickDocumentsAtURLs: List<*>
                ) {
                    pickerDelegate = null
                    if (cont.isActive) cont.resume(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
                }

                override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                    pickerDelegate = null
                    if (cont.isActive) cont.resume(null)
                }
            }
            pickerDelegate = delegate
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData), asCopy = true)
            picker.delegate = delegate
            presenter.presentViewController(picker, animated = true, completion = null)
        } ?: return null
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        if (data.length.toLong() > IncomingRoutineChannel.MAX_BYTES) return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override val canScanQr: Boolean = false

    override suspend fun scanQr(): String? = null

    private fun topController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}
