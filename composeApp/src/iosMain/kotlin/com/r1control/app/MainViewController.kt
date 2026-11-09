package com.r1control.app

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.r1control.app.data.local.IosSettingsDataSource
import com.r1control.app.ui.App
import platform.UIKit.UIColor
import platform.UIKit.UIViewController

/**
 * The iOS entry point, called from `ContentView.swift`. The container is one per process, as on Android.
 * The app pads its pages by the keyboard itself (`imePadding` in `App`), so Compose must not also slide
 * the whole view up to show a focused field: together they pushed the top bar under the status bar.
 */
fun MainViewController(): UIViewController =
    ComposeUIViewController(configure = { onFocusBehavior = OnFocusBehavior.DoNothing }) { App(container) }.also {
        // The launch screen's colour (#141416, LaunchBackground in Assets.xcassets), so nothing white shows before Compose's first frame.
        it.view.backgroundColor = UIColor(red = 0x14 / 255.0, green = 0x14 / 255.0, blue = 0x16 / 255.0, alpha = 1.0)
    }

private val container: AppContainer by lazy {
    AppContainer(settings = IosSettingsDataSource())
}
