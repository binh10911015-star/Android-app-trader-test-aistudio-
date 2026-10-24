# Kế Hoạch Triển Khai (Cập Nhật): Thuần Nhất FloorLock & ATR Trailing Stop

## 1. Triết Lý & Yêu Cầu Chiến Lược Cập Nhật
Theo chỉ đạo chính xác từ người dùng:
1. **Loại bỏ Stop Loss (SL):** Không cắt lỗ non/cố định.
2. **Loại bỏ Timeout giải phóng vốn:** Không giới hạn thời gian nắm giữ, giữ kiên trì dù lời hay lỗ cho tới khi chạm điểm chốt lời.
3. **Loại bỏ Take Profit cố định ($K_{TP}$):** Không chặn trần lợi nhuận, để xu hướng tự do phát triển.
4. **Loại bỏ BTC Panic Circuit Breaker:** Bỏ hoàn toàn cơ chế ngắt mạch BTC theo yêu cầu của người dùng.
5. **CƠ CHẾ THOÁT LỆNH DUY NHẤT CỦA CHIẾN LƯỢC: FLOORLOCK & ATR TRAILING STOP (`TRAILING_STOP_HIT`)**
   - Vị thế được giữ liên tục (Spot / Cross 1x với hạn mức tối đa 60% vốn).
   - Khi giỏ đạt mức lợi nhuận dương tối thiểu (FloorLock $\ge +0.5\%$ net sau phí), hệ thống kích hoạt chế độ bám đỉnh:
     - Ghi nhận đỉnh lợi nhuận cao nhất đạt được ($ROI_{max}$).
     - Ngưỡng chốt lời động: $StopRoi = \max(0.005, ROI_{max} - K_{Trail} \times ATR_{basket})$.
     - Toàn bộ giỏ chỉ thoát khi $ROI_{giỏ} \le StopRoi$, bảo toàn trọn vẹn phần lợi nhuận lớn nhất của sóng tăng.
6. **Tích hợp thông báo cạnh nút Cài đặt:** Thu gọn banner thông báo dài thành biểu tượng Chuông thông báo nhỏ gọn có Badge số lượng cảnh báo chưa đọc, bấm vào mở danh sách cảnh báo xem nhanh.
7. **Nút kích hoạt Bot ngay bên dưới nút Cài đặt:** Nút switch compact có đèn LED trạng thái (Chạy/Tạm dừng) đặt ngay dưới nút Cài đặt tại góc phải header.
8. **Bộ thông số Cài đặt chiến lược đầy đủ & đồng bộ 2 chiều:**
   - Màn hình Cài đặt chỉ tập trung vào các thông số cốt lõi: Vốn chu kỳ (USDT), Giới hạn phân bổ danh mục (60%), Lọc Volume 24h tối thiểu (USD), Lọc RVol 10m tối thiểu, Biên độ nến D1 Min - Max (%), ATR(14) D1 Min - Max (%), Hệ số Trailing Stop $K_{Trail}$, Ngưỡng FloorLock (+0.5%).
   - Tự động đồng bộ 2 chiều giữa Cài đặt và Backtest.
9. **Thu gọn tỷ lệ giao diện (Compact Density):** Tinh chỉnh padding, margin, kích thước card và font chữ để tổng thể giao diện nhỏ gọn, hiển thị nhiều dữ liệu hơn.

---

## 2. Chi Tiết Các Thay Đổi Kỹ Thuật

### A. Loại Bỏ Hoàn Toàn SL, TP Cố Định, Timeout & BTC Circuit Breaker
- **`TradeModels.kt`:**
  - Xóa `kSl`, `enableStopLoss` khỏi `BacktestConfig`.
  - Xóa `ktp`, `enableTakeProfit` khỏi `BacktestConfig`.
  - Xóa `timeoutHours`, `enableTimeoutRelease` khỏi `BacktestConfig`.
  - Xóa `enableBtcPanicBreaker`, `btcDrop15mThreshold` khỏi `BacktestConfig`.
  - Xóa `kSl`, `initialStopLossRoi` khỏi `ActiveBasket`.
  - Lý do thoát lệnh `BasketExitReason`: **Chỉ còn DUY NHẤT 1 lý do thoát là `TRAILING_STOP_HIT`** (và kết thúc backtest `BACKTEST_END` nếu còn vị thế mở ở nến cuối cùng).
- **`CryptoRepository.kt`:**
  - Trong `evaluateBasketExit` và `runBasketD1MomentumBacktest`:
    - Xóa toàn bộ logic kiểm tra SL, TP cố định, Timeout và kiểm tra nến BTC.
    - Giỏ lệnh chạy xuyên suốt các nến 1h. Nếu $basketRoi \ge 0.005$, kích hoạt trailing bám đỉnh. Thoát khi thoái lui chạm ngưỡng trailing stop.
- **`AdaptiveOptimizationEngine.kt` & `AiAssistantViewModel.kt`:**
  - Dọn sạch các trường SL, TP, Timeout, BTC Breaker. Tập trung gợi ý tối ưu bộ lọc vào lệnh (Volume, RVol, biên độ D1, ATR) và độ nhạy $K_{Trail}$.
- **`BacktestScreen.kt`:**
  - Xóa tất cả các nút bật/tắt và thanh trượt của SL, TP cố định, Timeout, và BTC Breaker.

### B. Tinh Gọn Cụm Top Bar (Thông Báo, Cài Đặt, Kích Hoạt Bot)
- **`MainActivity.kt`:**
  - Xóa banner thông báo lớn phía trên.
  - Cụm điều khiển trên thanh Top Bar:
    - **Hàng trên:** Tiêu đề app bên trái; bên phải là Chuông thông báo (kèm Badge đỏ đếm cảnh báo) đặt cạnh icon Cài đặt (Settings gear). Bấm vào Chuông mở hộp thoại xem nhanh các cảnh báo bot/thị trường.
    - **Hàng dưới (ngay dưới nút Cài đặt):** Nút switch compact kích hoạt bot ("BOT AUTO") kèm chấm LED xanh lá (Đang chạy) hoặc xám (Tạm dừng).

### C. Mở Rộng Cài Đặt Chiến Lược & Đồng Bộ 2 Chiều
- **`TradeModels.kt` & `SettingsScreen.kt`:**
  - Cấu trúc lại `TradeSettings` thuần túy tập trung vào lọc vào lệnh và $K_{Trail}$:
    - Vốn đầu tư mỗi chu kỳ (USDT) & Giới hạn vốn tối đa 60%.
    - Lọc Volume 24h tối thiểu (USD).
    - Lọc RVol 10m tối thiểu.
    - Biên độ nến D1 Min - Max (%).
    - ATR(14) ngày Min - Max (%).
    - Hệ số Trailing Stop $K_{Trail}$ & Ngưỡng khóa lãi FloorLock (+0.5%).
  - Giao diện Cài đặt phân nhóm thẻ trực quan, đồng bộ 2 chiều với tab Backtest.

### D. Tinh Chỉnh Thu Nhỏ Tỷ Lệ Giao Diện (Compact UI)
- Giảm padding từ `16.dp` xuống `10.dp - 12.dp`.
- Thu gọn kích thước card và font chữ để tối ưu mật độ hiển thị trên màn hình.

---

## 3. Các Bước Xác Minh
1. Chạy `compile_applet` kiểm tra biên dịch thành công.
2. Kiểm tra mã nguồn đảm bảo 100% không còn sót tham chiếu đến SL, TP, Timeout hay BTC Breaker.
3. Chạy backtest: Xác nhận 100% các chu kỳ đóng lệnh chỉ do Trailing Stop bám đỉnh (`TRAILING_STOP_HIT`).
4. Kiểm tra cụm Top Bar: Chuông cạnh Cài đặt, nút kích hoạt Bot ở hàng dưới, giao diện compact cân đối.
