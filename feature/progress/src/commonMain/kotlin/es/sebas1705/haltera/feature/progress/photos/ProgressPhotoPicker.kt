package es.sebas1705.haltera.feature.progress.photos

/**
 * A photo chosen with the system picker, already made small for storage: a JPEG of at most a couple of thousand pixels
 * on its long side ([image]), a small one for lists ([thumbnail]), with no metadata (location and camera details are
 * dropped by re-encoding). [takenAt] is when the photo was taken, if the image says so (epoch millis).
 */
class PickedPhoto(val image: ByteArray, val thumbnail: ByteArray, val takenAt: Long?)

/**
 * The platform photo picker, implemented by the app shell: Android's system photo picker (no storage or camera
 * permission), iOS's PHPickerViewController (no photo library permission either).
 */
interface ProgressPhotoPicker {
    val isAvailable: Boolean

    /** Lets the user choose one photo; null when they cancel. Throws when the photo cannot be read. */
    suspend fun pick(): PickedPhoto?
}

/** No picker (previews, tests, a platform without one): adding photos is hidden. */
object NoProgressPhotoPicker : ProgressPhotoPicker {
    override val isAvailable: Boolean = false

    override suspend fun pick(): PickedPhoto? = null
}
