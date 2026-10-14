package es.sebas1705.ironlog.feature.auth

import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.security.SecureStorage
import kotlinx.coroutines.Job

private const val TOKEN_KEY = "auth_token"

class AuthViewModel(
    private val secureStorage: SecureStorage,
) : MVIBaseViewModel<AuthState, AuthIntent>() {

    init { onEvent(AuthIntent.CheckAuth) }

    override fun initState() = AuthState()

    override fun intentHandler(intent: AuthIntent): Job = when (intent) {
        AuthIntent.CheckAuth       -> checkAuth()
        is AuthIntent.Login        -> login(intent.token)
        AuthIntent.LoginAnonymous  -> loginAnonymous()
        AuthIntent.Logout          -> logout()
        AuthIntent.ClearError      -> execute { updateState { it.copy(error = null) } }
    }

    private fun checkAuth() = execute {
        val token = secureStorage.getString(TOKEN_KEY)
        updateState {
            it.copy(
                status    = if (token.isNotBlank()) AuthStatus.Authenticated else AuthStatus.Unauthenticated,
                isLoading = false,
            )
        }
    }

    private fun login(token: String) = execute {
        updateState { it.copy(isLoading = true, error = null) }
        try {
            if (token.isBlank()) error("Token vacío")
            secureStorage.putString(TOKEN_KEY, token)
            updateState { it.copy(isLoading = false, status = AuthStatus.Authenticated) }
        } catch (e: Exception) {
            updateState { it.copy(isLoading = false, error = e.message ?: "Error de autenticación") }
        }
    }

    private fun loginAnonymous() = execute {
        secureStorage.putString(TOKEN_KEY, "anonymous")
        updateState { it.copy(status = AuthStatus.Authenticated) }
    }

    private fun logout() = execute {
        secureStorage.remove(TOKEN_KEY)
        updateState { it.copy(status = AuthStatus.Unauthenticated) }
    }
}
