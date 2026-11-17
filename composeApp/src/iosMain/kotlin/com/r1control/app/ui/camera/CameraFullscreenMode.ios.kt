package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

/**
 * Camera full screen, iOS side: turn the phone sideways and put it back.
 *
 * Three pieces have to agree, because an iOS app cannot rotate itself on
 * request. `Info.plist` lists portrait and both landscapes as the outer bound.
 * The Swift app delegate answers the system's question from [OrientationLock],
 * narrowing that bound. This function flips the lock, tells the controller its
 * answer changed, and asks the window scene to rotate.
 * `setNeedsUpdateOfSupportedInterfaceOrientations` is the step easy to miss:
 * without it the delegate's new answer is never asked for and the request is
 * refused against the old one. The status bar is left alone, as the picture
 * already draws under it.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun ApplyCameraFullscreen() {
    DisposableEffect(Unit) {
        apply(true)
        onDispose { apply(false) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun apply(landscape: Boolean) {
    OrientationLock.allow(landscape)

    val scene = UIApplication.sharedApplication.connectedScenes
        .firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return

    // Ask again before requesting: the delegate's answer has just changed, and
    // without this the request is judged against the previous one.
    scene.keyWindow?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()

    val mask = if (landscape) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait
    scene.requestGeometryUpdateWithPreferences(
        UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask),
    ) {
        // Refused when delegate and request disagree. Swallowed rather than
        // thrown: a camera that stays portrait is a letdown, one that takes the
        // app down is a fault.
    }
}
