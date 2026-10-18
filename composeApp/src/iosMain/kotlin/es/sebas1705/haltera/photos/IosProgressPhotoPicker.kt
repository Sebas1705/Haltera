@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.sebas1705.haltera.photos

import es.sebas1705.haltera.feature.progress.photos.PickedPhoto
import es.sebas1705.haltera.feature.progress.photos.ProgressPhotoPicker
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS: PHPickerViewController (images only, one at a time). It runs out of process and needs no photo library
 * permission. The chosen image is redrawn upright and small and re-encoded as JPEG, so no metadata is kept. The date
 * it was taken is not read (that needs the photo library permission): new photos are dated today, editable.
 */
class IosProgressPhotoPicker : ProgressPhotoPicker {
    // The picker holds its delegate weakly: keep it alive while the picker is shown.
    private var delegate: PickerDelegate? = null

    override val isAvailable: Boolean = true

    override suspend fun pick(): PickedPhoto? = suspendCancellableCoroutine { cont ->
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = configuration)
        val handler = PickerDelegate { result ->
            picker.dismissViewControllerAnimated(true, completion = null)
            delegate = null
            if (result == null) {
                if (cont.isActive) cont.resume(null)
                return@PickerDelegate
            }
            result.itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, error ->
                val photo = data?.let(::process)
                dispatch_async(dispatch_get_main_queue()) {
                    if (!cont.isActive) return@dispatch_async
                    if (photo != null) {
                        cont.resume(photo)
                    } else {
                        cont.resumeWithException(
                            IllegalStateException(error?.localizedDescription ?: "cannot read the photo")
                        )
                    }
                }
            }
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
            dispatch_async(dispatch_get_main_queue()) { picker.dismissViewControllerAnimated(true, completion = null) }
        }
    }

    private fun process(data: NSData): PickedPhoto? {
        val image = UIImage.imageWithData(data) ?: return null
        val full = redraw(image, MAX_SIDE) ?: return null
        val thumb = redraw(full, THUMB_SIDE) ?: return null
        val fullBytes = UIImageJPEGRepresentation(full, QUALITY)?.toByteArray() ?: return null
        val thumbBytes = UIImageJPEGRepresentation(thumb, THUMB_QUALITY)?.toByteArray() ?: return null
        return PickedPhoto(fullBytes, thumbBytes, takenAt = null)
    }

    /** [image] drawn upright (its orientation applied) inside [maxSide] points on the long side, at scale 1. */
    private fun redraw(image: UIImage, maxSide: Double): UIImage? {
        val (width, height) = image.size.useContents { width to height }
        if (width <= 0.0 || height <= 0.0) return null
        val scale = minOf(1.0, maxSide / maxOf(width, height))
        val w = maxOf(1.0, width * scale)
        val h = maxOf(1.0, height * scale)
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply {
            this.scale = 1.0
            opaque = true
        }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format)
        return renderer.imageWithActions { image.drawInRect(CGRectMake(0.0, 0.0, w, h)) }
    }

    private fun NSData.toByteArray(): ByteArray {
        val bytes = ByteArray(length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes
    }

    private fun topController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }

    private class PickerDelegate(private val done: (PHPickerResult?) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            done(didFinishPicking.firstOrNull() as? PHPickerResult)
        }
    }

    private companion object {
        const val MAX_SIDE = 1600.0
        const val THUMB_SIDE = 360.0
        const val QUALITY = 0.85
        const val THUMB_QUALITY = 0.8
    }
}
