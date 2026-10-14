package es.sebas1705.ironlog.feature.settings

import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.AppPreferences
import es.sebas1705.ironlog.core.security.SecureStorage
import kotlinx.coroutines.Job

private const val TOKEN_KEY   = "auth_token"
private const val BASE_URL_KEY = "base_url"

class SettingsViewModel(
    private val prefs  : AppPreferences,
    private val secure : SecureStorage,
) : MVIBaseViewModel<SettingsState, SettingsIntent>() {

    init { onEvent(SettingsIntent.Load) }

    override fun initState() = SettingsState()

    override fun intentHandler(intent: SettingsIntent): Job = when (intent) {
        SettingsIntent.Load              -> load()
        is SettingsIntent.SetDarkMode    -> execute { prefs.setDarkMode(intent.on); updateState { it.copy(isDarkMode = intent.on) } }
        is SettingsIntent.SetLanguage    -> execute { prefs.setLanguage(intent.code); updateState { it.copy(language = intent.code) } }
        is SettingsIntent.SaveBaseUrl    -> execute {
            secure.putString(BASE_URL_KEY, intent.url)
            updateState { it.copy(baseUrl = intent.url, saveSuccess = true) }
        }
        is SettingsIntent.SaveToken      -> execute {
            secure.putString(TOKEN_KEY, intent.token)
            val masked = if (intent.token.length > 8) "••••••${intent.token.takeLast(4)}" else "••••"
            updateState { it.copy(tokenMasked = masked, saveSuccess = true) }
        }
        SettingsIntent.Logout            -> execute {
            secure.remove(TOKEN_KEY)
            updateState { it.copy(tokenMasked = "") }
        }
        SettingsIntent.ClearSuccess      -> execute { updateState { it.copy(saveSuccess = false) } }
        SettingsIntent.ClearError        -> execute { updateState { it.copy(error = null) } }
    }

    private fun load() = execute {
        val token  = secure.getString(TOKEN_KEY)
        val url    = secure.getString(BASE_URL_KEY)
        val masked = if (token.length > 8) "••••••${token.takeLast(4)}" else if (token.isNotBlank()) "••••" else ""
        updateState { it.copy(baseUrl = url, tokenMasked = masked) }
    }
}
