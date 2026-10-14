package es.sebas1705.ironlog.feature.settings

import es.sebas1705.ironlog.core.common.mvi.MVIBaseState

data class SettingsState(
    val isDarkMode   : Boolean = false,
    val language     : String  = "es",
    val baseUrl      : String  = "",
    val tokenMasked  : String  = "",
    val isLoading    : Boolean = false,
    val saveSuccess  : Boolean = false,
    val error        : String? = null,
) : MVIBaseState
