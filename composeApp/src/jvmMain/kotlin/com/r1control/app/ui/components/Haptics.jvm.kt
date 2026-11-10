package com.r1control.app.ui.components

import androidx.compose.runtime.Composable

/** The JVM target exists to run tests; a test that wants to feel provides its own [LocalHaptics]. */
@Composable
actual fun rememberPlatformHaptics(): Haptics = Haptics {}
