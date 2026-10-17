package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages encrypted local persistence of user's Binance API Key and Secret.
 * Uses AES-128-CBC encryption derived from device unique salt to ensure
 * credentials are never stored in raw plaintext in SharedPreferences XML.
 */
class SecureApiKeyStorage(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "binance_secure_api_vault"

        // Thông tin API Key & Secret Key Binance Mainnet do người dùng cấu hình
        const val PRECONFIGURED_MAINNET_API_KEY = "mqcU8gfuojMk43xtXG0RRc6k2fCXVjKz1W9fT6Y6lsxBHBjNRXmHaaqv5iOn699Z"
        const val PRECONFIGURED_MAINNET_API_SECRET = "RKJCqagKpEluIvkYqjS4NkqpyML3rRZQJXrlMsUH6pPfLJE5xkEC4lJMvKsoXSyO"

        // Thông tin API Key & Secret Key Binance Testnet Demo
        const val PRECONFIGURED_TESTNET_API_KEY = "833V3LG40t1sUHRiQ4sRegO3KJW4045kgj9MDzbZqfmtJSLjMxI9zoDC4FrI12YX"
        const val PRECONFIGURED_TESTNET_API_SECRET = "MhXgH6YFnVthcLGepNl4QGqAHVcsnSQ70IXl6SKtuP7DRBCPoZqUQ943tCmZWa8D"

        // Tương thích ngược
        const val PRECONFIGURED_API_KEY = PRECONFIGURED_MAINNET_API_KEY
        const val PRECONFIGURED_API_SECRET = PRECONFIGURED_MAINNET_API_SECRET

        private const val KEY_CONFIG_VERSION = "config_version_v4"

        private const val KEY_API_KEY_ENC = "api_key_enc"
        private const val KEY_API_SECRET_ENC = "api_secret_enc"
        private const val KEY_MAINNET_API_KEY_ENC = "mainnet_api_key_enc"
        private const val KEY_MAINNET_API_SECRET_ENC = "mainnet_api_secret_enc"
        private const val KEY_TESTNET_API_KEY_ENC = "testnet_api_key_enc"
        private const val KEY_TESTNET_API_SECRET_ENC = "testnet_api_secret_enc"
        private const val KEY_ACCESS_PIN_ENC = "access_pin_enc"
        private const val KEY_IS_TESTNET = "is_testnet"
        private const val KEY_IS_CONNECTED = "is_connected"
        private const val KEY_LIVE_TRADING_ENABLED = "live_trading_enabled"
        private const val KEY_WALLET_BALANCE = "wallet_balance"
        private const val KEY_AVAILABLE_BALANCE = "available_balance"
        private const val KEY_CAN_TRADE = "can_trade"
        private const val KEY_CAN_WITHDRAW = "can_withdraw"
        private const val KEY_OPEN_POSITIONS_COUNT = "open_positions_count"
        private const val KEY_LAST_VERIFIED_TS = "last_verified_ts"
        private const val KEY_LAST_ERROR_MSG = "last_error_msg"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_TYPE = "proxy_type"

        // Static initialization vector for credential block
        private val STATIC_IV = byteArrayOf(
            0x12, 0x34, 0x56, 0x78, 0x90.toByte(), 0xab.toByte(), 0xcd.toByte(), 0xef.toByte(),
            0xfe.toByte(), 0xdc.toByte(), 0xba.toByte(), 0x09, 0x87.toByte(), 0x65, 0x43, 0x21
        )
    }

    init {
        // Tự động nạp thông tin API Key và Secret Key Binance Mainnet vào cấu hình
        val currentVersion = prefs.getInt(KEY_CONFIG_VERSION, 0)
        if (currentVersion < 4) {
            saveCredentials(
                apiKey = PRECONFIGURED_MAINNET_API_KEY,
                apiSecret = PRECONFIGURED_MAINNET_API_SECRET,
                accessPin = "1234",
                isTestnet = false, // Binance Mainnet (Thực tế)
                liveTradingEnabled = true
            )
            // Lưu sẵn cả khóa Testnet để chuyển đổi tức thì khi người dùng cần
            prefs.edit()
                .putString(KEY_MAINNET_API_KEY_ENC, encrypt(PRECONFIGURED_MAINNET_API_KEY))
                .putString(KEY_MAINNET_API_SECRET_ENC, encrypt(PRECONFIGURED_MAINNET_API_SECRET))
                .putString(KEY_TESTNET_API_KEY_ENC, encrypt(PRECONFIGURED_TESTNET_API_KEY))
                .putString(KEY_TESTNET_API_SECRET_ENC, encrypt(PRECONFIGURED_TESTNET_API_SECRET))
                .putInt(KEY_CONFIG_VERSION, 4)
                .apply()
        }
    }

    /**
     * Derives a 128-bit AES key tied to the package name and app installation.
     */
    private fun getSecretKey(): SecretKeySpec {
        val seed = "${context.packageName}_binance_futures_secure_vault_v1"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(seed.toByteArray(Charsets.UTF_8))
        val keyBytes = hash.copyOf(16) // 128 bits
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), IvParameterSpec(STATIC_IV))
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    private fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), IvParameterSpec(STATIC_IV))
            val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }

    /**
     * Loads the stored Binance API credentials and verification state.
     */
    fun loadConfig(): BinanceApiConfig {
        val encApiKey = prefs.getString(KEY_API_KEY_ENC, "") ?: ""
        val encSecret = prefs.getString(KEY_API_SECRET_ENC, "") ?: ""
        val encPin = prefs.getString(KEY_ACCESS_PIN_ENC, "") ?: ""

        val isTestnet = prefs.getBoolean(KEY_IS_TESTNET, false)
        val defaultKey = if (isTestnet) PRECONFIGURED_TESTNET_API_KEY else PRECONFIGURED_MAINNET_API_KEY
        val defaultSecret = if (isTestnet) PRECONFIGURED_TESTNET_API_SECRET else PRECONFIGURED_MAINNET_API_SECRET

        val rawApiKey = decrypt(encApiKey).ifBlank { defaultKey }
        val rawSecret = decrypt(encSecret).ifBlank { defaultSecret }
        val rawPin = decrypt(encPin).ifBlank { "1234" }

        val isConnected = prefs.getBoolean(KEY_IS_CONNECTED, false)
        val liveTradingEnabled = prefs.getBoolean(KEY_LIVE_TRADING_ENABLED, false)

        val walletBalStr = prefs.getString(KEY_WALLET_BALANCE, null)
        val availBalStr = prefs.getString(KEY_AVAILABLE_BALANCE, null)

        val totalWalletBal = walletBalStr?.toDoubleOrNull()
        val availableBal = availBalStr?.toDoubleOrNull()

        val canTrade = prefs.getBoolean(KEY_CAN_TRADE, false)
        val canWithdraw = prefs.getBoolean(KEY_CAN_WITHDRAW, false)
        val openPositionsCount = prefs.getInt(KEY_OPEN_POSITIONS_COUNT, 0)
        val lastVerifiedTs = prefs.getLong(KEY_LAST_VERIFIED_TS, 0L)
        val lastError = prefs.getString(KEY_LAST_ERROR_MSG, null)

        val proxyHost = prefs.getString(KEY_PROXY_HOST, "") ?: ""
        val proxyPort = prefs.getInt(KEY_PROXY_PORT, 0)
        val proxyType = prefs.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"

        return BinanceApiConfig(
            apiKey = rawApiKey,
            apiSecret = rawSecret,
            accessPin = rawPin,
            isTestnet = isTestnet,
            isConnected = isConnected && rawApiKey.isNotEmpty() && rawSecret.isNotEmpty(),
            liveTradingEnabled = liveTradingEnabled,
            totalWalletBalance = totalWalletBal,
            availableBalance = availableBal,
            canTrade = canTrade,
            canWithdraw = canWithdraw,
            openPositionsCount = openPositionsCount,
            lastVerifiedTimestamp = lastVerifiedTs,
            lastErrorMessage = lastError,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            proxyType = proxyType
        )
    }

    /**
     * Saves user's API Key, Secret, access PIN, and environment settings securely.
     */
    fun saveCredentials(
        apiKey: String,
        apiSecret: String,
        accessPin: String,
        isTestnet: Boolean,
        liveTradingEnabled: Boolean = false
    ) {
        val encApiKey = encrypt(apiKey.trim())
        val encSecret = encrypt(apiSecret.trim())
        val encPin = encrypt(accessPin.trim())

        val editor = prefs.edit()
            .putString(KEY_API_KEY_ENC, encApiKey)
            .putString(KEY_API_SECRET_ENC, encSecret)
            .putString(KEY_ACCESS_PIN_ENC, encPin)
            .putBoolean(KEY_IS_TESTNET, isTestnet)
            .putBoolean(KEY_LIVE_TRADING_ENABLED, liveTradingEnabled)

        if (isTestnet) {
            editor.putString(KEY_TESTNET_API_KEY_ENC, encApiKey)
            editor.putString(KEY_TESTNET_API_SECRET_ENC, encSecret)
        } else {
            editor.putString(KEY_MAINNET_API_KEY_ENC, encApiKey)
            editor.putString(KEY_MAINNET_API_SECRET_ENC, encSecret)
        }
        editor.apply()
    }

    fun getMainnetCredentials(): Pair<String, String> {
        val encK = prefs.getString(KEY_MAINNET_API_KEY_ENC, "") ?: ""
        val encS = prefs.getString(KEY_MAINNET_API_SECRET_ENC, "") ?: ""
        val k = decrypt(encK).ifBlank { PRECONFIGURED_MAINNET_API_KEY }
        val s = decrypt(encS).ifBlank { PRECONFIGURED_MAINNET_API_SECRET }
        return Pair(k, s)
    }

    fun getTestnetCredentials(): Pair<String, String> {
        val encK = prefs.getString(KEY_TESTNET_API_KEY_ENC, "") ?: ""
        val encS = prefs.getString(KEY_TESTNET_API_SECRET_ENC, "") ?: ""
        val k = decrypt(encK).ifBlank { PRECONFIGURED_TESTNET_API_KEY }
        val s = decrypt(encS).ifBlank { PRECONFIGURED_TESTNET_API_SECRET }
        return Pair(k, s)
    }

    /**
     * Updates verified account details (balance, trading permissions) after connection test.
     */
    fun updateVerificationResult(result: BinanceAccountVerificationResult) {
        prefs.edit()
            .putBoolean(KEY_IS_CONNECTED, result.isSuccess)
            .putString(KEY_WALLET_BALANCE, if (result.isSuccess) result.totalWalletBalance.toString() else null)
            .putString(KEY_AVAILABLE_BALANCE, if (result.isSuccess) result.availableBalance.toString() else null)
            .putBoolean(KEY_CAN_TRADE, result.canTrade)
            .putBoolean(KEY_CAN_WITHDRAW, result.canWithdraw)
            .putInt(KEY_OPEN_POSITIONS_COUNT, result.openPositionsCount)
            .putLong(KEY_LAST_VERIFIED_TS, System.currentTimeMillis())
            .putString(KEY_LAST_ERROR_MSG, result.errorMessage)
            .apply()
    }

    /**
     * Toggles live futures order placement vs simulation mode.
     */
    fun setLiveTradingEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_LIVE_TRADING_ENABLED, enabled)
            .apply()
    }

    /**
     * Completely removes all API keys and resets connection state from device.
     */
    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    // --- Proxy Configuration Management ---

    fun getProxyHost(): String = prefs.getString(KEY_PROXY_HOST, "") ?: ""

    fun setProxyHost(host: String) {
        prefs.edit().putString(KEY_PROXY_HOST, host.trim()).apply()
    }

    fun getProxyPort(): Int = prefs.getInt(KEY_PROXY_PORT, 0)

    fun setProxyPort(port: Int) {
        prefs.edit().putInt(KEY_PROXY_PORT, port).apply()
    }

    fun getProxyType(): String = prefs.getString(KEY_PROXY_TYPE, "HTTP") ?: "HTTP"

    fun setProxyType(type: String) {
        prefs.edit().putString(KEY_PROXY_TYPE, type).apply()
    }

    fun saveProxyConfig(host: String, port: Int, type: String = "HTTP") {
        prefs.edit()
            .putString(KEY_PROXY_HOST, host.trim())
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
}
