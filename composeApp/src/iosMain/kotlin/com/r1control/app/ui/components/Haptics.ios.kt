package com.r1control.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * Apple's generators, one of each kind for the whole app: a light impact for a
 * press and a switch turned off, a medium one for a switch turned on, a knock
 * and a lift, and the selection generator, made for a value swept through
 * notches. Each is prepared again after it fires: a cold Taptic Engine answers
 * late enough that the buzz belongs to the notch before last.
 */
@Composable
actual fun rememberPlatformHaptics(): Haptics = remember {
    val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply { prepare() }
    val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).apply { prepare() }
    val selection = UISelectionFeedbackGenerator().apply { prepare() }
    Haptics { haptic ->
        when (haptic) {
            Haptic.Tap, Haptic.Off -> light.run { impactOccurred(); prepare() }
            Haptic.On, Haptic.Knock, Haptic.Lift -> medium.run { impactOccurred(); prepare() }
            Haptic.Tick -> selection.run { selectionChanged(); prepare() }
        }
    }
}
