package com.example.network

import android.util.Log
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

data class MiniTickerUpdate(
    val symbol: String,
    val closePrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Double
)

/**
 * Luồng Binance Futures WebSocket chuyên dụng:
 * Nhận dữ liệu giá Realtime từng giây (0ms REST overhead, không bao giờ bị dính Rate Limit 429/418)
 */
class BinanceWebSocketClient(
    private val onTickersUpdate: (List<MiniTickerUpdate>) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var currentTestnet: Boolean = false

    companion object {
        private const val TAG = "BinanceWebSocket"
        private const val MAINNET_WS_URL = "wss://fstream.binance.com/ws/!miniTicker@arr"
        private const val TESTNET_WS_URL = "wss://fstream.binancefuture.com/ws/!miniTicker@arr"
    }

    fun start(isTestnet: Boolean = false) {
        currentTestnet = isTestnet
        isRunning.set(true)
        val wsUrl = if (isTestnet) TESTNET_WS_URL else MAINNET_WS_URL
        connect(wsUrl)
    }

    private fun connect(url: String) {
        try {
            webSocket?.close(1000, "Reconnecting")
            val request = Request.Builder().url(url).build()
            webSocket = BinanceClient.okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "Binance Futures WebSocket Connected: $url")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseMiniTickers(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket failure: ${t.message}. Reconnecting in 5s...")
                    scheduleReconnect(url)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $reason ($code)")
                    if (isRunning.get()) {
                        scheduleReconnect(url)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting WebSocket: ${e.message}")
            scheduleReconnect(url)
        }
    }

    private fun parseMiniTickers(jsonString: String) {
        try {
            val jsonArray = JSONArray(jsonString)
            val updates = ArrayList<MiniTickerUpdate>(jsonArray.length())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val symbol = obj.optString("s", "")
                if (symbol.endsWith("USDT") && !symbol.contains("_")) {
                    val close = obj.optString("c", "0").toDoubleOrNull() ?: 0.0
                    val open = obj.optString("o", "0").toDoubleOrNull() ?: 0.0
                    val high = obj.optString("h", "0").toDoubleOrNull() ?: 0.0
                    val low = obj.optString("l", "0").toDoubleOrNull() ?: 0.0
                    val volume = obj.optString("v", "0").toDoubleOrNull() ?: 0.0
                    if (close > 0.0) {
                        updates.add(MiniTickerUpdate(symbol, close, open, high, low, volume))
                    }
                }
            }
            if (updates.isNotEmpty()) {
                onTickersUpdate(updates)
            }
        } catch (_: Exception) {}
    }

    private fun scheduleReconnect(url: String) {
        if (!isRunning.get()) return
        Thread {
            try {
                Thread.sleep(5000L)
                if (isRunning.get()) {
                    connect(url)
                }
            } catch (_: InterruptedException) {}
        }.start()
    }

    fun stop() {
        isRunning.set(false)
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (_: Exception) {}
        webSocket = null
    }
}
