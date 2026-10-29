package es.sebas1705.haltera.data.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import co.touchlab.kermit.Logger
import es.sebas1705.haltera.data.local.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Local-first sync engine (design §5).
 *
 * While the account is SignedIn: a first pass downloads every collection from its cursor (in foreign-key
 * order) and then uploads the dirty roots; after it, one live listener per collection applies remote changes
 * and the upload loop pushes local changes as they appear, with exponential backoff on failure.
 *
 * Conflicts: a remote document is applied only when it is newer than the local root (last writer wins by
 * `updatedAt`); an older or equal one is our own echo or stale. A dirty root is cleared only when its
 * `updatedAt` did not change while it was being uploaded.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class SyncEngineImpl(
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val account: AccountService,
    private val settings: SettingsSyncBridge,
    private val io: CoroutineDispatcher,
    private val now: () -> Long,
    private val batchSize: Int = BATCH_SIZE,
    /** The cloud copy switch: while false the engine does not touch the network even if a user is signed in. */
    private val enabled: StateFlow<Boolean> = MutableStateFlow(true),
) : SyncEngine {
    private val store = AggregateStore(db, settings)
    private val q get() = db.syncTrackingQueries
    private val lock = Mutex()
    private val state = MutableStateFlow<SyncStatus>(SyncStatus.Disabled)
    override val status: StateFlow<SyncStatus> = state.asStateFlow()

    private var started = false
    private var currentUid: String? = null
    private var lastSyncedAt: Long? = null
    private var failures = 0
    private val forgotten = MutableStateFlow<String?>(null)

    override fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch(io) {
            combine(
                account.session,
                forgotten,
                enabled,
            ) { session, gone, on ->
                session.takeIf { on && !(session is AccountSession.SignedIn && session.uid == gone) }
            }
                .collectLatest { session ->
                    if (session is AccountSession.SignedIn) {
                        runGuarded(session.uid)
                    } else {
                        currentUid = null
                        state.value = SyncStatus.Disabled
                    }
                }
        }
    }

    /** A sync failure must never take the app down: it becomes the Error status. */
    private suspend fun runGuarded(uid: String) {
        try {
            run(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failures = ERROR_AFTER_FAILURES
            failed(e)
        }
    }

    private suspend fun run(uid: String) = coroutineScope {
        prepare(uid)
        currentUid = uid
        state.value = SyncStatus.Syncing(pending())
        launch { settings.observe() }
        pass(uid)
        SyncCollections.ALL.forEach { collection -> launch { listen(uid, collection) } }
        launch { uploadLoop(uid) }
    }

    override suspend fun syncNow() {
        if (!enabled.value) return
        val uid = currentUid ?: (account.session.value as? AccountSession.SignedIn)?.uid ?: return
        if (uid == forgotten.value) return
        withContext(io) {
            prepare(uid)
            pass(uid)
        }
    }

    override suspend fun markAllDirty() = withContext(io) {
        db.transaction {
            backfill()
            q.markAllDirty()
        }
    }

    override suspend fun forgetAccount() {
        forgotten.value = currentUid ?: (account.session.value as? AccountSession.SignedIn)?.uid
        withContext(io) {
            lock.withLock {
                db.transaction {
                    q.clearAllDirty()
                    q.resetCursors()
                    db.metaQueries.put(META_UID, "") // no previous account: the next one does not merge this data
                }
            }
        }
        currentUid = null
        state.value = SyncStatus.Disabled
    }

    /** First run: tracks the data logged before sync existed. Another uid: merges everything into it. */
    internal fun prepare(uid: String) {
        db.transaction {
            if (db.metaQueries.get(META_BACKFILLED).executeAsOneOrNull() != "1") {
                backfill()
                db.metaQueries.put(META_BACKFILLED, "1")
            }
            val previous = db.metaQueries.get(META_UID).executeAsOneOrNull()?.takeIf { it.isNotEmpty() }
            if (previous != uid) {
                if (previous != null) {
                    q.resetCursors()
                    q.markAllDirty()
                }
                db.metaQueries.put(META_UID, uid)
            }
        }
    }

    private fun backfill() {
        q.backfillRoutines()
        q.backfillSessions()
        q.backfillBodyWeight()
        q.backfillExercises()
        q.backfillFoods()
        q.backfillMealDays()
        // settings/user also carries the active routine.
        val active = db.routineEntityQueries.selectActive().executeAsOneOrNull() != null
        if (active || !settings.isDefault()) q.backfillSettings()
    }

    /** Download every collection from its cursor, then upload. */
    private suspend fun pass(uid: String) {
        lock.withLock {
            try {
                SyncCollections.ALL.forEach { collection ->
                    val first = withTimeoutOrNull(
                        FIRST_BATCH_TIMEOUT_MILLIS
                    ) { remote.changes(uid, collection, since(collection)).first() }
                    receive(collection, first ?: throw RemoteTimeoutException(collection))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                failed(e)
                return
            }
        }
        upload(uid)
    }

    private suspend fun listen(uid: String, collection: String) {
        var attempt = 0
        while (true) {
            try {
                remote.changes(uid, collection, since(collection)).collect { docs ->
                    attempt = 0
                    lock.withLock { receive(collection, docs) }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                failed(e)
                delay(backoff(attempt++))
            }
        }
    }

    private suspend fun uploadLoop(uid: String) {
        val loop = currentCoroutineContext().job // cancelled only when sync stops (sign-out, cloud copy off)
        q.countDirty().asFlow().mapToOne(io).collectLatest { pending ->
            if (pending == 0L) return@collectLatest
            var attempt = 0
            // Every acknowledged batch clears dirty flags, which changes the count and makes collectLatest cancel
            // this block while the next batch awaits its ack; so does every set logged meanwhile. The server keeps
            // a write it already accepted, so a cancelled pass re-pushes it, and steady logging starved the upload.
            // A pass therefore always finishes (each push has its own timeout) and stops between batches only when
            // sync stops; just the backoff wait is cancelled by a new count (docs/adr/0001).
            while (!withContext(NonCancellable) { upload(uid) { loop.isActive } }) delay(backoff(attempt++))
        }
    }

    /** Applies remote documents newer than the local roots and advances the cursor. */
    internal fun receive(collection: String, docs: List<SyncDocument>) {
        var cursor = q.selectCursor(collection).executeAsOneOrNull() ?: 0L
        for (doc in docs.sortedBy { it.updatedAt }) {
            val local = q.selectState(doc.collection, doc.id).executeAsOneOrNull()
            if (local == null || doc.updatedAt > local.updatedAt) {
                try {
                    store.apply(doc)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // E.g. a session whose USER variant has not arrived: retried from this cursor next pass.
                    Logger.w(e) { "Sync: could not apply ${doc.collection}/${doc.id}" }
                    break
                }
            }
            cursor = maxOf(cursor, doc.updatedAt)
        }
        q.putCursor(collection, cursor)
    }

    /**
     * Uploads every dirty root, checking [keepGoing] before each batch. False on failure (the caller backs off).
     */
    internal suspend fun upload(uid: String, keepGoing: () -> Boolean = { true }): Boolean = lock.withLock {
        if (uid == forgotten.value) return@withLock true
        try {
            var rounds = 0
            while (rounds++ < MAX_ROUNDS && keepGoing() && uid != forgotten.value) {
                val dirty = q.selectDirty(batchSize.toLong()).executeAsList()
                if (dirty.isEmpty()) break
                state.value = SyncStatus.Syncing(pending())
                val docs = dirty.map { s ->
                    val payload = if (s.deleted == 1L) null else store.read(s.collection, s.id)
                    SyncDocument(
                        s.collection,
                        s.id,
                        s.updatedAt,
                        deleted = payload == null,
                        payload = payload.orEmpty()
                    )
                }
                remote.push(uid, docs)
                db.transaction { docs.forEach { q.clearDirty(it.collection, it.id, it.updatedAt) } }
            }
            failures = 0
            lastSyncedAt = now()
            state.value = if (pending() == 0L) SyncStatus.Idle(lastSyncedAt) else SyncStatus.Syncing(pending())
            true
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            failed(e)
            false
        }
    }

    /** Offline while retrying; Error once it keeps failing. */
    private fun failed(e: Exception) {
        failures++
        Logger.w(e) { "Sync failed ($failures)" }
        state.value = if (failures < ERROR_AFTER_FAILURES) {
            SyncStatus.Offline(pending())
        } else {
            SyncStatus.Error(e.message ?: e::class.simpleName.orEmpty(), pending())
        }
    }

    private fun pending(): Long = q.countDirty().executeAsOne()

    /** The cursor, minus a look-back window: a device that was offline may push documents dated earlier. */
    private fun since(collection: String): Long =
        q.selectCursor(collection).executeAsOneOrNull()?.let { (it - CURSOR_LOOKBACK_MILLIS).coerceAtLeast(0) } ?: 0L

    private fun backoff(attempt: Int): Long =
        (BACKOFF_BASE_MILLIS shl attempt.coerceAtMost(MAX_BACKOFF_SHIFT)).coerceAtMost(BACKOFF_MAX_MILLIS)

    internal companion object {
        const val BATCH_SIZE = 100
        const val META_BACKFILLED = "syncBackfilled"
        const val META_UID = "syncUid"
        const val CURSOR_LOOKBACK_MILLIS = 7L * 24 * 60 * 60 * 1_000
        private const val MAX_ROUNDS = 1_000
        private const val ERROR_AFTER_FAILURES = 5
        private const val FIRST_BATCH_TIMEOUT_MILLIS = 60_000L
        private const val BACKOFF_BASE_MILLIS = 1_000L
        private const val BACKOFF_MAX_MILLIS = 5 * 60 * 1_000L
        private const val MAX_BACKOFF_SHIFT = 9
    }
}

/** The remote did not deliver the current state of a collection in time (offline, or a stuck listener). */
internal class RemoteTimeoutException(collection: String) : Exception("No answer from the server for $collection")
