package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays the camera's live HLS stream at [url] while it is in the composition: entering starts it, leaving
 * releases it, so a camera off screen holds no decoder or socket. [fill] crops the picture to its box (the
 * card, a 16:9 frame); false fits the whole picture inside the box and letterboxes the rest (the fullscreen,
 * where the original shape is wanted). [muted] silences it, which the cards are and the fullscreen is not.
 * Android uses Media3/ExoPlayer, iOS uses AVFoundation; the jvm target (tests only) draws a placeholder.
 */
@Composable
expect fun CameraStream(url: String, modifier: Modifier = Modifier, fill: Boolean = true, muted: Boolean = true)
