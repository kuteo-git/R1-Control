package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIView

/**
 * A view whose one sublayer is [playerLayer], sized to the view on every layout.
 *
 * An AVPlayerLayer added as a sublayer does not follow its parent's bounds, and
 * UIKitView's onResize does not fire in this version of Compose Multiplatform,
 * so the layer keeps the zero frame it was born with and draws a black nothing.
 * layoutSubviews is where UIKit asks a view to place its contents; it is called
 * on every bounds change and needs no help from the interop API. Bounds, not
 * frame: a sublayer is placed in its parent's own space, where the origin is
 * always zero.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class VideoContainer(private val playerLayer: AVPlayerLayer) :
    UIView(frame = CGRectZero.readValue()) {
    override fun layoutSubviews() {
        super.layoutSubviews()
        withoutImplicitAnimation { playerLayer.setFrame(bounds) }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraStream(url: String, modifier: Modifier, fill: Boolean, muted: Boolean) {
    // Keyed to the url: a new camera builds its own player; the effect below pauses the one before.
    val player = remember(url) { AVPlayer(uRL = NSURL(string = url)) }
    // Remembered rather than found again by casting the first sublayer: that cast
    // does nothing when it fails, which here is a blank picture with no error.
    val playerLayer = remember(player) {
        AVPlayerLayer().apply {
            this.player = player
            backgroundColor = UIColor.blackColor.CGColor
        }
    }
    // Mute and gravity follow the flags on every change, not once at build. Fill
    // crops to the layer's bounds; aspect fits the whole picture inside them.
    SideEffect {
        player.muted = muted
        withoutImplicitAnimation {
            playerLayer.videoGravity = if (fill) AVLayerVideoGravityResizeAspectFill else AVLayerVideoGravityResizeAspect
        }
    }
    DisposableEffect(url) {
        player.play()
        onDispose { player.pause() }
    }
    UIKitView(
        factory = {
            VideoContainer(playerLayer).apply {
                backgroundColor = UIColor.blackColor
                layer.addSublayer(playerLayer)
            }
        },
        modifier = modifier,
        // Kept below layoutSubviews, which is the one that fires. The rect is in
        // the superview's space, so the layer is sized from the size alone.
        onResize = { view: UIView, rect: CValue<CGRect> ->
            withoutImplicitAnimation {
                view.layer.setFrame(rect)
                rect.useContents { playerLayer.setFrame(CGRectMake(0.0, 0.0, size.width, size.height)) }
            }
        },
        // An interop view sits over its Compose siblings and would eat the touch,
        // so the card below and the controls above would never see it.
        interactive = false,
        accessibilityEnabled = false,
    )
}

/**
 * Runs a layer change with CoreAnimation's implicit animation off. A sublayer
 * added by hand is not a view's backing layer, so setting its frame outside a
 * transaction starts a quarter-second animation toward a target that has moved.
 */
@OptIn(ExperimentalForeignApi::class)
private inline fun withoutImplicitAnimation(block: () -> Unit) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    try {
        block()
    } finally {
        CATransaction.commit()
    }
}
