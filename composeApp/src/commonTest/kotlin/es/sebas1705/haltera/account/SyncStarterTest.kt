package es.sebas1705.haltera.account

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.CloudBackupState
import es.sebas1705.haltera.core.datastore.SettingsCloudBackupStore
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import es.sebas1705.haltera.data.sync.SyncEngine
import es.sebas1705.haltera.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingAccount(initial: AccountSession, private val failTimes: Int = 0) : AccountService {
    val state = MutableStateFlow(initial)
    override val session: StateFlow<AccountSession> = state
    var signInAttempts = 0

    override suspend fun ensureSignedIn() {
        signInAttempts++
        check(signInAttempts > failTimes) { "offline" }
        if (state.value == AccountSession.SignedOut) state.value = AccountSession.SignedIn("anon", isAnonymous = true)
    }

    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Linked
    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = Unit
    override suspend fun signOut() = Unit
    override suspend fun reauthenticate(credentials: GoogleIdToken) = Unit
    override suspend fun deleteUser() = Unit
}

private class RecordingEngine : SyncEngine {
    var starts = 0
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Disabled)
    override fun start(scope: CoroutineScope) {
        starts++
    }

    override suspend fun syncNow() = Unit
    override suspend fun markAllDirty() = Unit
    override suspend fun forgetAccount() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncStarterTest {
    private val cloud = SettingsCloudBackupStore(MapSettings())
    private val engine = RecordingEngine()

    private fun TestScope.starter(account: RecordingAccount) =
        SyncStarter(account, engine, cloud, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `a fresh install makes no sign-in before the user opts in`() = runTest {
        val account = RecordingAccount(AccountSession.SignedOut)

        starter(account).start()
        advanceTimeBy(10 * 60 * 1_000L)
        runCurrent()

        assertEquals(0, account.signInAttempts)
        assertEquals(AccountSession.SignedOut, account.state.value)
        assertEquals(CloudBackupState(), cloud.state.value)
        assertEquals(1, engine.starts) // the engine runs but is gated by the same switch
    }

    @Test
    fun `opting in signs in anonymously`() = runTest {
        val account = RecordingAccount(AccountSession.SignedOut)
        starter(account).start()

        cloud.setEnabled(true)
        runCurrent()

        assertEquals(1, account.signInAttempts)
        assertTrue(account.state.value is AccountSession.SignedIn)
    }

    @Test
    fun `opting in while offline keeps retrying until the sign-in works`() = runTest {
        val account = RecordingAccount(AccountSession.SignedOut, failTimes = 2)
        starter(account).start()

        cloud.setEnabled(true)
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(3, account.signInAttempts)
        assertTrue(account.state.value is AccountSession.SignedIn)
    }

    @Test
    fun `turning it off while retrying stops the sign-in attempts`() = runTest {
        val account = RecordingAccount(AccountSession.SignedOut, failTimes = 100)
        starter(account).start()
        cloud.setEnabled(true)
        runCurrent()
        cloud.setEnabled(false)
        val attempts = account.signInAttempts

        advanceTimeBy(10 * 60 * 1_000L)
        runCurrent()

        assertEquals(attempts, account.signInAttempts)
    }

    @Test
    fun `an install that is already signed in keeps syncing and gets the one-time notice`() = runTest {
        val account = RecordingAccount(AccountSession.SignedIn("anon", isAnonymous = true))

        starter(account).start()
        runCurrent()

        assertEquals(CloudBackupState(enabled = true, promptHandled = true, noticePending = true), cloud.state.value)
        assertEquals(1, account.signInAttempts) // ensureSignedIn is a no-op for a signed-in user
        assertEquals(AccountSession.SignedIn("anon", isAnonymous = true), account.state.value)
    }

    @Test
    fun `a signed-in install that already chose off stays off`() = runTest {
        cloud.setEnabled(false)
        val account = RecordingAccount(AccountSession.SignedIn("anon", isAnonymous = true))

        starter(account).start()
        runCurrent()

        assertFalse(cloud.enabled.value)
        assertFalse(cloud.state.value.noticePending)
        assertEquals(0, account.signInAttempts)
    }

    @Test
    fun `nothing starts without a Firebase configuration`() = runTest {
        val account = RecordingAccount(AccountSession.NotConfigured)
        cloud.setEnabled(true)

        starter(account).start()
        runCurrent()

        assertEquals(0, account.signInAttempts)
        assertEquals(0, engine.starts)
    }
}
