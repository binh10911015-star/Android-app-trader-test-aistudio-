package com.example.util

object SearchUtils {
    fun stripUsdtSuffix(query: String): String {
        val trimmed = query.trim()
        return when {
            trimmed.endsWith("USDT", ignoreCase = true) -> trimmed.substring(0, trimmed.length - 4).trim()
            else -> trimmed
        }
    }
}
