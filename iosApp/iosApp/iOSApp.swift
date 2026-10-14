import SwiftUI
import FirebaseCore
import GoogleSignIn
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // GoogleService-Info.plist comes from Doppler (scripts/doppler-sync-local.sh); without it the
        // app runs with sync off instead of crashing.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
        KoinIosKt.startIronlogKoin(googleSignIn: GoogleSignInSwift())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Google sign-in returns to the app through its URL scheme (REVERSED_CLIENT_ID).
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}
