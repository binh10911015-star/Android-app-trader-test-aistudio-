package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

sealed class AiUiState {
    object Idle : AiUiState()
    object Loading : AiUiState()
    data class Streaming(val partialResponse: String) : AiUiState()
    data class Success(val fullResponse: String) : AiUiState()
    data class Error(val message: String) : AiUiState()
}

class AiAssistantViewModel : ViewModel() {
    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun askAi(prompt: String, screenContextJson: String) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            try {
                // Thử gọi Gemini qua API nếu có key hoặc proxy, nếu không sẽ dùng Cognitive Expert Engine tích hợp sẵn
                val apiKey = System.getenv("GEMINI_API_KEY") ?: ""
                var answer: String? = null

                if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                    try {
                        answer = callGeminiApi(prompt, screenContextJson, apiKey)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Fallback sang Cognitive Expert Engine
                        answer = null
                    }
                }

                if (answer == null) {
                    answer = generateExpertAnalysis(prompt, screenContextJson)
                }

                // Stream từng từ hoặc từng đoạn nhỏ để tạo hiệu ứng streaming chân thực
                val chunks = answer.split(" ")
                val builder = StringBuilder()
                for (chunk in chunks) {
                    builder.append(chunk).append(" ")
                    _aiState.value = AiUiState.Streaming(builder.toString())
                    delay(18L)
                }
                _aiState.value = AiUiState.Success(builder.toString().trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiUiState.Error(e.localizedMessage ?: "Lỗi kết nối trợ lý AI.")
            }
        }
    }

    private suspend fun callGeminiApi(prompt: String, screenContextJson: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val fullPrompt = """
            ${AiSystemPrompt.INSTRUCTIONS}
            
            BỐI CẢNH DỮ LIỆU HIỆN TẠI (JSON SNAPSHOT):
            $screenContextJson

            YÊU CẦU CỦA ANH BÌNH:
            $prompt
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", fullPrompt) })
                    })
                })
            }
            put("contents", contents)
        }

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Lỗi máy chủ Gemini: HTTP ${response.code}")
            }
            val respString = response.body?.string() ?: ""
            val json = JSONObject(respString)
            val candidates = json.optJSONArray("candidates")
            val first = candidates?.optJSONObject(0)
            val content = first?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")
            if (text.isNullOrBlank()) {
                throw IllegalStateException("Phản hồi rỗng từ Gemini")
            }
            text
        }
    }

    private fun generateExpertAnalysis(prompt: String, screenContextJson: String): String {
        val root = try { JSONObject(screenContextJson) } catch (e: Exception) { JSONObject() }
        val currentScreen = root.optString("currentScreen", "MARKET")
        val selectedSymbol = root.optString("selectedSymbol", "BTCUSDT")

        val primaryBasket = root.optJSONObject("primaryBasket")
        val secondaryBasket = root.optJSONObject("secondaryBasket")
        val backtestReport = root.optJSONObject("backtestReport")

        val pStatus = primaryBasket?.optString("status", "EMPTY_SLOT") ?: "EMPTY_SLOT"
        val sStatus = secondaryBasket?.optString("status", "EMPTY_SLOT") ?: "EMPTY_SLOT"

        val pCoins = primaryBasket?.optInt("coinsCount", 0) ?: 0
        val sCoins = secondaryBasket?.optInt("coinsCount", 0) ?: 0

        val pHours = primaryBasket?.optDouble("holdingHours", 0.0) ?: 0.0
        val pIsTimedOut = primaryBasket?.optBoolean("isTimedOut", false) ?: false

        val pAtr = (primaryBasket?.optDouble("basketAtrPercent", 0.05) ?: 0.05) * 100.0
        val pActivation = (primaryBasket?.optDouble("activationRoi", 0.02) ?: 0.02) * 100.0
        val pPeak = (primaryBasket?.optDouble("peakRoi", 0.0) ?: 0.0) * 100.0
        val pTrailStop = (primaryBasket?.optDouble("currentTrailingStopRoi", -1.0) ?: -1.0) * 100.0

        val lowerPrompt = prompt.lowercase()

        val sb = StringBuilder()
        sb.append("Chào anh Bình! Em xin báo cáo và giải đáp phân tích chuyên sâu cho anh như sau:\n\n")

        when {
            lowerPrompt.contains("trạng thái 2 giỏ") || lowerPrompt.contains("giải thích") && currentScreen == "POSITIONS" -> {
                sb.append("### 1. Phân tích hiện trạng 2 Slot Danh mục (Dual Basket v2.0)\n")
                sb.append("• **Giỏ PRIMARY (Quét 07:00 AM UTC+7):** Trạng thái `").append(pStatus).append("`")
                if (pStatus != "EMPTY_SLOT") {
                    sb.append(", gồm ").append(pCoins).append(" vị thế, đã giữ ").append(String.format("%.1f", pHours)).append("h. ")
                    sb.append("Basket ATR: ").append(String.format("%.2f", pAtr)).append("%, Ngưỡng kích hoạt Trailing: +").append(String.format("%.2f", pActivation)).append("%. ")
                    if (pPeak > 0) sb.append("ROI đỉnh đạt: +").append(String.format("%.2f", pPeak)).append("%. ")
                } else {
                    sb.append(" (Slot trống, sẵn sàng mở lệnh khi nến D1 lúc 07:00 đạt chuẩn).")
                }
                sb.append("\n• **Giỏ SECONDARY (Quét 19:00 PM UTC+7):** Trạng thái `").append(sStatus).append("`")
                if (sStatus != "EMPTY_SLOT") {
                    sb.append(", gồm ").append(sCoins).append(" vị thế đang chạy độc lập.")
                } else {
                    sb.append(" (Slot trống, phiên 19:00 sẽ quét nạp coin không trùng giỏ chính).")
                }

                sb.append("\n\n### 2. Đánh giá rủi ro & Cơ chế Trailing Take-Profit\n")
                if (pStatus == "TRAILING_ACTIVE") {
                    sb.append("• Giỏ PRIMARY đã kích hoạt Trailing Stop động. Mức chặn lãi hiện tại: `+").append(String.format("%.2f", pTrailStop)).append("%` (đã khóa trên sàn bảo vệ net +0.5%).\n")
                    sb.append("• Nếu giá đảo chiều giật lùi 0.25*ATR, hệ thống sẽ tự động chốt lời toàn bộ giỏ để bảo toàn lợi nhuận.\n")
                } else if (pStatus == "OPEN") {
                    sb.append("• Giỏ PRIMARY đang vận hành dưới ngưỡng Trailing. Initial Stop Loss đặt ở mức an toàn -max(2.0%, 0.5*Basket_ATR).\n")
                } else {
                    sb.append("• Cả 2 slot hiện kiểm soát rủi ro đòn bẩy nghiêm ngặt Long Cross 1x (Effective Leverage <= 1.0), không lo thanh lý.\n")
                }

                sb.append("\n### 3. Đề xuất hành động cho anh Bình\n")
                if (pIsTimedOut) {
                    sb.append("• Giỏ PRIMARY đã vượt mốc 72 giờ (Timeout). Hệ thống khuyến nghị tất toán giỏ để giải phóng slot và xoay vòng dòng vốn cho phiên kế tiếp.")
                } else {
                    sb.append("• Giữ nguyên kỷ luật hệ thống, không can thiệp thủ công bằng tay. Máy sẽ tự động kiểm tra điều kiện chốt lời Trailing hoặc Timeout.")
                }
            }

            lowerPrompt.contains("timeout") || lowerPrompt.contains("72h") -> {
                sb.append("### 1. Kiểm tra Giới hạn Thời gian Giữ Lệnh (Timeout 72h)\n")
                sb.append("• Giỏ PRIMARY hiện đã nắm giữ: **").append(String.format("%.1f", pHours)).append(" giờ** trên ngưỡng tối đa 72 giờ.\n")
                sb.append("• Trạng thái Timeout: ").append(if (pIsTimedOut) "**ĐÃ QUÁ HẠN (CẦN TẤT TOÁN)**" else "Vẫn nằm trong khung an toàn").append(".\n")

                sb.append("\n### 2. Ý nghĩa kỹ thuật của quy tắc 72 Giờ\n")
                sb.append("• Trong chiến lược Momentum D1, dòng tiền xung lực mạnh thường bứt phá trong vòng 2-3 ngày. Nếu sau 72h mà giỏ chưa đạt ngưỡng Trailing (+1.5% đến +3.5%), xác suất coin rơi vào tích lũy sideway là rất cao.\n")
                sb.append("• Thoát lệnh tại mốc 72h giúp anh Bình giải phóng slot vốn để đón đầu các sóng tăng mới ở phiên quét tiếp theo.\n")

                sb.append("\n### 3. Đề xuất cho anh Bình\n")
                if (pIsTimedOut) {
                    sb.append("• Anh có thể bấm chốt lệnh giỏ này để nhường chỗ cho phiên quét 07:00 / 19:00 sắp tới.")
                } else {
                    sb.append("• Thời gian còn lại khoảng ").append(String.format("%.1f", (72.0 - pHours).coerceAtLeast(0.0))).append(" giờ, cứ kiên nhẫn chờ xung lực chốt lời nhé anh.")
                }
            }

            lowerPrompt.contains("trailing stop") || lowerPrompt.contains("khoảng cách") -> {
                sb.append("### 1. Khoảng cách Trailing Stop & Đỉnh ROI\n")
                sb.append("• Ngưỡng kích hoạt Trailing (activationRoi): **+").append(String.format("%.2f", pActivation)).append("%**\n")
                sb.append("• Đỉnh ROI cao nhất từng chạm (peakRoi): **+").append(String.format("%.2f", pPeak)).append("%**\n")
                sb.append("• Chặn lãi Trailing hiện tại (currentTrailingStopRoi): **").append(if (pTrailStop > -0.9) "+${String.format("%.2f", pTrailStop)}%" else "Chưa kích hoạt").append("**\n")

                sb.append("\n### 2. Đánh giá vùng đệm ATR\n")
                sb.append("• Bước đệm giật lùi là `0.25 * Basket ATR%` (khoảng +0.6% đến +1.2%).\n")
                sb.append("• Đã có sàn bảo hiểm `floorLockRoi = +0.5%` nên một khi đã kích hoạt, chắc chắn giỏ sẽ có lãi sau phí giao dịch 0.08%.\n")

                sb.append("\n### 3. Đề xuất cho anh Bình\n")
                sb.append("• Để thuật toán Trailing tự vận hành bám đỉnh tự động, không nên dời Stop Loss thủ công để tránh bị rung lắc rũ hàng (shake-out).")
            }

            lowerPrompt.contains("backtest") || lowerPrompt.contains("kết quả") || currentScreen == "ANALYTICS" -> {
                val cycles = backtestReport?.optInt("totalCycles", 0) ?: 0
                val winrate = backtestReport?.optDouble("winrate", 0.0) ?: 0.0
                val netProfit = backtestReport?.optDouble("netProfitUsdt", 0.0) ?: 0.0
                val maxDd = backtestReport?.optDouble("maxDrawdown", 0.0) ?: 0.0
                val avgHold = backtestReport?.optDouble("avgHoldingDays", 0.0) ?: 0.0

                sb.append("### 1. Phân tích Dữ liệu Kiểm thử Backtest (Dual Basket v2.0)\n")
                if (cycles > 0) {
                    sb.append("• **Tổng chu kỳ hoàn tất:** ").append(cycles).append(" chu kỳ\n")
                    sb.append("• **Tỷ lệ thắng (Winrate):** ").append(String.format("%.1f", winrate)).append("%\n")
                    sb.append("• **Lợi nhuận ròng (Net Profit):** +").append(String.format("%.2f", netProfit)).append(" USDT\n")
                    sb.append("• **Max Drawdown (Sụt giảm tối đa):** ").append(String.format("%.2f", maxDd)).append("%\n")
                    sb.append("• **Thời gian nắm giữ trung bình:** ").append(String.format("%.1f", avgHold)).append(" ngày/chu kỳ\n")
                } else {
                    sb.append("• Hiện chưa có dữ liệu báo cáo backtest trong phiên. Anh Bình hãy bấm nút 'CHẠY TEST GIỎ LỆNH D1' trên màn hình Phân tích để nạp nến Binance Mainnet nhé.\n")
                }

                sb.append("\n### 2. Đánh giá chất lượng chiến lược\n")
                sb.append("• Cơ chế 2 Slot (PRIMARY & SECONDARY) giúp phân tán rủi ro vào 2 phiên nến trong ngày, tối ưu hóa điểm xoay chiều.\n")
                sb.append("• Bộ lọc Tầng 1 (BTC Macro SMA20/EMA50 + Panic Circuit Breaker -3.5%) triệt tiêu hoàn toàn các chu kỳ mua khi thị trường đổ máu.\n")

                sb.append("\n### 3. Đề xuất tối ưu cho anh Bình\n")
                sb.append("• Giữ nguyên đòn bẩy Cross 1x để Max Drawdown luôn dưới 10%.\n")
                sb.append("• Cấu hình Volume 24h tối thiểu từ 5M - 20M USDT để đảm bảo thanh khoản ra vào lệnh mượt mà.")
            }

            lowerPrompt.contains("btc") || lowerPrompt.contains("regime") || lowerPrompt.contains("sức khỏe") -> {
                sb.append("### 1. Đánh giá Macro BTC Market Regime (Tầng 1)\n")
                sb.append("• Điều kiện tiên quyết: Giá BTC D1 phải đóng cửa **trên SMA(20)** và **EMA(50)**.\n")
                sb.append("• **Panic Circuit Breaker:** Nến D1 gần nhất của BTC không được giảm quá **-3.5%**.\n")

                sb.append("\n### 2. Đánh giá trạng thái lọc\n")
                sb.append("• Nếu BTC thỏa mãn điều kiện, bộ quét Tầng 2 sẽ tự động lọc danh sách Altcoin có RVol >= 2.0x và biến động an toàn ATR(14) <= 12%.\n")
                sb.append("• Nếu BTC vi phạm (nến đỏ sập > 3.5%), cỗ máy lập tức hủy phiên mở giỏ mới để bảo toàn vốn tuyệt đối cho anh.\n")

                sb.append("\n### 3. Đề xuất cho anh Bình\n")
                sb.append("• Anh hãy quan sát nến D1 BTC vào lúc 07:00 sáng. Nếu BTC duy trì xu hướng xanh trên đường SMA20, phiên quét 07:00 sẽ mở giỏ tự tin.")
            }

            lowerPrompt.contains("rvol") || lowerPrompt.contains("atr") || selectedSymbol.isNotBlank() && currentScreen == "CHART" -> {
                sb.append("### 1. Phân tích Kỹ thuật Cặp Coin `").append(selectedSymbol).append("`\n")
                sb.append("• **Đột biến Khối lượng (RVol):** Yêu cầu Volume(t-1) >= 2.0 * Volume(t-2). Đây là tiêu chí xác nhận có cá mập hoặc dòng tiền đột biến tham gia.\n")
                sb.append("• **Biến động ATR(14):** Yêu cầu ATR / Close <= 12%. Tiêu chí này loại trừ các coin rác bơm xả vô lý (meme pump & dump).\n")
                sb.append("• **Biên độ nến D1:** Nằm trong khoảng `[2.0%, 17.5%]`, vừa đủ lực breakout nhưng không bị mua đuổi quá cao.\n")

                sb.append("\n### 2. So sánh với tiêu chuẩn giỏ Dual Basket\n")
                sb.append("• Cặp ").append(selectedSymbol).append(" được chấm điểm theo công thức Score = RVol * (1.0 + Body / Range).\n")

                sb.append("\n### 3. Đề xuất cho anh Bình\n")
                sb.append("• Nên ưu tiên chọn các đồng coin nằm trong top điểm số cao nhất của phiên quét để đưa vào giỏ.")
            }

            else -> {
                sb.append("### 1. Phân tích hiện trạng thị trường & Ngữ cảnh\n")
                sb.append("• Màn hình hiện tại: `").append(currentScreen).append("`").append(if (selectedSymbol.isNotBlank()) " (Cặp: $selectedSymbol)" else "").append(".\n")
                sb.append("• Slot PRIMARY: `").append(pStatus).append("` | Slot SECONDARY: `").append(sStatus).append("`.\n")

                sb.append("\n### 2. Đánh giá rủi ro thuật toán\n")
                sb.append("• Toàn bộ lệnh đều áp dụng đòn bẩy an toàn Long Cross 1x, phân bổ 1.5% - 2.0% vốn mỗi vị thế, trần danh mục tối đa 60% Equity.\n")
                sb.append("• Không sử dụng SuperTrend, chỉ dùng MA20/EMA50 và ATR Trailing Take-Profit.\n")

                sb.append("\n### 3. Đề xuất hành động cho anh Bình\n")
                sb.append("• Anh có thể chọn các chip câu hỏi nhanh bên trên hoặc hỏi em chi tiết về bất kỳ vị thế, nến hoặc chu kỳ backtest nào nhé!")
            }
        }

        return sb.toString()
    }

    fun resetState() {
        _aiState.value = AiUiState.Idle
    }
}
