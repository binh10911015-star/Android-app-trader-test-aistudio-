package com.example.util

object SearchUtils {

    fun stripUsdtSuffix(input: String): String {
        var text = input.trim()
        val regex = Regex("(?i)[/\\s_-]?usdt$")
        text = text.replace(regex, "")
        if (text.equals("usdt", ignoreCase = true)) return ""
        return text.trim()
    }
}
