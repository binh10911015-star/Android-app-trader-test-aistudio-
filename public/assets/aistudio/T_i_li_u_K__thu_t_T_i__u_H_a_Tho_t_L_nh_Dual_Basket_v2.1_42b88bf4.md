# ĐẶC TẢ KỸ THUẬT: HIỆU CHỈNH TOÀN DIỆN TIMEOUT & STOP-LOSS BASKET V2.1
*(Cơ chế Pure Trend Following Trailing - Loại bỏ Timeout khi Trailing)*

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC CẢI TIẾN
* **Khắc phục lỗi "TIMEOUT_IN_TRAIL":** Loại bỏ hoàn toàn cơ chế cưỡng bức đóng lệnh theo thời gian khi giỏ lệnh đang ở trạng thái sinh lời (`TRAILING_ACTIVE`). Triệt tiêu hiện tượng "cắt lãi non" (cutting winners).
* **Nới rộng chu kỳ Timeout ở trạng thái OPEN:** Tăng từ 72h lên **144 giờ (6 ngày)** để giỏ lệnh có đủ 5–7 cây nến D1 hấp thụ các nhịp retest sau phiên bùng nổ khối lượng.
* **Tăng khoảng đệm dừng lỗ ban đầu ($K_{SL}$):** Tăng $K_{SL}$ từ `0.50` lên `0.75` và nâng sàn dừng lỗ tối thiểu lên `-3.5%` (thay vì `-2.0%`) để tránh bị quét trúng râu nến nhiễu trước khi Altcoin chạy sóng chính.

---

## 2. CÔNG THỨC QUẢN TRỊ RỦI RO & THOÁT LỆNH MỚI

1. **Biến động giỏ lệnh chuẩn hóa ($\text{Basket ATR}_{\%}$):**
   $$\text{Basket ATR}_{\%} = \sum_{i=1}^{N} \left( \text{entryAtrPercent}_i \times \frac{\text{marginAllocated}_i}{\text{TotalInvested}} \right)$$

2. **Ngưỡng kích hoạt Trailing Stop ($\text{Activation ROI}$):**
   $$\text{Activation ROI} = \max\Big(1.5\%, \; 0.50 \times \text{Basket ATR}_{\%}\Big)$$

3. **Ngưỡng Stop-Loss ban đầu ($\text{Initial SL}$ - Áp dụng khi chưa vào Trailing):**
   $$\text{Initial Stop-Loss ROI} = -\max\Big(\mathbf{3.5\%}, \; \mathbf{0.75} \times \text{Basket ATR}_{\%}\Big)$$

4. **Khoảng lùi Trailing ($\text{Trail Step}$):**
   $$\text{Trail Step ROI} = \max\Big(0.6\%, \; 0.25 \times \text{Basket ATR}_{\%}\Big)$$

5. **Sàn bảo toàn vốn khi Trailing:**
   $$\text{Floor Lock ROI} = +0.5\% \text{ net}$$

---

## 3. MÁY TRẠNG THÁI THOÁT LỆNH (STATE MACHINE SPECIFICATION)

```
                       ┌───────────────────────────────┐
                       │      OPEN (Chờ tín hiệu)      │
                       └──────────────┬────────────────┘
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
[ROI <= Initial SL]            [Hết hạn 144h]             [ROI >= Activation ROI]
           │                          │                          │
           ▼                          ▼                          ▼
┌─────────────────────┐    ┌─────────────────────┐    ┌───────────────────────────┐
│ ĐÓNG: SL_HIT_RELAXED│    │   ĐÓNG: TIMEOUT_144H│    │      TRAILING_ACTIVE      │
└─────────────────────┘    └─────────────────────┘    └─────────────┬─────────────┘
                                                                    │
                                                 ┌──────────────────┴──────────────────┐
                                                 │                                     │
                                         [ROI tạo Đỉnh Mới]              [ROI <= CurrentTrailingStop]
                                                 │                                     │
                                                 ▼                                     ▼
                                      ┌─────────────────────┐               ┌─────────────────────┐
                                      │  Cập nhật Peak ROI  │               │ ĐÓNG: TRAILING_STOP │
                                      │& Kéo Trailing Stop lên│             └─────────────────────┘
                                      └─────────────────────┘
                                      *(VÔ HIỆU HÓA TIMEOUT)*
```

* **Tại trạng thái `OPEN`:**
  * Giỏ chỉ thoát khi:
    1. `currentRoi <= basket.initialStopLossRoi` $\rightarrow$ Đóng với lý do: `"SL_HIT_RELAXED"`.
    2. `isTimedOut == true` (đã giữ $\ge 144$h mà chưa chạm ngưỡng kích hoạt) $\rightarrow$ Đóng với lý do: `"TIMEOUT_144H"`.
  * Nếu `currentRoi >= basket.activationRoi` $\rightarrow$ Chuyển trạng thái sang `TRAILING_ACTIVE`, gán đỉnh ban đầu `peakRoi = currentRoi` và neo chốt chặn `currentTrailingStopRoi = maxOf(floorLockRoi, currentRoi - trailStepRoi)`.

* **Tại trạng thái `TRAILING_ACTIVE` (PHƯƠNG ÁN A - PURE TREND FOLLOWING):**
  * **HỦY BỎ HOÀN TOÀN BIẾN KIỂM TRA TIMEOUT.** Không cho phép bất kỳ điều kiện thời gian nào can thiệp vào giai đoạn này.
  * Nếu `currentRoi > basket.peakRoi`: Cập nhật đỉnh mới và kéo đường dừng lỗ trượt:
    $$\text{New Stop} = \max\Big(\text{floorLockRoi}, \; \text{peakRoi} - \text{trailStepRoi}\Big)$$
    *(Chỉ kéo tịnh tiến lên, tuyệt đối không lùi xuống).*
  * Nếu `currentRoi <= basket.currentTrailingStopRoi`: Đóng toàn bộ giỏ lệnh với lý do: `"TRAILING_STOP_HIT"`.

---

## 4. MÃ NGUỒN KOTLIN CHI TIẾT CHO BACKEND

### A. Cập nhật `app/src/main/java/com/example/model/TradeModels.kt`

```kotlin
package com.example.model

enum class BasketSlotId {
    PRIMARY,    // Quét lúc 07:00 AM (UTC+7)
    SECONDARY   // Quét lúc 19:00 PM (UTC+7)
}

enum class BasketStatus {
    OPEN,               // Đang chạy bình thường, chưa đạt activationRoi
    TRAILING_ACTIVE,    // Đã kích hoạt bám đỉnh theo ATR, vô hiệu hóa Timeout
    CLOSING,            // Đang phát lệnh đóng vị thế
    CLOSED              // Đã tất toán hoàn toàn, sẵn sàng giải phóng slot
}

data class BasketPosition(
    val symbol: String,
    val entryPrice: Double,
    val quantity: Double,
    val marginAllocated: Double,
    val entryAtrPercent: Double // ATR(14) D1 / entryPrice
)

data class ActiveBasket(
    val slotId: BasketSlotId,
    val openTime: Long,
    val positions: List<BasketPosition>,
    val totalInvested: Double,
    var status: BasketStatus = BasketStatus.OPEN,
    
    // NỚI RỘNG TIMEOUT: 144 giờ (6 ngày) thay vì 72 giờ
    val maxHoldingMillis: Long = 144 * 3600 * 1000L
) {
    // 1. Độ biến động trung bình giỏ lệnh theo tỷ trọng vốn
    val basketAtrPercent: Double by lazy {
        if (totalInvested <= 0.0) 0.0
        else positions.sumOf { it.entryAtrPercent * (it.marginAllocated / totalInvested) }
    }

    // 2. Ngưỡng kích hoạt Trailing Stop (K_TP = 0.50, tối thiểu +1.5%)
    val activationRoi: Double by lazy {
        maxOf(0.015, 0.50 * basketAtrPercent)
    }

    // 3. Nới rộng khoảng đệm dừng lỗ (K_SL = 0.75, sàn dừng lỗ cứng -3.5%)
    val initialStopLossRoi: Double by lazy {
        -maxOf(0.035, 0.75 * basketAtrPercent)
    }

    // 4. Bước giật lùi Trailing khi bám đỉnh (K_trail = 0.25, tối thiểu 0.6%)
    val trailStepRoi: Double by lazy {
        maxOf(0.006, 0.25 * basketAtrPercent)
    }

    // 5. Mức sàn khóa lãi ròng tối thiểu khi đã bước vào Trailing
    val floorLockRoi: Double = 0.005 // +0.5% net

    // Biến trạng thái động
    var peakRoi: Double = 0.0
    var currentTrailingStopRoi: Double = -1.0
}
```

---

### B. Cập nhật cỗ máy kiểm tra thoát lệnh trong `app/src/main/java/com/example/repository/CryptoRepository.kt`

Thay thế khối logic trong hàm `evaluateExit` bên trong `runBasketD1MomentumBacktest`:

```kotlin
// --- TRÍCH ĐOẠN ĐÁNH GIÁ THOÁT LỆNH HOÀN CHỈNH TRONG CRYPTOREPOSITORY.KT ---
fun evaluateExit(basket: ActiveBasket?): ActiveBasket? {
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
        // TRẠNG THÁI 1: OPEN (Chưa chạm ngưỡng kích hoạt Trailing)
        BasketStatus.OPEN -> {
            when {
                // Đủ điều kiện kích hoạt Trailing
                currentRoi >= basket.activationRoi -> {
                    basket.status = BasketStatus.TRAILING_ACTIVE
                    basket.peakRoi = currentRoi
                    basket.currentTrailingStopRoi = maxOf(basket.floorLockRoi, currentRoi - basket.trailStepRoi)
                }
                // Cắt lỗ theo biên độ dừng lỗ nới rộng
                currentRoi <= basket.initialStopLossRoi -> {
                    exitReason = "SL_HIT_RELAXED"
                }
                // Hết 144 giờ mà vẫn lình xình chưa chạm activationRoi
                isTimedOut -> {
                    exitReason = "TIMEOUT_144H"
                }
            }
        }

        // TRẠNG THÁI 2: TRAILING_ACTIVE (Pure Trend Following - VÔ HIỆU HÓA TIMEOUT HOÀN TOÀN)
        BasketStatus.TRAILING_ACTIVE -> {
            // Liên tục bám đỉnh cao mới
            if (currentRoi > basket.peakRoi) {
                basket.peakRoi = currentRoi
                val newStop = maxOf(basket.floorLockRoi, basket.peakRoi - basket.trailStepRoi)
                if (newStop > basket.currentTrailingStopRoi) {
                    basket.currentTrailingStopRoi = newStop
                }
            }

            // Chỉ thoát lệnh khi giá thoái lui chạm đường Trailing Stop
            if (currentRoi <= basket.currentTrailingStopRoi) {
                exitReason = "TRAILING_STOP_HIT"
            }
            // LƯU Ý BẮT BUỘC: KHÔNG ĐƯỢC KIỂM TRA isTimedOut TẠI ĐÂY!
        }

        else -> Unit
    }

    // Xử lý tất toán khi có tín hiệu thoát
    if (exitReason != null) {
        val grossProfit = basket.totalInvested * currentRoi
        val takerFee = basket.totalInvested * 0.0008 // Phí taker 2 chiều
        val netProfit = grossProfit - takerFee
        currentEquity += netProfit
        peakEquity = maxOf(peakEquity, currentEquity)

        cyclesHistory.add(
            BasketCycleResult(
                cycleIndex = ++basketCycleCounter,
                openTime = basket.openTime,
                closeTime = currentTime,
                holdingDays = maxOf(1.0, (currentTime - basket.openTime) / 86400000.0),
                totalInvested = basket.totalInvested,
                realizedProfitUsdt = netProfit,
                basketRoiPercent = currentRoi * 100.0,
                equityAfterClose = currentEquity,
                coinsCount = basket.positions.size,
                symbolsList = basket.positions.map { it.symbol }
            )
        )
        return null // Giải phóng slot giỏ
    }

    // Cập nhật Max Drawdown
    val tempEquity = currentEquity + unrealizedPnL
    val dd = if (peakEquity > 0) ((peakEquity - tempEquity) / peakEquity) * 100.0 else 0.0
    if (dd > maxDrawdown) maxDrawdown = dd

    return basket
}
```

---

### C. Cập nhật đồng bộ trong `app/src/main/java/com/example/ai/AiSystemPrompt.kt`

Backend cập nhật lại mô tả quy tắc trong System Prompt để AI Co-pilot phân tích chính xác logic v2.1:

```kotlin
// Sửa mục 3 trong AiSystemPrompt.kt:
"""
- Tầng 3 (Quản trị vốn & Thoát lệnh v2.1):
  * Ký quỹ: Long Cross 1x (Effective Leverage <= 1.0).
  * Vốn: Tối đa 2% Equity/lệnh; Trần tối đa 60% Equity toàn danh mục.
  * Dynamic Target ROI (K_TP = 0.5): Kích hoạt Trailing khi ROI giỏ >= max(1.5%, 0.5 * Basket_ATR%).
  * Trailing Step: Giật lùi 0.25 * Basket_ATR% (tối thiểu 0.6%); Sàn bảo vệ lãi ròng floorLockRoi = +0.5% net.
  * Pure Trend Following: Khi đã vào trạng thái Trailing, VÔ HIỆU HÓA HOÀN TOÀN Timeout, để vị thế chạy hết biên độ sóng nến D1.
  * Dynamic Stop Loss (K_SL = 0.75): Cắt lỗ cứng ban đầu ở mức -max(3.5%, 0.75 * Basket_ATR%).
  * Smart Timeout: Chỉ áp dụng khi giỏ ở trạng thái OPEN, tự động tất toán sau 144 giờ (6 ngày) nếu không có động lượng bứt phá.
"""
```