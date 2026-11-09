package com.r1control.app.domain.model

/**
 * The speaker's hardware volume scale.
 *
 * The R1 has [steps] discrete levels (15) while the wire speaks percent. A
 * value shown or sent must be one the hardware can hold, so it is snapped the
 * way the web panel snaps it: `idx = round(pct * steps / 100)`,
 * `pct = round(idx * 100 / steps)`. `steps == 0` means the speaker reported no
 * scale, and percent passes through untouched.
 */
data class VolumeScale(val steps: Int) {
    val hasHardwareSteps: Boolean get() = steps > 0

    fun snap(pct: Int): Int {
        val clamped = pct.coerceIn(0, 100)
        if (!hasHardwareSteps) return clamped
        val index = (clamped * steps + 50) / 100
        return (index * 200 + steps) / (2 * steps)
    }
}

/** The level the speaker reports, already on its own scale. */
data class Volume(val pct: Int, val scale: VolumeScale)
