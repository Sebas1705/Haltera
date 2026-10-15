package es.sebas1705.haltera.account

import co.touchlab.kermit.Logger
import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import es.sebas1705.haltera.data.sync.RecentLoginRequiredException
import es.sebas1705.haltera.data.sync.RemoteStore
import es.sebas1705.haltera.data.sync.SyncEngine
import es.sebas1705.haltera.data.sync.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** One-off outcome shown under the Account section. */
enum class AccountMessage { LINKED, MERGED, SIGNED_IN, DELETED, REAUTH_CANCELLED, FAILED }

data class AccountState(
    val session: AccountSession = AccountSession.NotConfigured,
    /** Null only before the first status. */
    val syncStatus: SyncStatus? = null,
    val googleAvailable: Boolean = false,
    val busy: Boolean = false,
    val confirmDelete: Boolean = false,
    /** The "Also erase data on this device" checkbox of the delete dialog (default off). */
    val eraseLocal: Boolean = false,
    val message: AccountMessage? = null,
    val error: String? = null,
) : MVIBaseState

sealed interface AccountIntent : MVIBaseIntent {
    data object LinkGoogle : AccountIntent
    data object SignInWithGoogle : AccountIntent
    data object SignOut : AccountIntent
    data object RequestDelete : AccountIntent
    data object DismissDelete : AccountIntent
    data object ConfirmDelete : AccountIntent
    data class SetEraseLocal(val erase: Boolean) : AccountIntent
    data object DismissMessage : AccountIntent
}

/** Settings → Account (design §2, §6). */
class AccountViewModel(
    private val account: AccountService,
    private val remote: RemoteStore,
    private val google: GoogleSignInLauncher,
    private val engine: SyncEngine,
    private val eraser: LocalDataEraser,
) : MVIBaseViewModel<AccountState, AccountIntent>(
    AccountState(
        session = account.session.value,
        syncStatus = engine.status.value,
        googleAvailable = google.isAvailable
    ),
) {

    init {
        execute { account.session.collect { session -> updateState { it.copy(session = session) } } }
        execute { engine.status.collect { status -> updateState { it.copy(syncStatus = status) } } }
    }

    override fun intentHandler(intent: AccountIntent): Job = when (intent) {
        AccountIntent.LinkGoogle -> busy { withGoogleToken { token -> link(token) } }
        AccountIntent.SignInWithGoogle -> busy {
            withGoogleToken { token ->
                account.signInWithGoogle(token)
                engine.markAllDirty()
                AccountMessage.SIGNED_IN
            }
        }
        AccountIntent.SignOut -> busy {
            account.signOut()
            null
        }
        AccountIntent.RequestDelete -> execute { updateState { it.copy(confirmDelete = true, eraseLocal = false) } }
        is AccountIntent.SetEraseLocal -> execute { updateState { it.copy(eraseLocal = intent.erase) } }
        AccountIntent.DismissDelete -> execute { updateState { it.copy(confirmDelete = false) } }
        AccountIntent.ConfirmDelete -> {
            updateState { it.copy(confirmDelete = false) }
            busy { deleteAccount() }
        }
        AccountIntent.DismissMessage -> execute { updateState { it.copy(message = null, error = null) } }
    }

    private suspend fun link(token: GoogleIdToken): AccountMessage = when (val result = account.linkGoogle(token)) {
        LinkResult.Linked -> AccountMessage.LINKED
        is LinkResult.AccountExists -> {
            // Signed in to the existing Google account: upload local data under it (last writer wins).
            engine.markAllDirty()
            AccountMessage.MERGED
        }
        is LinkResult.Failed -> throw AccountException(result.message)
    }

    private suspend fun deleteAccount(): AccountMessage? {
        val session = account.session.value as? AccountSession.SignedIn ?: return null
        // Stop syncing this uid and drop its pending uploads first, so nothing is re-uploaded after the delete.
        engine.forgetAccount()
        remote.deleteAll(session.uid)
        val outcome = deleteUserReauthenticating(session)
        if (outcome != null) return outcome
        if (currentState.eraseLocal) eraser.erase()
        return AccountMessage.DELETED
    }

    /**
     * Deletes the user. When the sign-in is too old, a Google account signs in again once and the deletion is
     * retried once; an anonymous user has nothing to re-confirm, so it is just signed out (its cloud data is
     * already gone). Returns a message when the deletion did not complete, null when it did.
     */
    private suspend fun deleteUserReauthenticating(session: AccountSession.SignedIn): AccountMessage? {
        try {
            account.deleteUser()
        } catch (_: RecentLoginRequiredException) {
            if (session.isAnonymous) {
                account.signOut()
                return null
            }
            when (val result = google.requestToken()) {
                is GoogleSignInResult.Token -> {
                    account.reauthenticate(result.token)
                    account.deleteUser() // A second failure is reported as it is.
                }
                GoogleSignInResult.Cancelled -> return AccountMessage.REAUTH_CANCELLED
                is GoogleSignInResult.Failed -> throw AccountException(result.message)
            }
        }
        return null
    }

    private suspend fun withGoogleToken(block: suspend (GoogleIdToken) -> AccountMessage): AccountMessage? =
        when (val result = google.requestToken()) {
            is GoogleSignInResult.Token -> block(result.token)
            GoogleSignInResult.Cancelled -> null
            is GoogleSignInResult.Failed -> throw AccountException(result.message)
        }

    /** Runs an account action with a progress state; failures become an error line, not a crash. */
    @Suppress("TooGenericExceptionCaught")
    private fun busy(block: suspend () -> AccountMessage?): Job = execute {
        if (currentState.busy) return@execute
        updateState { it.copy(busy = true, message = null, error = null) }
        val (message, error) = try {
            block() to null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Account action failed" }
            AccountMessage.FAILED to (e.message ?: e::class.simpleName.orEmpty())
        }
        updateState { it.copy(busy = false, message = message, error = error) }
    }
}

private class AccountException(message: String) : Exception(message)
