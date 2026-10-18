package com.example.util

import java.util.concurrent.TimeUnit

object ScanScheduler {
    fun getNextScanTimestamp(referenceTime: Long = System.currentTimeMillis()): Long {
        val from = referenceTime + TimeUnit.MINUTES.toMillis(1)
        val now = java.util.Calendar.getInstance()
        now.timeInMillis = from
        val minute = now.get(java.util.Calendar.MINUTE)
        val remainder = if (minute % 5 == 0) 0 else 5 - (minute % 5)
        now.add(java.util.Calendar.MINUTE, remainder)
        now.set(java.util.Calendar.SECOND, 0)
        now.set(java.util.Calendar.MILLISECOND, 0)
        return now.timeInMillis
    }

    fun formatCountdown(targetMillis: Long, nowMillis: Long): String {
        val diff = (targetMillis - nowMillis).coerceAtLeast(0L)
        val totalSeconds = diff / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
