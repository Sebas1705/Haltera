package es.sebas1705.ironlog.feature.auth

import es.sebas1705.ironlog.core.common.mvi.MVIBaseState

sealed interface AuthStatus {
    data object Checking        : AuthStatus
    data object Unauthenticated : AuthStatus
    data object Authenticated   : AuthStatus
}

data class AuthState(
    val status    : AuthStatus = AuthStatus.Checking,
    val isLoading : Boolean    = false,
    val error     : String?    = null,
) : MVIBaseState
