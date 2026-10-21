package com.example.util

object SearchUtils {
    /**
     * Loại bỏ hậu tố USDT và các ký tự phân cách (/ , - , _ , space) ở cuối từ khóa tìm kiếm.
     * Ví dụ: "BTCUSDT" -> "BTC", "ETH/USDT" -> "ETH", "doge_usdt" -> "doge", "USDT" -> ""
     */
    fun stripUsdtSuffix(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""
        val regex = Regex("""[\s/_\-]*usdt$""", RegexOption.IGNORE_CASE)
        return trimmed.replace(regex, "").trim().uppercase()
    }
}
