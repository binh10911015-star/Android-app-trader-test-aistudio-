package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BinanceApiConfig

class SecureApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("secure_crypto_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_IS_TESTNET = "is_testnet"
        private const val KEY_ACCESS_PIN = "access_pin"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_LIVE_ENABLED = "live_trading_enabled"

        const val DEFAULT_PIN = "1234"
    }

    fun loadConfig(): BinanceApiConfig {
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val apiSecret = prefs.getString(KEY_API_SECRET, "") ?: ""
        val isTestnet = prefs.getBoolean(KEY_IS_TESTNET, true)
        val pin = prefs.getString(KEY_ACCESS_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        val proxyHost = prefs.getString(KEY_PROXY_HOST, "") ?: ""
        val proxyPort = prefs.getInt(KEY_PROXY_PORT, 0)
        val proxyType = prefs.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"
        val liveEnabled = prefs.getBoolean(KEY_LIVE_ENABLED, false)

        return BinanceApiConfig(
            apiKey = apiKey,
            apiSecret = apiSecret,
            isTestnet = isTestnet,
            isConfigured = apiKey.isNotBlank() && apiSecret.isNotBlank(),
            liveTradingEnabled = liveEnabled,
            accessPin = pin,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = proxyType,
            isConnected = apiKey.isNotBlank() && apiSecret.isNotBlank()
        )
    }

    fun saveConfig(config: BinanceApiConfig) {
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_API_SECRET, config.apiSecret)
            .putBoolean(KEY_IS_TESTNET, config.isTestnet)
            .putString(KEY_ACCESS_PIN, config.accessPin)
            .putString(KEY_PROXY_HOST, config.proxyHost)
            .putInt(KEY_PROXY_PORT, config.proxyPort)
            .putString(KEY_PROXY_TYPE, config.proxyType)
            .putBoolean(KEY_LIVE_ENABLED, config.liveTradingEnabled)
            .apply()
    }

    fun verifyPin(inputPin: String): Boolean {
        val storedPin = prefs.getString(KEY_ACCESS_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        return inputPin == storedPin
    }

    fun savePin(newPin: String) {
        prefs.edit().putString(KEY_ACCESS_PIN, newPin).apply()
    }

    fun getPin(): String {
        return prefs.getString(KEY_ACCESS_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
    }
}
