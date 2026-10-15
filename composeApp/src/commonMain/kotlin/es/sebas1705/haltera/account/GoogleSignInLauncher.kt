package es.sebas1705.haltera.account

import es.sebas1705.haltera.data.sync.GoogleIdToken

/**
 * Google sign-in UI of the platform: Credential Manager on Android, the GoogleSignIn SDK (Swift
 * bridge) on iOS. Returns a Google ID token for [es.sebas1705.haltera.data.sync.AccountService].
 */
interface GoogleSignInLauncher {
    /** False when Google sign-in is not configured in this build: the button is hidden. */
    val isAvailable: Boolean

    suspend fun requestToken(): GoogleSignInResult
}

sealed interface GoogleSignInResult {
    data class Token(val token: GoogleIdToken) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult

    data class Failed(val message: String) : GoogleSignInResult
}

/** Builds without Google sign-in (no Firebase config). */
object NoGoogleSignIn : GoogleSignInLauncher {
    override val isAvailable: Boolean = false

    override suspend fun requestToken(): GoogleSignInResult = GoogleSignInResult.Failed("Google sign-in unavailable")
}
