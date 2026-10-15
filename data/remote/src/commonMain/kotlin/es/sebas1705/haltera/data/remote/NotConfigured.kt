package es.sebas1705.haltera.data.remote

import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.AccountSession
import es.sebas1705.haltera.data.sync.GoogleIdToken
import es.sebas1705.haltera.data.sync.LinkResult
import es.sebas1705.haltera.data.sync.RemoteStore
import es.sebas1705.haltera.data.sync.SyncDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

private const val NOT_CONFIGURED = "Firebase is not configured in this build"

/** Builds without google-services.json / GoogleService-Info.plist (CI, fresh clones): never signed in. */
object NotConfiguredAccountService : AccountService {
    override val session: StateFlow<AccountSession> =
        MutableStateFlow<AccountSession>(AccountSession.NotConfigured).asStateFlow()

    override suspend fun ensureSignedIn() = Unit

    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Failed(NOT_CONFIGURED)

    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = error(NOT_CONFIGURED)

    override suspend fun signOut() = Unit

    override suspend fun reauthenticate(credentials: GoogleIdToken) = error(NOT_CONFIGURED)

    override suspend fun deleteUser() = Unit
}

/** The remote side of a build without Firebase: nothing to push to, nothing ever arrives. */
object NotConfiguredRemoteStore : RemoteStore {
    override suspend fun push(uid: String, documents: List<SyncDocument>) = error(NOT_CONFIGURED)

    override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> =
        emptyFlow()

    override suspend fun deleteAll(uid: String) = Unit
}
