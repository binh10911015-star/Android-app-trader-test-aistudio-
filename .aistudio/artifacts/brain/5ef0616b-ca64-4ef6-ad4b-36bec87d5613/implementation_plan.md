# Kế Hoạch Triển Khai: Chandelier Exit Engine Theo Giá Tuyệt Đối Từ Đỉnh D1 & Cơ Chế Crash Recovery

Triển khai chuyển đổi toàn diện logic Trailing Stop sang **Chandelier Exit Engine theo đơn vị giá tuyệt đối** cho từng vị thế riêng lẻ (`Single Position`), áp dụng nguyên tắc **Ratchet (chỉ tiến lên theo đỉnh, không bao giờ lùi)**, bộ đệm Debounce 2 chu kỳ chống râu nến, và module **Crash Recovery** tự động dựng lại đỉnh lịch sử từ nến 1H khi khởi động lại ứng dụng.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Các nguyên tắc cốt lõi đã được chuẩn hóa theo đặc tả kỹ thuật:**
> 1. **Giá trị ATR(14) D1 cố định**: Lưu trữ trường `atr14D1` trực tiếp vào bảng `trade_positions` của Room DB khi mở vị thế. Giá trị này được giữ nguyên suốt vòng đời của lệnh để làm mốc biên độ tuyệt đối.
> 2. **Chuyển đổi hoàn toàn sang Giá Tuyệt Đối**: Thay thế công thức tính theo % ROI trước đây bằng công thức giá đô-la tuyệt đối:
>    - Ngưỡng kích hoạt: $\text{activationPrice} = \text{entryPrice} + 1.5 \times \text{atr14D1}$
>    - Sàn khóa lãi net: $\text{floorLockPrice} = \text{entryPrice} \times 1.005$ (+0.5% net)
>    - Mức Stop Chandelier ($k=2.0$): $\text{calculatedStopPrice} = \text{highestHighPrice} - 2.0 \times \text{atr14D1}$
>    - Nguyên tắc Ratchet: $\text{trailingStopPrice} = \max(\text{trailingStopPrice},\; \max(\text{floorLockPrice}, \text{calculatedStopPrice}))$
> 3. **Crash Recovery an toàn**: Khởi động lại hệ thống sẽ tự động quét nến 1H (`1h`) từ `entryTime`, cập nhật `highestHighPrice`, tính sẵn `trailingStopPrice` và lưu Room DB trước khi bật luồng Realtime Polling.
> 4. **Debounce 2 chu kỳ (6 – 10s)**: Chỉ đóng lệnh khi giá duy trì $\le \text{trailingStopPrice}$ qua 2 chu kỳ liên tiếp; nếu giá giật rút chân lên trên Stop, reset `debounceCounter = 0`.
> 5. **Thoát lệnh tuần tự**: Giãn cách 1.200 ms giữa các lệnh `ORDER_TYPE_MARKET` (`side = "SELL"`, `reduceOnly = true`).

---

## 1. Overview & Core Concept

- **Mục tiêu**: Loại bỏ hoàn toàn sự phụ thuộc vào % ROI tương đối vốn dễ bị méo mó do biến động biên độ nến khác nhau giữa các coin; thay vào đó sử dụng biến động thực tế nến ngày $ATR(14)_{D1}$ của chính coin đó để tính toán mức chặn dừng lỗ trượt (Chandelier Exit).
- **Vòng đời vị thế (Single Position Lifecycle)**:
  - Khởi tạo: Mở lệnh 5.1 USDT (Long Cross 1x) $\to$ Lưu `entryPrice`, `atr14D1`, `highestHighPrice = entryPrice`, `trailingStopPrice = 0.0`, `isTrailingActive = false`, `debounceCounter = 0`.
  - Giám sát thời gian thực: Cập nhật đỉnh cao nhất theo Mark Price từ `GET /fapi/v1/premiumIndex`.
  - Kích hoạt Trailing: Khi `markPrice >= entryPrice + 1.5 * atr14D1`.
  - Neo Stop một chiều: Khi đỉnh lập mức cao mới, đường Stop tự động kéo lên theo mốc $\text{highestHighPrice} - 2.0 \times \text{atr14D1}$ (nhưng không thấp hơn $\text{entryPrice} \times 1.005$).
  - Thoát lệnh an toàn: Debounce 2 chu kỳ hoặc Smart Timeout 7 ngày (168 giờ) nếu vị thế đang lỗ.

---

## 2. User Experience & Visual Design

- **Màn hình Danh sách Vị Thế (`PositionsScreen.kt`)**:
  - **Mức đỉnh cao nhất**: Hiển thị rõ giá trị đỉnh đã đạt được (`Đỉnh: $X.XXXX`).
  - **Trạng thái Chandelier Exit**:
    - Khi chưa kích hoạt: Hiển thị mốc cần chạm để kích hoạt (`Kích hoạt tại: $X.XXXX`).
    - Khi đã kích hoạt (`isTrailingActive == true`): Hiển thị huy hiệu `CHANDELIER ACTIVE` kèm mức giá dừng lãi hiện tại (`Stop: $X.XXXX`).
  - **Chỉ báo Debounce lọc râu nến**: Khi giá chạm ngưỡng Stop ở chu kỳ 1, hiển thị cảnh báo `Đang lọc râu ảo (1/2)`.
- **Cập nhật Cài đặt & Thông báo**:
  - Thông báo Alert rõ ràng lý do thoát: `CHANDELIER_TRAILING_HIT` hoặc `TIMEOUT_7D_LOSS_CUT`.

---

## 3. Key Product Decisions & Trade-Offs

- **Quyết định 1: Cấu trúc thực thể Room DB**:
  - Bổ sung 5 trường vào `@Entity data class TradePosition`: `atr14D1`, `highestHighPrice`, `trailingStopPrice`, `isTrailingActive`, `debounceCounter`.
  - Nâng `AppDatabase` lên `version = 4` với `fallbackToDestructiveMigration()`.
- **Quyết định 2: Điểm neo Chandelier tính theo giá tuyệt đối**:
  - *Ưu điểm*: Chính xác tuyệt đối theo biên độ dao động thị trường của từng coin, không bị phụ thuộc vào đòn bẩy hay vốn mở lệnh.
- **Quyết định 3: Nguyên tắc Ratchet**:
  - Giá trailing stop chỉ tăng, không bao giờ giảm kể cả khi giá coin điều chỉnh giảm mạnh sau khi lập đỉnh.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│            REALTIME REST POLLING (3.5s/lần)            │
│            GET /fapi/v1/premiumIndex                   │
└──────────────────────────┬─────────────────────────────┘
                           │ Lấy markPrice từng coin
                           ▼
┌────────────────────────────────────────────────────────┐
│ 1. Cập nhật Đỉnh cao nhất:                             │
│    markPrice > highestHighPrice                        │
│    -> highestHighPrice = markPrice                     │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ 2. Kiểm tra Kích hoạt Trailing (Mốc A):                │
│    activationPrice = entryPrice + (1.5 * atr14D1)      │
│    markPrice >= activationPrice -> isTrailingActive=true│
└──────────────────────────┬─────────────────────────────┘
                           │ Khi isTrailingActive == true
                           ▼
┌────────────────────────────────────────────────────────┐
│ 3. Tính mức Chandelier Stop & Nguyên tắc Ratchet:      │
│    floorLockPrice = entryPrice * 1.005                 │
│    calculatedStop = highestHighPrice - (2.0 * atr14D1) │
│    newStop = max(floorLockPrice, calculatedStop)       │
│    if (newStop > trailingStopPrice)                    │
│        trailingStopPrice = newStop                     │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ 4. Bộ đệm Debounce chống râu ảo (6 - 10s):             │
│    markPrice <= trailingStopPrice:                     │
│      - Chu kỳ 1: debounceCounter = 1 (Chờ)             │
│      - Chu kỳ 2: debounceCounter = 2                   │
│        -> enqueueExitOrder("CHANDELIER_TRAILING_HIT")  │
│    markPrice > trailingStopPrice:                      │
│      - debounceCounter = 0 (Reset)                     │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│ 5. Thoát lệnh tuần tự (Sequential Market Exit):        │
│    • Lệnh POST /fapi/v1/order: type=MARKET, side=SELL, │
│      reduceOnly=true                                   │
│    • Giãn cách an toàn 1.2s giữa các lệnh              │
│    • Cập nhật Room DB status = "CLOSED"                │
└────────────────────────────────────────────────────────┘
```

### Các bước chỉnh sửa mã nguồn cụ thể:

1. **`TradeModels.kt` & `AppDatabase.kt`**:
   - Thêm `atr14D1: Double = 0.0`, `highestHighPrice: Double = entryPrice`, `trailingStopPrice: Double = 0.0`, `isTrailingActive: Boolean = false`, `debounceCounter: Int = 0` vào `TradePosition`.
   - Nâng cấp `AppDatabase` version = 4.
2. **`BinanceClient.kt`**:
   - Thêm hỗ trợ tham số `startTime: Long? = null` vào hàm `fetchKlinesWithFallback(symbol, interval, limit, startTime)`.
3. **`CryptoRepository.kt`**:
   - Trong `executeSimulatedBuy`: Nhận và lưu `atr14D1` thực tế của coin.
   - Thêm hàm `recoverOpenPositionsChandelierState()`: Duyệt qua các vị thế `OPEN`, fetch nến 1H từ `entryTime`, tính `highestHighPrice = maxOf(highestHighPrice, maxHistoricalHigh)`, tính `trailingStopPrice` theo Chandelier Stop, và lưu vào Room DB.
4. **`MainViewModel.kt`**:
   - Khi khởi động (`init`), gọi `recoverOpenPositionsChandelierState()` để phục hồi đỉnh trước khi polling.
   - Viết lại hàm `evaluateSinglePositionExits()` tuân thủ 100% công thức Chandelier Exit theo giá tuyệt đối từ đỉnh, Ratchet rule, Debounce 2 chu kỳ, và Smart Timeout 7 ngày.
   - Viết hàm `executeSequentialExits()` với độ trễ 1.200 ms an toàn.
5. **`PositionsScreen.kt`**:
   - Cập nhật giao diện thẻ vị thế hiển thị đầy đủ: `highestHighPrice`, `trailingStopPrice`, nhãn `CHANDELIER ACTIVE` và trạng thái `Debounce (1/2)`.
6. **Biên dịch & Xác minh**: Chạy `compile_applet` để đảm bảo 0 lỗi biên dịch.
