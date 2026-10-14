package es.sebas1705.ironlog.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Contract between the account/sync workstreams: docs/designs/2026-10-14-sync-design.md §4 (fixed).

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

    /**
     * Emits batches of documents of [collection] changed after [sinceUpdatedAt], live. The first emission is
     * the current matching set, even when empty: the engine waits for it to finish its catch-up pass.
     */
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

    /** Follows [AccountService.session]: runs while SignedIn. */
    fun start(scope: CoroutineScope)

    suspend fun syncNow()

    /** Re-marks every local aggregate as dirty so it is uploaded under the current uid (merge after sign-in). */
    suspend fun markAllDirty()

    /**
     * Before deleting the account: stops syncing the current uid (Disabled) and forgets its local sync state
     * (dirty flags, cursors, uid), so nothing is re-uploaded, not even when another account signs in later.
     */
    suspend fun forgetAccount()
}

sealed interface AccountSession {
    /** Firebase is not configured in this build: sync stays off. */
    data object NotConfigured : AccountSession

    data object SignedOut : AccountSession

    data class SignedIn(
        val uid: String,
        val isAnonymous: Boolean,
        val email: String? = null,
        val displayName: String? = null,
    ) : AccountSession
}

/** A Google ID token from Credential Manager (Android) or GoogleSignIn (iOS, which also gives an access token). */
data class GoogleIdToken(val idToken: String, val accessToken: String? = null)

sealed interface LinkResult {
    /** The anonymous account was upgraded in place (same uid). */
    data object Linked : LinkResult

    /** That Google account already exists: sign in to it and merge. */
    data class AccountExists(val googleUid: String) : LinkResult

    data class Failed(val message: String) : LinkResult
}

sealed interface SyncStatus {
    data object Disabled : SyncStatus

    data class Idle(val lastSyncedAt: Long?) : SyncStatus

    data class Syncing(val pending: Long) : SyncStatus

    data class Offline(val pending: Long) : SyncStatus

    data class Error(val message: String, val pending: Long) : SyncStatus
}

/** Firestore collection names (design §3), in the order remote batches are applied (foreign keys). */
object SyncCollections {
    const val EXERCISES = "exercises"
    const val ROUTINES = "routines"
    const val SESSIONS = "sessions"
    const val BODY_WEIGHT = "bodyWeight"
    const val SETTINGS = "settings"

    /** The only document id of [SETTINGS]. */
    const val SETTINGS_ID = "user"

    val ALL: List<String> = listOf(EXERCISES, ROUTINES, SESSIONS, BODY_WEIGHT, SETTINGS)
}
