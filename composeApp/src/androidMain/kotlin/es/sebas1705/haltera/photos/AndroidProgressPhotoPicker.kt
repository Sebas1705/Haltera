package es.sebas1705.haltera.photos

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import es.sebas1705.haltera.feature.progress.photos.PickedPhoto
import es.sebas1705.haltera.feature.progress.photos.ProgressPhotoPicker
import es.sebas1705.haltera.share.CurrentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Android: the system photo picker (`PickVisualMedia`, images only). It needs no storage or camera permission; on
 * devices without the picker AndroidX falls back to the document picker for images. The chosen photo is copied small
 * ([PhotoProcessing]) into app-private storage by the repository; the original is never touched.
 */
class AndroidProgressPhotoPicker(private val context: Context) : ProgressPhotoPicker {
    override val isAvailable: Boolean = true

    override suspend fun pick(): PickedPhoto? {
        val activity = checkNotNull(CurrentActivity.activity) { "no activity to pick a photo" }
        val uri = suspendCancellableCoroutine<Uri?> { cont ->
            var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
            launcher = activity.activityResultRegistry.register(PICK_KEY, ActivityResultContracts.PickVisualMedia()) {
                launcher?.unregister()
                if (cont.isActive) cont.resume(it)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } ?: return null
        return withContext(Dispatchers.IO) { PhotoProcessing.process(context.contentResolver, uri) }
    }

    private companion object {
        const val PICK_KEY = "haltera-progress-photo"
    }
}

/**
 * Turns a picked image into what Haltera stores: upright (EXIF orientation applied), at most [MAX_SIDE] pixels on the
 * long side, re-encoded as JPEG so no metadata (location, camera) is kept, plus a [THUMB_SIDE] thumbnail.
 */
internal object PhotoProcessing {
    const val MAX_SIDE = 1600
    const val THUMB_SIDE = 360
    private const val QUALITY = 85
    private const val THUMB_QUALITY = 80

    fun process(resolver: ContentResolver, uri: Uri): PickedPhoto {
        val exif = resolver.openInputStream(uri)?.use { stream -> runCatching { ExifInterface(stream) }.getOrNull() }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
        val takenAt = exif?.let(::takenAt)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image: $uri")
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("cannot decode $uri")
        val full = transform(decoded, MAX_SIDE, orientation)
        val thumb = transform(full, THUMB_SIDE, ExifInterface.ORIENTATION_NORMAL)
        return PickedPhoto(jpeg(full, QUALITY), jpeg(thumb, THUMB_QUALITY), takenAt)
    }

    /** The largest power of two that keeps the decoded image at least [maxSide] on its long side (memory bound). */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** The size that fits [width] × [height] inside [maxSide] keeping the aspect ratio (never enlarged). */
    fun targetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= maxSide) return width to height
        val scale = maxSide.toDouble() / long
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    /** When the photo was taken: EXIF DateTimeOriginal (or DateTime), with its offset when the camera wrote one. */
    private fun takenAt(exif: ExifInterface): Long? {
        val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        return ExifDates.parse(text, exif.getAttribute(TAG_OFFSET_TIME_ORIGINAL))
    }

    private fun transform(source: Bitmap, maxSide: Int, orientation: Int): Bitmap {
        val (w, h) = targetSize(source.width, source.height, maxSide)
        val matrix = Matrix().apply {
            postScale(w.toFloat() / source.width, h.toFloat() / source.height)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(QUARTER)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(HALF)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(THREE_QUARTERS)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(QUARTER)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(THREE_QUARTERS)
                    postScale(-1f, 1f)
                }
                else -> Unit
            }
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** ExifInterface.TAG_OFFSET_TIME_ORIGINAL needs API 26; the tag name is the same everywhere. */
    private const val TAG_OFFSET_TIME_ORIGINAL = "OffsetTimeOriginal"
    private const val QUARTER = 90f
    private const val HALF = 180f
    private const val THREE_QUARTERS = 270f
}
