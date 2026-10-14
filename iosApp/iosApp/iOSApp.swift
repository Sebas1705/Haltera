import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        KoinIosKt.startIronlogKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
