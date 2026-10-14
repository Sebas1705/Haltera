package es.sebas1705.ironlog

import androidx.compose.ui.window.ComposeUIViewController
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(appModule, module { single { DatabaseDriverFactory() } })
        }
    },
) { App() }
