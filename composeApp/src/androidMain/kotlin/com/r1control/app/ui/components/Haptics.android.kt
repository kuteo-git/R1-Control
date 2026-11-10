package com.r1control.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * `View.performHapticFeedback`, which already obeys the system's touch-feedback
 * switch. `VIRTUAL_KEY` for a press (`KEYBOARD_TAP` is louder and belongs to
 * typing); `CLOCK_TICK` for a notch, short and dry, as mytube's seek bar;
 * `CONTEXT_CLICK` for a knock, the firmest that is still one knock. Android 14
 * has its own toggle and drag constants; older phones borrow the nearest.
 */
@Composable
actual fun rememberPlatformHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) {
        Haptics { haptic -> view.performHapticFeedback(constantOf(haptic)) }
    }
}

private fun constantOf(haptic: Haptic): Int {
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    return when (haptic) {
        Haptic.Tap -> HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.On -> if (modern) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Off -> if (modern) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.Knock -> HapticFeedbackConstants.CONTEXT_CLICK
        Haptic.Lift -> if (modern) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.LONG_PRESS
    }
}
