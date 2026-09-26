package com.livetube.tv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Date and time formats used by the settings surfaces. */
object DateFormats {
    private const val DATE_TIME_PATTERN = "d MMMM yyyy, h:mm a"

    fun dateTime(millis: Long): String {
        if (millis <= 0L) return "Never"
        return SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault()).format(Date(millis))
    }
}
