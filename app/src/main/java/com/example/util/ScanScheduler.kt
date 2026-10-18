package com.example.util

import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object ScanScheduler {

    val TIMEZONE_UTC7: TimeZone = TimeZone.getTimeZone("GMT+7")
    private val SCAN_HOURS = listOf(3, 7, 11, 15, 19, 23)

    fun getNextScanTimestamp(fromMillis: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance(TIMEZONE_UTC7).apply { timeInMillis = fromMillis }
        for (targetHour in SCAN_HOURS) {
            val candidate = Calendar.getInstance(TIMEZONE_UTC7).apply {
                timeInMillis = fromMillis
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > fromMillis) {
                return candidate.timeInMillis
            }
        }

        // If all hours today passed, next is 03:00 tomorrow
        val tomorrow = Calendar.getInstance(TIMEZONE_UTC7).apply {
            timeInMillis = fromMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return tomorrow.timeInMillis
    }

    fun formatCountdown(targetTimestamp: Long, currentTimestamp: Long = System.currentTimeMillis()): String {
        val diff = targetTimestamp - currentTimestamp
        if (diff <= 0) return "00:00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
