package es.sebas1705.ironlog

import android.app.Application
import es.sebas1705.ironlog.core.security.appContext
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class KmpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this

        startKoin {
            androidContext(this@KmpApplication)
            modules(
                appModule,
                module { single { DatabaseDriverFactory(this@KmpApplication) } }
            )
        }
    }
}
