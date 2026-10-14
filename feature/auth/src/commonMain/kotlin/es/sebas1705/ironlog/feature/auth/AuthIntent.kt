package es.sebas1705.ironlog.feature.auth

import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent

sealed interface AuthIntent : MVIBaseIntent {
    data class Login(val token: String) : AuthIntent
    data object LoginAnonymous          : AuthIntent
    data object Logout                  : AuthIntent
    data object CheckAuth               : AuthIntent
    data object ClearError              : AuthIntent
}
