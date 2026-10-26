# Kế hoạch Triển khai: Kích Hoạt Giao Dịch Tiền Thật Binance Futures Mainnet & Chuẩn Hóa Thẻ Vị Thế

Kế hoạch thực thi chi tiết dựa trên yêu cầu giao dịch tiền thật của bạn và kết quả phản hồi làm rõ:
1. **Kiểm tra và hoàn thiện toàn bộ logic vào lệnh thật**: Đảm bảo 100% an toàn kỹ thuật (Cross 1x, StepSize, MinNotional >= 5.0 USDT, Timestamp Offset đồng bộ sàn, kiểm tra trần vốn 60%).
2. **Chuyển đổi hoàn toàn Thẻ Vị Thế sang Binance Futures Mainnet**: Loại bỏ nhãn Paper Trade/Mô phỏng, hiển thị trực tiếp số dư ví thật, danh sách vị thế thật từ Binance, kèm nút đóng vị thế tức thì trên sàn.
3. **Đồng bộ 2 chiều với Database (Room DB)**: Lấy dữ liệu vị thế từ `/fapi/v2/positionRisk` và lưu trữ cục bộ để duy trì lịch sử lệnh và máy trạng thái Trailing ATR.
4. **Cơ chế bắn lệnh ngay lập tức**: Bắn lệnh tức thời khi hệ thống quét thấy tín hiệu hoặc khi người dùng bấm nút mà không bị cản trở bởi popup xác nhận PIN.

---

## I. Kiểm Tra & Thẩm Định Toàn Bộ Logic Vào Lệnh Thật (Mainnet Pre-Flight Checklist)

Trước khi thực hiện bất kỳ lệnh tiền thật nào, hệ thống phải vượt qua toàn bộ các khâu kiểm soát an toàn sau:

```
[BẮT ĐẦU VÀO LỆNH THẬT]
         │
         ├── 1. KIỂM TRA API & KẾT NỐI:
         │      • API Key & Secret Key không rỗng, đã lưu an toàn trong EncryptedSharedPreferences.
         │      • Endpoint cố định: https://fapi.binance.com (Mainnet).
         │
         ├── 2. ĐỒNG BỘ THỜI GIAN (CHỐNG LỖI -1021 TIMESTAMP OUTSIDE RECVWINDOW):
         │      • Lấy thời gian chuẩn từ Binance qua /fapi/v1/time.
         │      • Áp dụng serverTimeOffset + recvWindow=60000ms cho mọi request ký HMAC SHA256.
         │
         ├── 3. THIẾT LẬP CHẾ ĐỘ KÝ QUỸ & ĐÒN BẨY:
         │      • Gọi POST /fapi/v1/marginType: Ép cứng CROSSED (bỏ qua nếu sàn trả về -4046 "No need to change").
         │      • Gọi POST /fapi/v1/leverage: Ép cứng leverage = 1.
         │
         ├── 4. KIỂM TRA HẠN MỨC TRẦN VỐN DANH MỤC:
         │      • Tổng vốn các vị thế đang mở + Vốn lệnh mới <= 60% Tổng Equity Ví.
         │      • Nếu vượt trần 60%: Từ chối mở thêm để bảo toàn tài khoản.
         │
         ├── 5. TÍNH TOÁN KHỐI LƯỢNG (CHỐNG LỖI -1111 LOT_SIZE & -4164 MIN_NOTIONAL):
         │      • Lấy ExchangeInfo của coin (stepSize, minQty, minNotional).
         │      • Vốn lệnh = 5.1 USDT (hoặc cấu hình người dùng). Luôn đảm bảo >= minNotional (5.0 USDT).
         │      • Khối lượng Qty = Vốn / Giá_thị_trường.
         │      • Làm tròn Qty chuẩn xác theo stepSize (làm tròn xuống RoundingMode.DOWN).
         │      • Đảm bảo Qty >= minQty.
         │
         ├── 6. BẮN LỆNH THỊ TRƯỜNG (MARKET BUY):
         │      • Gửi POST /fapi/v1/order: side=BUY, type=MARKET, quantity=formattedQty.
         │      • Kiểm tra trạng thái phản hồi từ Binance: OrderId, status=FILLED, executedQty, avgPrice.
         │
         └── 7. LƯU DATABASE & THEO DÕI:
                • Lưu TradePosition vào Room SQLite (isLiveBinanceOrder = true, binanceOrderId, exactPrice, exactQty).
                • Bắt đầu theo dõi Trailing ATR độc lập và đồng hồ đếm ngược 7 ngày timeout.
```

---

## II. Chi Tiết Các Hạng Mục Cần Triển Khai

### 1. Nâng Cấp `BinanceFuturesApiVerifier.kt` & `BinanceClient.kt`
- **Đồng bộ thời gian triệt để**: Sử dụng `getSyncedTimestamp()` trong hàm thiết lập `marginType` và `leverage` của `BinanceClient.kt`, loại bỏ hoàn toàn `System.currentTimeMillis()` trần để ngăn lỗi lệch giờ máy.
- **Xử lý mã phản hồi sàn**: Khi gọi `marginType=CROSSED`, nếu nhận mã `-4046` (đã ở chế độ CROSSED), coi như thành công và tiếp tục đặt lệnh.
- **Bảo vệ an toàn giá 0**: Ngăn chặn tuyệt đối việc chia cho 0 hoặc giá âm trước khi tính khối lượng.

### 2. Thiết Kế Lại Thẻ Vị Thế (`PositionsScreen.kt`) Sang 100% Binance Mainnet
- **Thẻ Tổng Quan Ví Mainnet (Top Header)**:
  - Thay đổi toàn bộ nhãn "SỐ DƯ PAPER TRADE" thành: **"VÍ BINANCE FUTURES MAINNET (TIỀN THẬT)"**.
  - Hiển thị 3 chỉ số cốt lõi:
    - **Tổng số dư ví (Wallet Balance)**: Số tiền USDT thực tế trên Binance.
    - **Số dư khả dụng (Available Margin)**: Số tiền có thể dùng để mở thêm lệnh mới.
    - **Lãi/Lỗ chưa chốt (Unrealized PnL & ROI%)**: Tính tổng từ các vị thế đang chạy trên Binance với màu Xanh/Đỏ trực quan.
- **Danh Sách Vị Thế Mở (Active Positions)**:
  - Loại bỏ hoàn toàn khối phân tích giỏ Paper Trade.
  - Hiển thị trực tiếp danh sách vị thế Live từ Binance (đồng bộ với Room DB):
    - Tên cặp tiền (ví dụ: `BTCUSDT`, `SOLUSDT`).
    - Huy hiệu nổi bật: **"BINANCE LIVE (LONG CROSS 1X)"**.
    - **Giá vào lệnh (Entry Price)** và **Giá đánh dấu (Mark Price)** cập nhật thời gian thực.
    - **Ký quỹ (Margin USDT)** và **Khối lượng (Quantity)**.
    - **PnL (USDT)** và **Tỷ lệ sinh lời (ROI %)**.
    - **Giá thanh lý (Liq Price)**.
    - Nút **"Đóng vị thế (Thị trường)"**: Bấm một chạm để gửi ngay lệnh Market Close (`reduceOnly=true`) lên sàn Binance và xóa/cập nhật trạng thái trong database.
- **Nút "Đóng Tất Cả Vị Thế"**:
  - Gửi lệnh đóng toàn bộ các vị thế đang mở trên sàn Binance Mainnet.

### 3. Đồng Bộ Dữ Liệu Vị Thế Với Database Room (`MainViewModel.kt` & `CryptoRepository.kt`)
- Khi kết nối API thành công và gọi `refreshBinancePortfolio()`, hệ thống:
  - Quét danh sách vị thế từ `/fapi/v2/positionRisk`.
  - Với các vị thế có `positionAmt != 0`: Đồng bộ hoặc thêm mới vào bảng `trade_positions` của Room DB với cờ `isLiveBinanceOrder = true`.
  - Với các vị thế đã đóng trên sàn (`positionAmt == 0` nhưng trong DB vẫn mở): Tự động cập nhật trạng thái trong DB thành `STATUS_CLOSED`.
- Người dùng có thể xem lại lịch sử lệnh đầy đủ trong Tab "Lịch Sử".

### 4. Cơ Chế Bắn Lệnh Trực Tiếp (Không Bị Chặn Bởi PIN)
- Theo đúng câu trả lời làm rõ của bạn: Hệ thống sẽ **bắn lệnh ngay lập tức** khi:
  - Quét định kỳ chạm mốc 07:00 AM hoặc 19:00 PM UTC+7 tìm thấy coin thỏa mãn bộ lọc.
  - Người dùng bấm nút "Mở Tất Cả Vị Thế (Long 1x)" trên màn hình Thị Trường/Tín Hiệu.
  - Người dùng bấm mở vị thế đơn lẻ của một coin cụ thể.
- Bỏ qua bước xác nhận PIN nhập lại nhiều lần, chỉ yêu cầu API Key hợp lệ và còn đủ hạn mức giải ngân.

---

## III. Các Tệp Sẽ Chỉnh Sửa

| Tệp tin | Nội dung chỉnh sửa chính |
|---------|-------------------------|
| `app/src/main/java/com/example/network/BinanceClient.kt` | Áp dụng `getSyncedTimestamp()` cho marginType/leverage, bỏ qua lỗi -4046, bỏ URL testnet còn sót. |
| `app/src/main/java/com/example/network/BinanceFuturesApiVerifier.kt` | Kiểm tra độ chính xác làm tròn stepSize, minNotional, xử lý lệnh Market Close an toàn tuyệt đối. |
| `app/src/main/java/com/example/ui/screens/PositionsScreen.kt` | Chuyển đổi toàn bộ giao diện thành Binance Futures Mainnet, hiển thị số dư ví thật, danh sách vị thế thật và nút đóng lệnh sàn. |
| `app/src/main/java/com/example/ui/MainViewModel.kt` | Tích hợp đồng bộ tự động giữa `/fapi/v2/positionRisk` và Room DB, đảm bảo bắn lệnh trực tiếp khi có tín hiệu. |
| `app/src/main/java/com/example/repository/CryptoRepository.kt` | Bổ sung hàm đồng bộ trạng thái Live Position với SQLite để lưu trữ bền vững. |

---

## IV. Kế Hoạch Xác Minh Sau Khi Thực Hiện

1. Chạy biên dịch toàn bộ dự án bằng `compile_applet` để đảm bảo 0 lỗi biên dịch Kotlin/Compose.
2. Kiểm tra giao diện Thẻ Vị Thế:
   - Khi đã nhập API Binance: Hiển thị ngay số dư ví thật, lãi lỗ thật, danh sách vị thế thật từ Binance Mainnet.
   - Thao tác đóng vị thế: Thực hiện trực tiếp trên Binance Mainnet và phản ánh tức thì lên UI.
3. Kiểm tra logic mở lệnh: Vốn 5.1 USDT, đòn bẩy Cross 1x, làm tròn số lượng theo chuẩn Binance, không bị lỗi -1021 hay -1111.
