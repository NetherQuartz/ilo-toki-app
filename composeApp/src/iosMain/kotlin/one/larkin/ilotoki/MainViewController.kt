package one.larkin.ilotoki

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point used by iosApp/ContentView.swift to host the shared Compose UI. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
