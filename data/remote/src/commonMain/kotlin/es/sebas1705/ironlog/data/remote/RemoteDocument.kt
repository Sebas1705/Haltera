package es.sebas1705.ironlog.data.remote

import es.sebas1705.ironlog.data.sync.SyncDocument
import kotlinx.serialization.Serializable

/**
 * A synced aggregate in Firestore, at `users/{uid}/{collection}/{id}` (design §3). `schema`
 * versions the payload format.
 */
@Serializable
internal data class RemoteDocument(
    val payload: String = "",
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
    val schema: Int = SCHEMA_VERSION,
)

internal const val SCHEMA_VERSION = 1

/** Tombstones never carry a payload. */
internal fun SyncDocument.toRemote() = RemoteDocument(
    payload = if (deleted) "" else payload,
    updatedAt = updatedAt,
    deleted = deleted,
)

internal fun RemoteDocument.toSync(collection: String, id: String) = SyncDocument(
    collection = collection,
    id = id,
    updatedAt = updatedAt,
    deleted = deleted,
    payload = if (deleted) "" else payload,
)

/** The user collections (design §3); clients cannot list subcollections, so deletion walks these. */
internal val SYNCED_COLLECTIONS = listOf("routines", "sessions", "bodyWeight", "exercises", "settings")

internal const val USERS = "users"
