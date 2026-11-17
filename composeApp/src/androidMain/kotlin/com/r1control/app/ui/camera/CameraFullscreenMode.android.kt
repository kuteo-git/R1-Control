package com.r1control.app.ui.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun ApplyCameraFullscreen() {
    val activity = LocalContext.current.findActivity() ?: return

    DisposableEffect(Unit) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val previousOrientation = activity.requestedOrientation

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // A swipe brings the bars back for a moment and they leave again, so the
        // first touch on the picture is not spent only restoring the clock.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // Leaving the camera must not strand the phone sideways with no
            // bars. This is why the seam is state, not an event: an event has
            // nothing to undo.
            activity.requestedOrientation = previousOrientation
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * The Activity behind a Compose context.
 *
 * `LocalContext` is not the Activity; Compose wraps it, sometimes several times.
 * Unwrapping is the documented way to the window, and returning null rather than
 * casting lets a preview, which has no Activity, draw instead of crash.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
