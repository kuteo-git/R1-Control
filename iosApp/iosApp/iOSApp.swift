import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    // The one thing SwiftUI cannot answer. Rotation is decided by
    // `application(_:supportedInterfaceOrientationsFor:)`, which lives on the
    // UIKit app delegate and has no SwiftUI equivalent.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Compose view applies the safe area itself, so it is
                // deliberately not applied twice.
                .ignoresSafeArea(.all)
        }
    }
}

/// Which way up the app may be.
///
/// Holds no state of its own: the answer comes from Kotlin's `OrientationLock`,
/// which the camera flips when its stream fills the screen. Keeping the flag on
/// this side would put the decision in the file that knows least about it.
class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        OrientationLockKt.landscapeAllowed() ? .landscape : .portrait
    }
}
