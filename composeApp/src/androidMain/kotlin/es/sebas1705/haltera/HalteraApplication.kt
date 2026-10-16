package es.sebas1705.haltera

import android.app.Application
import android.os.Build
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import es.sebas1705.haltera.account.AndroidGoogleSignInLauncher
import es.sebas1705.haltera.account.GoogleSignInLauncher
import es.sebas1705.haltera.account.NoGoogleSignIn
import es.sebas1705.haltera.account.ResumedActivity
import es.sebas1705.haltera.data.local.DatabaseDriverFactory
import es.sebas1705.haltera.data.remote.FirebaseConfig
import es.sebas1705.haltera.data.remote.isFirebaseConfigured
import es.sebas1705.haltera.data.repository.di.AppVersionName
import es.sebas1705.haltera.di.appModules
import es.sebas1705.haltera.feature.nutrition.scan.BarcodeScanner
import es.sebas1705.haltera.export.AndroidExportFileBridge
import es.sebas1705.haltera.export.ExportFileBridge
import es.sebas1705.haltera.feature.routines.share.RoutineShareBridge
import es.sebas1705.haltera.share.AndroidBarcodeScanner
import es.sebas1705.haltera.settings.AppInfo
import es.sebas1705.haltera.share.AndroidRoutineShareBridge
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class HalteraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // google-services.json (Doppler, git-ignored) gives the default Firebase app; without it this is
        // a no-op and the app runs with sync off.
        Firebase.initialize(this)
        val firebase = FirebaseConfig(configured = isFirebaseConfigured(this))
        val activities = ResumedActivity().also(::registerActivityLifecycleCallbacks)
        startKoin {
            androidContext(this@HalteraApplication)
            modules(
                appModules + module {
                    single { DatabaseDriverFactory(androidContext()) }
                    single { firebase }
                    single<GoogleSignInLauncher> {
                        if (firebase.configured) AndroidGoogleSignInLauncher(androidContext(), activities) else NoGoogleSignIn
                    }
                    single<RoutineShareBridge> { AndroidRoutineShareBridge(androidContext(), get()) }
                    single<ExportFileBridge> { AndroidExportFileBridge(androidContext()) }
                    single<BarcodeScanner> { AndroidBarcodeScanner() }
                    single { AppVersionName(versionName()) }
                    single { appInfo() }
                },
            )
        }
    }

    @Suppress("DEPRECATION") // getPackageInfo(String, PackageInfoFlags) needs API 33.
    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "dev"

    /** Version name and code shown in About, read from the installed package. */
    @Suppress("DEPRECATION")
    private fun appInfo(): AppInfo {
        val info = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val code = info?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        }
        return AppInfo(versionName = info?.versionName ?: "dev", versionCode = code?.toString() ?: "?")
    }
}
