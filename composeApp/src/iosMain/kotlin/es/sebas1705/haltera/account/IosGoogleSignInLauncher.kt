package es.sebas1705.haltera.account

import es.sebas1705.haltera.data.sync.GoogleIdToken
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google sign-in implemented in Swift with the GoogleSignIn SDK (iosApp/iosApp/GoogleSignIn.swift):
 * its API is Swift-first, so the app hands an implementation of this interface to
 * [es.sebas1705.haltera.startHalteraKoin].
 */
interface GoogleSignInBridge {
    /** False until Google is enabled in Firebase and the app has its URL scheme. */
    val isConfigured: Boolean

    /** Exactly one call of [onResult]: tokens, or no token and an error (null error: cancelled). */
    fun requestTokens(onResult: (idToken: String?, accessToken: String?, error: String?) -> Unit)
}

class IosGoogleSignInLauncher(private val bridge: GoogleSignInBridge?) : GoogleSignInLauncher {
    override val isAvailable: Boolean get() = bridge?.isConfigured == true

    override suspend fun requestToken(): GoogleSignInResult {
        val bridge = bridge ?: return GoogleSignInResult.Failed("Google sign-in is not configured")
        return suspendCancellableCoroutine { cont ->
            bridge.requestTokens { idToken, accessToken, error ->
                val result = when {
                    idToken != null -> GoogleSignInResult.Token(GoogleIdToken(idToken, accessToken))
                    error == null -> GoogleSignInResult.Cancelled
                    else -> GoogleSignInResult.Failed(error)
                }
                if (cont.isActive) cont.resume(result)
            }
        }
    }
}
