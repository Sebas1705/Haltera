package es.sebas1705.haltera.account

import co.touchlab.kermit.Logger
import es.sebas1705.haltera.core.datastore.CloudBackupStore
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Runs the optional cloud copy. Nothing touches Firebase (no sign-in, no Firestore) until the user turns the
 * cloud copy on: then it signs in anonymously (silently, retrying while offline) and the sync engine, which only
 * runs while the switch is on, follows the session. An install that was already signed in before the choice
 * existed keeps syncing ([CloudBackupStore.adoptExistingAccount]). Called once after the catalogue import;
 * no-op without Firebase config.
 */
class SyncStarter(
    private val account: AccountService,
    private val engine: SyncEngine,
    private val cloud: CloudBackupStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    fun start() {
        if (started || account.session.value == AccountSession.NotConfigured) return
        started = true
        // The persisted Firebase user is read locally; a signed-in install made its choice by syncing already.
        if (account.session.value is AccountSession.SignedIn) cloud.adoptExistingAccount()
        scope.launch { cloud.enabled.collectLatest { on -> if (on) ensureSignedIn() } }
        engine.start(scope)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun ensureSignedIn() {
        var attempt = 0
        while (true) {
            try {
                account.ensureSignedIn()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0) Logger.w(e) { "Anonymous sign-in failed; retrying" }
                delay(min(RETRY_BASE_MILLIS shl min(attempt++, MAX_SHIFT), RETRY_MAX_MILLIS))
            }
        }
    }

    private companion object {
        const val RETRY_BASE_MILLIS = 2_000L
        const val RETRY_MAX_MILLIS = 5 * 60 * 1_000L
        const val MAX_SHIFT = 10
    }
}
