package es.sebas1705.haltera.data.repository.photos

import es.sebas1705.haltera.data.models.ProgressPhoto
import es.sebas1705.haltera.data.repository.ProgressPhotoRepository
import es.sebas1705.haltera.data.repository.sql.newId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One flat directory in app-private storage (Android `filesDir/progress_photos`, iOS Application Support), excluded
 * from the platform backups. Implemented per platform; names never contain a path separator.
 */
interface PhotoFiles {
    /** The file names in the directory (none when it does not exist yet). */
    fun list(): List<String>

    fun read(name: String): ByteArray?

    /** Writes [bytes] as [name] atomically (a temporary file renamed into place), creating the directory. */
    fun write(name: String, bytes: ByteArray)

    /** Deletes [name]; missing files are ignored. */
    fun delete(name: String)

    /** Deletes the directory and everything in it. */
    fun deleteAll()
}

/** The note and date of a photo, stored next to it as `<id>.json`. Written last: it is what makes a photo exist. */
@Serializable
internal data class PhotoMeta(
    val id: String,
    val takenAt: Long,
    val notes: String = "",
    val addedAt: Long = takenAt,
    val version: Int = 1,
)

/**
 * Progress photos as files: `<id>.jpg` (the image), `<id>.thumb.jpg` (for lists) and `<id>.json` ([PhotoMeta]).
 * Nothing about photos is in the database, so a database restored from a backup never points at missing images and no
 * photo or note leaves the device. A photo without its metadata (an interrupted add) or without its image (deleted
 * outside the app) is cleaned up the first time the directory is read.
 */
internal class FileProgressPhotoRepository(
    private val files: PhotoFiles,
    private val dispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : ProgressPhotoRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Mutex()
    private val photos = MutableStateFlow<List<ProgressPhoto>?>(null)

    override fun observeAll(): Flow<List<ProgressPhoto>> = flow {
        ensureLoaded()
        emitAll(photos.filterNotNull())
    }

    override suspend fun add(image: ByteArray, thumbnail: ByteArray, takenAt: Long, notes: String): String {
        require(image.isNotEmpty()) { "empty image" }
        return edit {
            val id = newId()
            val meta = PhotoMeta(id, takenAt, notes.trim(), addedAt = now())
            try {
                files.write(imageName(id), image)
                files.write(thumbName(id), thumbnail.takeIf { it.isNotEmpty() } ?: image)
                files.write(metaName(id), encode(meta))
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Leave nothing half-written behind; the caller reports the failure.
                listOf(metaName(id), thumbName(id), imageName(id)).forEach { runCatching { files.delete(it) } }
                throw e
            }
            it + meta.toModel() to id
        }
    }

    override suspend fun update(photo: ProgressPhoto) {
        edit { list ->
            val current = list.firstOrNull { it.id == photo.id } ?: return@edit list to Unit
            val updated = current.copy(takenAt = photo.takenAt, notes = photo.notes.trim())
            files.write(metaName(updated.id), encode(PhotoMeta(updated.id, updated.takenAt, updated.notes, updated.addedAt)))
            list.map { if (it.id == updated.id) updated else it } to Unit
        }
    }

    override suspend fun delete(id: String) {
        if (!isSafeId(id)) return
        edit { list ->
            // The metadata goes first: an interrupted delete leaves an orphan image, which the next load removes.
            files.delete(metaName(id))
            files.delete(imageName(id))
            files.delete(thumbName(id))
            list.filterNot { it.id == id } to Unit
        }
    }

    override suspend fun image(id: String): ByteArray? =
        if (!isSafeId(id)) null else withContext(dispatcher) { files.read(imageName(id)) }

    override suspend fun thumbnail(id: String): ByteArray? =
        if (!isSafeId(id)) {
            null
        } else {
            withContext(dispatcher) { files.read(thumbName(id)) ?: files.read(imageName(id)) }
        }

    override suspend fun deleteAll() {
        withContext(dispatcher) {
            lock.withLock {
                files.deleteAll()
                photos.value = emptyList()
            }
        }
    }

    private suspend fun ensureLoaded() {
        if (photos.value != null) return
        withContext(dispatcher) { lock.withLock { if (photos.value == null) photos.value = load() } }
    }

    /** Runs [change] on the loaded list under the lock and publishes the list it returns. */
    private suspend fun <T> edit(change: (List<ProgressPhoto>) -> Pair<List<ProgressPhoto>, T>): T =
        withContext(dispatcher) {
            lock.withLock {
                val (list, result) = change(photos.value ?: load())
                photos.value = sorted(list)
                result
            }
        }

    private fun load(): List<ProgressPhoto> {
        val names = files.list().toSet()
        val loaded = names.filter { it.endsWith(META_SUFFIX) }.mapNotNull { name ->
            val meta = files.read(name)?.let(::decode)
            val id = name.removeSuffix(META_SUFFIX)
            if (meta == null || meta.id != id || !isSafeId(id) || imageName(id) !in names) {
                // Unreadable metadata, or an image that is gone: the photo cannot be shown.
                files.delete(name)
                files.delete(thumbName(id))
                null
            } else {
                meta.toModel()
            }
        }
        val known = loaded.mapTo(HashSet()) { it.id }
        names.filterNot { it.endsWith(META_SUFFIX) }
            .filter { fileId(it) !in known }
            .forEach(files::delete) // interrupted adds and leftover temporary files
        return sorted(loaded)
    }

    private fun sorted(list: List<ProgressPhoto>) =
        list.sortedWith(compareByDescending<ProgressPhoto> { it.takenAt }.thenByDescending { it.addedAt }.thenBy { it.id })

    private fun encode(meta: PhotoMeta): ByteArray = json.encodeToString(PhotoMeta.serializer(), meta).encodeToByteArray()

    private fun decode(bytes: ByteArray): PhotoMeta? = try {
        json.decodeFromString(PhotoMeta.serializer(), bytes.decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun PhotoMeta.toModel() = ProgressPhoto(id = id, takenAt = takenAt, notes = notes, addedAt = addedAt)

    internal companion object {
        const val META_SUFFIX = ".json"
        const val IMAGE_SUFFIX = ".jpg"
        const val THUMB_SUFFIX = ".thumb.jpg"

        private val SAFE_ID = Regex("[A-Za-z0-9-]{1,64}")

        fun imageName(id: String) = "$id$IMAGE_SUFFIX"

        fun thumbName(id: String) = "$id$THUMB_SUFFIX"

        fun metaName(id: String) = "$id$META_SUFFIX"

        /** Ids become file names: only the characters of a UUID are accepted, never a path. */
        fun isSafeId(id: String): Boolean = SAFE_ID.matches(id)

        /** The photo id a file belongs to (the name up to its first dot). */
        fun fileId(name: String): String = name.substringBefore('.')
    }
}
