package es.sebas1705.haltera.account

import com.russhwolf.settings.MapSettings
import es.sebas1705.haltera.core.datastore.KeyValueSettingsRepository
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class SessionAccount(initial: AccountSession) : AccountService {
    val state = MutableStateFlow(initial)
    override val session: StateFlow<AccountSession> = state
    override suspend fun ensureSignedIn() = Unit
    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Linked
    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = Unit
    override suspend fun signOut() = Unit
    override suspend fun reauthenticate(credentials: GoogleIdToken) = Unit
    override suspend fun deleteUser() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShareNameDefaultTest {
    private val settings = KeyValueSettingsRepository(MapSettings())

    private fun TestScope.start(account: SessionAccount) =
        ShareNameDefault(account, settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler))).start()

    private fun google(name: String?) = AccountSession.SignedIn("u", isAnonymous = false, displayName = name)

    @Test
    fun `a linked Google account fills the unset name with its display name`() = runTest {
        start(SessionAccount(google("  Ana López ")))
        assertEquals("Ana López", settings.settings.value.shareName)
    }

    @Test
    fun `linking later fills it, anonymous or nameless sessions do not`() = runTest {
        val account = SessionAccount(AccountSession.NotConfigured)
        start(account)
        account.state.value = AccountSession.SignedOut
        account.state.value = AccountSession.SignedIn("anon", isAnonymous = true, displayName = "Ghost")
        account.state.value = google(null)
        account.state.value = google("   ")
        assertNull(settings.settings.value.shareName)

        account.state.value = google("Ana")
        assertEquals("Ana", settings.settings.value.shareName)
    }

    @Test
    fun `a name the user typed or cleared is never replaced`() = runTest {
        settings.setShareName("Sebas")
        val account = SessionAccount(google("Sebastián García"))
        start(account)
        assertEquals("Sebas", settings.settings.value.shareName)

        settings.setShareName("")
        account.state.value = google("Other")
        assertEquals("", settings.settings.value.shareName)
    }
}
