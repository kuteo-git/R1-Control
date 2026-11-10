package com.r1control.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt

/**
 * What a touch feels like. Each is a different sensation and each platform
 * names them apart, so they are kinds rather than strengths.
 *
 * - [Tap]: a press that does something (a button, a row, a tab).
 * - [On], [Off]: a switch thrown; Android 14 plays them differently.
 * - [Tick]: a value passing a notch (a volume step, a slider step, a row
 *   passing another while dragged).
 * - [Knock]: something coming to rest (a value meeting either end, a dragged
 *   row let go).
 * - [Lift]: a row picked up to be dragged.
 */
enum class Haptic { Tap, On, Off, Tick, Knock, Lift }

fun interface Haptics {
    fun play(haptic: Haptic)
}

/** One per app, provided by `App`: an iOS generator is prepared once, not per row. Previews and tests feel nothing. */
val LocalHaptics = staticCompositionLocalOf { Haptics {} }

/** The phone's own feedback. It follows the system's touch-feedback setting on both platforms. */
@Composable
expect fun rememberPlatformHaptics(): Haptics

/** The notch [value] is nearest to, on a scale cut into [last] parts (0..[last]). */
fun notchOf(value: Float, range: ClosedFloatingPointRange<Float>, last: Int): Int {
    val span = range.endInclusive - range.start
    if (span <= 0f || last <= 0) return 0
    return (((value - range.start) / span).coerceIn(0f, 1f) * last).roundToInt()
}

/**
 * How a value moving from notch [from] to notch [to] on 0..[last] is felt:
 * nothing within a notch, a knock on reaching either end, a tick past any
 * other. One tick per move, however many notches a fast finger skipped: a
 * burst of buzzes reads as a fault.
 */
fun notchFeel(from: Int, to: Int, last: Int): Haptic? = when {
    to == from -> null
    to <= 0 || to >= last -> Haptic.Knock
    else -> Haptic.Tick
}

/** The volume's notches: the speaker's hardware steps, or every 5% when it has none. */
fun volumeNotches(steps: Int): Int = if (steps > 0) steps else 20

/**
 * `clickable` that is felt. Every press in `ui` goes through here
 * (`HapticGuardTest`); [selected] non-null makes it `selectable`, for a radio row.
 */
@Composable
fun Modifier.tappable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClickLabel: String? = null,
    selected: Boolean? = null,
    feel: Haptic = Haptic.Tap,
    onClick: () -> Unit,
): Modifier {
    val haptics = LocalHaptics.current
    val felt = {
        haptics.play(feel)
        onClick()
    }
    return if (selected != null) {
        selectable(selected = selected, enabled = enabled, role = role, onClick = felt)
    } else {
        clickable(enabled = enabled, role = role, onClickLabel = onClickLabel, onClick = felt)
    }
}
