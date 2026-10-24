# TÀI LIỆU ĐẶC TẢ KỸ THUẬT: DUAL BASKET MOMENTUM V2.2
## TÍCH HỢP BỘ LỌC BTC MACRO REGIME & CƠ CHẾ SMART TIMEOUT ĐIỀU CHỈNH ĐỘNG

- **Phiên bản:** 2.2 (Long-term Macro Defense & Flexible Capital Rotation)
- **Kiến trúc:** Kotlin / Android Jetpack Compose / Repository Pattern
- **Mục tiêu:** Cải thiện tỷ suất lợi nhuận (ROI) trong các chu kỳ kiểm thử dài hạn (365 – 730 ngày), triệt tiêu bẫy kẹt vốn (Deadlock / Liquidity Trap) trong mùa Downtrend sâu của Bitcoin bằng cách bổ sung tùy chọn linh hoạt cho **Bộ Lọc BTC Macro Dài Hạn** và **Cơ Chế Smart Timeout**.

---

## 1. PHÂN TÍCH NGUYÊN NHÂN BIÊN LỢI NHUẬN THẤP (<10%) TRONG TEST DÀI HẠN

1. **Bẫy kẹt vốn dài hạn (Capital Deadlock):**
   - Khi không cài Stop-Loss và không Timeout, bot giữ nguyên trạng thái `isBasketActive = true` khi thị trường sập sâu.
   - Các altcoin breakout giả giảm từ 40% – 70%, khiến toàn bộ 60% hạn mức vốn bị đóng băng hàng trăm ngày (ví dụ trường hợp ATOM giữ 119 ngày), làm sụt giảm nghiêm trọng số chu kỳ quay vòng vốn mỗi năm.
2. **Hạn chế của bộ lọc biến động tức thời (Panic Circuit Breaker -3.5%):**
   - Mức giảm nến D1 > 3.5% chỉ chặn được các phiên bán tháo cục bộ trong 24h.
   - Trong các đợt Downtrend thực thụ, BTC giảm dần đều ("cưa chân bàn") mỗi ngày 1% – 2% trong nhiều tháng liên tục. Các altcoin vẫn có những cây nến tăng kỹ thuật ảo, kích hoạt bot gom giỏ rồi rơi vào trạng thái gồng lỗ dài hạn.

---

## 2. NÂNG CẤP MÔ HÌNH DỮ LIỆU CẤU HÌNH (CONFIG MODELS)

Bổ sung các cờ bật/tắt (Toggle) và trường tham số có thể điều chỉnh trực tiếp trên giao diện màn hình `AnalyticsScreen.kt` vào data class `BacktestConfig`:

```kotlin
package com.example.model

data class BacktestConfig(
    val initialCapital: Double = 10_000.0,
    val perTradePct: Double = 0.015,                // 1.5% vốn/lệnh
    val maxPortfolioAllocation: Double = 0.60,      // Trần giải ngân 60%
    val minQuoteVolume24h: Double = 5_000_000.0,    // Lọc thanh khoản 24h (USDT)
    val minVolRatio: Double = 2.0,                  // RVol tối thiểu
    val minD1ChangePct: Double = 2.0,               // Biên độ nến D1 tối thiểu (%)
    val maxD1ChangePct: Double = 17.5,              // Chặn trên nến D1 (%)

    // ========================================================
    // TÍNH NĂNG MỚI V2.2: BỘ LỌC THỊ TRƯỜNG BTC DÀI HẠN (BTC REGIME)
    // ========================================================
    val enableBtcMacroFilter: Boolean = true,       // Bật/Tắt bộ lọc BTC Macro dài hạn
    val btcEmaFastPeriod: Int = 20,                 // Chu kỳ EMA ngắn của BTC (D1)
    val btcEmaSlowPeriod: Int = 50,                 // Chu kỳ EMA dài của BTC (D1)
    val enableBtcCircuitBreaker: Boolean = true,    // Bật/Tắt ngắt mạch khẩn cấp khi BTC sập trong ngày
    val btcCircuitBreakerPct: Double = -3.5,        // Ngưỡng giảm D1 của BTC để hủy phiên quét (%)

    // ========================================================
    // TÍNH NĂNG MỚI V2.2: CƠ CHẾ SMART TIMEOUT LINH HOẠT
    // ========================================================
    val enableSmartTimeout: Boolean = true,         // Bật/Tắt tự động tất toán theo thời gian
    val timeoutDays: Int = 6,                       // Số ngày nắm giữ tối đa (Mặc định: 6 ngày = 144h)
    val disableTimeoutWhenTrailing: Boolean = true, // Không cắt nếu giỏ đang ở trạng thái TRAILING_ACTIVE (đang có lãi bám đỉnh)
    val timeoutLossFloorPct: Double = -0.025,       // Giới hạn lỗ tối đa cho phép khi dập Timeout (-2.5%)

    // THAM SỐ TRAILING ATR
    val kTpAtr: Double = 0.50,                      // Hệ số kích hoạt Trailing TP
    val minActivationRoi: Double = 0.015,           // Mức kích hoạt sàn (+1.5%)
    val kTrailStepAtr: Double = 0.25,               // Khoảng đệm giật lùi Trailing
    val floorLockRoi: Double = 0.005                // Sàn chốt lãi ròng (+0.5% net)
)
```

---

## 3. THIẾT KẾ BỘ LỌC XU HƯỚNG BTC DÀI HẠN (BTC TREND REGIME FILTER)

Tại mốc quét nến D1 (07:00 AM UTC+7):

1. **Nếu `enableBtcMacroFilter == false`:**
   - Bỏ qua kiểm tra xu hướng mẹ của BTC, trực tiếp chuyển sang quét tín hiệu các Altcoin.
2. **Nếu `enableBtcMacroFilter == true`:**
   - Tính toán chỉ báo kỹ thuật trên chuỗi nến D1 của cặp `BTCUSDT`:
     - $EMA_{fast} = EMA(btcEmaFastPeriod)$ (mặc định EMA20 nến Ngày).
     - $EMA_{slow} = EMA(btcEmaSlowPeriod)$ (mặc định EMA50 nến Ngày).
     - Độ dốc EMA: $\Delta EMA_{fast} = EMA_{fast}(t-1) - EMA_{fast}(t-2)$.
   - **Quy tắc cho phép mở giỏ (Bull Market / Safe Recovery):**
     $$Close_{BTC, t-1} > EMA_{fast} \quad \text{VÀ} \quad Close_{BTC, t-1} > EMA_{slow} \quad \text{VÀ} \quad \Delta EMA_{fast} \ge 0$$
   - **Nếu điều kiện trên không thỏa mãn:** 
     - Thị trường được xếp vào chế độ **Bear / Distribution (Phân phối hoặc Suy thoái)**.
     - Bot hủy toàn bộ phiên quét, giữ 100% tiền mặt USDT, không mở thêm bất kỳ vị thế Long nào cho đến khi BTC lấy lại xu hướng tăng trưởng trên D1.
3. **Nếu `enableBtcCircuitBreaker == true`:**
   - Tính biên độ nến D1 hôm qua của BTC: 
     $$\Delta Price_{BTC} = \frac{Close_{BTC, t-1} - Open_{BTC, t-1}}{Open_{BTC, t-1}} \times 100\%$$
   - Nếu $\Delta Price_{BTC} \le btcCircuitBreakerPct$ (ví dụ $\le -3.5\%$): Lập tức hủy phiên quét bất kể các chỉ báo EMA.

---

## 4. CƠ CHẾ SMART TIMEOUT THÍCH ỨNG (STATE MACHINE V2.2)

Khắc phục triệt để hiện tượng cắt lỗ ngay đỉnh sóng hồi hoặc kẹt vốn hàng tháng trời:

```
[Mỗi nhịp nến/giá] ──> Tính thời gian giữ = (currentTime - basket.openTime)
                             │
                             ▼
                     holdingDays >= timeoutDays ?
                             │
            ┌────────────────┴────────────────┐
          (CÓ)                              (KHÔNG)
            │                                 │
  status == TRAILING_ACTIVE                   ▼
  & disableTimeoutWhenTrailing ?        Tiếp tục theo dõi Trailing & TP
            │
     ┌──────┴──────┐
   (ĐÚNG)        (SAI)
     │             │
     ▼             ▼
Tiếp tục Trailing   Kiểm tra PnL:
(Ăn trọn sóng)     - PnL >= 0: Chốt hòa / lãi nhẹ (Giải phóng slot)
                   - PnL < 0: Chấp nhận cắt lỗ bảo toàn vốn (Lý do: TIMEOUT_EXPIRED)
```

### Quy tắc kỹ thuật trong vòng lặp thoát lệnh:

```kotlin
val holdingMillis = currentTime - basket.openTime
val isTimedOut = config.enableSmartTimeout && (holdingMillis >= config.timeoutDays * 86400000L)

when (basket.status) {
    BasketStatus.OPEN -> {
        when {
            // 1. Kích hoạt Trailing TP
            currentRoi >= basket.activationRoi -> {
                basket.status = BasketStatus.TRAILING_ACTIVE
                basket.peakRoi = currentRoi
                basket.currentTrailingStopRoi = maxOf(config.floorLockRoi, currentRoi - basket.trailStepRoi)
            }
            // 2. Kích hoạt Timeout khi chưa bật Trailing
            isTimedOut -> {
                exitReason = if (currentRoi >= 0) "TIMEOUT_PROFIT_TAKE" else "TIMEOUT_CAPITAL_RELEASE"
            }
        }
    }
    BasketStatus.TRAILING_ACTIVE -> {
        // Cập nhật bám đỉnh Trailing
        if (currentRoi > basket.peakRoi) {
            basket.peakRoi = currentRoi
            val newStop = maxOf(config.floorLockRoi, basket.peakRoi - basket.trailStepRoi)
            if (newStop > basket.currentTrailingStopRoi) {
                basket.currentTrailingStopRoi = newStop
            }
        }

        // Thoát lệnh khi chạm Trailing Stop
        if (currentRoi <= basket.currentTrailingStopRoi) {
            exitReason = "TRAILING_STOP_HIT"
        } else if (isTimedOut && !config.disableTimeoutWhenTrailing) {
            // Chỉ đóng khi người dùng KHÔNG bật cờ miễn trừ timeout lúc trailing
            exitReason = "TIMEOUT_IN_TRAIL"
        }
    }
    else -> Unit
}
```

---

## 5. MÃ NGUỒN CẬP NHẬT CỐT LÕI CHO `CryptoRepository.kt`

Backend cập nhật hàm tính toán EMA và vòng lặp `runBasketD1MomentumBacktest`:

```kotlin
// Helper tính Exponential Moving Average (EMA)
fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
    if (prices.size < period) return emptyList()
    val k = 2.0 / (period + 1)
    val emaList = mutableListOf<Double>()
    var ema = prices.take(period).average()
    emaList.add(ema)

    for (i in period until prices.size) {
        ema = (prices[i] * k) + (ema * (1 - k))
        emaList.add(ema)
    }
    return emaList
}

// Trong vòng lặp Backtest D1:
// 1. KIỂM TRA ĐIỀU KIỆN BTC MACRO DÀI HẠN
var isBtcSafeToTrade = true

if (config.enableBtcMacroFilter) {
    val btcCloses = btcCandles.subList(0, dayIdx).map { it.close }
    val emaFastList = calculateEMA(btcCloses, config.btcEmaFastPeriod)
    val emaSlowList = calculateEMA(btcCloses, config.btcEmaSlowPeriod)

    if (emaFastList.size >= 2 && emaSlowList.isNotEmpty()) {
        val currentBtcClose = btcCloses.last()
        val currentEmaFast = emaFastList.last()
        val prevEmaFast = emaFastList[emaFastList.size - 2]
        val currentEmaSlow = emaSlowList.last()

        val isAboveEmas = currentBtcClose > currentEmaFast && currentBtcClose > currentEmaSlow
        val isEmaSlopePositive = (currentEmaFast - prevEmaFast) >= 0.0

        isBtcSafeToTrade = isAboveEmas && isEmaSlopePositive
    } else {
        isBtcSafeToTrade = false
    }
}

if (config.enableBtcCircuitBreaker && isBtcSafeToTrade) {
    val btcChangePct = if (currentBtc.open > 0) ((currentBtc.close - currentBtc.open) / currentBtc.open) * 100.0 else 0.0
    if (btcChangePct <= config.btcCircuitBreakerPct) {
        isBtcSafeToTrade = false
    }
}

// 2. CHỈ MỞ GIỎ MỚI KHI BTC REGIME ĐẠT CHUẨN (HOẶC ĐÃ TẮT BỘ LỌC)
if (hasAvailableSlot && isBtcSafeToTrade) {
    // Thực thi quét tín hiệu RVol, ATR và phân bổ vốn...
}
```

---

## 6. THIẾT KẾ GIAO DIỆN MÀN HÌNH CẤU HÌNH (`AnalyticsScreen.kt`)

Backend bổ sung 2 nhóm thẻ điều khiển có công tắc chuyển mạch (`Switch`) và ô nhập giá trị tương ứng:

```
┌────────────────────────────────────────────────────────────────┐
│ [v] BỘ LỌC BTC MACRO REGIME (CHỐNG DOWNTREND)                  │
│ [Switch: ON/OFF]                                               │
│ Chu kỳ EMA Nhanh: [ 20 ]       Chu kỳ EMA Chậm: [ 50 ]         │
│ [X] Ngắt mạch sập nhanh (Circuit Breaker):  [ -3.5 ]%          │
├────────────────────────────────────────────────────────────────┤
│ [v] CƠ CHẾ SMART TIMEOUT (GIẢI PHÓNG VỐN)                      │
│ [Switch: ON/OFF]                                               │
│ Giới hạn thời gian giữ: [ 6 ] ngày (144 giờ)                   │
│ [X] Miễn trừ Timeout khi giỏ đang Trailing có lãi               │
└────────────────────────────────────────────────────────────────┘
```

Mã nguồn Composable minh họa cho hàng điều khiển:

```kotlin
// Khối cấu hình Bộ lọc BTC Macro
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bộ lọc BTC Macro Dài Hạn", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Switch(
                checked = enableBtcMacro,
                onCheckedChange = { enableBtcMacro = it },
                colors = SwitchDefaults.colors(checkedThumbColor = CryptoGold)
            )
        }
        if (enableBtcMacro) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = btcEmaFastText,
                    onValueChange = { btcEmaFastText = it },
                    label = { Text("EMA Nhanh") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = btcEmaSlowText,
                    onValueChange = { btcEmaSlowText = it },
                    label = { Text("EMA Chậm") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// Khối cấu hình Smart Timeout
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cơ Chế Smart Timeout (Ngày)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Switch(
                checked = enableTimeout,
                onCheckedChange = { enableTimeout = it },
                colors = SwitchDefaults.colors(checkedThumbColor = CryptoGold)
            )
        }
        if (enableTimeout) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = timeoutDaysText,
                    onValueChange = { timeoutDaysText = it },
                    label = { Text("Số ngày tối đa (Ngày)") },
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                        checked = exemptTrailingFromTimeout,
                        onCheckedChange = { exemptTrailingFromTimeout = it }
                    )
                    Text("Miễn trừ khi Trailing", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
```

---

## 7. CẬP NHẬT KHO TRI THỨC CHO AI CO-PILOT (`AiSystemPrompt.kt`)

Bổ sung thêm logic lý giải cho AI khi giải thích về các phiên không vào lệnh hoặc lệnh bị đóng do Timeout:

```kotlin
// Bổ sung vào AiSystemPrompt.INSTRUCTIONS:
"""
4. Cơ chế Bảo Vệ Xu Hướng Dài Hạn (BTC Macro Regime):
   - Khi bật: Bot chỉ mở giỏ mới nếu BTC > EMA(20) và BTC > EMA(50) trên khung D1 kèm độ dốc dương. Nếu BTC gãy xu hướng, bot chủ động giữ 100% tiền mặt để né Downtrend.
5. Cơ chế Xoay Vòng Vốn Smart Timeout:
   - Khi bật: Tự động tất toán giỏ sau N ngày (mặc định 6 ngày) nếu chưa chạm Trailing TP nhằm tránh kẹt vốn. Nếu giỏ đang trong trạng thái Trailing có lãi, Timeout được tự động vô hiệu hóa để tối đa hóa lợi nhuận.
"""
```

---

*Tài liệu này là căn cứ kỹ thuật chính thức để Backend cấu hình mã nguồn kiểm thử và triển khai các bài test 365 – 730 ngày trên Binance Futures Mainnet.*