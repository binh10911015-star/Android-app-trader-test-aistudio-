package com.example.util

object SearchUtils {

    /**
     * Strips 'USDT' suffix from the input search string as well as common pair separators
     * ('/', '-', '_', and spaces) to allow searching coins naturally (e.g., typing 'BTC',
     * 'BTCUSDT', 'btc usdt', 'eth/usdt', or 'sol-usdt' all resolve to the base coin name).
     *
     * Examples:
     * - "BTCUSDT" -> "BTC"
     * - "btcusdt" -> "BTC"
     * - "BTC/USDT" -> "BTC"
     * - "ETH USDT" -> "ETH"
     * - "SOL-USDT" -> "SOL"
     * - "1000PEPEUSDT" -> "1000PEPE"
     * - "BTC" -> "BTC"
     * - "USDT" -> ""
     */
    fun stripUsdtSuffix(input: String): String {
        var cleaned = input.trim()
            .replace("/", "")
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")

        if (cleaned.endsWith("USDT", ignoreCase = true)) {
            cleaned = cleaned.substring(0, cleaned.length - 4)
        }
        return cleaned.trim().uppercase(java.util.Locale.US)
    }
}
