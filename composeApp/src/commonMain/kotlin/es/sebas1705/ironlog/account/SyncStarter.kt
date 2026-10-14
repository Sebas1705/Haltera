package es.sebas1705.ironlog.account

import co.touchlab.kermit.Logger
import es.sebas1705.ironlog.data.sync.AccountService
import es.sebas1705.ironlog.data.sync.AccountSession
import es.sebas1705.ironlog.data.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Signs in anonymously on first launch (silently, retrying while offline) and starts the sync
 * engine when there is one. Called once after the catalogue import; no-op without Firebase config.
 */
class SyncStarter(
    private val account: AccountService,
    private val engine: SyncEngine?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    fun start() {
        if (started || account.session.value == AccountSession.NotConfigured) return
        started = true
        scope.launch { ensureSignedIn() }
        engine?.start(scope)
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
