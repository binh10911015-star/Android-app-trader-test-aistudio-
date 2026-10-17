package com.example.network

import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceAssetBalance
import com.example.model.BinanceLivePosition
import com.example.model.BinanceOpenOrderItem
import com.example.model.BinanceOrderPlacementResult
import com.example.model.BinanceOrderVerificationDetail
import com.example.model.BinancePortfolio
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
    private const val TESTNET_BASE_URL = "https://testnet.binancefuture.com"

    private var serverTimeOffset: Long = 0L
    private var lastTimeSync: Long = 0L
    private val symbolFiltersCache = ConcurrentHashMap<String, SymbolExchangeFilter>()

    /**
     * Đồng bộ thời gian với máy chủ Binance (/fapi/v1/time) để khắc phục triệt để lỗi lệch giờ (-1021)
     */
    suspend fun syncServerTimeOffset(baseUrl: String = TESTNET_BASE_URL): Long = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/fapi/v1/time")
                .get()
                .build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val serverTime = json.optLong("serverTime", 0L)
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

    /**
     * Lấy bộ lọc quy tắc khớp lệnh của cặp giao dịch (stepSize, minQty, tickSize, minNotional)
     */
    suspend fun fetchSymbolFilter(symbol: String, baseUrl: String): SymbolExchangeFilter = withContext(Dispatchers.IO) {
        val upper = symbol.uppercase()
        symbolFiltersCache[upper]?.let { return@withContext it }

        try {
            val request = Request.Builder()
                .url("$baseUrl/fapi/v1/exchangeInfo")
                .get()
                .build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val symbolsArr = json.optJSONArray("symbols")
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
                                val fType = f.optString("filterType", "")
                                when (fType) {
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
                        symbolFiltersCache[sName] = SymbolExchangeFilter(
                            symbol = sName,
                            stepSize = stepSize,
                            minQty = minQty,
                            tickSize = tickSize,
                            minNotional = minNotional
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext symbolFiltersCache[upper] ?: SymbolExchangeFilter(upper)
    }

    /**
     * Tự động làm tròn số lượng theo bước giá (stepSize) chuẩn xác bằng BigDecimal để loại bỏ lỗi -1111 / -1013
     */
    fun roundToStepSize(quantity: Double, stepSize: Double): String {
        if (stepSize <= 0.0) return String.format(Locale.US, "%.3f", quantity)
        val stepBd = BigDecimal(stepSize.toString()).stripTrailingZeros()
        val scale = stepBd.scale().coerceAtLeast(0)

        val qtyBd = BigDecimal(quantity.toString())
        val steps = qtyBd.divide(stepBd, 0, RoundingMode.DOWN)
        val adjusted = steps.multiply(stepBd).setScale(scale, RoundingMode.DOWN)
        return adjusted.toPlainString()
    }

    /**
     * Signs query parameters using HMAC-SHA256 according to Binance Futures API specifications.
     */
    fun signHmacSha256(queryString: String, apiSecret: String): String {
        val sha256Hmac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256Hmac.init(secretKey)
        val hashBytes = sha256Hmac.doFinal(queryString.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Lấy địa chỉ IP công khai thực tế của thiết bị (Public Egress IP)
     * để người dùng đưa vào IP Whitelist hạn chế quyền truy cập trên Binance API.
     */
    suspend fun fetchPublicIpAddress(): String? = withContext(Dispatchers.IO) {
        // Thử nguồn 1: api.ipify.org
        try {
            val request = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .get()
                .build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val ip = json.optString("ip", "").trim()
                if (ip.isNotEmpty()) return@withContext ip
            }
        } catch (_: Exception) {
            // Thử tiếp nguồn dự phòng
        }

        // Thử nguồn 2: checkip.amazonaws.com
        try {
            val request = Request.Builder()
                .url("https://checkip.amazonaws.com")
                .get()
                .build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val ip = response.body?.string()?.trim() ?: ""
                if (ip.isNotEmpty()) return@withContext ip
            }
        } catch (_: Exception) {
            // Thử tiếp nguồn dự phòng 3
        }

        // Thử nguồn 3: icanhazip.com
        try {
            val request = Request.Builder()
                .url("https://icanhazip.com")
                .get()
                .build()
            val response = BinanceClient.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val ip = response.body?.string()?.trim() ?: ""
                if (ip.isNotEmpty()) return@withContext ip
            }
        } catch (_: Exception) {
            // Không lấy được từ nguồn 3
        }
        null
    }

    /**
     * Verifies the user's Binance Futures API Key and Secret by calling GET /fapi/v2/account.
     * Returns live balance and trading permissions or human-friendly localized error messages.
     */
    suspend fun verifyAccount(
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean
    ): BinanceAccountVerificationResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()

        if (trimmedKey.isEmpty()) {
            return@withContext BinanceAccountVerificationResult(
                isSuccess = false,
                errorMessage = "API Key không được để trống."
            )
        }

        if (trimmedSecret.isEmpty()) {
            return@withContext BinanceAccountVerificationResult(
                isSuccess = false,
                errorMessage = "Secret Key không được để trống."
            )
        }

        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val timestamp = System.currentTimeMillis()
        val queryString = "recvWindow=5000&timestamp=$timestamp"

        val signature = try {
            signHmacSha256(queryString, trimmedSecret)
        } catch (e: Exception) {
            return@withContext BinanceAccountVerificationResult(
                isSuccess = false,
                errorMessage = "Lỗi tính toán chữ ký số HMAC-SHA256: ${e.message}"
            )
        }

        val fullUrl = "$baseUrl/fapi/v2/account?$queryString&signature=$signature"

        try {
            val request = Request.Builder()
                .url(fullUrl)
                .header("X-MBX-APIKEY", trimmedKey)
                .get()
                .build()

            val response = BinanceClient.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val totalWallet = json.optDouble("totalWalletBalance", 0.0)
                val totalMargin = json.optDouble("totalMarginBalance", 0.0)
                val canTrade = json.optBoolean("canTrade", false)
                val canDeposit = json.optBoolean("canDeposit", false)
                val canWithdraw = json.optBoolean("canWithdraw", false)

                var activePositions = 0
                val positionsArr = json.optJSONArray("positions")
                if (positionsArr != null) {
                    for (i in 0 until positionsArr.length()) {
                        val pos = positionsArr.getJSONObject(i)
                        val amt = pos.optDouble("positionAmt", 0.0)
                        if (amt != 0.0) {
                            activePositions++
                        }
                    }
                }

                BinanceAccountVerificationResult(
                    isSuccess = true,
                    totalWalletBalance = totalWallet,
                    availableBalance = totalMargin,
                    canTrade = canTrade,
                    canDeposit = canDeposit,
                    canWithdraw = canWithdraw,
                    openPositionsCount = activePositions
                )
            } else {
                val errorMsg = try {
                    val errorJson = JSONObject(responseBody)
                    val code = errorJson.optInt("code", 0)
                    val msg = errorJson.optString("msg", "Lỗi không xác định")
                    when (code) {
                        -2014 -> "API Key không hợp lệ. Vui lòng kiểm tra lại định dạng khóa."
                        -2015 -> "API Key hoặc Secret không đúng, chưa bật quyền 'Enable Futures' hoặc bị chặn bởi IP Whitelist."
                        -1021 -> "Lệch thời gian thiết bị (Timestamp error). Vui lòng đồng bộ ngày giờ tự động trên điện thoại."
                        -1002 -> "Bạn chưa được cấp quyền truy cập tính năng này trên tài khoản Binance."
                        else -> "Binance lỗi ($code): $msg"
                    }
                } catch (e: Exception) {
                    "Lỗi máy chủ Binance (${response.code}). Vui lòng kiểm tra lại kết nối mạng."
                }

                BinanceAccountVerificationResult(
                    isSuccess = false,
                    errorMessage = errorMsg
                )
            }
        } catch (e: Exception) {
            BinanceAccountVerificationResult(
                isSuccess = false,
                errorMessage = "Không thể kết nối đến Binance Futures: ${e.localizedMessage ?: "Vui lòng kiểm tra Internet"}"
            )
        }
    }

    /**
     * Tải toàn bộ danh mục tài khoản từ Binance Futures:
     * - Tổng quan số dư ký quỹ & số dư khả dụng
     * - Chi tiết tài sản từng đồng (assets: USDT, BNB, BTC...)
     * - Toàn bộ vị thế hợp đồng đang mở (positions với positionAmt != 0)
     * - Toàn bộ lệnh chờ đang treo trên sổ lệnh (openOrders)
     */
    suspend fun fetchFullPortfolio(
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean
    ): BinancePortfolio = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()

        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) {
            return@withContext BinancePortfolio(
                isLiveConnected = false,
                error = "Chưa cấu hình API Key và Secret Key."
            )
        }

        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val now = System.currentTimeMillis()

        // 1. Gọi /fapi/v2/account
        val accountQuery = "recvWindow=5000&timestamp=$now"
        val accountSig = try {
            signHmacSha256(accountQuery, trimmedSecret)
        } catch (e: Exception) {
            return@withContext BinancePortfolio(isLiveConnected = false, error = "Lỗi ký số: ${e.message}")
        }

        var totalWallet = 0.0
        var totalMargin = 0.0
        var totalUnrealizedProfit = 0.0
        var availableBalance = 0.0
        val assetsList = mutableListOf<BinanceAssetBalance>()
        val positionsList = mutableListOf<BinanceLivePosition>()

        try {
            val accountReq = Request.Builder()
                .url("$baseUrl/fapi/v2/account?$accountQuery&signature=$accountSig")
                .header("X-MBX-APIKEY", trimmedKey)
                .get()
                .build()

            val accountResp = BinanceClient.okHttpClient.newCall(accountReq).execute()
            val accountBody = accountResp.body?.string() ?: ""

            if (!accountResp.isSuccessful) {
                return@withContext BinancePortfolio(
                    isLiveConnected = false,
                    error = "Không thể tải danh mục Binance (${accountResp.code})"
                )
            }

            val json = JSONObject(accountBody)
            totalWallet = json.optDouble("totalWalletBalance", 0.0)
            totalMargin = json.optDouble("totalMarginBalance", 0.0)
            totalUnrealizedProfit = json.optDouble("totalUnrealizedProfit", 0.0)
            availableBalance = json.optDouble("availableBalance", 0.0)

            // Parse Assets
            val assetsArr = json.optJSONArray("assets")
            if (assetsArr != null) {
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.getJSONObject(i)
                    val assetName = a.optString("asset", "")
                    val walletBal = a.optDouble("walletBalance", 0.0)
                    val unPnl = a.optDouble("unrealizedProfit", 0.0)
                    val marginBal = a.optDouble("marginBalance", 0.0)
                    val maintMargin = a.optDouble("maintMargin", 0.0)
                    val initMargin = a.optDouble("initialMargin", 0.0)
                    val availBal = a.optDouble("availableBalance", 0.0)

                    // Lọc những tài sản có số dư hoặc coin chủ chốt
                    if (walletBal > 0.0 || unPnl != 0.0 || assetName in listOf("USDT", "BNB", "USDC", "BTC", "ETH")) {
                        assetsList.add(
                            BinanceAssetBalance(
                                asset = assetName,
                                walletBalance = walletBal,
                                unrealizedProfit = unPnl,
                                marginBalance = marginBal,
                                maintMargin = maintMargin,
                                initialMargin = initMargin,
                                availableBalance = availBal
                            )
                        )
                    }
                }
            }

            // Parse Positions
            val positionsArr = json.optJSONArray("positions")
            if (positionsArr != null) {
                for (i in 0 until positionsArr.length()) {
                    val p = positionsArr.getJSONObject(i)
                    val amt = p.optDouble("positionAmt", 0.0)
                    if (amt != 0.0) {
                        positionsList.add(
                            BinanceLivePosition(
                                symbol = p.optString("symbol", ""),
                                initialMargin = p.optDouble("initialMargin", 0.0),
                                maintMargin = p.optDouble("maintMargin", 0.0),
                                unrealizedProfit = p.optDouble("unrealizedProfit", 0.0),
                                positionInitialMargin = p.optDouble("positionInitialMargin", 0.0),
                                openOrderInitialMargin = p.optDouble("openOrderInitialMargin", 0.0),
                                leverage = p.optInt("leverage", 1),
                                isolated = p.optBoolean("isolated", false),
                                entryPrice = p.optDouble("entryPrice", 0.0),
                                markPrice = 0.0,
                                positionAmt = amt,
                                updateTime = p.optLong("updateTime", 0L)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext BinancePortfolio(isLiveConnected = false, error = e.localizedMessage)
        }

        // 2. Gọi /fapi/v1/openOrders để lấy các lệnh đang chờ khớp
        val openOrdersList = mutableListOf<BinanceOpenOrderItem>()
        try {
            val ordersTimestamp = System.currentTimeMillis()
            val ordersQuery = "recvWindow=5000&timestamp=$ordersTimestamp"
            val ordersSig = signHmacSha256(ordersQuery, trimmedSecret)

            val ordersReq = Request.Builder()
                .url("$baseUrl/fapi/v1/openOrders?$ordersQuery&signature=$ordersSig")
                .header("X-MBX-APIKEY", trimmedKey)
                .get()
                .build()

            val ordersResp = BinanceClient.okHttpClient.newCall(ordersReq).execute()
            val ordersBody = ordersResp.body?.string() ?: ""

            if (ordersResp.isSuccessful) {
                val arr = JSONArray(ordersBody)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    openOrdersList.add(
                        BinanceOpenOrderItem(
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
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Không làm gián đoạn việc hiển thị số dư và vị thế nếu lệnh chờ lỗi
        }

        BinancePortfolio(
            totalWalletBalance = totalWallet,
            totalMarginBalance = totalMargin,
            totalUnrealizedProfit = totalUnrealizedProfit,
            availableBalance = availableBalance,
            assets = assetsList,
            positions = positionsList,
            openOrders = openOrdersList,
            lastUpdatedTimestamp = System.currentTimeMillis(),
            isLiveConnected = true,
            error = null
        )
    }

    /**
     * Tra cứu chính xác trạng thái lệnh trực tiếp từ máy chủ Binance (GET /fapi/v1/order)
     * Xác thực lệnh đã được ghi nhận trên sàn, mã Binance Order ID, trạng thái khớp (FILLED/NEW/CANCELED).
     */
    suspend fun checkOrderStatus(
        symbol: String,
        orderId: Long,
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean
    ): BinanceOrderVerificationDetail = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()

        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) {
            return@withContext BinanceOrderVerificationDetail(
                orderId = orderId,
                clientOrderId = "",
                symbol = symbol,
                status = "LỖI",
                side = "",
                type = "",
                price = 0.0,
                origQty = 0.0,
                executedQty = 0.0,
                avgPrice = 0.0,
                time = 0L,
                updateTime = 0L,
                isVerified = false,
                message = "Chưa kết nối API Key và Secret Key."
            )
        }

        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val timestamp = System.currentTimeMillis()
        val queryString = "symbol=${symbol.uppercase()}&orderId=$orderId&recvWindow=5000&timestamp=$timestamp"

        val signature = try {
            signHmacSha256(queryString, trimmedSecret)
        } catch (e: Exception) {
            return@withContext BinanceOrderVerificationDetail(
                orderId = orderId,
                clientOrderId = "",
                symbol = symbol,
                status = "LỖI",
                side = "",
                type = "",
                price = 0.0,
                origQty = 0.0,
                executedQty = 0.0,
                avgPrice = 0.0,
                time = 0L,
                updateTime = 0L,
                isVerified = false,
                message = "Lỗi tính chữ ký HMAC-SHA256: ${e.message}"
            )
        }

        val fullUrl = "$baseUrl/fapi/v1/order?$queryString&signature=$signature"

        try {
            val request = Request.Builder()
                .url(fullUrl)
                .header("X-MBX-APIKEY", trimmedKey)
                .get()
                .build()

            val response = BinanceClient.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val returnedOrderId = json.optLong("orderId", orderId)
                val clientOrderId = json.optString("clientOrderId", "")
                val status = json.optString("status", "NEW")
                val side = json.optString("side", "BUY")
                val type = json.optString("type", "MARKET")
                val price = json.optDouble("price", 0.0)
                val origQty = json.optDouble("origQty", 0.0)
                val executedQty = json.optDouble("executedQty", 0.0)
                val avgPrice = json.optDouble("avgPrice", 0.0)
                val time = json.optLong("time", 0L)
                val updateTime = json.optLong("updateTime", 0L)

                BinanceOrderVerificationDetail(
                    orderId = returnedOrderId,
                    clientOrderId = clientOrderId,
                    symbol = symbol,
                    status = status,
                    side = side,
                    type = type,
                    price = price,
                    origQty = origQty,
                    executedQty = executedQty,
                    avgPrice = avgPrice,
                    time = time,
                    updateTime = updateTime,
                    isVerified = true,
                    message = "Đã xác thực thành công trên máy chủ Binance Futures!"
                )
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    val code = errJson.optInt("code", 0)
                    val msg = errJson.optString("msg", "")
                    "Binance ($code): $msg"
                } catch (_: Exception) {
                    "Lỗi phản hồi Binance (HTTP ${response.code})"
                }

                BinanceOrderVerificationDetail(
                    orderId = orderId,
                    clientOrderId = "",
                    symbol = symbol,
                    status = "KHÔNG TÌM THẤY",
                    side = "",
                    type = "",
                    price = 0.0,
                    origQty = 0.0,
                    executedQty = 0.0,
                    avgPrice = 0.0,
                    time = 0L,
                    updateTime = 0L,
                    isVerified = false,
                    message = errorMsg
                )
            }
        } catch (e: Exception) {
            BinanceOrderVerificationDetail(
                orderId = orderId,
                clientOrderId = "",
                symbol = symbol,
                status = "LỖI MẠNG",
                side = "",
                type = "",
                price = 0.0,
                origQty = 0.0,
                executedQty = 0.0,
                avgPrice = 0.0,
                time = 0L,
                updateTime = 0L,
                isVerified = false,
                message = "Không thể kết nối máy chủ Binance: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Gửi lệnh trực tiếp lên Binance Demo / Mainnet (POST /fapi/v1/order).
     * Tự động sửa lỗi:
     * 1. Đồng bộ thời gian máy chủ Binance (xóa bỏ lỗi lệch giờ -1021).
     * 2. Tự điều chỉnh bước giá (stepSize/LOT_SIZE) và số chữ số thập phân chuẩn xác (lỗi -1111, -1013).
     * 3. Tự tăng khối lượng nếu thấp hơn ngưỡng tối thiểu MIN_NOTIONAL (lỗi -4164).
     * 4. Thực hiện kiểm tra đối soát trạng thái tức thì trên Binance Demo sau khi gửi.
     */
    suspend fun postMarketOrder(
        symbol: String,
        side: String = "BUY",
        targetUsdtAmount: Double,
        approxPrice: Double,
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean = true,
        reduceOnly: Boolean = false,
        exactQuantity: Double? = null
    ): BinanceOrderPlacementResult = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = apiSecret.trim()
        val baseUrl = if (isTestnet) TESTNET_BASE_URL else MAINNET_BASE_URL
        val upperSymbol = symbol.uppercase()

        if (trimmedKey.isEmpty() || trimmedSecret.isEmpty()) {
            return@withContext BinanceOrderPlacementResult(
                success = false,
                errorMessage = "Chưa kết nối API Key và Secret Key."
            )
        }

        // 1. Luôn bảo đảm đồng bộ giờ với máy chủ Binance nếu quá 60 giây
        if (System.currentTimeMillis() - lastTimeSync > 60_000L || serverTimeOffset == 0L) {
            syncServerTimeOffset(baseUrl)
        }

        // 2. Lấy bộ lọc quy tắc khớp lệnh cho cặp tiền
        val filter = fetchSymbolFilter(upperSymbol, baseUrl)
        val effectivePrice = if (approxPrice > 0.0) approxPrice else 1.0

        val autoFixes = mutableListOf<String>()
        var workingAmount = targetUsdtAmount

        // Tự động sửa lỗi 1: Min Notional (Giá trị lệnh tối thiểu)
        if (!reduceOnly && exactQuantity == null && workingAmount < filter.minNotional) {
            val adjusted = filter.minNotional + 0.5 // Thêm 0.5 USDT biên độ an toàn
            autoFixes.add("Tự nâng khối lượng đạt chuẩn sàn tối thiểu ${filter.minNotional} USDT (thực tế ${String.format(Locale.US, "%.1f", adjusted)} USDT)")
            workingAmount = adjusted
        }

        var calculatedQty = exactQuantity ?: (workingAmount / effectivePrice)

        // Tự động sửa lỗi 2: Min Qty
        if (calculatedQty < filter.minQty) {
            calculatedQty = filter.minQty
            autoFixes.add("Tự chỉnh số lượng lên mức min ${filter.minQty}")
        }

        var formattedQty = roundToStepSize(calculatedQty, filter.stepSize)
        val parsedQty = formattedQty.toDoubleOrNull() ?: 0.0
        if (parsedQty < filter.minQty) {
            formattedQty = BigDecimal(filter.minQty.toString()).stripTrailingZeros().toPlainString()
            autoFixes.add("Làm tròn khớp bước giá sàn (${filter.stepSize})")
        }

        var lastError = ""

        // Vòng lặp gửi lệnh tối đa 3 lần với cơ chế tự động sửa lỗi
        for (attempt in 1..3) {
            val timestamp = getSyncedTimestamp()
            val clientOrderId = "ais_${System.currentTimeMillis()}"
            val reduceParam = if (reduceOnly) "&reduceOnly=true" else ""
            val queryString = "symbol=$upperSymbol&side=$side&type=MARKET&quantity=$formattedQty&newClientOrderId=$clientOrderId$reduceParam&recvWindow=60000&timestamp=$timestamp"

            val signature = try {
                signHmacSha256(queryString, trimmedSecret)
            } catch (e: Exception) {
                return@withContext BinanceOrderPlacementResult(
                    success = false,
                    errorMessage = "Lỗi tạo chữ ký số HMAC-SHA256: ${e.message}"
                )
            }

            val request = Request.Builder()
                .url("$baseUrl/fapi/v1/order?$queryString&signature=$signature")
                .header("X-MBX-APIKEY", trimmedKey)
                .post(ByteArray(0).toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            try {
                val response = BinanceClient.okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val orderId = json.optLong("orderId", 0L)
                    val retClientOrderId = json.optString("clientOrderId", clientOrderId)
                    val status = json.optString("status", "FILLED")
                    val executedQty = json.optDouble("executedQty", formattedQty.toDoubleOrNull() ?: 0.0)
                    val cumQuote = json.optDouble("cumQuote", 0.0)
                    val avgPrice = if (executedQty > 0.0 && cumQuote > 0.0) cumQuote / executedQty else effectivePrice

                    // 4. Đối soát và kiểm tra ngay lập tức trên máy chủ Binance
                    val checkDetail = checkOrderStatus(upperSymbol, orderId, trimmedKey, trimmedSecret, isTestnet)

                    return@withContext BinanceOrderPlacementResult(
                        success = true,
                        orderId = orderId,
                        clientOrderId = retClientOrderId,
                        status = if (checkDetail.isVerified) checkDetail.status else status,
                        executedQty = if (checkDetail.executedQty > 0.0) checkDetail.executedQty else executedQty,
                        avgPrice = if (checkDetail.avgPrice > 0.0) checkDetail.avgPrice else avgPrice,
                        autoFixApplied = if (autoFixes.isNotEmpty()) autoFixes.joinToString("; ") else null,
                        errorMessage = null,
                        isVerifiedOnExchange = true
                    )
                } else {
                    val errJson = try { JSONObject(responseBody) } catch (_: Exception) { null }
                    val code = errJson?.optInt("code", 0) ?: 0
                    val msg = errJson?.optString("msg", "") ?: responseBody
                    lastError = "Binance ($code): $msg"

                    when (code) {
                        -1021 -> {
                            // Lỗi timestamp ngoài recvWindow -> Đồng bộ lại giờ tức thì
                            syncServerTimeOffset(baseUrl)
                            autoFixes.add("Tự động đồng bộ lại giờ máy chủ Binance (Lỗi -1021)")
                        }
                        -1111, -1013 -> {
                            // Lỗi bước giá hoặc precision -> Cắt giảm bớt 1 chữ số thập phân
                            val dotIndex = formattedQty.indexOf('.')
                            if (dotIndex >= 0) {
                                val currentDecimals = formattedQty.length - dotIndex - 1
                                if (currentDecimals > 0) {
                                    val newScale = currentDecimals - 1
                                    val bd = BigDecimal(formattedQty).setScale(newScale, RoundingMode.DOWN)
                                    formattedQty = bd.stripTrailingZeros().toPlainString()
                                    autoFixes.add("Tự sửa số thập phân bước giá (${newScale} số sau dấu phẩy)")
                                }
                            }
                        }
                        -4164 -> {
                            // Lỗi Notional không đủ -> Tăng khối lượng 35%
                            val cur = formattedQty.toDoubleOrNull() ?: filter.minQty
                            val bumped = cur * 1.35
                            formattedQty = roundToStepSize(bumped, filter.stepSize)
                            autoFixes.add("Tự tăng 35% khối lượng đạt giá trị tối thiểu của Binance")
                        }
                        else -> {
                            // Không thể tự phục hồi (ví dụ: Số dư không đủ -2019, v.v.)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = "Lỗi kết nối: ${e.localizedMessage}"
            }
        }

        return@withContext BinanceOrderPlacementResult(
            success = false,
            autoFixApplied = if (autoFixes.isNotEmpty()) autoFixes.joinToString("; ") else null,
            errorMessage = lastError
        )
    }

    /**
     * Đóng vị thế Binance thực tế bằng lệnh MARKET đối ứng (reduceOnly = true)
     */
    suspend fun postMarketCloseOrder(
        symbol: String,
        amount: Double,
        approxPrice: Double,
        apiKey: String,
        apiSecret: String,
        isTestnet: Boolean = true
    ): BinanceOrderPlacementResult {
        return postMarketOrder(
            symbol = symbol,
            side = "SELL",
            targetUsdtAmount = amount * approxPrice,
            approxPrice = approxPrice,
            apiKey = apiKey,
            apiSecret = apiSecret,
            isTestnet = isTestnet,
            reduceOnly = true,
            exactQuantity = amount
        )
    }
}
