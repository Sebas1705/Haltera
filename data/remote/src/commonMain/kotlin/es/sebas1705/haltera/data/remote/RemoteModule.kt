package es.sebas1705.haltera.data.remote

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import es.sebas1705.haltera.data.sync.AccountService
import es.sebas1705.haltera.data.sync.RemoteStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/** RemoteStore and AccountService; no-op `NotConfigured` versions when there is no Firebase config. */
val remoteModule = module {
    single<AccountService> {
        if (get<FirebaseConfig>().configured) {
            FirebaseAccountService(Firebase.auth, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        } else {
            NotConfiguredAccountService
        }
    }
    single<RemoteStore> {
        if (get<FirebaseConfig>().configured) {
            // SQLite is the local copy: Firestore keeps only a memory cache.
            val firestore = Firebase.firestore.apply {
                settings = firestoreSettings { cacheSettings = memoryCacheSettings { } }
            }
            FirestoreRemoteStore(firestore)
        } else {
            NotConfiguredRemoteStore
        }
    }
}
