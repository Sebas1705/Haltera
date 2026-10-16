package es.sebas1705.haltera.account

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.SettingsCloudBackupStore
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import es.sebas1705.haltera.data.sync.SyncEngine
import es.sebas1705.haltera.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class SessionOnlyAccount(initial: AccountSession) : AccountService {
    val state = MutableStateFlow(initial)
    override val session: StateFlow<AccountSession> = state
    override suspend fun ensureSignedIn() = Unit
    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Linked
    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = Unit
    override suspend fun signOut() = Unit
    override suspend fun reauthenticate(credentials: GoogleIdToken) = Unit
    override suspend fun deleteUser() = Unit
}

private class CountingEngine : SyncEngine {
    var markedDirty = 0
    override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Disabled)
    override fun start(scope: CoroutineScope) = Unit
    override suspend fun syncNow() = Unit
    override suspend fun markAllDirty() {
        markedDirty++
    }

    override suspend fun forgetAccount() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class CloudPromptViewModelTest {
    private val cloud = SettingsCloudBackupStore(MapSettings())
    private val engine = CountingEngine()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel(session: AccountSession) = CloudPromptViewModel(SessionOnlyAccount(session), engine, cloud)

    @Test
    fun `a fresh install is asked once and Not now is remembered`() {
        val vm = viewModel(AccountSession.SignedOut)
        assertEquals(CloudCard.PROMPT, vm.uiState.value.card)

        vm.onEvent(CloudPromptIntent.Decline)

        assertEquals(CloudCard.NONE, vm.uiState.value.card)
        assertFalse(cloud.enabled.value)
        assertEquals(0, engine.markedDirty)
        // Not asked again on the next launch.
        assertEquals(CloudCard.NONE, viewModel(AccountSession.SignedOut).uiState.value.card)
    }

    @Test
    fun `Yes opts in and queues what was logged before for upload`() {
        val vm = viewModel(AccountSession.SignedOut)

        vm.onEvent(CloudPromptIntent.Accept)

        assertTrue(cloud.enabled.value)
        assertEquals(1, engine.markedDirty)
        assertEquals(CloudCard.NONE, vm.uiState.value.card)
    }

    @Test
    fun `an install that was already syncing sees the notice once instead of the question`() {
        cloud.adoptExistingAccount()
        val vm = viewModel(AccountSession.SignedIn("u", isAnonymous = true))
        assertEquals(CloudCard.NOTICE, vm.uiState.value.card)

        vm.onEvent(CloudPromptIntent.DismissNotice)

        assertEquals(CloudCard.NONE, vm.uiState.value.card)
        assertTrue(cloud.enabled.value) // still syncing
        assertEquals(CloudCard.NONE, viewModel(AccountSession.SignedIn("u", isAnonymous = true)).uiState.value.card)
    }

    @Test
    fun `no card without a Firebase configuration`() {
        assertEquals(CloudCard.NONE, viewModel(AccountSession.NotConfigured).uiState.value.card)
    }

    @Test
    fun `a signed-in install is never asked before it is migrated`() {
        assertEquals(CloudCard.NONE, viewModel(AccountSession.SignedIn("u", isAnonymous = true)).uiState.value.card)
    }
}
