package es.sebas1705.haltera.account

import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fills "Your name for shared routines" with the Google display name the first time a linked (non-anonymous)
 * account is seen, as long as the user never set the name (null). A name the user typed, or cleared on purpose
 * (""), is never replaced. Called once after the catalogue import.
 */
class ShareNameDefault(
    private val account: AccountService,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            account.session.collect { session ->
                val name = (session as? AccountSession.SignedIn)
                    ?.takeUnless { it.isAnonymous }
                    ?.displayName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                if (name != null && settings.settings.value.shareName == null) settings.setShareName(name)
            }
        }
    }
}
