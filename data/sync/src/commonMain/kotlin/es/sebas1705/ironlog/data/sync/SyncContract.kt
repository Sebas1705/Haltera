package es.sebas1705.ironlog.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// The contract between the sync engine (data:sync) and the remote side (data:remote):
// docs/designs/2026-10-14-sync-design.md §4. Keep it identical in both workstreams.

/** A synced aggregate as stored remotely. `payload` is "" when deleted. */
data class SyncDocument(
    val collection: String, // "routines" | "sessions" | "bodyWeight" | "exercises" | "settings"
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val payload: String,
)

/** Remote side, implemented with Firestore in `data:remote` (workstream A). */
interface RemoteStore {
    /** Upserts documents (tombstones included). Throws on failure; the engine retries. */
    suspend fun push(uid: String, documents: List<SyncDocument>)

    /** Emits batches of documents of [collection] changed after [sinceUpdatedAt], live. */
    fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>>

    /** Deletes every document of the user (account deletion). */
    suspend fun deleteAll(uid: String)
}

/** Who is signed in, implemented in `data:remote` with Firebase Auth (workstream A). */
interface AccountService {
    val session: StateFlow<AccountSession>

    /** Anonymous sign-in when signed out. */
    suspend fun ensureSignedIn()

    suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult

    suspend fun signInWithGoogle(credentials: GoogleIdToken)

    suspend fun signOut()

    suspend fun deleteUser()
}

/** The engine (workstream B). */
interface SyncEngine {
    val status: StateFlow<SyncStatus>

    /** Follows AccountService.session: runs while SignedIn. */
    fun start(scope: CoroutineScope)

    suspend fun syncNow()

    /** Re-marks every local aggregate as dirty so it is uploaded under the current uid (merge after sign-in). */
    suspend fun markAllDirty()
}

sealed interface AccountSession {
    /** No Firebase config in this build: sync stays off. */
    data object NotConfigured : AccountSession

    data object SignedOut : AccountSession

    data class SignedIn(
        val uid: String,
        val isAnonymous: Boolean,
        val email: String?,
        val displayName: String?,
    ) : AccountSession
}

/** Tokens returned by Google sign-in on the platform. */
data class GoogleIdToken(val idToken: String, val accessToken: String? = null)

sealed interface LinkResult {
    data object Linked : LinkResult

    /** The Google account already had a user: now signed in to it as [googleUid]. */
    data class AccountExists(val googleUid: String) : LinkResult

    data class Failed(val message: String) : LinkResult
}

sealed interface SyncStatus {
    data object Disabled : SyncStatus

    data class Idle(val lastSyncedAt: Long?) : SyncStatus

    data class Syncing(val pending: Int) : SyncStatus

    data class Offline(val pending: Int) : SyncStatus

    data class Error(val message: String, val pending: Int) : SyncStatus
}
