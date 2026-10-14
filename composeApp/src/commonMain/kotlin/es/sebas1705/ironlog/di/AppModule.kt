package es.sebas1705.ironlog.di

import es.sebas1705.ironlog.account.AccountViewModel
import es.sebas1705.ironlog.account.SyncStarter
import es.sebas1705.ironlog.core.datastore.datastoreModule
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.data.local.createDatabase
import es.sebas1705.ironlog.data.remote.remoteModule
import es.sebas1705.ironlog.data.repository.di.repositoryModule
import es.sebas1705.ironlog.domain.usecases.di.useCaseModule
import es.sebas1705.ironlog.feature.exercises.di.exercisesModule
import es.sebas1705.ironlog.feature.progress.di.progressModule
import es.sebas1705.ironlog.feature.routines.di.routinesModule
import es.sebas1705.ironlog.feature.session.di.sessionModule
import es.sebas1705.ironlog.feature.today.di.todayModule
import es.sebas1705.ironlog.settings.SettingsViewModel
import es.sebas1705.ironlog.startup.StartupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { createDatabase(get<DatabaseDriverFactory>()) }
    // The sync engine (data:sync, SyncEngine) is optional until its module is loaded.
    single { SyncStarter(get(), getOrNull()) }
    viewModel { StartupViewModel(get(), onImported = { get<SyncStarter>().start() }) }
    viewModelOf(::SettingsViewModel)
    viewModel { AccountViewModel(get(), get(), get(), getOrNull()) }
}

/**
 * Every shared module; the platform adds its `DatabaseDriverFactory`, `FirebaseConfig` and
 * `GoogleSignInLauncher`.
 */
val appModules = listOf(
    appModule,
    remoteModule,
    datastoreModule,
    repositoryModule,
    useCaseModule,
    todayModule,
    routinesModule,
    exercisesModule,
    progressModule,
    sessionModule,
)
