package com.r1control.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The jvm target exists only to run tests; it draws a black box where a stream would play. */
@Composable
actual fun CameraStream(url: String, modifier: Modifier, fill: Boolean, muted: Boolean) {
    Box(modifier.background(Color.Black))
}
