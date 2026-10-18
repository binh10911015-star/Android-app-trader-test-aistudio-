package com.example.util

import android.content.Context
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig

class SecureApiKeyStorage(context: Context) {
    companion object {
        const val PRECONFIGURED_MAINNET_API_KEY = "demo_mainnet_api_key"
        const val PRECONFIGURED_MAINNET_API_SECRET = "demo_mainnet_api_secret"
        const val PRECONFIGURED_TESTNET_API_KEY = "demo_testnet_api_key"
        const val PRECONFIGURED_TESTNET_API_SECRET = "demo_testnet_api_secret"
    }

    private val prefs = context.getSharedPreferences("binance_api_secure", Context.MODE_PRIVATE)

    fun saveCredentials(
        apiKey: String,
        apiSecret: String,
        accessPin: String,
        isTestnet: Boolean,
        liveTradingEnabled: Boolean
    ) {
        prefs.edit()
            .putString("api_key", apiKey)
            .putString("api_secret", apiSecret)
            .putString("access_pin", accessPin)
            .putBoolean("is_testnet", isTestnet)
            .putBoolean("live_trading_enabled", liveTradingEnabled)
            .apply()
    }

    fun loadConfig(): BinanceApiConfig {
        val apiKey = prefs.getString("api_key", "") ?: ""
        val apiSecret = prefs.getString("api_secret", "") ?: ""
        val accessPin = prefs.getString("access_pin", "") ?: ""
        val isTestnet = prefs.getBoolean("is_testnet", true)
        val liveTradingEnabled = prefs.getBoolean("live_trading_enabled", false)
        val proxyHost = prefs.getString("proxy_host", "") ?: ""
        val proxyPort = prefs.getInt("proxy_port", 0)
        val proxyType = prefs.getString("proxy_type", "HTTP") ?: "HTTP"
        val lastError = prefs.getString("last_error", "") ?: ""
        val isConnected = prefs.getBoolean("is_connected", false)
        val totalWalletBalance = prefs.getFloat("total_wallet_balance", 0f).toDouble()
        val availableBalance = prefs.getFloat("available_balance", 0f).toDouble()

        return BinanceApiConfig(
            apiKey = apiKey,
            apiSecret = apiSecret,
            accessPin = accessPin,
            isTestnet = isTestnet,
            liveTradingEnabled = liveTradingEnabled,
            isConnected = isConnected,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = proxyType,
            lastErrorMessage = lastError,
            totalWalletBalance = totalWalletBalance,
            availableBalance = availableBalance,
            isConfigured = apiKey.isNotBlank() && apiSecret.isNotBlank(),
            isProxyEnabled = proxyHost.isNotBlank() && proxyPort > 0
        )
    }

    fun updateVerificationResult(result: BinanceAccountVerificationResult) {
        prefs.edit()
            .putBoolean("is_connected", result.isSuccess)
            .putString("last_error", result.errorMessage)
            .putFloat("total_wallet_balance", result.totalWalletBalance.toFloat())
            .putFloat("available_balance", result.availableBalance.toFloat())
            .apply()
    }

    fun saveProxyConfig(host: String, port: Int, type: String) {
        prefs.edit()
            .putString("proxy_host", host)
            .putInt("proxy_port", port)
            .putString("proxy_type", type)
            .apply()
    }

    fun clearProxyConfig() {
        prefs.edit()
            .remove("proxy_host")
            .remove("proxy_port")
            .remove("proxy_type")
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove("api_key")
            .remove("api_secret")
            .remove("access_pin")
            .remove("is_connected")
            .remove("last_error")
            .remove("live_trading_enabled")
            .apply()
    }

    fun getMainnetCredentials(): Pair<String, String> {
        val apiKey = prefs.getString("mainnet_api_key", PRECONFIGURED_MAINNET_API_KEY) ?: PRECONFIGURED_MAINNET_API_KEY
        val apiSecret = prefs.getString("mainnet_api_secret", PRECONFIGURED_MAINNET_API_SECRET) ?: PRECONFIGURED_MAINNET_API_SECRET
        return apiKey to apiSecret
    }

    fun getTestnetCredentials(): Pair<String, String> {
        val apiKey = prefs.getString("testnet_api_key", PRECONFIGURED_TESTNET_API_KEY) ?: PRECONFIGURED_TESTNET_API_KEY
        val apiSecret = prefs.getString("testnet_api_secret", PRECONFIGURED_TESTNET_API_SECRET) ?: PRECONFIGURED_TESTNET_API_SECRET
        return apiKey to apiSecret
    }

    fun setLiveTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("live_trading_enabled", enabled).apply()
    }
}
