package com.r1control.app.ui.i18n

/** The local `HH:mm` of an instant, for a chat bubble. Needs the device's time zone, so one implementation per platform. */
expect fun formatLocalTime(epochMillis: Long): String

/** The local calendar day of an instant as a day count, so two instants compare by the day they fall on here. */
expect fun localDay(epochMillis: Long): Long

/** The local date of an instant in the device's own medium form, for a day before yesterday. */
expect fun formatLocalDate(epochMillis: Long): String

/** "Today", "Yesterday" or the date, for the day [epochMillis] falls on as seen at [nowMillis]. */
fun Strings.dayOf(epochMillis: Long, nowMillis: Long): String = when (localDay(nowMillis) - localDay(epochMillis)) {
    0L -> today
    1L -> yesterday
    else -> formatLocalDate(epochMillis)
}
