package com.example.network

import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceAssetBalance
import com.example.model.BinanceLivePosition
import com.example.model.BinanceOpenOrderItem
import com.example.model.BinanceOrderPlacementResult
import com.example.model.BinanceOrderVerificationDetail
import com.example.model.BinancePortfolio
import com.example.model.BinanceTpSlResult
import com.example.model.SymbolExchangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object BinanceFuturesApiVerifier {

    private const val MAINNET_BASE_URL = "https://fapi.binance.com"
    private const val TESTNET_BASE_URL = BinanceClient.TESTNET_FAPI_URL

    private var serverTimeOffset: Long = 0L
    private var lastTimeSync: Long = 0L
    private val symbolFiltersCache = ConcurrentHashMap<String, SymbolExchangeFilter>()

    suspend fun syncServerTimeOffset(baseUrl: String = MAINNET_BASE_URL): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/fapi/v1/time").get().build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val serverTime = JSONObject(body).optLong("serverTime", 0L)
                if (serverTime > 0) {
                    serverTimeOffset = serverTime - System.currentTimeMillis()
                    lastTimeSync = System.currentTimeMillis()
                    return@withContext serverTimeOffset
                }
            }
        } catch (_: Exception) {}
        serverTimeOffset
    }

    fun getSyncedTimestamp(): Long {
        return System.currentTimeMillis() + serverTimeOffset
    }

    suspend fun fetchSymbolFilter(symbol: String, baseUrl: String): SymbolExchangeFilter = withContext(Dispatchers.IO) {
        val upper = symbol.uppercase()
        symbolFiltersCache[upper]?.let { return@withContext it }

        try {
            val request = Request.Builder().url("$baseUrl/fapi/v1/exchangeInfo").get().build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val symbolsArr = JSONObject(body).optJSONArray("symbols")
                if (symbolsArr != null) {
                    for (i in 0 until symbolsArr.length()) {
                        val sObj = symbolsArr.getJSONObject(i)
                        val sName = sObj.optString("symbol", "")
                        val filtersArr = sObj.optJSONArray("filters")
                        var stepSize = 0.001
                        var minQty = 0.001
                        var tickSize = 0.01
                        var minNotional = 5.0

                        if (filtersArr != null) {
                            for (j in 0 until filtersArr.length()) {
                                val f = filtersArr.getJSONObject(j)
                                when (f.optString("filterType", "")) {
                                    "LOT_SIZE" -> {
                                        stepSize = f.optString("stepSize", "0.001").toDoubleOrNull() ?: 0.001
                                        minQty = f.optString("minQty", "0.001").toDoubleOrNull() ?: 0.001
                                    }
                                    "PRICE_FILTER" -> {
                                        tickSize = f.optString("tickSize", "0.01").toDoubleOrNull() ?: 0.01
                                    }
                                    "MIN_NOTIONAL" -> {
                                        minNotional = f.optString("notional", "5.0").toDoubleOrNull()
                                            ?: f.optString("minNotional", "5.0").toDoubleOrNull() ?: 5.0
                                    }
                                }
                            }
                        }
                        symbolFiltersCache[sName] = SymbolExchangeFilter(sName, stepSize, minQty, tickSize, minNotional)
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext symbolFiltersCache[upper] ?: SymbolExchangeFilter(upper)
    }

    fun roundToStepSize(value: Double, stepSize: Double): String {
        if (stepSize <= 0.0) return String.format(Locale.US, "%.3f", value)
        val stepBd = BigDecimal(stepSize.toString()).stripTrailingZeros()
        val scale = stepBd.scale().coerceAtLeast(0)
        val valBd = BigDecimal(value.toString())
        val steps = valBd.divide(stepBd, 0, RoundingMode.DOWN)
        return steps.multiply(stepBd).setScale(scale, RoundingMode.DOWN).toPlainString()
    }

    fun signHmacSha256(queryString: String, apiSecret: String): String {
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256Hmac.init(secretKey)
        return sha256Hmac.doFinal(queryString.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    suspend fun fetchPublicIpAddress(): String? = withContext(Dispatchers.IO) {
        val urls = listOf("https://api.ipify.org?format=json", "https://checkip.amazonaws.com", "https://icanhazip.com")
        for (url in urls) {
            try {
                val response = BinanceClient.okHttpClient.newCall(Request.Builder().url(url).get().build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim() ?: ""
                    val ip = if (body.startsWith("{")) JSONObject(body).optString("ip", "") else body
                    if (ip.isNotEmpty()) return@withContext ip
                }
            } catch (_: Exception) {}
        }
        null
    }

    suspend fun verifyAccount(apiKey: String, apiSecret: String, isTestnet: Boolean): BinanceAccountVerificationResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()
        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) {
            return@withContext BinanceAccountVerificationResult(isSuccess = false, errorMessage = "API Key/Secret không được để trống.")
        }

        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        syncServerTimeOffset(baseUrl)
        val queryString = "recvWindow=60000&timestamp=${getSyncedTimestamp()}"
        val signature = try { signHmacSha256(queryString, trimmedSecret) } catch (e: Exception) {
            return@withContext BinanceAccountVerificationResult(isSuccess = false, errorMessage = "Lỗi chữ ký HMAC: ${e.message}")
        }

        try {
            val req = Request.Builder().url("$baseUrl/fapi/v2/account?$queryString&signature=$signature").header("X-MBX-APIKEY", trimmedKey).get().build()
            val resp = BinanceClient.okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""

            if (resp.isSuccessful) {
                val json = JSONObject(body)
                var activePositions = 0
                val posArr = json.optJSONArray("positions")
                if (posArr != null) {
                    for (i in 0 until posArr.length()) {
                        if (posArr.getJSONObject(i).optDouble("positionAmt", 0.0) != 0.0) activePositions++
                    }
                }
                BinanceAccountVerificationResult(
                    isSuccess = true,
                    totalWalletBalance = json.optDouble("totalWalletBalance", 0.0),
                    availableBalance = json.optDouble("totalMarginBalance", 0.0),
                    canTrade = json.optBoolean("canTrade", false),
                    canDeposit = json.optBoolean("canDeposit", false),
                    canWithdraw = json.optBoolean("canWithdraw", false),
                    openPositionsCount = activePositions
                )
            } else {
                val errCode = try { JSONObject(body).optInt("code", 0) } catch (_: Exception) { 0 }
                val msg = when (errCode) {
                    -2014 -> "API Key không hợp lệ."
                    -2015 -> "API Key/Secret sai hoặc bị chặn IP Whitelist."
                    -1021 -> "Lệch giờ máy chủ, vui lòng thử lại."
                    else -> "Lỗi Binance ($errCode): $body"
                }
                BinanceAccountVerificationResult(isSuccess = false, errorMessage = msg)
            }
        } catch (e: Exception) {
            BinanceAccountVerificationResult(isSuccess = false, errorMessage = "Lỗi mạng: ${e.message}")
        }
    }

    suspend fun fetchFullPortfolio(apiKey: String, apiSecret: String, isTestnet: Boolean): BinancePortfolio = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()
        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) return@withContext BinancePortfolio(isLiveConnected = false, error = "Chưa cấu hình API.")

        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        syncServerTimeOffset(baseUrl)
        val qs = "recvWindow=60000&timestamp=${getSyncedTimestamp()}"
        val sig = try { signHmacSha256(qs, trimmedSecret) } catch (e: Exception) { return@withContext BinancePortfolio(isLiveConnected = false, error = "Lỗi ký số") }

        try {
            val accReq = Request.Builder().url("$baseUrl/fapi/v2/account?$qs&signature=$sig").header("X-MBX-APIKEY", trimmedKey).get().build()
            val accResp = BinanceClient.okHttpClient.newCall(accReq).execute()
            if (!accResp.isSuccessful) return@withContext BinancePortfolio(isLiveConnected = false, error = "Lỗi tải tài khoản: ${accResp.code}")

            val json = JSONObject(accResp.body?.string() ?: "")
            val assetsList = mutableListOf<BinanceAssetBalance>()
            val positionsList = mutableListOf<BinanceLivePosition>()

            val assetsArr = json.optJSONArray("assets")
            if (assetsArr != null) {
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.getJSONObject(i)
                    if (a.optDouble("walletBalance", 0.0) > 0.0 || a.optDouble("unrealizedProfit", 0.0) != 0.0 || a.optString("asset") in listOf("USDT", "BNB")) {
                        assetsList.add(BinanceAssetBalance(
                            asset = a.optString("asset", ""),
                            walletBalance = a.optDouble("walletBalance", 0.0),
                            unrealizedProfit = a.optDouble("unrealizedProfit", 0.0),
                            marginBalance = a.optDouble("marginBalance", 0.0),
                            maintMargin = a.optDouble("maintMargin", 0.0),
                            initialMargin = a.optDouble("initialMargin", 0.0),
                            availableBalance = a.optDouble("availableBalance", 0.0)
                        ))
                    }
                }
            }

            // Gọi PositionRisk để lấy PnL và Mark Price chính xác
            val riskMap = mutableMapOf<String, JSONObject>()
            try {
                val rQs = "recvWindow=60000&timestamp=${getSyncedTimestamp()}"
                val rSig = signHmacSha256(rQs, trimmedSecret)
                val riskReq = Request.Builder().url("$baseUrl/fapi/v2/positionRisk?$rQs&signature=$rSig").header("X-MBX-APIKEY", trimmedKey).get().build()
                val riskResp = BinanceClient.okHttpClient.newCall(riskReq).execute()
                if (riskResp.isSuccessful) {
                    val rArr = JSONArray(riskResp.body?.string() ?: "[]")
                    for (i in 0 until rArr.length()) riskMap[rArr.getJSONObject(i).optString("symbol")] = rArr.getJSONObject(i)
                }
            } catch (_: Exception) {}

            val posArr = json.optJSONArray("positions")
            if (posArr != null) {
                for (i in 0 until posArr.length()) {
                    val p = posArr.getJSONObject(i)
                    val amt = p.optDouble("positionAmt", 0.0)
                    if (amt != 0.0) {
                        val sym = p.optString("symbol", "")
                        val r = riskMap[sym]
                        val lev = r?.optInt("leverage", p.optInt("leverage", 1)) ?: 1
                        val entry = p.optDouble("entryPrice", 0.0)
                        val initM = if (lev > 0) (kotlin.math.abs(amt) * entry) / lev else p.optDouble("initialMargin", 0.0)

                        positionsList.add(BinanceLivePosition(
                            symbol = sym,
                            initialMargin = initM,
                            maintMargin = p.optDouble("maintMargin", 0.0),
                            unrealizedProfit = r?.optDouble("unRealizedProfit", p.optDouble("unrealizedProfit", 0.0)) ?: 0.0,
                            positionInitialMargin = p.optDouble("positionInitialMargin", 0.0),
                            openOrderInitialMargin = p.optDouble("openOrderInitialMargin", 0.0),
                            leverage = lev,
                            isolated = p.optBoolean("isolated", false),
                            entryPrice = entry,
                            markPrice = r?.optDouble("markPrice", p.optDouble("markPrice", 0.0)) ?: 0.0,
                            positionAmt = amt,
                            liquidationPrice = r?.optDouble("liquidationPrice", 0.0) ?: 0.0,
                            updateTime = p.optLong("updateTime", 0L)
                        ))
                    }
                }
            }
            positionsList.sortByDescending { it.roiPercent }

            val openOrdersList = mutableListOf<BinanceOpenOrderItem>()
            try {
                val oQs = "recvWindow=60000&timestamp=${getSyncedTimestamp()}"
                val oSig = signHmacSha256(oQs, trimmedSecret)
                val oReq = Request.Builder().url("$baseUrl/fapi/v1/openOrders?$oQs&signature=$oSig").header("X-MBX-APIKEY", trimmedKey).get().build()
                val oResp = BinanceClient.okHttpClient.newCall(oReq).execute()
                if (oResp.isSuccessful) {
                    val oArr = JSONArray(oResp.body?.string() ?: "[]")
                    for (i in 0 until oArr.length()) {
                        val o = oArr.getJSONObject(i)
                        openOrdersList.add(BinanceOpenOrderItem(
                            orderId = o.optLong("orderId", 0L),
                            symbol = o.optString("symbol", ""),
                            clientOrderId = o.optString("clientOrderId", ""),
                            price = o.optDouble("price", 0.0),
                            origQty = o.optDouble("origQty", 0.0),
                            executedQty = o.optDouble("executedQty", 0.0),
                            status = o.optString("status", "NEW"),
                            timeInForce = o.optString("timeInForce", "GTC"),
                            type = o.optString("type", "LIMIT"),
                            side = o.optString("side", "BUY"),
                            updateTime = o.optLong("updateTime", 0L),
                            avgPrice = o.optDouble("avgPrice", 0.0)
                        ))
                    }
                }
            } catch (_: Exception) {}

            BinancePortfolio(
                totalWalletBalance = json.optDouble("totalWalletBalance", 0.0),
                totalMarginBalance = json.optDouble("totalMarginBalance", 0.0),
                totalUnrealizedProfit = json.optDouble("totalUnrealizedProfit", 0.0),
                availableBalance = json.optDouble("availableBalance", 0.0),
                assets = assetsList,
                positions = positionsList,
                openOrders = openOrdersList,
                lastUpdatedTimestamp = System.currentTimeMillis(),
                isLiveConnected = true
            )
        } catch (e: Exception) {
            BinancePortfolio(isLiveConnected = false, error = "Lỗi mạng: ${e.message}")
        }
    }

    suspend fun checkOrderStatus(symbol: String, orderId: Long, apiKey: String, apiSecret: String, isTestnet: Boolean): BinanceOrderVerificationDetail = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()
        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) return@withContext BinanceOrderVerificationDetail(orderId, "", symbol, "LỖI", "", "", 0.0, 0.0, 0.0, 0.0, 0L, 0L, false, "Chưa cấu hình API.")

        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val qs = "symbol=${symbol.uppercase()}&orderId=$orderId&recvWindow=5000&timestamp=${System.currentTimeMillis()}"
        val sig = try { signHmacSha256(qs, trimmedSecret) } catch (e: Exception) { return@withContext BinanceOrderVerificationDetail(orderId, "", symbol, "LỖI", "", "", 0.0, 0.0, 0.0, 0.0, 0L, 0L, false, "Lỗi chữ ký") }

        try {
            val req = Request.Builder().url("$baseUrl/fapi/v1/order?$qs&signature=$sig").header("X-MBX-APIKEY", trimmedKey).get().build()
            val resp = BinanceClient.okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val j = JSONObject(body)
                BinanceOrderVerificationDetail(
                    orderId = j.optLong("orderId", orderId),
                    clientOrderId = j.optString("clientOrderId", ""),
                    symbol = symbol,
                    status = j.optString("status", "NEW"),
                    side = j.optString("side", "BUY"),
                    type = j.optString("type", "MARKET"),
                    price = j.optDouble("price", 0.0),
                    origQty = j.optDouble("origQty", 0.0),
                    executedQty = j.optDouble("executedQty", 0.0),
                    avgPrice = j.optDouble("avgPrice", 0.0),
                    time = j.optLong("time", 0L),
                    updateTime = j.optLong("updateTime", 0L),
                    isVerified = true,
                    message = "Đã xác thực trên sàn."
                )
            } else {
                BinanceOrderVerificationDetail(orderId, "", symbol, "KHÔNG TÌM THẤY", "", "", 0.0, 0.0, 0.0, 0.0, 0L, 0L, false, "Lỗi API: $body")
            }
        } catch (e: Exception) {
            BinanceOrderVerificationDetail(orderId, "", symbol, "LỖI MẠNG", "", "", 0.0, 0.0, 0.0, 0.0, 0L, 0L, false, "Lỗi kết nối")
        }
    }

    suspend fun postMarketOrder(
        symbol: String,
        side: String = "BUY",
        targetUsdtAmount: Double,
        approxPrice: Double,
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean = false,
        reduceOnly: Boolean = false,
        exactQuantity: Double? = null
    ): BinanceOrderPlacementResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()
        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val upperSymbol = symbol.uppercase()

        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) return@withContext BinanceOrderPlacementResult(success = false, errorMessage = "Chưa kết nối API.")
        
        // CHỐNG MUA NHẦM SỐ LƯỢNG KHỔNG LỒ NẾU GIÁ BỊ LỖI = 0
        if (exactQuantity == null && approxPrice <= 0.0) {
            return@withContext BinanceOrderPlacementResult(success = false, errorMessage = "Giá coin hiện tại không hợp lệ (bằng 0). Lệnh bị hủy để bảo vệ tài khoản.")
        }

        if (System.currentTimeMillis() - lastTimeSync > 60_000L || serverTimeOffset == 0L) syncServerTimeOffset(baseUrl)

        val filter = fetchSymbolFilter(upperSymbol, baseUrl)
        val autoFixes = mutableListOf<String>()
        var workingAmount = kotlin.math.abs(targetUsdtAmount)

        if (!reduceOnly && exactQuantity == null && workingAmount < filter.minNotional) {
            val adjusted = filter.minNotional + 0.5
            autoFixes.add("Nâng Margin đạt chuẩn sàn (${filter.minNotional} USDT)")
            workingAmount = adjusted
        }

        // BẮT BUỘC DÙNG ABS() ĐỂ LUÔN GỬI SỐ DƯƠNG CHO BINANCE DÙ LÀ ĐÓNG LỆNH SHORT
        var calculatedQty = kotlin.math.abs(exactQuantity ?: (workingAmount / approxPrice))

        if (!reduceOnly && calculatedQty < filter.minQty) {
            calculatedQty = filter.minQty
            autoFixes.add("Chỉnh lên lượng Min Qty: ${filter.minQty}")
        }

        var formattedQty = roundToStepSize(calculatedQty, filter.stepSize)
        if (!reduceOnly && (formattedQty.toDoubleOrNull() ?: 0.0) < filter.minQty) {
            formattedQty = BigDecimal(filter.minQty.toString()).stripTrailingZeros().toPlainString()
        }

        var lastError = ""
        val emptyBody = ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaType())

        for (attempt in 1..3) {
            val qs = "symbol=$upperSymbol&side=$side&type=MARKET&quantity=$formattedQty&newClientOrderId=ais_${System.currentTimeMillis()}${if (reduceOnly) "&reduceOnly=true" else ""}&recvWindow=60000&timestamp=${getSyncedTimestamp()}"
            val sig = try { signHmacSha256(qs, trimmedSecret) } catch (e: Exception) { return@withContext BinanceOrderPlacementResult(false, errorMessage = "Lỗi HMAC: ${e.message}") }

            try {
                val req = Request.Builder().url("$baseUrl/fapi/v1/order?$qs&signature=$sig").header("X-MBX-APIKEY", trimmedKey).post(emptyBody).build()
                val resp = BinanceClient.okHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                if (resp.isSuccessful) {
                    val j = JSONObject(body)
                    val oId = j.optLong("orderId", 0L)
                    val checkDetail = checkOrderStatus(upperSymbol, oId, trimmedKey, trimmedSecret, isTestnet)
                    return@withContext BinanceOrderPlacementResult(
                        success = true,
                        orderId = oId,
                        clientOrderId = j.optString("clientOrderId", ""),
                        status = if (checkDetail.isVerified) checkDetail.status else j.optString("status", "FILLED"),
                        executedQty = if (checkDetail.executedQty > 0.0) checkDetail.executedQty else j.optDouble("executedQty", formattedQty.toDoubleOrNull() ?: 0.0),
                        avgPrice = if (checkDetail.avgPrice > 0.0) checkDetail.avgPrice else approxPrice,
                        autoFixApplied = if (autoFixes.isNotEmpty()) autoFixes.joinToString("; ") else null,
                        isVerifiedOnExchange = true
                    )
                } else {
                    val errCode = try { JSONObject(body).optInt("code", 0) } catch (_: Exception) { 0 }
                    lastError = "Binance ($errCode): $body"
                    when (errCode) {
                        -1021 -> syncServerTimeOffset(baseUrl)
                        -1111, -1013 -> {
                            val dotIndex = formattedQty.indexOf('.')
                            if (dotIndex >= 0 && formattedQty.length - dotIndex - 1 > 0) {
                                formattedQty = BigDecimal(formattedQty).setScale(formattedQty.length - dotIndex - 2, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
                            }
                        }
                        -4164 -> formattedQty = roundToStepSize((formattedQty.toDoubleOrNull() ?: filter.minQty) * 1.35, filter.stepSize)
                        else -> break
                    }
                }
            } catch (e: Exception) { lastError = "Lỗi mạng: ${e.message}" }
        }
        return@withContext BinanceOrderPlacementResult(success = false, autoFixApplied = autoFixes.joinToString("; "), errorMessage = lastError)
    }

suspend fun setLeverage(
        symbol: String,
        leverage: Int = 1,
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        val targetLeverage = leverage.coerceIn(1, 50)
        val cleanSymbol = symbol.trim().uppercase()
        val qs = "symbol=$cleanSymbol&leverage=$targetLeverage&recvWindow=60000&timestamp=${getSyncedTimestamp()}"
        val sig = try {
            signHmacSha256(qs, apiSecret.trim())
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Lỗi ký HMAC SHA256"))
        }

        try {
            val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
            val req = Request.Builder()
                .url("$baseUrl/fapi/v1/leverage?$qs&signature=$sig")
                .header("X-MBX-APIKEY", apiKey.trim())
                .post(ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            val resp = BinanceClient.okHttpClient.newCall(req).execute()
            resp.use { r ->
                if (r.isSuccessful) {
                    val bodyStr = r.body?.string() ?: ""
                    val appliedLeverage = JSONObject(bodyStr).optInt("leverage", targetLeverage)
                    Result.success(appliedLeverage)
                } else {
                    Result.failure(Exception("Lỗi Binance set leverage: HTTP ${r.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTakeProfitAndStopLoss(symbol: String, isLong: Boolean, takeProfitPrice: Double?, stopLossPrice: Double?, apiKey: String, apiSecret: String, isTestnet: Boolean): BinanceTpSlResult = withContext(Dispatchers.IO) {
        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val filter = fetchSymbolFilter(symbol.uppercase(), baseUrl)
        val exitSide = if (isLong) "SELL" else "BUY"
        val emptyBody = ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaType())

        var tpOrderId: Long? = null
        var slOrderId: Long? = null
        val errors = mutableListOf<String>()

        if (takeProfitPrice != null && takeProfitPrice > 0.0) {
            val formattedTp = roundToStepSize(takeProfitPrice, filter.tickSize)
            val qsTp = "symbol=${symbol.uppercase()}&side=$exitSide&type=TAKE_PROFIT_MARKET&stopPrice=$formattedTp&closePosition=true&recvWindow=60000&timestamp=${getSyncedTimestamp()}"
            try {
                val reqTp = Request.Builder().url("$baseUrl/fapi/v1/order?$qsTp&signature=${signHmacSha256(qsTp, apiSecret.trim())}").header("X-MBX-APIKEY", apiKey.trim()).post(emptyBody).build()
                val resp = BinanceClient.okHttpClient.newCall(reqTp).execute()
                if (resp.isSuccessful) tpOrderId = JSONObject(resp.body?.string() ?: "").optLong("orderId", 0L) else errors.add("TP Error: ${resp.body?.string()}")
            } catch (e: Exception) { errors.add("TP Net Error: ${e.message}") }
        }

        if (stopLossPrice != null && stopLossPrice > 0.0) {
            val formattedSl = roundToStepSize(stopLossPrice, filter.tickSize)
            val qsSl = "symbol=${symbol.uppercase()}&side=$exitSide&type=STOP_MARKET&stopPrice=$formattedSl&closePosition=true&recvWindow=60000&timestamp=${getSyncedTimestamp()}"
            try {
                val reqSl = Request.Builder().url("$baseUrl/fapi/v1/order?$qsSl&signature=${signHmacSha256(qsSl, apiSecret.trim())}").header("X-MBX-APIKEY", apiKey.trim()).post(emptyBody).build()
                val resp = BinanceClient.okHttpClient.newCall(reqSl).execute()
                if (resp.isSuccessful) slOrderId = JSONObject(resp.body?.string() ?: "").optLong("orderId", 0L) else errors.add("SL Error: ${resp.body?.string()}")
            } catch (e: Exception) { errors.add("SL Net Error: ${e.message}") }
        }

        BinanceTpSlResult(tpSuccess = tpOrderId != null, slSuccess = slOrderId != null, tpOrderId = tpOrderId, slOrderId = slOrderId, errorMessage = if (errors.isNotEmpty()) errors.joinToString() else null)
    }

    suspend fun postMarketCloseOrder(symbol: String, amount: Double, approxPrice: Double, apiKey: String, apiSecret: String, isTestnet: Boolean = false, side: String = "SELL"): BinanceOrderPlacementResult {
        // Tự động tính giá trị Dương an toàn tuyệt đối
        val absAmount = kotlin.math.abs(amount)
        return postMarketOrder(
            symbol = symbol, side = side,
            targetUsdtAmount = absAmount * approxPrice, approxPrice = approxPrice,
            apiKey = apiKey, apiSecret = apiSecret, isTestnet = isTestnet,
            reduceOnly = true, exactQuantity = absAmount
        )
    }
}