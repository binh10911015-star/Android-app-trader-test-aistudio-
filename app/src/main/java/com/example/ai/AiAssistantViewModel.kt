package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BacktestConfig
import com.example.model.BacktestHistoryRecord
import com.example.model.BasketBacktestReport
import com.example.model.BasketCycleResult
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
import java.util.Locale

sealed class AiUiState {
    object Idle : AiUiState()
    object Loading : AiUiState()
    data class Streaming(val partialResponse: String) : AiUiState()
    data class Success(
        val fullResponse: String,
        val recommendedConfig: BacktestConfig? = null,
        val recommendationRationale: String? = null,
        val diffs: List<ParameterDiff> = emptyList(),
        val goal: OptimizationGoal = OptimizationGoal.BALANCED
    ) : AiUiState()
    data class Error(val message: String) : AiUiState()
}

class AiAssistantViewModel : ViewModel() {
    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private var currentGoal: OptimizationGoal = OptimizationGoal.BALANCED

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun setOptimizationGoal(goal: OptimizationGoal) {
        currentGoal = goal
    }

    fun askAi(
        prompt: String,
        screenContextJson: String,
        goal: OptimizationGoal = currentGoal
    ) {
        currentGoal = goal
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            try {
                // Parse context JSON
                val (parsedConfig, parsedReport, parsedHistory) = parseContext(screenContextJson)

                val apiKey = try {
                    val field = Class.forName("com.example.BuildConfig").getField("GEMINI_API_KEY")
                    field.get(null) as? String ?: ""
                } catch (e: Exception) {
                    System.getenv("GEMINI_API_KEY") ?: ""
                }

                var answer: String? = null

                if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                    try {
                        answer = callGeminiApi(prompt, screenContextJson, apiKey, goal)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        answer = null
                    }
                }

                // If Gemini is not configured, offline, or returns error:
                // execute the deep data-driven adaptive optimization engine
                val optResult = AdaptiveOptimizationEngine.optimize(
                    currentConfig = parsedConfig,
                    currentReport = parsedReport,
                    history = parsedHistory,
                    goal = goal
                )

                if (answer == null) {
                    answer = generateExpertAnalysis(prompt, screenContextJson, optResult)
                }

                val recPair = extractRecommendedConfig(answer)
                val finalConfig = recPair?.first ?: optResult.recommendedConfig
                val finalRationale = recPair?.second ?: optResult.rationale

                val chunks = answer.split(" ")
                val builder = StringBuilder()
                for (chunk in chunks) {
                    builder.append(chunk).append(" ")
                    _aiState.value = AiUiState.Streaming(builder.toString())
                    delay(12L)
                }

                _aiState.value = AiUiState.Success(
                    fullResponse = builder.toString().trim(),
                    recommendedConfig = finalConfig,
                    recommendationRationale = finalRationale,
                    diffs = optResult.diffs,
                    goal = goal
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiUiState.Error(e.localizedMessage ?: "Lỗi kết nối trợ lý AI.")
            }
        }
    }

    private fun parseContext(jsonStr: String): Triple<BacktestConfig, BasketBacktestReport?, List<BacktestHistoryRecord>> {
        return try {
            val root = JSONObject(jsonStr)

            // 1. Config
            val cObj = root.optJSONObject("backtestConfig")
            val config = if (cObj != null) {
                BacktestConfig(
                    initialCapital = cObj.optDouble("initialCapital", 10000.0),
                    perTradePct = cObj.optDouble("perTradePct", 0.015),
                    maxPortfolioAllocation = cObj.optDouble("maxPortfolioAllocation", 0.60),
                    minQuoteVolume24h = cObj.optDouble("minQuoteVolume24h", 20_000_000.0),
                    minVolRatio = cObj.optDouble("minVolRatio", 2.0),
                    maxD1ChangePct = cObj.optDouble("maxD1ChangePct", 10.0),
                    maxAtrPercent = cObj.optDouble("maxAtrPercent", 0.12),
                    slotScanMode = cObj.optString("slotScanMode", "DUAL"),
                    kTrail = cObj.optDouble("kTrail", 0.25),
                    enableTrailingStop = cObj.optBoolean("enableTrailingStop", true),
                    enableFloorLock = cObj.optBoolean("enableFloorLock", true),
                    floorLockMinRoi = cObj.optDouble("floorLockMinRoi", 0.005)
                )
            } else {
                BacktestConfig()
            }

            // 2. Report
            val rObj = root.optJSONObject("backtestReport")
            val report = if (rObj != null) {
                val cyclesCount = rObj.optInt("totalCycles", 0)
                val dummyCycles = mutableListOf<BasketCycleResult>()
                val slHits = rObj.optInt("slHits", 0)
                val timeoutHits = rObj.optInt("timeoutHits", 0)
                val trailingHits = rObj.optInt("trailingHits", 0)

                for (i in 0 until slHits) {
                    dummyCycles.add(BasketCycleResult(i, openTime = 0L, closeTime = 0L, holdingDays = 1.0, totalInvested = 1000.0, realizedProfitUsdt = -50.0, basketRoiPercent = -5.0, equityAfterClose = 9950.0, coinsCount = 3, symbolsList = emptyList(), exitReason = "SL_HIT"))
                }
                for (i in 0 until timeoutHits) {
                    dummyCycles.add(BasketCycleResult(i, openTime = 0L, closeTime = 0L, holdingDays = 6.0, totalInvested = 1000.0, realizedProfitUsdt = 10.0, basketRoiPercent = 1.0, equityAfterClose = 10010.0, coinsCount = 3, symbolsList = emptyList(), exitReason = "TIMEOUT_EXPIRED"))
                }
                for (i in 0 until trailingHits) {
                    dummyCycles.add(BasketCycleResult(i, openTime = 0L, closeTime = 0L, holdingDays = 3.0, totalInvested = 1000.0, realizedProfitUsdt = 120.0, basketRoiPercent = 12.0, equityAfterClose = 10120.0, coinsCount = 3, symbolsList = emptyList(), exitReason = "TRAILING_STOP_HIT"))
                }

                BasketBacktestReport(
                    totalCycles = cyclesCount,
                    winratePercent = rObj.optDouble("winrate", 0.0),
                    netProfitUsdt = rObj.optDouble("netProfitUsdt", 0.0),
                    netProfitPercent = rObj.optDouble("netProfitPercent", 0.0),
                    maxDrawdownPercent = rObj.optDouble("maxDrawdown", 0.0),
                    averageHoldingDays = rObj.optDouble("avgHoldingDays", 0.0),
                    finalEquity = rObj.optDouble("finalEquity", 10000.0),
                    cycles = dummyCycles
                )
            } else null

            // 3. History
            val hArr = root.optJSONArray("backtestHistory")
            val history = mutableListOf<BacktestHistoryRecord>()
            if (hArr != null) {
                for (i in 0 until hArr.length()) {
                    val item = hArr.optJSONObject(i) ?: continue
                    val cItem = item.optJSONObject("config")
                    val hCfg = BacktestConfig(
                        kTrail = cItem?.optDouble("kTrail", 0.25) ?: 0.25,
                        perTradePct = cItem?.optDouble("perTradePct", 0.015) ?: 0.015,
                        maxPortfolioAllocation = cItem?.optDouble("maxPortfolioAllocation", 0.60) ?: 0.60,
                        minQuoteVolume24h = cItem?.optDouble("minQuoteVolume24h", 20_000_000.0) ?: 20_000_000.0,
                        minVolRatio = cItem?.optDouble("minVolRatio", 2.0) ?: 2.0,
                        maxD1ChangePct = cItem?.optDouble("maxD1ChangePct", 10.0) ?: 10.0,
                        enableTrailingStop = cItem?.optBoolean("enableTrailingStop", true) ?: true,
                        enableFloorLock = cItem?.optBoolean("enableFloorLock", true) ?: true,
                        floorLockMinRoi = cItem?.optDouble("floorLockMinRoi", 0.005) ?: 0.005
                    )
                    history.add(
                        BacktestHistoryRecord(
                            id = item.optString("id", i.toString()),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            days = item.optInt("days", 180),
                            config = hCfg,
                            netProfitUsdt = item.optDouble("netProfitUsdt", 0.0),
                            netProfitPercent = 0.0,
                            winratePercent = item.optDouble("winratePercent", 0.0),
                            maxDrawdownPercent = item.optDouble("maxDrawdownPercent", 0.0),
                            totalCycles = item.optInt("totalCycles", 0),
                            winCycles = 0,
                            lossCycles = 0,
                            trailingHits = item.optInt("trailingHits", 0)
                        )
                    )
                }
            }

            Triple(config, report, history)
        } catch (e: Exception) {
            Triple(BacktestConfig(), null, emptyList())
        }
    }

    private suspend fun callGeminiApi(
        prompt: String,
        screenContextJson: String,
        apiKey: String,
        goal: OptimizationGoal
    ): String = withContext(Dispatchers.IO) {
        val fullPrompt = """
            ${AiSystemPrompt.INSTRUCTIONS}
            
            MỤC TIÊU TỐI ƯU HÓA ĐƯỢC CHỌN:
            ${goal.title} - ${goal.description}
            
            BỐI CẢNH DỮ LIỆU HIỆN TẠI (JSON SNAPSHOT KÈM LỊCH SỬ CÁC LẦN TEST TRƯỚC):
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

    private fun extractRecommendedConfig(text: String): Pair<BacktestConfig, String>? {
        return try {
            val startIdx = text.indexOf("\"recommendation\"")
            if (startIdx == -1) return null
            val braceStart = text.lastIndexOf('{', startIdx)
            if (braceStart == -1) return null

            var depth = 0
            var braceEnd = -1
            for (i in braceStart until text.length) {
                if (text[i] == '{') depth++
                else if (text[i] == '}') {
                    depth--
                    if (depth == 0) {
                        braceEnd = i + 1
                        break
                    }
                }
            }
            if (braceEnd <= braceStart) return null

            val jsonStr = text.substring(braceStart, braceEnd)
            val obj = JSONObject(jsonStr)
            val rec = obj.optJSONObject("recommendation") ?: return null
            val rationale = obj.optString("rationale", "")

            val cfg = BacktestConfig(
                initialCapital = rec.optDouble("initialCapital", 10000.0),
                perTradePct = rec.optDouble("perTradePct", 0.015),
                maxPortfolioAllocation = rec.optDouble("maxPortfolioAllocation", 0.60),
                minQuoteVolume24h = rec.optDouble("minQuoteVolume24h", 20_000_000.0),
                minVolRatio = rec.optDouble("minVolRatio", 2.0),
                maxD1ChangePct = rec.optDouble("maxD1ChangePct", 10.0),
                maxAtrPercent = rec.optDouble("maxAtrPercent", 0.12),
                slotScanMode = rec.optString("slotScanMode", "DUAL"),
                kTrail = rec.optDouble("kTrail", 0.25),
                enableTrailingStop = rec.optBoolean("enableTrailingStop", true),
                enableFloorLock = rec.optBoolean("enableFloorLock", true),
                floorLockMinRoi = rec.optDouble("floorLockMinRoi", 0.005)
            )
            Pair(cfg, rationale)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateExpertAnalysis(
        prompt: String,
        screenContextJson: String,
        optResult: OptimizationResult
    ): String {
        val root = try { JSONObject(screenContextJson) } catch (e: Exception) { JSONObject() }
        val currentScreen = root.optString("currentScreen", "MARKET")
        val selectedSymbol = root.optString("selectedSymbol", "BTCUSDT")

        val primaryBasket = root.optJSONObject("primaryBasket")
        val secondaryBasket = root.optJSONObject("secondaryBasket")

        val pStatus = primaryBasket?.optString("status", "EMPTY_SLOT") ?: "EMPTY_SLOT"
        val sStatus = secondaryBasket?.optString("status", "EMPTY_SLOT") ?: "EMPTY_SLOT"
        val pCoins = primaryBasket?.optInt("coinsCount", 0) ?: 0
        val sCoins = secondaryBasket?.optInt("coinsCount", 0) ?: 0
        val pHours = primaryBasket?.optDouble("holdingHours", 0.0) ?: 0.0
        val pAtr = (primaryBasket?.optDouble("basketAtrPercent", 0.05) ?: 0.05) * 100.0
        val pPeak = (primaryBasket?.optDouble("peakRoi", 0.0) ?: 0.0) * 100.0
        val pTrailStop = (primaryBasket?.optDouble("currentTrailingStopRoi", -1.0) ?: -1.0) * 100.0

        val lowerPrompt = prompt.lowercase(Locale.ROOT)
        val sb = StringBuilder()
        sb.append("Chào anh Bình! Em xin gửi phân tích chiến lược chuyên sâu theo dữ liệu thực tế Dual Basket Momentum (Pure Trailing Stop):\n\n")

        when {
            lowerPrompt.contains("tối ưu") || lowerPrompt.contains("gợi ý") || lowerPrompt.contains("đề xuất") ||
            lowerPrompt.contains("đánh giá") || lowerPrompt.contains("thông số") -> {

                sb.append("### 1. Chẩn đoán Điểm Nghẽn & Dữ Liệu Thực Tế\n")
                sb.append(optResult.bottleneckDiagnosis)

                sb.append("\n")
                sb.append(optResult.historicalComparison)

                sb.append("\n### 2. Định Hướng Theo Mục Tiêu: **${optResult.goal.title}**\n")
                sb.append("• ${optResult.rationale}\n")

                if (optResult.diffs.isNotEmpty()) {
                    sb.append("\n### 3. Bảng Chi Tiết Tinh Chỉnh Thông Số (Trước vs Đề Xuất Mới):\n")
                    optResult.diffs.forEach { diff ->
                        sb.append("• **${diff.name}:** `${diff.oldValue}` ➔ `**${diff.newValue}**`\n")
                        sb.append("  ↳ *Lý do:* ${diff.changeDescription}\n")
                    }
                }

                val rec = optResult.recommendedConfig
                sb.append("\n### 4. Bộ Thông Số Tối Ưu Thích Ứng (Nhấn 'Áp Dụng' để nạp):\n")
                sb.append("```json\n")
                sb.append("{\n")
                sb.append("  \"recommendation\": {\n")
                sb.append("    \"slotScanMode\": \"${rec.slotScanMode}\",\n")
                sb.append("    \"initialCapital\": ${rec.initialCapital},\n")
                sb.append("    \"perTradePct\": ${rec.perTradePct},\n")
                sb.append("    \"maxPortfolioAllocation\": ${rec.maxPortfolioAllocation},\n")
                sb.append("    \"minQuoteVolume24h\": ${rec.minQuoteVolume24h},\n")
                sb.append("    \"minVolRatio\": ${rec.minVolRatio},\n")
                sb.append("    \"maxD1ChangePct\": ${rec.maxD1ChangePct},\n")
                sb.append("    \"maxAtrPercent\": ${rec.maxAtrPercent},\n")
                sb.append("    \"kTrail\": ${rec.kTrail},\n")
                sb.append("    \"enableTrailingStop\": ${rec.enableTrailingStop},\n")
                sb.append("    \"enableFloorLock\": ${rec.enableFloorLock},\n")
                sb.append("    \"floorLockMinRoi\": ${rec.floorLockMinRoi}\n")
                sb.append("  },\n")
                sb.append("  \"rationale\": \"${optResult.rationale}\"\n")
                sb.append("}\n")
                sb.append("```\n")
            }

            lowerPrompt.contains("trạng thái 2 giỏ") || (lowerPrompt.contains("giải thích") && currentScreen == "POSITIONS") -> {
                sb.append("### 1. Phân tích Hiện trạng 2 Slot Danh mục (Dual Basket v2.0)\n")
                sb.append("• **Giỏ PRIMARY (Quét 07:00 AM UTC+7):** Trạng thái `").append(pStatus).append("`")
                if (pStatus != "EMPTY_SLOT") {
                    sb.append(", gồm ").append(pCoins).append(" vị thế, đã giữ ").append(String.format(Locale.US, "%.1f", pHours)).append("h. ")
                    sb.append("Basket ATR: ").append(String.format(Locale.US, "%.2f", pAtr)).append("%. ")
                    if (pPeak > 0) sb.append("ROI đỉnh đạt: +").append(String.format(Locale.US, "%.2f", pPeak)).append("%. ")
                } else {
                    sb.append(" (Slot trống, sẵn sàng mở lệnh khi nến D1 lúc 07:00 đạt chuẩn).")
                }
                sb.append("\n• **Giỏ SECONDARY (Quét 19:00 PM UTC+7):** Trạng thái `").append(sStatus).append("`")
                if (sStatus != "EMPTY_SLOT") {
                    sb.append(", gồm ").append(sCoins).append(" vị thế đang chạy độc lập.")
                } else {
                    sb.append(" (Slot trống, phiên 19:00 sẽ quét nạp coin không trùng giỏ chính).")
                }

                sb.append("\n\n### 2. Đánh giá Rủi ro & Cơ Chế Trailing Take-Profit\n")
                if (pStatus == "TRAILING_ACTIVE") {
                    sb.append("• Giỏ PRIMARY đã kích hoạt Trailing Stop động. Mức chặn lãi hiện tại: `+").append(String.format(Locale.US, "%.2f", pTrailStop)).append("%` (đã khóa trên sàn bảo vệ net +0.5%).\n")
                    sb.append("• Theo nguyên tắc Pure Trend Following, vị thế bám trọn con sóng lớn mà không bị timeout giải phóng vốn non.\n")
                } else if (pStatus == "OPEN") {
                    sb.append("• Giỏ PRIMARY đang vận hành theo cơ chế Pure Trend Following (Spot / Cross 1x). Khi có lãi dương sẽ khóa sàn FloorLock +0.5% net và bám sát đỉnh bằng Trailing Stop ATR.\n")
                } else {
                    sb.append("• Cả 2 slot kiểm soát đòn bẩy an toàn Long Cross 1x (Effective Leverage <= 1.0), hoàn toàn loại trừ rủi ro thanh lý.\n")
                }

                sb.append("\n### 3. Đề xuất Hành động cho Anh Bình\n")
                sb.append("• Duy trì kỷ luật hệ thống để thuật toán tự động bám đỉnh hoặc chốt lời bằng Trailing Stop & FloorLock, không can thiệp thủ công.")
            }

            lowerPrompt.contains("thoát lệnh") || lowerPrompt.contains("trailing stop") || lowerPrompt.contains("floorlock") || lowerPrompt.contains("khoảng cách") -> {
                sb.append("### 1. Cơ Chế Thoát Lệnh Duy Nhất (Pure Trailing Stop & FloorLock)\n")
                sb.append("• Đã loại bỏ hoàn toàn Stop Loss cứng, Timeout và TP cố định để tối ưu hóa khả năng bùng nổ của altcoin.\n")
                sb.append("• Đỉnh ROI cao nhất từng chạm (peakRoi): **+").append(String.format(Locale.US, "%.2f", pPeak)).append("%**\n")
                sb.append("• Chặn lãi Trailing hiện tại (currentTrailingStopRoi): **").append(if (pTrailStop > -0.9) "+${String.format(Locale.US, "%.2f", pTrailStop)}%" else "Chờ kích hoạt").append("**\n")

                sb.append("\n### 2. Đánh giá Vùng Đệm ATR & Sàn Khóa Lãi\n")
                sb.append("• Bước đệm giật lùi là `K_Trail * Basket ATR%` (khoảng +0.6% đến +1.2%).\n")
                sb.append("• Sàn bảo hiểm `floorLockRoi = +0.5% net` đảm bảo lệnh luôn có lãi dương sau khi trừ phí giao dịch.\n")
            }

            else -> {
                sb.append("### 1. Phân tích Hiện trạng Thị trường & Ngữ cảnh\n")
                sb.append("• Màn hình: `").append(currentScreen).append("`").append(if (selectedSymbol.isNotBlank()) " (Cặp: $selectedSymbol)" else "").append(".\n")
                sb.append("• PRIMARY Slot: `").append(pStatus).append("` | SECONDARY Slot: `").append(sStatus).append("`.\n")

                sb.append("\n### 2. Đánh giá Rủi ro Thuật toán\n")
                sb.append("• Đòn bẩy Long Cross 1x, phân bổ vốn kỷ luật, trần danh mục tối đa 60% Equity.\n")
                sb.append("• Bộ lọc 3 tầng bảo vệ Macro BTC, thanh khoản Altcoin và cơ chế ATR Trailing Take-Profit.\n")

                sb.append("\n### 3. Đề xuất Hành động cho Anh Bình\n")
                sb.append("• Anh có thể chọn các mục tiêu tối ưu (Cân bằng, Tối đa Lợi nhuận, Giảm Max DD) phía trên để em phân tích bộ thông số phù hợp nhất!")
            }
        }

        return sb.toString()
    }

    fun resetState() {
        _aiState.value = AiUiState.Idle
    }
}
