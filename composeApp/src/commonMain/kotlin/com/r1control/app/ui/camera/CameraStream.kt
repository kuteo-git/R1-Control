package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays the camera's live HLS stream at [url], muted, filling its box. It streams only while it is in the
 * composition: entering starts it, leaving releases it, so a camera off screen holds no decoder or socket.
 * Android uses Media3/ExoPlayer, iOS uses AVFoundation; the jvm target (tests only) draws a placeholder.
 */
@Composable
expect fun CameraStream(url: String, modifier: Modifier = Modifier)
