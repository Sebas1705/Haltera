package es.sebas1705.ironlog.feature.session.di

import com.russhwolf.settings.Settings
import es.sebas1705.ironlog.feature.session.platform.sessionPlatformModule
import es.sebas1705.ironlog.feature.session.timer.RestTimerController
import es.sebas1705.ironlog.feature.session.timer.SettingsTimerStore
import es.sebas1705.ironlog.feature.session.timer.TimerStore
import es.sebas1705.ironlog.feature.session.ui.SessionCatalog
import es.sebas1705.ironlog.feature.session.ui.SessionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Koin bindings of the session feature (ViewModels, feature-local classes). Registered in composeApp. */
@OptIn(ExperimentalTime::class)
val sessionModule = module {
    includes(sessionPlatformModule)
    single<TimerStore> { SettingsTimerStore(Settings()) }
    // One controller shared by the screen and the Android notification actions.
    single { RestTimerController(get(), get(), get()) { Clock.System.now().toEpochMilliseconds() } }
    factory { SessionCatalog(get(), get(), get(), get()) }
    viewModel { params ->
        SessionViewModel(
            sessionId = params.get(),
            sessions = get(),
            catalog = get(),
            settings = get(),
            timer = get(),
            indicator = get(),
            loadSteps = get(),
        )
    }
}
