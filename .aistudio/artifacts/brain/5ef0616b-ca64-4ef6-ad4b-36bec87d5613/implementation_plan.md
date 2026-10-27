# Kế Hoạch Triển Khai: Ưu Tiên Coin Có % Thay Đổi Nến t-1 Nhỏ Nhất & Quy Tắc Khớp Lệnh Thận Trọng Cho Chiến Lược SMA20 D1

Tài liệu này xác định chi tiết các quy tắc sắp xếp ưu tiên vào lệnh và quy tắc tất toán lệnh trong backtest cho Chiến lược SMA20 D1 (RR 1:3), áp dụng thống nhất cho cả **hệ thống quét & mở vị thế thực tế** lẫn **công cụ kiểm thử lịch sử (Backtest)**.

---

## 1. Quyết Định & Quy Tắc Cốt Lõi Đã Thống Nhất

1. **Công thức tính % thay đổi nến $t-1$**:
   $$\text{changePct}_{t-1} = \frac{\text{Close}_{t-1} - \text{Open}_{t-1}}{\text{Open}_{t-1}} \times 100\%$$
   - Đo lường mức độ biến động thực tế từ lúc mở nến đến lúc đóng nến của nến ngày $D1$ liền trước ($t-1$, nến đóng lúc 00:00 UTC / 07:00 AM UTC+7).
   - Vì điều kiện chiến lược yêu cầu nến $t-1$ là nến tăng ($\text{Close}_{t-1} > \text{Open}_{t-1}$), nên $\text{changePct}_{t-1} > 0$.

2. **Tiêu chí ưu tiên coin vào lệnh**:
   - **Tăng dần (Ascending Sort)**: Coin nào có $\text{changePct}_{t-1}$ **nhỏ nhất** sẽ đứng đầu danh sách và được mở lệnh trước tiên.
   - **Mục tiêu chiến lược**: Ưu tiên những coin vừa mới bứt phá nhẹ nhàng qua SMA20 (nến tăng vừa phải, ít bị overextended / quá mua) so với những coin đã pump nến quá dài, qua đó khoảng cách cắt lỗ $R = \text{Entry} - \text{Low}_{t-1}$ hợp lý hơn và xác suất chạm TP $3R$ cao hơn.

3. **Phạm vi áp dụng đồng bộ**:
   - **Quét tín hiệu & Mở vị thế thực tế** (`MainViewModel.kt` & `MarketScreen.kt`): Sắp xếp danh sách tín hiệu theo thứ tự ưu tiên tăng dần của $\text{changePct}_{t-1}$, hiển thị số thứ tự ưu tiên trên giao diện, và gửi lệnh theo đúng thứ tự này.
   - **Hệ thống Backtest** (`CryptoRepository.kt`): Tại mỗi ngày nến $D1$, các coin thỏa mãn điều kiện SMA20 D1 sẽ được gom và sắp xếp theo $\text{changePct}_{t-1}$ tăng dần trước khi mở vị thế mô phỏng.

4. **Quy tắc khớp lệnh kiểm thử thận trọng (Backtest Worst-Case Rule)**:
   - **Nếu trong cùng một ngày nến D1 mà giá vừa chạm TP vừa chạm SL**: Hệ thống backtest **bắt buộc ghi nhận lệnh đó là SL (Cắt lỗ)**.
   - Cơ chế này mô phỏng kịch bản bất lợi thực tế trong ngày khi thị trường biến động mạnh: giá có thể sụt giảm quét SL trước khi hồi phục lên TP. Giá thoát lệnh được tính bằng chính xác mức Stop Loss (`pos.stopLoss`).

---

## 2. Kiến Trúc & Luồng Xử Lý

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    QUÉT TÍN HIỆU SMA20 D1 (LÚC 07:00 AM UTC+7)              │
│       Điều kiện: Close[t-1] > Open[t-1] & Close[t-1] > SMA20 & Low[t-1] <= SMA20     │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│             TÍNH % THAY ĐỔI NẾN t-1: (Close[t-1] - Open[t-1]) / Open[t-1]   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│         SẮP XẾP TĂNG DẦN (ASCENDING): Coin có % nhỏ nhất lên đầu tiên        │
└──────────────────┬───────────────────────────────────────┬──────────────────┘
                   │                                       │
                   ▼                                       ▼
┌──────────────────────────────────────┐┌──────────────────────────────────────┐
│  QUÉT & MỞ VỊ THẾ THỰC TẾ (LIVE/BOT) ││      BACKTEST LỊCH SỬ (SIMULATION)   │
├──────────────────────────────────────┤├──────────────────────────────────────┤
│ 1. `d1Matches.sortBy { it.score }`   ││ 1. Gom candidate tín hiệu trong ngày │
│ 2. `openAllScannedPositions` duyệt   ││ 2. `candidatesToday.sortBy { ... }`  │
│    tuần tự từ index 0 (ưu tiên nhất) ││ 3. Vào lệnh lần lượt theo thứ tự nhỏ │
│ 3. UI Market: hiển thị badge thứ hạng││    nhất vào trước                    │
│    và % thay đổi nến t-1             ││ 4. KIỂM THỬ: Nếu cùng chạm cả TP & SL│
│                                      ││    -> BẮT BUỘC TÍNH LÀ CHẠM SL!      │
└──────────────────────────────────────┘└──────────────────────────────────────┘
```

---

## 3. Các Bước Triển Khai Chi Tiết

### Bước 1: Cập nhật Logic Quét và Mở Lệnh (`MainViewModel.kt`)
1. **Trong hàm `scanAllFuturesCoins`**:
   - Tính toán $\text{changePct}_{t-1} = \frac{\text{Close}_{t-1} - \text{Open}_{t-1}}{\text{Open}_{t-1}} \times 100.0$.
   - Gán `score = changePctT1` cho `BinanceTicker`.
   - Sắp xếp danh sách `d1Matches.sortBy { it.score }` (ưu tiên % nhỏ nhất trước).
2. **Trong hàm `applyFilters`**:
   - Khi bộ lọc là `MarketFilter.BULLISH_SIGNAL`, sắp xếp danh sách hiển thị theo `score` tăng dần để người dùng dễ dàng theo dõi coin nào có độ ưu tiên cao nhất.
3. **Trong hàm `openAllScannedPositions`**:
   - Đảm bảo danh sách `symbolsToOpen` luôn giữ trật tự tăng dần theo `score` (`.sortedBy { it.score }`), đảm bảo coin có % thay đổi nhỏ nhất được gửi lệnh trước.

### Bước 2: Cập nhật Trình Mô Phỏng Backtest (`CryptoRepository.kt`)
1. **Ưu tiên tín hiệu vào lệnh**:
   - Tại mỗi bước nến ngày $D1$ (`dayIdx`), khởi tạo danh sách tạm `candidatesToday`.
   - Khi một coin thỏa mãn cả 3 điều kiện SMA20 D1, tính `changePctT1 = ((k1.close - k1.open) / k1.open) * 100.0`.
   - Sắp xếp `candidatesToday.sortBy { it.changePctT1 }` trước khi đưa vào danh sách `openPositions`.
2. **Quy tắc thoát lệnh kiểm thử**:
   - Kiểm tra điều kiện:
     ```kotlin
     if (candleToday.low <= pos.stopLoss && candleToday.high >= pos.takeProfit) {
         // CÙNG CHẠM CẢ TP VÀ SL TRONG NGÀY -> GHI NHẬN LÀ SL (CẮT LỖ)
         exitReason = "SL (Cùng chạm TP & SL)"
         exitPrice = pos.stopLoss
     } else if (candleToday.low <= pos.stopLoss) {
         exitReason = "SL (-1R)"
         exitPrice = minOf(candleToday.open, pos.stopLoss)
     } else if (candleToday.high >= pos.takeProfit) {
         exitReason = "TP (+3R)"
         exitPrice = maxOf(candleToday.open, pos.takeProfit)
     }
     ```
   - Đảm bảo giá trị `exitPrice` khi chạm SL luôn được ghi nhận chính xác ở mức `pos.stopLoss` (hoặc thấp hơn nếu nến mở cửa Gap Down dưới SL).

### Bước 3: Cập nhật Giao Diện Danh Sách Tín Hiệu (`MarketScreen.kt`)
1. **Thẻ mô tả chiến lược SMA20 D1**:
   - Bổ sung chú thích rõ ràng: *"Quy tắc ưu tiên: Coin có % thay đổi nến t-1 nhỏ nhất vào trước"*.
2. **Thẻ coin tín hiệu (`CoinItemCard`)**:
   - Khi đang ở tab tín hiệu `BULLISH_SIGNAL`, hiển thị nhãn `% nến t-1` và thứ tự ưu tiên (ví dụ: `#1 - Nến t-1: +0.85%`, `#2 - Nến t-1: +1.20%`).

### Bước 4: Biên Dịch & Xác Nhận Kiểm Thử
- Chạy `compile_applet` để xác minh dự án không có lỗi cú pháp hoặc tương thích.
- Kiểm tra tính nhất quán giữa UI, Live Bot và Backtest Engine.
