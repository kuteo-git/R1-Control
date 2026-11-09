import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Compose view applies the safe area itself, so it is
                // deliberately not applied twice.
                .ignoresSafeArea(.all)
        }
    }
}
