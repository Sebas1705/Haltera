package es.sebas1705.haltera.account

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.SettingsCloudBackupStore
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import es.sebas1705.haltera.data.sync.RecentLoginRequiredException
import es.sebas1705.haltera.data.sync.RemoteStore
import es.sebas1705.haltera.data.sync.SyncDocument
import es.sebas1705.haltera.data.sync.SyncEngine
import es.sebas1705.haltera.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeAccount(
    initial: AccountSession,
    /** How many deleteUser calls fail with "recent login required" before one succeeds. */
    var staleDeletes: Int = 0,
    var reauthFails: Boolean = false,
    /** True: signing in again does not make the credential fresh (the second deletion fails too). */
    var staysStale: Boolean = false,
) : AccountService {
    val calls = mutableListOf<String>()
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<AccountSession> = state

    override suspend fun ensureSignedIn() = Unit
    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Linked
    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = Unit
    override suspend fun signOut() {
        calls += "signOut"
        state.value = AccountSession.SignedOut
    }

    override suspend fun reauthenticate(credentials: GoogleIdToken) {
        calls += "reauthenticate"
        check(!reauthFails) { "wrong account" }
        if (!staysStale) staleDeletes = 0
    }

    override suspend fun deleteUser() {
        calls += "deleteUser"
        if (staleDeletes > 0) {
            staleDeletes--
            throw RecentLoginRequiredException("requires-recent-login")
        }
        state.value = AccountSession.SignedOut
    }
}

private class FakeRemote : RemoteStore {
    val deleted = mutableListOf<String>()
    override suspend fun push(uid: String, documents: List<SyncDocument>) = Unit
    override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> = emptyFlow()
    override suspend fun deleteAll(uid: String) {
        deleted += uid
    }
}

private class FakeEngine : SyncEngine {
    var forgotten = 0
    var markedDirty = 0
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Disabled)
    override fun start(scope: CoroutineScope) = Unit
    override suspend fun syncNow() = Unit
    override suspend fun markAllDirty() {
        markedDirty++
    }
    override suspend fun forgetAccount() {
        forgotten++
    }
}

private class FakeGoogle(var result: GoogleSignInResult) : GoogleSignInLauncher {
    var requests = 0
    override val isAvailable = true
    override suspend fun requestToken(): GoogleSignInResult {
        requests++
        return result
    }
}

private class FakeEraser : LocalDataEraser {
    var erased = 0
    override suspend fun erase() {
        erased++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val google = FakeGoogle(GoogleSignInResult.Token(GoogleIdToken("fresh")))
    private val remote = FakeRemote()
    private val engine = FakeEngine()
    private val eraser = FakeEraser()
    private val cloud = SettingsCloudBackupStore(MapSettings())

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private val googleUser = AccountSession.SignedIn("uid", isAnonymous = false, email = "a@b.c")
    private val anonymousUser = AccountSession.SignedIn("anon", isAnonymous = true)

    private fun viewModel(account: FakeAccount) = AccountViewModel(account, remote, google, engine, eraser, cloud)

    private fun AccountViewModel.delete() {
        onEvent(AccountIntent.RequestDelete)
        onEvent(AccountIntent.ConfirmDelete)
    }

    @Test
    fun `deletes the cloud data and the account without asking for a new sign-in`() {
        val account = FakeAccount(googleUser)
        val vm = viewModel(account)

        vm.delete()

        assertEquals(AccountMessage.DELETED, vm.uiState.value.message)
        assertEquals(listOf("uid"), remote.deleted)
        assertEquals(1, engine.forgotten)
        assertEquals(0, google.requests)
        assertEquals(listOf("deleteUser"), account.calls)
        assertEquals(0, eraser.erased)
    }

    @Test
    fun `an old sign-in asks for Google again and retries the deletion once`() {
        val account = FakeAccount(googleUser, staleDeletes = 1)
        val vm = viewModel(account)

        vm.delete()

        assertEquals(AccountMessage.DELETED, vm.uiState.value.message)
        assertEquals(1, google.requests)
        assertEquals(listOf("deleteUser", "reauthenticate", "deleteUser"), account.calls)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `a cancelled re-authentication leaves the account and does not retry`() {
        google.result = GoogleSignInResult.Cancelled
        val account = FakeAccount(googleUser, staleDeletes = 1)
        val vm = viewModel(account)

        vm.delete()

        assertEquals(AccountMessage.REAUTH_CANCELLED, vm.uiState.value.message)
        assertEquals(listOf("deleteUser"), account.calls)
        assertEquals(0, eraser.erased)
    }

    @Test
    fun `a failed re-authentication is reported and not retried`() {
        val account = FakeAccount(googleUser, staleDeletes = 1, reauthFails = true)
        val vm = viewModel(account)

        vm.delete()

        assertEquals(AccountMessage.FAILED, vm.uiState.value.message)
        assertEquals(listOf("deleteUser", "reauthenticate"), account.calls)
    }

    @Test
    fun `the deletion is retried only once`() {
        val account = FakeAccount(googleUser, staleDeletes = 5, staysStale = true)
        val vm = viewModel(account)

        vm.delete()

        assertEquals(AccountMessage.FAILED, vm.uiState.value.message)
        assertEquals(listOf("deleteUser", "reauthenticate", "deleteUser"), account.calls)
        assertEquals(0, eraser.erased)
    }

    @Test
    fun `an anonymous account needs no sign-in again`() {
        val account = FakeAccount(anonymousUser, staleDeletes = 1)
        val vm = viewModel(account)

        vm.delete()

        assertEquals(AccountMessage.DELETED, vm.uiState.value.message)
        assertEquals(0, google.requests)
        assertEquals(listOf("deleteUser", "signOut"), account.calls)
        assertEquals(listOf("anon"), remote.deleted)
    }

    @Test
    fun `the device data is erased only when the box is ticked`() {
        val vm = viewModel(FakeAccount(googleUser))
        vm.onEvent(AccountIntent.RequestDelete)
        assertFalse(vm.uiState.value.eraseLocal)
        vm.onEvent(AccountIntent.SetEraseLocal(true))
        vm.onEvent(AccountIntent.ConfirmDelete)

        assertEquals(1, eraser.erased)
        assertEquals(AccountMessage.DELETED, vm.uiState.value.message)
    }

    @Test
    fun `the erase box starts unticked every time the dialog opens`() {
        val vm = viewModel(FakeAccount(googleUser))
        vm.onEvent(AccountIntent.RequestDelete)
        vm.onEvent(AccountIntent.SetEraseLocal(true))
        vm.onEvent(AccountIntent.DismissDelete)
        vm.onEvent(AccountIntent.RequestDelete)

        assertFalse(vm.uiState.value.eraseLocal)
        assertTrue(vm.uiState.value.confirmDelete)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `the cloud copy starts off`() {
        val vm = viewModel(FakeAccount(AccountSession.SignedOut))
        assertFalse(vm.uiState.value.cloudBackup)
    }

    @Test
    fun `turning the switch on opts in and queues the existing data for upload`() {
        val vm = viewModel(FakeAccount(AccountSession.SignedOut))

        vm.onEvent(AccountIntent.SetCloudBackup(true))

        assertTrue(vm.uiState.value.cloudBackup)
        assertTrue(cloud.enabled.value)
        assertEquals(1, engine.markedDirty)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `turning the switch off asks first and keeps the cloud data by default`() {
        cloud.setEnabled(true)
        val account = FakeAccount(anonymousUser)
        val vm = viewModel(account)

        vm.onEvent(AccountIntent.SetCloudBackup(false))
        assertTrue(vm.uiState.value.confirmTurnOff)
        assertTrue(cloud.enabled.value) // nothing changes until confirmed
        assertFalse(vm.uiState.value.deleteCloud)

        vm.onEvent(AccountIntent.ConfirmTurnOff)

        assertFalse(cloud.enabled.value)
        assertEquals(AccountMessage.CLOUD_OFF, vm.uiState.value.message)
        assertTrue(remote.deleted.isEmpty())
        assertEquals(0, engine.forgotten)
        assertTrue(account.calls.isEmpty())
        assertEquals(0, eraser.erased)
    }

    @Test
    fun `cancelling the turn-off dialog leaves the cloud copy on`() {
        cloud.setEnabled(true)
        val vm = viewModel(FakeAccount(anonymousUser))
        vm.onEvent(AccountIntent.SetCloudBackup(false))
        vm.onEvent(AccountIntent.DismissTurnOff)

        assertFalse(vm.uiState.value.confirmTurnOff)
        assertTrue(cloud.enabled.value)
    }

    @Test
    fun `turning off can also delete the cloud data through the existing delete path and keeps local data`() {
        cloud.setEnabled(true)
        val account = FakeAccount(anonymousUser)
        val vm = viewModel(account)

        vm.onEvent(AccountIntent.SetCloudBackup(false))
        vm.onEvent(AccountIntent.SetDeleteCloud(true))
        vm.onEvent(AccountIntent.ConfirmTurnOff)

        assertFalse(cloud.enabled.value)
        assertEquals(AccountMessage.CLOUD_OFF_DELETED, vm.uiState.value.message)
        assertEquals(listOf("anon"), remote.deleted)
        assertEquals(1, engine.forgotten)
        assertEquals(listOf("deleteUser"), account.calls)
        assertEquals(0, eraser.erased)
    }

    @Test
    fun `the delete-data box starts unticked every time the turn-off dialog opens`() {
        cloud.setEnabled(true)
        val vm = viewModel(FakeAccount(anonymousUser))
        vm.onEvent(AccountIntent.SetCloudBackup(false))
        vm.onEvent(AccountIntent.SetDeleteCloud(true))
        vm.onEvent(AccountIntent.DismissTurnOff)
        vm.onEvent(AccountIntent.SetCloudBackup(false))

        assertFalse(vm.uiState.value.deleteCloud)
    }

    @Test
    fun `deleting the account also turns the cloud copy off so nothing signs in again`() {
        cloud.setEnabled(true)
        val vm = viewModel(FakeAccount(googleUser))

        vm.delete()

        assertEquals(AccountMessage.DELETED, vm.uiState.value.message)
        assertFalse(cloud.enabled.value)
    }
}
