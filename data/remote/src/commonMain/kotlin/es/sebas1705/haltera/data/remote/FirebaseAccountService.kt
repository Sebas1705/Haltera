package es.sebas1705.haltera.data.remote

import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import es.sebas1705.haltera.data.sync.RecentLoginRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** [AccountService] on Firebase Auth: anonymous first, upgraded in place by linking Google (design §2). */
class FirebaseAccountService(private val auth: FirebaseAuth, scope: CoroutineScope) : AccountService {

    /** Linking changes the same user without a new auth state: this bumps the session to re-read it. */
    private val userChanged = MutableStateFlow(0)

    override val session: StateFlow<AccountSession> =
        combine(auth.authStateChanged, userChanged) { user, _ -> user.toSession() }
            .stateIn(scope, SharingStarted.Eagerly, auth.currentUser.toSession())

    override suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously()
    }

    // Every SDK failure becomes a result the UI can show.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult {
        val credential = credentials.toCredential()
        return try {
            val user = auth.currentUser
            if (user == null) {
                LinkResult.AccountExists(signIn(credential))
            } else {
                user.linkWithCredential(credential)
                user.reload()
                userChanged.update { it + 1 }
                LinkResult.Linked
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthUserCollisionException) {
            // That Google account already has a user: switch to it; the caller re-uploads local data.
            signInReplacingAnonymous(credential)
        } catch (e: Exception) {
            LinkResult.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    override suspend fun signInWithGoogle(credentials: GoogleIdToken) {
        signIn(credentials.toCredential())
    }

    override suspend fun signOut() = auth.signOut()

    override suspend fun reauthenticate(credentials: GoogleIdToken) {
        auth.currentUser?.reauthenticate(credentials.toCredential())
    }

    @Suppress("SwallowedException") // Mapped to the contract exception the UI reacts to.
    override suspend fun deleteUser() {
        try {
            auth.currentUser?.delete()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            throw RecentLoginRequiredException(e.message)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun signInReplacingAnonymous(credential: AuthCredential): LinkResult = try {
        val previous = auth.currentUser?.takeIf { it.isAnonymous }
        val uid = signIn(credential)
        // The anonymous user's data lives on this device and is re-uploaded: drop the user (best effort).
        runCatching { previous?.delete() }
        LinkResult.AccountExists(uid)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LinkResult.Failed(e.message ?: e::class.simpleName.orEmpty())
    }

    private suspend fun signIn(credential: AuthCredential): String =
        requireNotNull(auth.signInWithCredential(credential).user) { "Google sign-in returned no user" }.uid

    private fun GoogleIdToken.toCredential() = GoogleAuthProvider.credential(idToken, accessToken)
}

internal fun FirebaseUser?.toSession(): AccountSession = when (this) {
    null -> AccountSession.SignedOut
    else -> AccountSession.SignedIn(
        uid = uid,
        isAnonymous = isAnonymous,
        email = email ?: providerData.firstNotNullOfOrNull { it.email },
        displayName = displayName ?: providerData.firstNotNullOfOrNull { it.displayName },
    )
}
