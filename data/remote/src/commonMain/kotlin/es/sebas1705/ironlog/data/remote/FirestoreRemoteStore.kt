package es.sebas1705.ironlog.data.remote

import dev.gitlive.firebase.firestore.ChangeType
import dev.gitlive.firebase.firestore.CollectionReference
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FirebaseFirestore
import es.sebas1705.ironlog.data.sync.RemoteStore
import es.sebas1705.ironlog.data.sync.SyncDocument
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Thrown when Firestore does not confirm a write in time (offline): the engine retries later. */
class RemoteUnavailableException(message: String) : Exception(message)

/** [RemoteStore] on Cloud Firestore: one document per aggregate root under `users/{uid}`. */
class FirestoreRemoteStore(
    private val firestore: FirebaseFirestore,
    private val timeout: Duration = 30.seconds,
) : RemoteStore {

    override suspend fun push(uid: String, documents: List<SyncDocument>) {
        documents.chunked(MAX_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc ->
                batch.set(collection(uid, doc.collection).document(doc.id), RemoteDocument.serializer(), doc.toRemote())
            }
            commit("push") { batch.commit() }
        }
    }

    override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> =
        collection(uid, collection)
            .where { "updatedAt" greaterThan sinceUpdatedAt }
            .orderBy("updatedAt", Direction.ASCENDING)
            .snapshots
            // The SDK callback must never drop a snapshot: each one carries only its changes.
            .buffer(Channel.UNLIMITED)
            .map { snapshot ->
                snapshot.documentChanges
                    .filter { it.type != ChangeType.REMOVED }
                    .map { it.document.data(RemoteDocument.serializer()).toSync(collection, it.document.id) }
            }
            .filter { it.isNotEmpty() }

    override suspend fun deleteAll(uid: String) {
        SYNCED_COLLECTIONS.forEach { name ->
            val documents = collection(uid, name).get().documents
            documents.chunked(MAX_BATCH).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                commit("deleteAll") { batch.commit() }
            }
        }
    }

    private fun collection(uid: String, name: String): CollectionReference =
        firestore.collection(USERS).document(uid).collection(name)

    // Offline, a write never completes (the SDK queues it): fail so the engine keeps it dirty.
    private suspend fun commit(what: String, block: suspend () -> Unit) {
        withTimeoutOrNull(timeout) { block() } ?: throw RemoteUnavailableException("Firestore $what timed out")
    }

    private companion object {
        /** Firestore allows 500 writes per batch. */
        const val MAX_BATCH = 450
    }
}
