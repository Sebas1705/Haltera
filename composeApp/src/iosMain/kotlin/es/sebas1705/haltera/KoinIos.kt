package es.sebas1705.haltera

import es.sebas1705.haltera.account.GoogleSignInBridge
import es.sebas1705.haltera.account.GoogleSignInLauncher
import es.sebas1705.haltera.account.IosGoogleSignInLauncher
import es.sebas1705.haltera.data.local.DatabaseDriverFactory
import es.sebas1705.haltera.data.remote.FirebaseConfig
import es.sebas1705.haltera.data.remote.isFirebaseConfigured
import es.sebas1705.haltera.data.repository.di.AppVersionName
import es.sebas1705.haltera.di.appModules
import es.sebas1705.haltera.feature.nutrition.scan.BarcodeScanner
import es.sebas1705.haltera.feature.nutrition.scan.NoBarcodeScanner
import platform.Foundation.NSBundle
import es.sebas1705.haltera.dataimport.ImportFileBridge
import es.sebas1705.haltera.dataimport.IosImportFileBridge
import es.sebas1705.haltera.export.ExportFileBridge
import es.sebas1705.haltera.export.IosExportFileBridge
import es.sebas1705.haltera.feature.routines.share.RoutineShareBridge
import es.sebas1705.haltera.settings.AppInfo
import es.sebas1705.haltera.share.IosRoutineShareBridge
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Starts Koin once per process. Called from `iOSApp.init()` as
 * `KoinIosKt.startHalteraKoin(googleSignIn:)`, after `FirebaseApp.configure()` (when
 * GoogleService-Info.plist is bundled). [googleSignIn] is the Swift Google sign-in (null: none).
 * Not named `initKoin`: Kotlin/Native would export an `init…` function to Swift as `doInitKoin()`.
 */
fun startHalteraKoin(googleSignIn: GoogleSignInBridge?) {
    val firebase = FirebaseConfig(configured = isFirebaseConfigured())
    startKoin {
        modules(
            appModules + module {
                single { DatabaseDriverFactory() }
                single { firebase }
                single<GoogleSignInLauncher> { IosGoogleSignInLauncher(googleSignIn.takeIf { firebase.configured }) }
                single<RoutineShareBridge> { IosRoutineShareBridge(get()) }
                single<ExportFileBridge> { IosExportFileBridge() }
                single<ImportFileBridge> { IosImportFileBridge() }
                // Product barcode scanning is not available on iOS yet (an AVFoundation scanner is future work).
                single<BarcodeScanner> { NoBarcodeScanner }
                single {
                    AppInfo(
                        versionName = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "dev",
                        versionCode = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?",
                    )
                }
                single {
                    AppVersionName(NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "dev")
                }
            },
        )
    }
}
