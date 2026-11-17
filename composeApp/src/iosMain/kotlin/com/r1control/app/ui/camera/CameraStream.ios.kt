package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIColor
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraStream(url: String, modifier: Modifier) {
    // Keyed to the url: a new camera builds its own player; the effect below pauses the one before.
    val player = remember(url) {
        AVPlayer(uRL = NSURL(string = url)).apply { muted = true }
    }
    DisposableEffect(url) { onDispose { player.pause() } }
    UIKitView(
        factory = {
            val view = UIView()
            view.backgroundColor = UIColor.blackColor
            val layer = AVPlayerLayer.playerLayerWithPlayer(player)
            layer.videoGravity = AVLayerVideoGravityResizeAspectFill
            view.layer.addSublayer(layer)
            player.play()
            view
        },
        modifier = modifier,
        onResize = { view, rect ->
            // A hand-added layer animates its frame implicitly; wrap the change so it tracks the view at once.
            CATransaction.begin()
            CATransaction.setValue(true, kCATransactionDisableActions)
            (view.layer.sublayers?.firstOrNull() as? AVPlayerLayer)?.setFrame(rect)
            CATransaction.commit()
        },
    )
}
