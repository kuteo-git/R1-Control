package com.r1control.app.domain.model

import kotlin.math.abs

/** Millibels as the dB a person reads: `+3 dB`, `-1.5 dB`, `0 dB`. Units do not change with the language. */
fun formatDb(mb: Int, signed: Boolean = false): String {
    val whole = mb / 100
    val tenth = abs(mb % 100) / 10
    val number = if (mb % 100 == 0) "$whole" else {
        val sign = if (mb < 0 && whole == 0) "-" else ""
        "$sign$whole.$tenth"
    }
    val sign = if (signed && mb > 0) "+" else ""
    return "$sign$number dB"
}

/** An equaliser band's label: `60 Hz`, `910 Hz`, `3.6 kHz`, `14 kHz`. */
fun formatHz(hz: Int): String {
    if (hz < 1000) return "$hz Hz"
    val tenths = (hz + 50) / 100
    return if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
}
