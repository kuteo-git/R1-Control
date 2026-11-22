package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
actual fun CameraStream(url: String, modifier: Modifier, fill: Boolean, muted: Boolean) {
    val context = LocalContext.current
    // Keyed to the url: a new camera builds its own player, and the one before is released by the effect below.
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            prepare()
        }
    }
    // Volume follows the flag on every change, not only when the player is built.
    SideEffect { player.volume = if (muted) 0f else 1f }
    DisposableEffect(url) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setKeepContentOnPlayerReset(true)
                // The picture reacts to nothing; it must not eat the touch the
                // card below or the controls above want.
                isClickable = false
                isFocusable = false
                this.player = player
            }
        },
        // Zoom crops to the view; fit keeps the whole picture and letterboxes the rest.
        update = {
            it.resizeMode =
                if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        modifier = modifier,
    )
}
