package com.r1control.app.ui.camera

import androidx.compose.runtime.Composable

/**
 * Turn the phone sideways for a camera, and put it back.
 *
 * Mounted while the camera fills the screen, disposed when it closes, so the
 * seam takes no argument. Being in the tree is the on state. An `expect/actual`
 * rather than a port, because both platforms reach for the thing that owns the
 * window and finding it is the work. Called as state, not an event, so a screen
 * disposed while full screen cannot leave the phone stuck sideways.
 */
@Composable
expect fun ApplyCameraFullscreen()
