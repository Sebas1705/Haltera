package es.sebas1705.ironlog.di

import es.sebas1705.ironlog.account.AccountViewModel
import es.sebas1705.ironlog.account.DatabaseLocalDataEraser
import es.sebas1705.ironlog.account.LocalDataEraser
import es.sebas1705.ironlog.account.SyncStarter
import es.sebas1705.ironlog.core.datastore.datastoreModule
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.data.local.createDatabase
import es.sebas1705.ironlog.data.remote.remoteModule
import es.sebas1705.ironlog.data.repository.di.repositoryModule
import es.sebas1705.ironlog.data.sync.di.syncModule
import es.sebas1705.ironlog.domain.usecases.di.useCaseModule
import es.sebas1705.ironlog.feature.exercises.di.exercisesModule
import es.sebas1705.ironlog.feature.nutrition.di.nutritionModule
import es.sebas1705.ironlog.feature.progress.di.progressModule
import es.sebas1705.ironlog.feature.routines.di.routinesModule
import es.sebas1705.ironlog.feature.session.di.sessionModule
import es.sebas1705.ironlog.feature.today.di.todayModule
import es.sebas1705.ironlog.settings.SettingsViewModel
import es.sebas1705.ironlog.share.IncomingRoutineChannel
import es.sebas1705.ironlog.startup.StartupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { createDatabase(get<DatabaseDriverFactory>()) }
    // Anonymous sign-in, then the sync engine, once the catalogue is imported (the only start).
    single { SyncStarter(get(), get()) }
    viewModel { StartupViewModel(get(), get(), onImported = { get<SyncStarter>().start() }) }
    viewModelOf(::SettingsViewModel)
    single<LocalDataEraser> { DatabaseLocalDataEraser(get(), get(), get()) }
    viewModel { AccountViewModel(get(), get(), get(), get(), get()) }
    single { IncomingRoutineChannel() }
}

/**
 * Every shared module; the platform adds its `DatabaseDriverFactory`, `FirebaseConfig`,
 * `GoogleSignInLauncher`, `BarcodeScanner` and `AppVersionName`.
 */
val appModules = listOf(
    appModule,
    remoteModule,
    datastoreModule,
    repositoryModule,
    syncModule,
    useCaseModule,
    todayModule,
    routinesModule,
    exercisesModule,
    nutritionModule,
    progressModule,
    sessionModule,
)
