package com.example.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object ScanScheduler {

    val TIMEZONE_UTC7: TimeZone = TimeZone.getTimeZone("GMT+7")
    // Giờ quét hàng ngày lúc 07:00 AM UTC+7 (00:00 UTC - thời điểm nến D1 đóng)
    val SCAN_HOURS = listOf(7)

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

        // Mốc tiếp theo là 07:00 sáng ngày mai
        val tomorrow = Calendar.getInstance(TIMEZONE_UTC7).apply {
            timeInMillis = fromMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return tomorrow.timeInMillis
    }

    fun getNextScanSlot(targetTimestamp: Long): String {
        return "SESSION_D1_07AM"
    }

    fun getNextScanHourText(targetTimestamp: Long): String {
        return "07:00 AM UTC+7 (Đóng nến D1)"
    }

    fun formatCountdown(targetTimestamp: Long, currentTimestamp: Long = System.currentTimeMillis()): String {
        val diff = targetTimestamp - currentTimestamp
        if (diff <= 0) return "00:00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatRemainingReadable(targetTimestamp: Long, currentTimestamp: Long = System.currentTimeMillis()): String {
        val diff = targetTimestamp - currentTimestamp
        if (diff <= 0) return "ngay bây giờ"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        return if (hours > 0) "${hours}h ${minutes}p" else "${minutes} phút"
    }
}
