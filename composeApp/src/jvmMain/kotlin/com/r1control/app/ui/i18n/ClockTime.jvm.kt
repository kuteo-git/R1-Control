package com.r1control.app.ui.i18n

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

actual fun formatLocalTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

actual fun localDay(epochMillis: Long): Long =
    (epochMillis + TimeZone.getDefault().getOffset(epochMillis)).floorDiv(MILLIS_PER_DAY)

actual fun formatLocalDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMillis))

private const val MILLIS_PER_DAY = 86_400_000L
