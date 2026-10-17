package com.example.model

/**
 * Configuration and state for user's connected Binance Futures account.
 */
data class BinanceApiConfig(
    val apiKey: String = "",
    val apiSecret: String = "",
    val accessPin: String = "", // Mã PIN bảo mật - cần nhập mới được vào lệnh
    val isTestnet: Boolean = true, // Mặc định Testnet để chạy demo an toàn trên Binance Futures
    val isConnected: Boolean = false,
    val liveTradingEnabled: Boolean = false, // When true, uses real/testnet Futures orders; when false, uses paper trading
    val totalWalletBalance: Double? = null,
    val availableBalance: Double? = null,
    val canTrade: Boolean = false,
    val canWithdraw: Boolean = false,
    val openPositionsCount: Int = 0,
    val lastVerifiedTimestamp: Long = 0L,
    val lastErrorMessage: String? = null,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyType: String = "HTTP" // "HTTP" hoặc "SOCKS"
) {
    val isProxyEnabled: Boolean
        get() = proxyHost.isNotBlank() && proxyPort > 0

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    val hasAccessPin: Boolean
        get() = accessPin.isNotBlank()

    fun verifyPin(inputPin: String): Boolean {
        return !hasAccessPin || accessPin == inputPin.trim()
    }

    val maskedApiKey: String
        get() = if (apiKey.length >= 8) {
            "${apiKey.take(4)}...${apiKey.takeLast(4)}"
        } else if (apiKey.isNotEmpty()) {
            "****"
        } else {
            ""
        }

    val maskedSecret: String
        get() = if (apiSecret.isNotEmpty()) "••••••••••••••••••••••••" else ""

    val environmentLabel: String
        get() = if (isTestnet) "Binance Futures Testnet (Demo)" else "Binance Futures Mainnet (Thực)"

    val baseUrl: String
        get() = if (isTestnet) "https://testnet.binancefuture.com" else "https://fapi.binance.com"
}

data class BinanceAccountVerificationResult(
    val isSuccess: Boolean,
    val totalWalletBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val canTrade: Boolean = false,
    val canDeposit: Boolean = false,
    val canWithdraw: Boolean = false,
    val openPositionsCount: Int = 0,
    val errorMessage: String? = null
)
