package es.sebas1705.ironlog

import android.app.Application
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class IronlogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@IronlogApplication)
            modules(
                appModule,
                module { single { DatabaseDriverFactory(androidContext()) } },
            )
        }
    }
}
