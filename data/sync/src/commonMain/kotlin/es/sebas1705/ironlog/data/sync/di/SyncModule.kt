package es.sebas1705.ironlog.data.sync.di

import es.sebas1705.ironlog.data.sync.SettingsSyncBridge
import es.sebas1705.ironlog.data.sync.SyncEngine
import es.sebas1705.ironlog.data.sync.SyncEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The sync engine and local change tracking (design §4). Binds nothing remote: `RemoteStore` and
 * `AccountService` come from `remoteModule` (data:remote).
 */
@OptIn(ExperimentalTime::class)
val syncModule = module {
    single { SettingsSyncBridge(get(), get()) }
    single<SyncEngine> {
        SyncEngineImpl(
            db = get(),
            remote = get(),
            account = get(),
            settings = get(),
            io = Dispatchers.IO,
            now = { Clock.System.now().toEpochMilliseconds() },
        )
    }
}
