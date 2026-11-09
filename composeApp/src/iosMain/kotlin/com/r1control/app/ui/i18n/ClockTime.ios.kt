package com.r1control.app.ui.i18n

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

actual fun formatLocalTime(epochMillis: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "HH:mm"
    return formatter.stringFromDate(dateOf(epochMillis))
}

actual fun localDay(epochMillis: Long): Long {
    val offsetMillis = NSTimeZone.localTimeZone.secondsFromGMTForDate(dateOf(epochMillis)) * 1000L
    return (epochMillis + offsetMillis).floorDiv(MILLIS_PER_DAY)
}

actual fun formatLocalDate(epochMillis: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterMediumStyle
    formatter.timeStyle = NSDateFormatterNoStyle
    return formatter.stringFromDate(dateOf(epochMillis))
}

private fun dateOf(epochMillis: Long) = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)

private const val MILLIS_PER_DAY = 86_400_000L
