package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ScanScheduler {
    // Scheduled hours in UTC+7 (every 4 hours starting from 7:00 AM)
    private val SCHEDULED_HOURS_UTC7 = intArrayOf(3, 7, 11, 15, 19, 23)
    val TIMEZONE_UTC7: TimeZone = TimeZone.getTimeZone("GMT+7")

    /**
     * Calculates the next scheduled scan timestamp in milliseconds
     */
    fun getNextScanTimestamp(fromMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance(TIMEZONE_UTC7).apply { timeInMillis = fromMillis }
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentSecond = cal.get(Calendar.SECOND)

        // Find the next hour in today's schedule
        for (targetHour in SCHEDULED_HOURS_UTC7) {
            if (targetHour > currentHour || (targetHour == currentHour && currentMinute == 0 && currentSecond < 30)) {
                return (cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }

        // If after 23:00, next is 03:00 tomorrow
        return (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Format remaining countdown to next scan (e.g. "03:24:12" or "3h 24p")
     */
    fun formatCountdown(targetMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (targetMillis - now).coerceAtLeast(0)
        val hours = diff / 3600000L
        val minutes = (diff % 3600000L) / 60000L
        val seconds = (diff % 60000L) / 1000L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatHour(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm (UTC+7)", Locale.getDefault()).apply {
            timeZone = TIMEZONE_UTC7
        }
        return sdf.format(Date(millis))
    }
}
