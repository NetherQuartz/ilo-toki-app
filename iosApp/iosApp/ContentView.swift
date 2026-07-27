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
            // Every safe area, not just the keyboard. SwiftUI would otherwise inset
            // the Compose view by the status bar and the home indicator, leaving the
            // window's own white showing above and below the app's background —
            // Compose already applies all of these insets itself, edge to edge.
            .ignoresSafeArea()
    }
}
