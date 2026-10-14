package es.sebas1705.ironlog

import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** Starts Koin once per process. Called from `iOSApp.init()` as `KoinIosKt.initKoin()`. */
fun initKoin() {
    startKoin {
        modules(appModule, module { single { DatabaseDriverFactory() } })
    }
}
