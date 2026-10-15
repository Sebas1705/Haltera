package es.sebas1705.ironlog

import android.app.Application
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import es.sebas1705.ironlog.account.AndroidGoogleSignInLauncher
import es.sebas1705.ironlog.account.GoogleSignInLauncher
import es.sebas1705.ironlog.account.NoGoogleSignIn
import es.sebas1705.ironlog.account.ResumedActivity
import es.sebas1705.ironlog.data.local.DatabaseDriverFactory
import es.sebas1705.ironlog.data.remote.FirebaseConfig
import es.sebas1705.ironlog.data.remote.isFirebaseConfigured
import es.sebas1705.ironlog.data.repository.di.AppVersionName
import es.sebas1705.ironlog.di.appModules
import es.sebas1705.ironlog.feature.nutrition.scan.BarcodeScanner
import es.sebas1705.ironlog.feature.routines.share.RoutineShareBridge
import es.sebas1705.ironlog.share.AndroidBarcodeScanner
import es.sebas1705.ironlog.share.AndroidRoutineShareBridge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class IronlogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // google-services.json (Doppler, git-ignored) gives the default Firebase app; without it this is
        // a no-op and the app runs with sync off.
        Firebase.initialize(this)
        val firebase = FirebaseConfig(configured = isFirebaseConfigured(this))
        val activities = ResumedActivity().also(::registerActivityLifecycleCallbacks)
        startKoin {
            androidContext(this@IronlogApplication)
            modules(
                appModules + module {
                    single { DatabaseDriverFactory(androidContext()) }
                    single { firebase }
                    single<GoogleSignInLauncher> {
                        if (firebase.configured) AndroidGoogleSignInLauncher(androidContext(), activities) else NoGoogleSignIn
                    }
                    single<RoutineShareBridge> { AndroidRoutineShareBridge(androidContext(), get()) }
                    single<BarcodeScanner> { AndroidBarcodeScanner() }
                    single { AppVersionName(versionName()) }
                },
            )
        }
    }

    @Suppress("DEPRECATION") // getPackageInfo(String, PackageInfoFlags) needs API 33.
    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "dev"
}
