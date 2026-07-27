import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose UI. Everything above this line — download, model
/// loading, translation — lives in Kotlin and is shared with the Android app.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose applies its own IME insets; letting SwiftUI do it too
            // would shift the content twice.
            .ignoresSafeArea(.keyboard)
    }
}
