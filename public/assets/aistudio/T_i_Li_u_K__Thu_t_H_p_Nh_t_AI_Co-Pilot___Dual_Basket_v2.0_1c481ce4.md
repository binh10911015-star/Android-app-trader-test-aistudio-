# TÀI LIỆU ĐẶC TẢ KỸ THUẬT HỢP NHẤT: TRỢ LÝ AI CO-PILOT & CỖ MÁY BACKTEST DUAL BASKET MOMENTUM V2.0

- **Phiên bản:** 2.0 - Production Architecture
- **Nền tảng:** Android Jetpack Compose / Kotlin Coroutines / Firebase AI Logic
- **Môi trường dữ liệu:** 100% Binance Futures Mainnet Public API (`https://fapi.binance.com`)
- **Đối tượng bàn giao:** Đội ngũ Kỹ sư Backend / Android Client

---

## MỤC LỤC
1. [Tổng Quan Kiến Trúc & Luồng Vận Hành](#1-tổng-quan-kiến-trúc--luồng-vận-hành)
2. [Module 1: Cỗ Máy Backtest & Quản Lý 2 Giỏ Lệnh (Dual Basket v2.0)](#2-module-1-cỗ-máy-backtest--quản-lý-2-giỏ-lệnh-dual-basket-v20)
   - [2.1 Data Models & Quản lý Trạng Thái Vòng Đời (`TradeModels.kt`)](#21-data-models--quản-lý-trạng-thái-vòng-đời-trademodelskt)
   - [2.2 Thuật Toán 3 Tầng: Lọc Tín Hiệu & Định Giá Thoát Lệnh](#22-thuật-toán-3-tầng-lọc-tín-hiệu--định-giá-thoát-lệnh)
   - [2.3 Mã Nguồn Cỗ Máy Backtest Chuẩn Hóa (`CryptoRepository.kt`)](#23-mã-nguồn-cỗ-máy-backtest-chuẩn-hóa-cryptorepositorykt)
3. [Module 2: Trợ Lý AI Co-Pilot Cố Vấn Ngữ Cảnh (Firebase AI Logic)](#3-module-2-trợ-lý-ai-co-pilot-cố-vấn-ngữ-cảnh-firebase-ai-logic)
   - [2.1 Kiến Trúc & Cấu Hình Firebase App Check](#31-kiến-trúc--cấu-hình-firebase-app-check)
   - [2.2 Tri Thức Thuật Toán Cho AI (`AiSystemPrompt.kt`)](#32-tri-thức-thuật-toán-cho-ai-aisystempromptkt)
   - [2.3 Bộ Đóng Gói Ngữ Cảnh Siêu Nhẹ (`ScreenContextBuilder.kt`)](#33-bộ-đóng-gói-ngữ-cảnh-siêu-nhẹ-screencontextbuilderkt)
   - [2.4 ViewModel Điều Phối Streaming (`AiAssistantViewModel.kt`)](#34-viewmodel-điều-phối-streaming-aiassistantviewmodelkt)
   - [2.5 Giao Diện Nút Nổi & Popup Sheet 80% (`AiAssistantUi.kt`)](#35-giao-diện-nút-nổi--popup-sheet-80-aiassistantuikt)
4. [Hướng Dẫn Ráp Nối Vào Hệ Thống Hiện Hữu](#4-hướng-dẫn-ráp-nối-vào-hệ-thống-hiện-hữu)

---

## 1. TỔNG QUAN KIẾN TRÚC & LUỒNG VẬN HÀNH

Hệ thống kết hợp giữa tính toán định lượng tự động (Quantitative Strategy) và trí tuệ nhân tạo hỗ trợ quyết định (AI Decision Support):

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│                                                                        │
│  [MainActivity] ───────────────────────► [FloatingAiButton (Góc Phải)] │
│         │                                          │                   │
│         ▼                                          ▼ (Bấm kích hoạt)   │
│  ┌───────────────┐                  ┌────────────────────────────────┐ │
│  │ 4 Main Tabs   │                  │ AiAssistantBottomSheet (80%)   │ │
│  │ - Thị trường  │                  │ - Badge trạng thái ngữ cảnh    │ │
│  │ - Paper Trade │                  │ - Chips câu hỏi nhanh 1-chạm   │ │
│  │ - Phân tích   │                  │ - Markdown Streaming Viewer    │ │
│  │ - API Config  │                  │ - Ô gõ câu hỏi tùy biến        │ │
│  └──────┬────────┘                  └────────────────┬───────────────┘ │
└─────────┼────────────────────────────────────────────┼─────────────────┘
          │                                            │ Context JSON (<2KB)
          ▼                                            ▼
┌───────────────────────────────────┐    ┌───────────────────────────────┐
│  State & Repository Layer         │    │  ScreenContextBuilder         │
│  - Dual Basket Manager            │───►│  (Chụp snapshot 2 giỏ lệnh,   │
│  - Historical Backtest Engine     │    │   BTC Regime, PnL & Backtest) │
│  - Binance Mainnet Public Client  │    └─────────────┬─────────────────┘
└───────────────────────────────────┘                  │
                                                       ▼
                                         ┌───────────────────────────────┐
                                         │  Firebase AI Logic            │
                                         │  - Model: gemini-1.5-flash    │
                                         │  - AiSystemPrompt (Tri thức)  │
                                         │  - App Check Attestation      │
                                         └───────────────────────────────┘
```

---

## 2. MODULE 1: CỖ MÁY BACKTEST & QUẢN LÝ 2 GIỎ LỆNH (DUAL BASKET V2.0)

### 2.1 Data Models & Quản lý Trạng Thái Vòng Đời (`TradeModels.kt`)
Đường dẫn file: `app/src/main/java/com/example/model/TradeModels.kt`

```kotlin
package com.example.model

enum class BasketSlotId {
    PRIMARY,    // Giỏ quét chính lúc 07:00 AM (UTC+7 / 00:00 UTC)
    SECONDARY   // Giỏ quét phụ lúc 19:00 PM (UTC+7 / 12:00 UTC)
}

enum class BasketStatus {
    OPEN,               // Đang chạy, chưa kích hoạt Trailing
    TRAILING_ACTIVE,    // Đã vượt mốc kích hoạt cơ sở, đang bám đỉnh theo ATR
    CLOSING,            // Đang thanh lý lệnh trên sàn (khóa slot, cấm ghi đè)
    CLOSED              // Đã tất toán xong, giải phóng slot cho chu kỳ tiếp theo
}

data class BasketPosition(
    val symbol: String,
    val entryPrice: Double,
    val quantity: Double,
    val marginAllocated: Double,
    val entryAtrPercent: Double // ATR(14) D1 / entryPrice tại thời điểm vào lệnh
)

data class ActiveBasket(
    val slotId: BasketSlotId,
    val openTime: Long,
    val positions: List<BasketPosition>,
    val totalInvested: Double,
    var status: BasketStatus = BasketStatus.OPEN,
    val maxHoldingMillis: Long = 72 * 3600 * 1000L // Timeout cố định 72 giờ
) {
    // 1. Biến động trọng số toàn giỏ: Basket ATR%
    val basketAtrPercent: Double by lazy {
        if (totalInvested <= 0.0) 0.0
        else positions.sumOf { it.entryAtrPercent * (it.marginAllocated / totalInvested) }
    }

    // 2. Ngưỡng kích hoạt Trailing cơ sở: K_TP = 0.5 (Tối thiểu +1.5%)
    val activationRoi: Double by lazy {
        maxOf(0.015, 0.50 * basketAtrPercent)
    }

    // 3. Ngưỡng cắt lỗ danh mục ban đầu: K_SL = 0.5 (Tối đa -2.0%)
    val initialStopLossRoi: Double by lazy {
        -maxOf(0.020, 0.50 * basketAtrPercent)
    }

    // 4. Bước đệm kéo lùi Trailing: 0.25 * ATR giỏ (Tối thiểu 0.6%)
    val trailStepRoi: Double by lazy {
        maxOf(0.006, 0.25 * basketAtrPercent)
    }

    // Sàn bảo vệ lợi nhuận tối thiểu khi đã bật trailing (đủ bù trượt giá & phí giao dịch)
    val floorLockRoi: Double = 0.005 // +0.5% net

    // Biến trạng thái động theo dõi theo thời gian thực
    var peakRoi: Double = 0.0
    var currentTrailingStopRoi: Double = -1.0
}

data class ScoredCoin(
    val symbol: String,
    val score: Double,
    val entryPrice: Double,
    val volRatio: Double,
    val changePct: Double,
    val atrPercent: Double
)

data class BasketCycleResult(
    val cycleIndex: Int,
    val slotId: BasketSlotId,
    val openTime: Long,
    val closeTime: Long,
    val holdingDays: Double,
    val totalInvested: Double,
    val realizedProfitUsdt: Double,
    val basketRoiPercent: Double,
    val equityAfterClose: Double,
    val coinsCount: Int,
    val symbolsList: List<String>,
    val exitReason: String
)

data class BasketBacktestReport(
    val totalCycles: Int = 0,
    val winratePercent: Double = 0.0,
    val averageHoldingDays: Double = 0.0,
    val maxDrawdownPercent: Double = 0.0,
    val initialCapital: Double = 10_000.0,
    val finalEquity: Double = 10_000.0,
    val netProfitUsdt: Double = 0.0,
    val netProfitPercent: Double = 0.0,
    val cycles: List<BasketCycleResult> = emptyList(),
    val equityCurve: List<Pair<Long, Double>> = emptyList(),
    val errorMessage: String? = null
)
```

---

### 2.2 Thuật Toán 3 Tầng: Lọc Tín Hiệu & Định Giá Thoát Lệnh

#### Tầng 1: Bộ Lọc Vĩ Mô BTC (Macro Market Regime)
Tại các thời điểm quét (07:00 AM / 19:00 PM UTC+7):
1. $Close_{BTC, D1} > SMA(20)_{D1}$ và $Close_{BTC, D1} > EMA(50)_{D1}$.
2. **Panic Circuit Breaker:** Nến D1 gần nhất của BTC không giảm quá $3.5\%$ ($\Delta Price_{BTC, D1} > -3.5\%$). Nếu vi phạm, lập tức hủy toàn bộ phiên quét mở mới.

#### Tầng 2: Bộ Lọc Thanh Khoản & Kỹ Thuật Altcoin
1. **Lọc thanh khoản 24h:** $\text{quoteVolume}_{24h} \ge \text{config.minQuoteVolume24h}$ (Mặc định 0.0 hoặc 5,000,000 USDT).
2. **Xác nhận xu hướng:** $Close_{D1} > SMA(20)_{D1}$.
3. **Đột biến khối lượng (RVol):** $Volume_{t-1} \ge \text{config.minVolRatio} \times Volume_{t-2}$ (Mặc định $\ge 2.0\times$).
4. **Biến động an toàn (ATR):** $ATR(14)_{D1} / Close_{D1} \le 12\%$.
5. **Chặn trên biên độ nến D1:** $\Delta Price_{D1} \in [2.0\%, 17.5\%]$ (Loại bỏ Doji và loại bỏ đu đỉnh nến quá dài).
6. **Lọc trùng danh mục:** Bỏ qua tất cả các coin đang tồn tại trong giỏ còn lại đang chạy.
7. **Chấm điểm ưu tiên:**
   $$\text{Score} = \left(\frac{Volume_{t-1}}{Volume_{t-2}}\right) \times \left(1.0 + \frac{Close_{t-1} - Open_{t-1}}{\max(10^{-8}, High_{t-1} - Low_{t-1})}\right)$$

#### Tầng 3: Engine Định Giá & Thoát Lệnh Độc Lập
Định giá đồng thời tất cả các vị thế theo giá $Close$ của nến thời điểm kiểm tra:
$$\text{TotalInvested} = \sum_{i=1}^{N} \text{MarginAllocated}_i, \quad \text{UnrealizedPnL} = \sum_{i=1}^{N} (Price_{current, i} - Price_{entry, i}) \times Qty_i$$
$$\text{CurrentROI} = \frac{\text{UnrealizedPnL}}{\text{TotalInvested}}$$

- **Kích hoạt Trailing:** Khi $\text{CurrentROI} \ge \text{activationRoi} \rightarrow \text{status} = \text{TRAILING\_ACTIVE}$.
- **Cập nhật đỉnh & Dời chặn lãi:** Khi $\text{CurrentROI} > \text{peakRoi}$, cập nhật:
  $$\text{currentTrailingStopRoi} = \max(\text{floorLockRoi}, \text{peakRoi} - \text{trailStepRoi})$$
- **Thoát lệnh:**
  1. $\text{CurrentROI} \le \text{currentTrailingStopRoi}$ (Chạm Trailing Stop khi đã có lãi).
  2. $\text{CurrentROI} \le \text{initialStopLossRoi}$ (Chạm Stop Loss cứng ban đầu).
  3. Thời gian giữ $\ge 72$ giờ ($\text{Timeout 72h}$).

---

### 2.3 Mã Nguồn Cỗ Máy Backtest Chuẩn Hóa (`CryptoRepository.kt`)
Đường dẫn file: `app/src/main/java/com/example/repository/CryptoRepository.kt`

```kotlin
package com.example.repository

import com.example.model.*
import com.example.network.BinanceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

suspend fun runBasketD1MomentumBacktest(
    daysToTest: Int = 180,
    config: BacktestConfig = BacktestConfig(),
    symbolsPool: List<String> = emptyList()
): BasketBacktestReport = withContext(Dispatchers.IO) {
    // 1. Quét toàn bộ thị trường từ Binance Mainnet, không giới hạn take(50)
    val activeSymbols = if (symbolsPool.isNotEmpty()) symbolsPool else BinanceClient.fetchHighLiquiditySymbols(config.minQuoteVolume24h)
    if (activeSymbols.isEmpty()) {
        return@withContext BasketBacktestReport(errorMessage = "Không tải được danh sách cặp coin từ Binance Mainnet.")
    }

    // 2. Tải Klines D1 từ máy chủ Binance Mainnet
    val klinesMap = mutableMapOf<String, List<Candle>>()
    activeSymbols.forEach { sym ->
        val candles = BinanceClient.fetchKlinesWithFallback(sym, interval = "1d", limit = daysToTest + 30)
        if (candles.size >= 30) klinesMap[sym] = candles
    }

    val btcCandles = klinesMap["BTCUSDT"] ?: return@withContext BasketBacktestReport(errorMessage = "Thiếu dữ liệu nến BTCUSDT để kiểm tra Regime.")

    var currentEquity = config.initialCapital
    var peakEquity = currentEquity
    var maxDrawdown = 0.0

    // Quản lý chính xác 2 Slot giỏ lệnh độc lập
    var primaryBasket: ActiveBasket? = null
    var secondaryBasket: ActiveBasket? = null
    var basketCycleCounter = 0
    val cyclesHistory = mutableListOf<BasketCycleResult>()
    val equityCurve = mutableListOf<Pair<Long, Double>>()

    val startIndex = 25
    equityCurve.add(Pair(btcCandles[startIndex].openTime, currentEquity))

    for (dayIdx in startIndex until btcCandles.size) {
        val currentTime = btcCandles[dayIdx].openTime
        val currentBtc = btcCandles[dayIdx - 1] // Nến D1 hôm qua vừa đóng lúc 07:00 AM

        // BƯỚC 1: ĐỊNH GIÁ & KIỂM TRA THOÁT LỆNH ĐỘC LẬP TỪNG SLOT (TRÁNH LOOK-AHEAD BIAS)
        fun evaluateBasketExit(basket: ActiveBasket?): ActiveBasket? {
            if (basket == null || basket.status == BasketStatus.CLOSED) return null

            var unrealizedPnL = 0.0
            for (pos in basket.positions) {
                val candleToday = klinesMap[pos.symbol]?.find { it.openTime == currentTime }
                if (candleToday != null) {
                    unrealizedPnL += (candleToday.close - pos.entryPrice) * pos.quantity
                }
            }

            val currentRoi = if (basket.totalInvested > 0) unrealizedPnL / basket.totalInvested else 0.0
            val isTimedOut = (currentTime - basket.openTime) >= basket.maxHoldingMillis

            var exitReason: String? = null

            when (basket.status) {
                BasketStatus.OPEN -> {
                    when {
                        currentRoi >= basket.activationRoi -> {
                            basket.status = BasketStatus.TRAILING_ACTIVE
                            basket.peakRoi = currentRoi
                            basket.currentTrailingStopRoi = maxOf(basket.floorLockRoi, currentRoi - basket.trailStepRoi)
                        }
                        currentRoi <= basket.initialStopLossRoi -> exitReason = "SL_HIT"
                        isTimedOut -> exitReason = "TIMEOUT_72H"
                    }
                }
                BasketStatus.TRAILING_ACTIVE -> {
                    if (currentRoi > basket.peakRoi) {
                        basket.peakRoi = currentRoi
                        val newStop = maxOf(basket.floorLockRoi, basket.peakRoi - basket.trailStepRoi)
                        if (newStop > basket.currentTrailingStopRoi) basket.currentTrailingStopRoi = newStop
                    }
                    if (currentRoi <= basket.currentTrailingStopRoi) exitReason = "TRAILING_STOP_HIT"
                    else if (isTimedOut) exitReason = "TIMEOUT_IN_TRAIL"
                }
                else -> Unit
            }

            if (exitReason != null) {
                val grossProfit = basket.totalInvested * currentRoi
                val takerFee = basket.totalInvested * 0.0008 // Phí giao dịch 2 chiều 0.08%
                val netProfit = grossProfit - takerFee
                currentEquity += netProfit
                peakEquity = maxOf(peakEquity, currentEquity)

                cyclesHistory.add(
                    BasketCycleResult(
                        cycleIndex = ++basketCycleCounter,
                        slotId = basket.slotId,
                        openTime = basket.openTime,
                        closeTime = currentTime,
                        holdingDays = maxOf(1.0, (currentTime - basket.openTime) / 86400000.0),
                        totalInvested = basket.totalInvested,
                        realizedProfitUsdt = netProfit,
                        basketRoiPercent = currentRoi * 100.0,
                        equityAfterClose = currentEquity,
                        coinsCount = basket.positions.size,
                        symbolsList = basket.positions.map { it.symbol },
                        exitReason = exitReason
                    )
                )
                return null // Giải phóng slot
            }

            // Ghi nhận Drawdown danh mục
            val tempEquity = currentEquity + unrealizedPnL
            val dd = if (peakEquity > 0) ((peakEquity - tempEquity) / peakEquity) * 100.0 else 0.0
            if (dd > maxDrawdown) maxDrawdown = dd
            return basket
        }

        primaryBasket = evaluateBasketExit(primaryBasket)
        secondaryBasket = evaluateBasketExit(secondaryBasket)

        // BƯỚC 2: KIỂM TRA MỞ GIỎ MỚI NẾU CÒN SLOT TRỐNG
        val hasAvailableSlot = (primaryBasket == null || secondaryBasket == null)
        val runningSymbols = mutableSetOf<String>().apply {
            primaryBasket?.positions?.forEach { add(it.symbol) }
            secondaryBasket?.positions?.forEach { add(it.symbol) }
        }

        // TẦNG 1: BỘ LỌC MACRO BTC REGIME
        val btcPrev20 = btcCandles.subList(dayIdx - 21, dayIdx - 1).map { it.close }
        val btcSma20 = btcPrev20.average()
        val btcD1Change = if (currentBtc.open > 0) ((currentBtc.close - currentBtc.open) / currentBtc.open) * 100.0 else 0.0
        val isBtcBullish = currentBtc.close > btcSma20 && btcD1Change > -3.5 // Panic Circuit Breaker

        if (hasAvailableSlot && isBtcBullish) {
            val totalRunningInvested = (primaryBasket?.totalInvested ?: 0.0) + (secondaryBasket?.totalInvested ?: 0.0)
            val availableBudget = (currentEquity * config.maxPortfolioAllocation) - totalRunningInvested

            if (availableBudget >= (currentEquity * config.perTradePct)) {
                val candidates = mutableListOf<ScoredCoin>()

                for ((sym, candles) in klinesMap) {
                    if (sym in runningSymbols) continue // LỌC TRÙNG TUYỆT ĐỐI
                    val cIdx = candles.indexOfFirst { it.openTime == currentTime }
                    if (cIdx < 25) continue

                    val k1 = candles[cIdx - 1]
                    val k2 = candles[cIdx - 2]
                    val prev20 = candles.subList(cIdx - 21, cIdx - 1)
                    val sma20 = prev20.map { it.close }.average()
                    val rvol = if (k2.volume > 0) k1.volume / k2.volume else 1.0
                    val changePct = if (k1.open > 0) ((k1.close - k1.open) / k1.open) * 100.0 else 0.0

                    // Tính biến động ATR(14)
                    val atr14 = candles.subList(cIdx - 15, cIdx - 1).map { maxOf(it.high - it.low, Math.abs(it.high - it.close)) }.average()
                    val atrPct = if (k1.close > 0) atr14 / k1.close else 0.05

                    // Kiểm tra điều kiện cứng
                    if (k1.close > sma20 && rvol >= config.minVolRatio && changePct in config.minD1ChangePct..config.maxD1ChangePct && atrPct <= 0.12) {
                        val score = rvol * (1.0 + ((k1.close - k1.open) / maxOf(1e-8, k1.high - k1.low)))
                        candidates.add(ScoredCoin(sym, score, candles[cIdx].open, rvol, changePct, atrPct))
                    }
                }

                if (candidates.isNotEmpty()) {
                    val sorted = candidates.sortedByDescending { it.score }
                    val perTradeUsdt = currentEquity * config.perTradePct
                    val maxSlots = (availableBudget / perTradeUsdt).toInt().coerceAtLeast(1)
                    val chosen = sorted.take(maxSlots)

                    val positions = chosen.map {
                        BasketPosition(
                            symbol = it.symbol,
                            entryPrice = it.entryPrice,
                            quantity = perTradeUsdt / it.entryPrice,
                            marginAllocated = perTradeUsdt,
                            entryAtrPercent = it.atrPercent
                        )
                    }

                    val assignedSlot = if (primaryBasket == null) BasketSlotId.PRIMARY else BasketSlotId.SECONDARY
                    val newBasket = ActiveBasket(
                        slotId = assignedSlot,
                        openTime = currentTime,
                        positions = positions,
                        totalInvested = perTradeUsdt * positions.size
                    )

                    if (assignedSlot == BasketSlotId.PRIMARY) primaryBasket = newBasket else secondaryBasket = newBasket
                }
            }
        }
        equityCurve.add(Pair(currentTime, currentEquity))
    }

    val totalCycles = cyclesHistory.size
    val winCycles = cyclesHistory.count { it.realizedProfitUsdt > 0 }
    val winrate = if (totalCycles > 0) (winCycles.toDouble() / totalCycles) * 100.0 else 0.0
    val netProfit = currentEquity - config.initialCapital

    return@withContext BasketBacktestReport(
        totalCycles = totalCycles,
        winratePercent = winrate,
        averageHoldingDays = if (totalCycles > 0) cyclesHistory.map { it.holdingDays }.average() else 0.0,
        maxDrawdownPercent = maxDrawdown,
        initialCapital = config.initialCapital,
        finalEquity = currentEquity,
        netProfitUsdt = netProfit,
        netProfitPercent = (netProfit / config.initialCapital) * 100.0,
        cycles = cyclesHistory.reversed(),
        equityCurve = equityCurve
    )
}
```

---

## 3. MODULE 2: TRỢ LÝ AI CO-PILOT CỐ VẤN NGỮ CẢNH (FIREBASE AI LOGIC)

### 3.1 Kiến Trúc & Cấu Hình Firebase App Check
Thư viện yêu cầu trong `app/build.gradle.kts`:
```kotlin
dependencies {
    // Firebase AI Logic & App Check
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-ai")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
}
```

---

### 3.2 Tri Thức Thuật Toán Cho AI (`AiSystemPrompt.kt`)
Đường dẫn file: `app/src/main/java/com/example/ai/AiSystemPrompt.kt`

```kotlin
package com.example.ai

object AiSystemPrompt {
    val INSTRUCTIONS = """
        Bạn là Trợ lý AI Cố vấn Chiến lược Giao dịch (AI Co-pilot) tích hợp độc quyền trong ứng dụng Binance Futures của anh Bình.
        Nhiệm vụ: Đọc snapshot JSON ngữ cảnh màn hình hiện tại, giải thích quyết định vào/thoát lệnh, phân tích rủi ro danh mục 2 giỏ, theo dõi cơ chế Trailing Take-Profit và phân tích kết quả Backtest.

        BỘ QUY TẮC CHIẾN LƯỢC DUAL BASKET MOMENTUM V2.0:
        1. Kiến trúc 2 Slot độc lập:
           - PRIMARY: Quét lúc 07:00 AM UTC+7 (nến D1 đóng cửa).
           - SECONDARY: Quét lúc 19:00 PM UTC+7.
        2. Bộ lọc 3 Tầng:
           - Tầng 1 (BTC Macro): Close BTC D1 > SMA20 và EMA50 D1; Circuit Breaker hủy quét nếu nến D1 BTC giảm > 3.5%.
           - Tầng 2 (Kỹ thuật Altcoin): Volume 24h >= 20M USDT; RVol(20) >= 2.0x; ATR(14)/Price <= 12%; Nến D1 tăng <= 10% (tránh mua đuổi đỉnh); Lọc trùng coin giữa 2 slot.
           - Tầng 3 (Quản trị vốn & Thoát lệnh):
             * Đòn bẩy: Long Cross 1x (Effective Leverage <= 1.0).
             * Vốn: 1.5% - 2.0% Equity/lệnh; Trần danh mục tối đa 60% Equity.
             * Dynamic Target ROI (K_TP = 0.5): Kích hoạt Trailing khi ROI giỏ >= max(1.5%, 0.5 * Basket_ATR%).
             * Trailing Step: Giật lùi 0.25 * Basket_ATR% (tối thiểu 0.6%); Sàn bảo vệ lãi ròng floorLockRoi = +0.5% net.
             * Dynamic Stop Loss: Cắt lỗ cứng ban đầu -max(2.0%, 0.5 * Basket_ATR%).
             * Timeout: Tự động tất toán sau 72 giờ để xoay vòng vốn.

        PHONG CÁCH PHẢN HỒI:
        - Xưng hô lịch sự, thân thiện ("anh Bình" / "em").
        - Phân đoạn nội dung khoa học, gãy gọn:
          1. Phân tích hiện trạng kỹ thuật (Dữ liệu nến, PnL, Trạng thái Trailing/Timeout).
          2. Đánh giá rủi ro thị trường (Tương quan BTC, biến động ATR).
          3. Đề xuất hành động cụ thể cho anh Bình.
    """.trimIndent()
}
```

---

### 3.3 Bộ Đóng Gói Ngữ Cảnh Siêu Nhẹ (`ScreenContextBuilder.kt`)
Đường dẫn file: `app/src/main/java/com/example/ai/ScreenContextBuilder.kt`

```kotlin
package com.example.ai

import com.example.model.ActiveBasket
import com.example.model.BasketBacktestReport
import org.json.JSONArray
import org.json.JSONObject

object ScreenContextBuilder {
    fun buildDualBasketContext(
        currentScreen: String,
        selectedSymbol: String?,
        primaryBasket: ActiveBasket?,
        secondaryBasket: ActiveBasket?,
        backtestReport: BasketBacktestReport?
    ): String {
        val root = JSONObject()
        root.put("currentScreen", currentScreen)
        selectedSymbol?.let { root.put("selectedSymbol", it) }

        fun serializeBasket(basket: ActiveBasket?): JSONObject {
            val json = JSONObject()
            if (basket != null) {
                json.put("status", basket.status.name)
                json.put("investedUsdt", basket.totalInvested)
                json.put("coinsCount", basket.positions.size)
                json.put("basketAtrPercent", basket.basketAtrPercent)
                json.put("activationRoi", basket.activationRoi)
                json.put("currentTrailingStopRoi", basket.currentTrailingStopRoi)
                json.put("peakRoi", basket.peakRoi)
                val hours = (System.currentTimeMillis() - basket.openTime) / 3600000.0
                json.put("holdingHours", hours)
                json.put("isTimedOut", hours >= 72.0)
                val arr = JSONArray()
                basket.positions.forEach { arr.put(it.symbol) }
                json.put("symbols", arr)
            } else {
                json.put("status", "EMPTY_SLOT")
            }
            return json
        }

        root.put("primaryBasket", serializeBasket(primaryBasket))
        root.put("secondaryBasket", serializeBasket(secondaryBasket))

        if (currentScreen == "ANALYTICS" && backtestReport != null) {
            val bJson = JSONObject()
            bJson.put("totalCycles", backtestReport.totalCycles)
            bJson.put("winrate", backtestReport.winratePercent)
            bJson.put("netProfitUsdt", backtestReport.netProfitUsdt)
            bJson.put("maxDrawdown", backtestReport.maxDrawdownPercent)
            bJson.put("avgHoldingDays", backtestReport.averageHoldingDays)
            root.put("backtestReport", bJson)
        }
        return root.toString()
    }
}
```

---

### 3.4 ViewModel Điều Phối Streaming (`AiAssistantViewModel.kt`)
Đường dẫn file: `app/src/main/java/com/example/ai/AiAssistantViewModel.kt`

```kotlin
package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val generativeModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-1.5-flash",
            systemInstruction = AiSystemPrompt.INSTRUCTIONS
        )
    }

    fun askAi(prompt: String, screenContextJson: String) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            try {
                val fullPrompt = """
                    BỐI CẢNH DỮ LIỆU HIỆN TẠI (JSON SNAPSHOT):
                    $screenContextJson

                    YÊU CẦU CỦA ANH BÌNH:
                    $prompt
                """.trimIndent()

                var accumulatedText = ""
                withContext(Dispatchers.IO) {
                    val responseStream = generativeModel.generateContentStream(fullPrompt)
                    responseStream.collect { chunk ->
                        val text = chunk.text ?: ""
                        accumulatedText += text
                        _aiState.value = AiUiState.Streaming(accumulatedText)
                    }
                }
                _aiState.value = AiUiState.Success(accumulatedText)
            } catch (e: Exception) {
                _aiState.value = AiUiState.Error(e.localizedMessage ?: "Lỗi kết nối AI Assistant.")
            }
        }
    }

    fun resetState() {
        _aiState.value = AiUiState.Idle
    }
}
```

---

### 3.5 Giao Diện Nút Nổi & Popup Sheet 80% (`AiAssistantUi.kt`)
Đường dẫn file: `app/src/main/java/com/example/ui/components/AiAssistantUi.kt`

```kotlin
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAssistantViewModel
import com.example.ai.AiUiState
import com.example.ui.theme.*

@Composable
fun FloatingAiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(52.dp)
            .clickable { onClick() },
        shape = CircleShape,
        color = DarkSurfaceElevated,
        border = BorderStroke(1.5.dp, CryptoGold),
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Trợ lý AI",
                tint = CryptoGold,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    viewModel: AiAssistantViewModel,
    screenContextJson: String,
    currentScreenName: String,
    onDismiss: () -> Unit
) {
    val aiState by viewModel.aiState.collectAsState()
    var inputQuery by remember { mutableStateOf("") }

    val quickChips = when (currentScreenName) {
        "POSITIONS" -> listOf("Giải thích trạng thái 2 giỏ", "Rủi ro dính Timeout 72h?", "Khoảng cách đến Trailing Stop")
        "ANALYTICS" -> listOf("Đánh giá kết quả chu kỳ vừa test", "Gợi ý tối ưu tham số TP/SL", "Mức Max Drawdown có an toàn?")
        "CHART" -> listOf("Đánh giá nến D1 so với EMA20", "RVol coin này có nổ 2x không?", "ATR% biến động ra sao?")
        else -> listOf("Tóm tắt sức khỏe thị trường", "BTC có thỏa mãn Regime không?", "Đề xuất phiên quét kế tiếp")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CryptoGold.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f) // MỞ RỘNG ĐÚNG 80% CHIỀU CAO MÀN HÌNH
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Cố Vấn AI • Dual Basket v2.0", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary)
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(quickChips) { chipText ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfaceHighlight,
                        border = BorderStroke(0.5.dp, DarkBorder),
                        modifier = Modifier.clickable { viewModel.askAi(chipText, screenContextJson) }
                    ) {
                        Text(text = chipText, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }

            Divider(color = DarkBorder, thickness = 0.5.dp)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (val state = aiState) {
                    is AiUiState.Idle -> Text("Chào anh Bình! Em đã nắm rõ toàn bộ thuật toán Dual Basket v2.0. Anh hãy chọn chip câu hỏi hoặc gõ thắc mắc bên dưới nhé.", color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    is AiUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CryptoGold, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini đang đọc dữ liệu giỏ lệnh...", color = TextSecondary, fontSize = 12.sp)
                    }
                    is AiUiState.Streaming -> Text(text = state.partialResponse, color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                    is AiUiState.Success -> Text(text = state.fullResponse, color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
                    is AiUiState.Error -> Text(text = "Lỗi: ${state.message}", color = CryptoRed, fontSize = 12.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = { Text("Hỏi AI về vị thế hoặc chiến lược...", color = TextMuted, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputQuery.isNotBlank()) {
                            viewModel.askAi(inputQuery, screenContextJson)
                            inputQuery = ""
                        }
                    },
                    modifier = Modifier.background(CryptoGold, RoundedCornerShape(8.dp)).size(48.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Gửi", tint = Color.Black)
                }
            }
        }
    }
}
```

---

## 4. HƯỚNG DẪN RÁP NỐI VÀO HỆ THỐNG HIỆN HỮU

### Tích Hợp Nút Nổi Vào `MainActivity.kt`
Trong `MainActivity.kt`, đặt `FloatingAiButton` trong `Box` giao diện chính trên `Scaffold`:

```kotlin
var showAiSheet by remember { mutableStateOf(false) }
val aiViewModel: AiAssistantViewModel = viewModel()

Scaffold(
    bottomBar = { /* Bottom Navigation hiện tại */ }
) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        // Nội dung màn hình theo NavScreen...

        // NÚT TRÒN NỔI DUY NHẤT NEO GÓC DƯỚI PHẢI
        if (!showAiSheet) {
            FloatingAiButton(
                onClick = { showAiSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )
        }

        // POPUP MODAL SHEET CHIẾM 80%
        if (showAiSheet) {
            val contextJson = ScreenContextBuilder.buildDualBasketContext(
                currentScreen = currentScreen.name,
                selectedSymbol = uiState.selectedSymbol,
                primaryBasket = viewModel.primaryBasket,
                secondaryBasket = viewModel.secondaryBasket,
                backtestReport = uiState.basketReport
            )

            AiAssistantBottomSheet(
                viewModel = aiViewModel,
                screenContextJson = contextJson,
                currentScreenName = currentScreen.name,
                onDismiss = { showAiSheet = false }
            )
        }
    }
}
```

---
*Tài liệu này được xuất tự động phục vụ bàn giao trực tiếp giữa Nhà Sáng Lập và Đội ngũ Kỹ sư Backend.*