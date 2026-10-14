package es.sebas1705.ironlog

import android.app.Application
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModules
import es.sebas1705.ironlog.feature.routines.share.RoutineShareBridge
import es.sebas1705.ironlog.share.AndroidRoutineShareBridge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class IronlogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@IronlogApplication)
            modules(
                appModules + module {
                    single { DatabaseDriverFactory(androidContext()) }
                    single<RoutineShareBridge> { AndroidRoutineShareBridge(androidContext(), get()) }
                },
            )
        }
    }
}
