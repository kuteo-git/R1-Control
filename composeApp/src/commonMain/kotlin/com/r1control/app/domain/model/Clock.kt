package com.r1control.app.domain.model

/** `m:ss`, or `h:mm:ss` from an hour up, for a seek bar. Negative counts as zero. */
fun formatClock(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

/** `1:23 / 4:05`; just the position while the length is not known. */
fun formatProgress(positionS: Int, durationS: Int): String =
    if (durationS > 0) "${formatClock(positionS)} / ${formatClock(durationS)}" else formatClock(positionS)
