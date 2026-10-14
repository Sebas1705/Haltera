package es.sebas1705.ironlog.di

import es.sebas1705.ironlog.data.sync.AccountService
import es.sebas1705.ironlog.data.sync.AccountSession
import es.sebas1705.ironlog.data.sync.GoogleIdToken
import es.sebas1705.ironlog.data.sync.LinkResult
import es.sebas1705.ironlog.data.sync.RemoteStore
import es.sebas1705.ironlog.data.sync.SyncDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.dsl.module

// PLACEHOLDER (account and sync, part B): until `data:remote` (workstream A) lands, sync is bound to a
// NotConfigured account and a no-op remote, so the engine stays Disabled. Workstream A replaces this file
// with its `remoteModule` in `appModules`.

private object NotConfiguredAccountService : AccountService {
    override val session: StateFlow<AccountSession> = MutableStateFlow(AccountSession.NotConfigured)
    override suspend fun ensureSignedIn() = Unit
    override suspend fun linkGoogle(credentials: GoogleIdToken): LinkResult = LinkResult.Failed("Not configured")
    override suspend fun signInWithGoogle(credentials: GoogleIdToken) = Unit
    override suspend fun signOut() = Unit
    override suspend fun deleteUser() = Unit
}

private object NoRemoteStore : RemoteStore {
    override suspend fun push(uid: String, documents: List<SyncDocument>) = Unit
    override fun changes(uid: String, collection: String, sinceUpdatedAt: Long): Flow<List<SyncDocument>> = emptyFlow()
    override suspend fun deleteAll(uid: String) = Unit
}

val syncPlaceholderModule = module {
    single<AccountService> { NotConfiguredAccountService }
    single<RemoteStore> { NoRemoteStore }
}
