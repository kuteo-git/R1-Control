package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
actual fun CameraStream(url: String, modifier: Modifier) {
    val context = LocalContext.current
    // Keyed to the url: a new camera builds its own player, and the one before is released by the effect below.
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
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
        modifier = modifier,
    )
}
