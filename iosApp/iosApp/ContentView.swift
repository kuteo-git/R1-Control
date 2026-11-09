import SwiftUI
import ComposeApp

/// The whole app, which is Kotlin. `MainViewControllerKt` is generated from
/// `MainViewController.kt` in `iosMain`, where the object graph is built.
struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
