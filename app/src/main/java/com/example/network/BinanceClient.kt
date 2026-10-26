package com.example.network

import com.example.data.DefaultCoinData
import com.example.model.BinanceTicker
import com.example.model.Candle
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BinanceClient {

    @Volatile
    var isTestnet: Boolean = false

    // Danh sách Endpoint Klines Binance chính thức (hỗ trợ cả domain vision công khai chống nghẽn)
    private val CANDLE_DATA_ENDPOINTS = listOf(
        "https://fapi.binance.com/fapi/v1/klines",
        "https://data-api.binance.vision/api/v3/klines",
        "https://api.binance.com/api/v3/klines"
    )

    private val MAINNET_PUBLIC_URLS = listOf(
        "https://fapi.binance.com",
        "https://fapi1.binance.com",
        "https://fapi2.binance.com",
        "https://fapi3.binance.com"
    )

    fun getTradeBaseUrl(): String = "https://fapi.binance.com"

    @Volatile
    private var activeProxyHost: String = ""
    @Volatile
    private var activeProxyPort: Int = 0
    @Volatile
    private var activeProxyType: String = "HTTP"

    private val activePerpetualSymbols = ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var lastExchangeInfoFetchTime: Long = 0L
    private const val EXCHANGE_INFO_CACHE_TTL_MS = 3600_000L

    val usedWeight1m = AtomicInteger(0)

    @Volatile
    var okHttpClient: OkHttpClient = buildClient(null)
        private set

    fun hmacSha256(secret: String, data: String): String {
        return try {
            val sha256Hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            sha256Hmac.init(secretKey)
            val signedBytes = sha256Hmac.doFinal(data.toByteArray(Charsets.UTF_8))
            signedBytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun setCrossMarginAndLeverage1x(symbol: String, apiKey: String, apiSecret: String, isTestnet: Boolean = false): Boolean {
        val baseUrl = "https://fapi.binance.com"
        val emptyBody = "".toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())

        try {
            val ts1 = BinanceFuturesApiVerifier.getSyncedTimestamp()
            val queryMargin = "symbol=${symbol.uppercase()}&marginType=CROSSED&timestamp=$ts1&recvWindow=60000"
            val sigMargin = hmacSha256(apiSecret, queryMargin)
            val requestMargin = Request.Builder().url("$baseUrl/fapi/v1/marginType?$queryMargin&signature=$sigMargin").header("X-MBX-APIKEY", apiKey).post(emptyBody).build()
            okHttpClient.newCall(requestMargin).execute().use { resp ->
                // Mã -4046 nghĩa là Margin Type đã là CROSSED sẵn rồi, hoàn toàn hợp lệ
            }
        } catch (_: Exception) {}

        try {
            val ts2 = BinanceFuturesApiVerifier.getSyncedTimestamp()
            val queryLev = "symbol=${symbol.uppercase()}&leverage=1&timestamp=$ts2&recvWindow=60000"
            val sigLev = hmacSha256(apiSecret, queryLev)
            val requestLev = Request.Builder().url("$baseUrl/fapi/v1/leverage?$queryLev&signature=$sigLev").header("X-MBX-APIKEY", apiKey).post(emptyBody).build()
            return okHttpClient.newCall(requestLev).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            return false
        }
    }

    fun refreshValidPerpetualSymbols(forceRefresh: Boolean = false): Set<String> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && activePerpetualSymbols.isNotEmpty() && (now - lastExchangeInfoFetchTime < EXCHANGE_INFO_CACHE_TTL_MS)) {
            return activePerpetualSymbols.keys
        }

        val urlsToTry = MAINNET_PUBLIC_URLS

        for (baseUrl in urlsToTry) {
            try {
                val request = Request.Builder().url("$baseUrl/fapi/v1/exchangeInfo").build()
                okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: ""
                        val json = JSONObject(body)
                        val symbolsArr = json.optJSONArray("symbols")
                        if (symbolsArr != null && symbolsArr.length() > 0) {
                            val newSet = mutableSetOf<String>()
                            for (i in 0 until symbolsArr.length()) {
                                val sObj = symbolsArr.getJSONObject(i)
                                val sym = sObj.optString("symbol", "")
                                val status = sObj.optString("status", "")
                                val contractType = sObj.optString("contractType", "")
                                val quoteAsset = sObj.optString("quoteAsset", "")

                                // Chỉ tập trung vào hợp đồng vĩnh cửu USDT-M Perpetual đang giao dịch
                                if (status.equals("TRADING", ignoreCase = true) &&
                                    contractType.equals("PERPETUAL", ignoreCase = true) &&
                                    quoteAsset.equals("USDT", ignoreCase = true)
                                ) {
                                    newSet.add(sym)
                                }
                            }
                            if (newSet.isNotEmpty()) {
                                activePerpetualSymbols.clear()
                                newSet.forEach { activePerpetualSymbols[it] = true }
                                lastExchangeInfoFetchTime = now
                                return newSet
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return activePerpetualSymbols.keys
    }

    fun getValidSymbols(): Set<String> {
        if (activePerpetualSymbols.isEmpty()) return refreshValidPerpetualSymbols()
        return activePerpetualSymbols.keys
    }

    fun getActiveSymbolsCount(): Int {
        val set = getValidSymbols()
        return if (set.isNotEmpty()) set.size else DefaultCoinData.getSeedTickers().size
    }

    fun updateProxy(host: String, port: Int, isSocks: Boolean = false) {
        val trimmedHost = host.trim()
        activeProxyHost = trimmedHost
        activeProxyPort = port
        activeProxyType = if (isSocks) "SOCKS" else "HTTP"

        val proxy = if (trimmedHost.isNotEmpty() && port > 0) {
            val type = if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(type, InetSocketAddress(trimmedHost, port))
        } else {
            null
        }
        okHttpClient = buildClient(proxy)
    }

    private fun buildClient(proxy: Proxy?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .build()
                val response = chain.proceed(request)
                response.header("x-mbx-used-weight-1m")?.toIntOrNull()?.let { usedWeight1m.set(it) }
                response
            }
        if (proxy != null) builder.proxy(proxy)
        return builder.build()
    }

    fun fetchTickersWithFallback(): List<BinanceTicker> {
        val urlsToTry = MAINNET_PUBLIC_URLS
        for (baseUrl in urlsToTry) {
            try {
                val request = Request.Builder().url("$baseUrl/fapi/v1/ticker/24hr").build()
                okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        val bodyString = res.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val parsed = parseTickers(bodyString)
                            if (parsed.isNotEmpty()) return parsed
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return DefaultCoinData.getSeedTickers()
    }

    fun fetchMarkPricesWithFallback(): Map<String, Double> {
        val urlsToTry = MAINNET_PUBLIC_URLS
        for (baseUrl in urlsToTry) {
            try {
                val request = Request.Builder().url("$baseUrl/fapi/v1/premiumIndex").build()
                okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        val bodyString = res.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val map = mutableMapOf<String, Double>()
                            val array = JSONArray(bodyString)
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val sym = obj.optString("symbol", "")
                                val markPrice = obj.optString("markPrice", "0").toDoubleOrNull() ?: 0.0
                                if (sym.isNotEmpty() && markPrice > 0.0) {
                                    map[sym] = markPrice
                                }
                            }
                            if (map.isNotEmpty()) return map
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyMap()
    }

    /**
     * Tải nến thật 100% từ mạng lưới Binance
     */
    fun fetchKlinesWithFallback(
        symbol: String,
        interval: String = "4h",
        limit: Int = 100,
        startTime: Long? = null
    ): List<Candle> {
        val cleanSymbol = symbol.trim().uppercase()
        if (cleanSymbol.isEmpty()) return emptyList()

        for (endpoint in CANDLE_DATA_ENDPOINTS) {
            try {
                val url = if (startTime != null && startTime > 0) {
                    "$endpoint?symbol=$cleanSymbol&interval=$interval&startTime=$startTime&limit=$limit"
                } else {
                    "$endpoint?symbol=$cleanSymbol&interval=$interval&limit=$limit"
                }
                val request = Request.Builder().url(url).build()
                okHttpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        val bodyString = res.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val parsed = parseKlines(bodyString)
                            if (parsed.isNotEmpty()) return parsed
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    fun parseTickers(jsonString: String): List<BinanceTicker> {
        val list = mutableListOf<BinanceTicker>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val symbol = item.optString("symbol", "")
                // Chỉ tập trung vào các cặp hợp đồng vĩnh cửu USDT-M Perpetual (loại trừ hợp đồng kỳ hạn chứa '_')
                val isUsdtPerpetual = symbol.endsWith("USDT") && !symbol.contains("_")
                if (isUsdtPerpetual) {
                    val lastPrice = item.optString("lastPrice", "0").toDoubleOrNull() ?: 0.0
                    val priceChangePercent = item.optString("priceChangePercent", "0").toDoubleOrNull() ?: 0.0
                    val volume = item.optString("volume", "0").toDoubleOrNull() ?: 0.0
                    val highPrice = item.optString("highPrice", "0").toDoubleOrNull() ?: 0.0
                    val lowPrice = item.optString("lowPrice", "0").toDoubleOrNull() ?: 0.0

                    if (lastPrice > 0.0) {
                        list.add(
                            BinanceTicker(
                                symbol = symbol, lastPrice = lastPrice, priceChangePercent = priceChangePercent,
                                volume = volume, highPrice = highPrice, lowPrice = lowPrice,
                                sma20OpenD1 = lastPrice, volumeRatioD1 = 1.0
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list.sortedByDescending { it.volume * it.lastPrice }
    }

    fun parseKlines(jsonString: String): List<Candle> {
        val candles = mutableListOf<Candle>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val kline = array.getJSONArray(i)
                val open = kline.optString(1, "0").toDoubleOrNull() ?: 0.0
                val close = kline.optString(4, "0").toDoubleOrNull() ?: 0.0
                if (open > 0.0 && close > 0.0) {
                    candles.add(
                        Candle(
                            openTime = kline.optLong(0, 0L),
                            open = open,
                            high = kline.optString(2, "0").toDoubleOrNull() ?: 0.0,
                            low = kline.optString(3, "0").toDoubleOrNull() ?: 0.0,
                            close = close,
                            volume = kline.optString(5, "0").toDoubleOrNull() ?: 0.0,
                            closeTime = kline.optLong(6, 0L)
                        )
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return candles
    }
}