package es.sebas1705.haltera.di

import es.sebas1705.haltera.account.AccountViewModel
import es.sebas1705.haltera.account.CloudPromptViewModel
import es.sebas1705.haltera.account.DatabaseLocalDataEraser
import es.sebas1705.haltera.account.LocalDataEraser
import es.sebas1705.haltera.account.ShareNameDefault
import es.sebas1705.haltera.account.SyncStarter
import es.sebas1705.haltera.core.datastore.datastoreModule
import es.sebas1705.haltera.data.local.DatabaseDriverFactory
import es.sebas1705.haltera.data.local.createDatabase
import es.sebas1705.haltera.data.remote.remoteModule
import es.sebas1705.haltera.data.repository.di.repositoryModule
import es.sebas1705.haltera.data.sync.di.syncModule
import es.sebas1705.haltera.domain.usecases.di.useCaseModule
import es.sebas1705.haltera.feature.exercises.di.exercisesModule
import es.sebas1705.haltera.feature.nutrition.di.nutritionModule
import es.sebas1705.haltera.feature.progress.di.progressModule
import es.sebas1705.haltera.feature.routines.di.routinesModule
import es.sebas1705.haltera.feature.session.di.sessionModule
import es.sebas1705.haltera.feature.today.di.todayModule
import es.sebas1705.haltera.dataimport.DataImportViewModel
import es.sebas1705.haltera.export.DataExportViewModel
import es.sebas1705.haltera.settings.SettingsViewModel
import es.sebas1705.haltera.share.IncomingRoutineChannel
import es.sebas1705.haltera.startup.StartupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { createDatabase(get<DatabaseDriverFactory>()) }
    // The sync engine (and the anonymous sign-in once the cloud copy is on), after the catalogue import (the only start).
    single { SyncStarter(get(), get(), get()) }
    single { ShareNameDefault(get(), get()) }
    viewModel {
        StartupViewModel(
            get(),
            get(),
            onImported = {
                get<SyncStarter>().start()
                get<ShareNameDefault>().start()
            },
        )
    }
    viewModelOf(::SettingsViewModel)
    single<LocalDataEraser> { DatabaseLocalDataEraser(get(), get(), get()) }
    viewModel { AccountViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { CloudPromptViewModel(get(), get(), get()) }
    single { IncomingRoutineChannel() }
    viewModel { DataExportViewModel(get(), get(), get()) }
    viewModel { DataImportViewModel(get(), get(), get(), get()) }
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
