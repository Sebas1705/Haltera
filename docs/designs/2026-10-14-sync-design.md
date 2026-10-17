# Haltera — account and sync design (F8)

- **Date:** 2026-10-14
- **Status:** approved by the owner ("sync completa"). Built in parallel by two workstreams that meet at the contract in §4.
- **Principle:** local-first. The app works fully offline; the cloud is a copy of the user's data so nothing is lost on uninstall or a new phone.

## 1. Firebase

Same project `ironlog-sebas1705` (Spark plan). Authentication with **Anonymous** and **Google** providers; **Firestore** in `eur3`. Config files (`google-services.json`, `GoogleService-Info.plist`) live in Doppler `ironlog/prd` (`GOOGLE_SERVICES_JSON`, `GOOGLE_SERVICE_INFO_PLIST`), are written by `scripts/doppler-sync-local.sh` locally and by the build-release action in CI, and are git-ignored. Without them the app builds and runs, sync simply stays off. SDK: GitLive Firebase KMP 2.7.0 (same as Owlsplit).

## 2. Account

- First launch signs in anonymously, silently. Data syncs to that uid from the start.
- Settings → Account: **Link Google** (Credential Manager on Android, GoogleSignIn on iOS) upgrades the anonymous account in place (same uid, nothing to migrate). If that Google account already exists, sign in to it instead and **merge**: local data is re-uploaded under the Google uid (last-writer-wins per document), then the local anonymous uid is dropped.
- Shows sync status (synced / pending N / offline / error) and last sync time; **Sign out** (keeps local data, stops sync) and **Delete account** (deletes the user's Firestore data and the auth user; required by Play).

## 3. Data in Firestore

Everything under `users/{uid}/{collection}/{docId}`; the bundled exercise catalogue is never synced. One document per **aggregate root**:

| Collection | Doc id | Payload (JSON of the aggregate) |
|---|---|---|
| `routines` | routine id | routine + its days + their slots |
| `sessions` | session id | session + its session exercises + their sets (schema 8 adds each exercise's optional `bilateral`, the "Both hands" snapshot of core design §3.3; a document without it is decoded with the flag of the receiver's copy of the slot, 0 without one, which is what older versions summed) |
| `bodyWeight` | entry id | one entry |
| `exercises` | USER exercise id | a custom exercise + its variants (schema 6 adds the optional `author` and `originId`; core design §3.1.1) |
| `settings` | `user` | user settings (units, bar, plates, toggles) and the active routine id |

Every document: `{ payload: <json string>, updatedAt: <epoch ms>, deleted: <bool>, schema: 1 }`. Deletes are tombstones (`deleted = true`, empty payload). Conflict rule: **last writer wins per document by `updatedAt`**. Security rules: a signed-in user reads/writes only `users/{request.auth.uid}/**`; tested with the Firestore emulator in CI (`firebase/rules-test`, as in Owlsplit).

Custom exercises (core design §3.1.1): deleting an exercise nothing references writes an `exercises` tombstone; a
soft delete (the exercise has history) is an ordinary update with `deprecated = 1`, and so is a restore. Applying a
tombstone, or an edit that drops a variant, never deletes a variant that a session on the receiving phone
references: that variant (or the exercise) is deprecated instead, so the session keeps its foreign key and syncs as
before (`AggregateStore`, `CustomExerciseSyncTest`).

## 4. Contract between the workstreams (fixed)

Package `es.sebas1705.haltera.data.sync` in module **`data:sync`** (new; depends on `data:local`, `data:models`; the `data:repository` implementations stay unaware of sync):

```kotlin
/** A synced aggregate as stored remotely. `payload` is "" when deleted. */
data class SyncDocument(
    val collection: String,   // "routines" | "sessions" | "bodyWeight" | "exercises" | "settings"
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
    val session: StateFlow<AccountSession>   // NotConfigured | SignedOut | SignedIn(uid, isAnonymous, email?, displayName?)
    suspend fun ensureSignedIn()              // anonymous sign-in when signed out
    suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult  // Linked | AccountExists(googleUid) | Failed(message)
    suspend fun signInWithGoogle(credentials: GoogleIdToken)
    suspend fun signOut()
    suspend fun deleteUser()
}

/** The engine (workstream B). */
interface SyncEngine {
    val status: StateFlow<SyncStatus>         // Disabled | Idle(lastSyncedAt) | Syncing(pending) | Offline(pending) | Error(message, pending)
    fun start(scope: CoroutineScope)          // follows AccountService.session: runs while SignedIn
    suspend fun syncNow()
    /** Re-marks every local aggregate as dirty so it is uploaded under the current uid (merge after sign-in). */
    suspend fun markAllDirty()
}
```

`AccountSession`, `GoogleIdToken`, `LinkResult` and `SyncStatus` are small types in the same package. Koin: workstream B provides `syncModule` (engine, local change tracking) and binds nothing remote; workstream A provides `remoteModule` (RemoteStore, AccountService; a no-op `NotConfigured` implementation when Firebase is not configured) — `composeApp` loads both and starts the engine after the catalogue import.

## 5. Local change tracking (workstream B)

No changes to existing tables or repositories. A new `.sq` file adds:

- `SyncState(collection, id, updatedAt, dirty, deleted)` — one row per local aggregate root.
- `SyncCursor(collection, sinceUpdatedAt)` — download cursor per collection.
- **SQLite triggers** on every synced table (insert/update/delete on routines, days, slots, sessions, session exercises, sets, body weight, USER exercises/variants) that upsert the owning aggregate root's `SyncState` row with `dirty = 1`, `updatedAt = now` (and `deleted = 1` when the root itself is deleted). Triggers are skipped while applying remote changes: `WHEN (SELECT value FROM Meta WHERE key = 'syncApplying') IS NOT '1'`.
- Settings changes are tracked by the settings repository hook (multiplatform-settings is not SQLite): workstream B adds a small `SettingsSyncBridge` that observes `SettingsRepository.settings` and marks `settings/user` dirty.

Engine: upload dirty roots (serialize the aggregate from SQLite → payload; batch, exponential backoff, keep `dirty` until acknowledged and unchanged since), download via `RemoteStore.changes` per collection from its cursor, apply in one transaction with `syncApplying = '1'` (replace the aggregate's rows; tombstones delete), skip incoming documents older than the local `updatedAt` of a dirty root (LWW). Fully unit-tested with an in-memory `RemoteStore` fake: round trip between two databases, LWW, tombstones, offline queueing, triggers coverage of every table, no echo loops.

## 6. Account UI (workstream A)

Settings → Account section in `composeApp` settings: status line from `SyncEngine.status`, Link Google / Sign in with Google, Sign out, Delete account (confirm). Android Google sign-in with Credential Manager (`androidx.credentials` + `googleid`, web client id from `google-services.json` `oauth_client` type 3); iOS via the GoogleSignIn SPM package bridged from Swift (as Owlsplit), behind an interface; if not configured, the button is hidden.

## 7. Out of scope now

Sharing between users, web, push. Privacy policy page and Play data-safety form are prepared with the Play listing.
