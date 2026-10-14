package es.sebas1705.ironlog.feature.settings

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent

sealed interface SettingsIntent : MVIBaseIntent {
    data object Load                      : SettingsIntent
    data class  SetDarkMode(val on: Boolean) : SettingsIntent
    data class  SetLanguage(val code: String): SettingsIntent
    data class  SaveBaseUrl(val url: String) : SettingsIntent
    data class  SaveToken(val token: String) : SettingsIntent
    data object Logout                    : SettingsIntent
    data object ClearSuccess              : SettingsIntent
    data object ClearError                : SettingsIntent
}
