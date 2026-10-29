package es.sebas1705.haltera

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import dev.gitlive.firebase.initialize
import es.sebas1705.haltera.core.datastore.CrashReportsStore
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
import es.sebas1705.haltera.dataimport.AndroidImportFileBridge
import es.sebas1705.haltera.dataimport.ImportFileBridge
import es.sebas1705.haltera.export.AndroidExportFileBridge
import es.sebas1705.haltera.export.ExportFileBridge
import es.sebas1705.haltera.data.health.HealthConnectGateway
import es.sebas1705.haltera.data.repository.health.HealthGateway
import es.sebas1705.haltera.health.AndroidHealthConnectBridge
import es.sebas1705.haltera.health.HealthConnectBridge
import es.sebas1705.haltera.feature.routines.share.RoutineShareBridge
import es.sebas1705.haltera.share.AndroidBarcodeScanner
import es.sebas1705.haltera.settings.AppInfo
import es.sebas1705.haltera.settings.CrashReportsAvailability
import es.sebas1705.haltera.share.AndroidRoutineShareBridge
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
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
        val koin = startKoin {
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
                    single<ImportFileBridge> { AndroidImportFileBridge(androidContext()) }
                    // Health Connect (read only in the foreground, after the user connects in Settings → Integrations).
                    single<HealthGateway> { HealthConnectGateway(androidContext()) }
                    single<HealthConnectBridge> { AndroidHealthConnectBridge(androidContext()) }
                    single<BarcodeScanner> { AndroidBarcodeScanner() }
                    single { AppVersionName(versionName()) }
                    single { appInfo() }
                    single { CrashReportsAvailability(available = firebase.configured) }
                },
            )
        }.koin
        if (firebase.configured) applyCrashReportsSwitch(koin.get())
    }

    /**
     * Crashlytics collection follows Settings → Privacy → "Send crash reports" in release builds. The manifest turns it
     * on for release and off for debug builds; Crashlytics persists the override, so a user who turned it off sends
     * nothing from the next start on, crashes during startup included. Debug builds never send.
     */
    private fun applyCrashReportsSwitch(store: CrashReportsStore) {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) return
        MainScope().launch { store.enabled.collect { Firebase.crashlytics.setCrashlyticsCollectionEnabled(it) } }
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
