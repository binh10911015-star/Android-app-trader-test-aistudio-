package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig

class SecureApiKeyStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("secure_crypto_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PRECONFIGURED_MAINNET_API_KEY = ""
        const val PRECONFIGURED_MAINNET_API_SECRET = ""
        const val PRECONFIGURED_TESTNET_API_KEY = "ZpsofRaa3VMg3NZh10HfCaxoJcMdmHLwfCxI7opxyWlrbzRQnKZtWFfxOGWw9DtZ"
        const val PRECONFIGURED_TESTNET_API_SECRET = "eYRso0CQuSJ6m9WDgy6nRF9PuoDLw1tsRNt4bVCqYhyo2Sd4OjmP5NvoXYRmiQrI"

        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_IS_TESTNET = "is_testnet"
        private const val KEY_ACCESS_PIN = "access_pin"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_TYPE = "proxy_type"
        private const val KEY_LIVE_ENABLED = "live_trading_enabled"
        private const val KEY_WALLET_BALANCE = "wallet_balance"
        private const val KEY_AVAILABLE_BALANCE = "available_balance"
        private const val KEY_POSITIONS_COUNT = "positions_count"

        const val DEFAULT_PIN = "1234"
    }

    init {
        // Đảm bảo cập nhật ngay API Key & Secret Testnet mới của người dùng và xóa sạch khóa cũ
        val currentStoredTestnetKey = prefs.getString("testnet_api_key", null)
        if (currentStoredTestnetKey != PRECONFIGURED_TESTNET_API_KEY) {
            prefs.edit()
                .putString("testnet_api_key", PRECONFIGURED_TESTNET_API_KEY)
                .putString("testnet_api_secret", PRECONFIGURED_TESTNET_API_SECRET)
                .putString(KEY_API_KEY, PRECONFIGURED_TESTNET_API_KEY)
                .putString(KEY_API_SECRET, PRECONFIGURED_TESTNET_API_SECRET)
                .putBoolean(KEY_IS_TESTNET, true)
                .remove("mainnet_api_key")
                .remove("mainnet_api_secret")
                .apply()
        }
    }

    fun getMainnetCredentials(): Pair<String, String> {
        val apiKey = prefs.getString("mainnet_api_key", PRECONFIGURED_MAINNET_API_KEY) ?: PRECONFIGURED_MAINNET_API_KEY
        val apiSecret = prefs.getString("mainnet_api_secret", PRECONFIGURED_MAINNET_API_SECRET) ?: PRECONFIGURED_MAINNET_API_SECRET
        return Pair(apiKey, apiSecret)
    }

    fun getTestnetCredentials(): Pair<String, String> {
        val apiKey = prefs.getString("testnet_api_key", PRECONFIGURED_TESTNET_API_KEY) ?: PRECONFIGURED_TESTNET_API_KEY
        val apiSecret = prefs.getString("testnet_api_secret", PRECONFIGURED_TESTNET_API_SECRET) ?: PRECONFIGURED_TESTNET_API_SECRET
        return Pair(apiKey, apiSecret)
    }

    fun saveCredentials(
        apiKey: String,
        apiSecret: String,
        accessPin: String = DEFAULT_PIN,
        isTestnet: Boolean = true,
        liveTradingEnabled: Boolean = false
    ) {
        val editor = prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_API_SECRET, apiSecret)
            .putBoolean(KEY_IS_TESTNET, isTestnet)
            .putString(KEY_ACCESS_PIN, accessPin)
            .putBoolean(KEY_LIVE_ENABLED, liveTradingEnabled)

        if (isTestnet) {
            editor.putString("testnet_api_key", apiKey)
            editor.putString("testnet_api_secret", apiSecret)
        } else {
            editor.putString("mainnet_api_key", apiKey)
            editor.putString("mainnet_api_secret", apiSecret)
        }
        editor.apply()
    }

    fun updateVerificationResult(result: BinanceAccountVerificationResult) {
        prefs.edit()
            .putFloat(KEY_WALLET_BALANCE, result.totalWalletBalance.toFloat())
            .putFloat(KEY_AVAILABLE_BALANCE, result.availableBalance.toFloat())
            .putInt(KEY_POSITIONS_COUNT, result.openPositionsCount)
            .apply()
    }

    fun saveProxyConfig(host: String, port: Int, type: String = "HTTP") {
        prefs.edit()
            .putString(KEY_PROXY_HOST, host)
            .putInt(KEY_PROXY_PORT, port)
            .putString(KEY_PROXY_TYPE, type)
            .apply()
    }

    fun clearProxyConfig() {
        prefs.edit()
            .remove(KEY_PROXY_HOST)
            .remove(KEY_PROXY_PORT)
            .remove(KEY_PROXY_TYPE)
            .apply()
    }

    fun setLiveTradingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_ENABLED, enabled).apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_API_SECRET)
            .apply()
    }

    fun loadConfig(): BinanceApiConfig {
        val isTestnet = prefs.getBoolean(KEY_IS_TESTNET, true)
        val defaultKey = if (isTestnet) PRECONFIGURED_TESTNET_API_KEY else PRECONFIGURED_MAINNET_API_KEY
        val defaultSecret = if (isTestnet) PRECONFIGURED_TESTNET_API_SECRET else PRECONFIGURED_MAINNET_API_SECRET

        val apiKey = prefs.getString(KEY_API_KEY, defaultKey) ?: defaultKey
        val apiSecret = prefs.getString(KEY_API_SECRET, defaultSecret) ?: defaultSecret
        val pin = prefs.getString(KEY_ACCESS_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        val proxyHost = prefs.getString(KEY_PROXY_HOST, "") ?: ""
        val proxyPort = prefs.getInt(KEY_PROXY_PORT, 0)
        val proxyType = prefs.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"
        val liveEnabled = prefs.getBoolean(KEY_LIVE_ENABLED, true)
        val walletBal = prefs.getFloat(KEY_WALLET_BALANCE, 10000f).toDouble()
        val availBal = prefs.getFloat(KEY_AVAILABLE_BALANCE, 9500f).toDouble()

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
            isConnected = apiKey.isNotBlank() && apiSecret.isNotBlank(),
            totalWalletBalance = walletBal,
            availableBalance = availBal
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
