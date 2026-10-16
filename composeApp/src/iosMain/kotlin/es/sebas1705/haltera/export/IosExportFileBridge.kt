@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.sebas1705.haltera.export

import es.sebas1705.haltera.data.repository.export.ByteArrayOutput
import es.sebas1705.haltera.data.repository.export.ByteSink
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/** iOS: the export is written to a temporary file and handed to the share sheet (which has "Save to Files"). */
class IosExportFileBridge : ExportFileBridge {
    override suspend fun <T : Any> save(fileName: String, mimeType: String, write: suspend (ByteSink) -> T): T? {
        val out = ByteArrayOutput()
        val result = write(out)
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        check(out.toByteArray().toNSData().writeToURL(url, atomically = true)) { "cannot write $fileName" }
        val controller = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        val presenter = topController()
        // On iPad the share sheet is a popover and needs an anchor.
        controller.popoverPresentationController?.sourceView = presenter?.view
        presenter?.presentViewController(controller, animated = true, completion = null)
        return result
    }

    private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
        NSData()
    } else {
        usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
    }

    private fun topController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}
