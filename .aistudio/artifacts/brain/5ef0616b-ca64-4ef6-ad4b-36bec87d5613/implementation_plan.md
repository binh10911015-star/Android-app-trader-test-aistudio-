# Kế Hoạch Triển Khai: Nâng Cấp Gọi API Binance Futures Lấy Toàn Bộ Hơn 500 Coin USDT-M Perpetual

Tài liệu này xác định nguyên nhân và kế hoạch sửa chữa toàn diện hàm gọi API Binance Futures, đảm bảo hệ thống quét và danh sách thị trường luôn tải và xử lý **toàn bộ hơn 500 cặp coin USDT-M Perpetual đang giao dịch trên Binance Futures**, loại bỏ triệt để giới hạn ~50 coin mẫu tạm thời.

---

## 1. Nguyên Nhân Khiến Số Lượng Coin Quá Ít (~50 Coin)

Qua kiểm tra chi tiết mã nguồn, có 3 nguyên nhân cốt lõi khiến hệ thống chỉ hiển thị và quét được khoảng 50 coin thay vì toàn bộ hơn 500 coin:

1. **Lỗi logic kiểm tra danh sách trong `scanAllFuturesCoins` (`MainViewModel.kt`)**:
   - Biến `_uiState.tickers` được khởi tạo mặc định bằng 52 coin mẫu trong `DefaultCoinData.getSeedTickers()`.
   - Trong hàm quét `scanAllFuturesCoins()`, mã nguồn kiểm tra:
     ```kotlin
     var currentTickers = _uiState.value.tickers
     if (currentTickers.isEmpty()) { // <--- LỖI: Luôn trả về FALSE vì đã có sẵn 52 coin mẫu!
         currentTickers = repository.getTickers()
     }
     ```
   - Do `currentTickers.isEmpty()` luôn bằng `false`, hàm gọi API tải danh sách coin thật từ Binance (`repository.getTickers()`) **bị bỏ qua hoàn toàn**. Kết quả là bot chỉ quét lặp đi lặp lại 50 coin mẫu ban đầu.

2. **Lọc thanh khoản cũ `minQuoteVolume24h = 5,000,000 USDT` trong Backtest (`CryptoRepository.kt`)**:
   - Khi chạy kiểm thử lịch sử, hàm `fetchHighLiquiditySymbolsFromMainnet` áp dụng bộ lọc cũ `volumeUsdt >= 5_000_000`, loại bỏ phần lớn các altcoin nhỏ, làm giảm số lượng coin kiểm thử từ hơn 500 coin xuống chỉ còn vài chục coin.

3. **Thời gian chờ (Timeout) và URL dự phòng trong `BinanceClient.kt`**:
   - Endpoint `fapi.binance.com/fapi/v1/exchangeInfo` trả về gói dữ liệu lớn (~1.2 MB với 523 hợp đồng Perpetual). Cấu hình timeout 8 giây trước đây có thể bị nghẽn mạng trên thiết bị di động, khiến lệnh gọi chuyển sang dùng dữ liệu mẫu.

---

## 2. Giải Pháp & Kiến Trúc Nâng Cấp

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    BINANCE FUTURES REST API (fapi.binance.com)              │
│       • GET /fapi/v1/exchangeInfo (523 USDT-M Perpetuals đang TRADING)      │
│       • GET /fapi/v1/ticker/24hr (Giá, biến động 24h & Khối lượng)         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              BINANCE CLIENT: BỘ NHẬN DIỆN VÀ LỌC COIN CHUẨN XÁC             │
│   • Lấy danh sách chính thức từ ExchangeInfo: status == "TRADING"           │
│     & contractType == "PERPETUAL" & quoteAsset == "USDT" (523 coins)        │
│   • Tăng timeout mạng lên 15s - 20s, bổ sung bộ đệm an toàn                 │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                   ┌───────────────────┴───────────────────┐
                   ▼                                       ▼
┌──────────────────────────────────────┐┌──────────────────────────────────────┐
│       QUÉT TÍN HIỆU THỊ TRƯỜNG       ││          BACKTEST LỊCH SỬ            │
│       (MainViewModel.kt)             ││       (CryptoRepository.kt)          │
├──────────────────────────────────────┤├──────────────────────────────────────┤
│ • Luôn ép tải mới toàn bộ > 500 coin ││ • Bỏ bộ lọc 5M USDT volume cũ        │
│   USDT-M Perpetual từ sàn            ││ • Nạp toàn bộ danh mục altcoin       │
│ • Quét song song (Chunk 12-15 coin)  ││   USDT-M Perpetual vào mô phỏng      │
│ • Hiển thị thanh tiến trình thực:    ││ • Phản ánh chính xác kết quả toàn    │
│   "Đã quét x/522 altcoin"            ││   bộ thị trường                      │
└──────────────────────────────────────┘└──────────────────────────────────────┘
```

---

## 3. Các Bước Triển Khai Chi Tiết

### Bước 1: Nâng cấp `BinanceClient.kt`
- Cải thiện `refreshValidPerpetualSymbols()`:
  - Tải danh sách chính thức từ `https://fapi.binance.com/fapi/v1/exchangeInfo`.
  - Lọc nghiêm ngặt: `status.equals("TRADING") && contractType.equals("PERPETUAL") && quoteAsset.equals("USDT")`.
  - Tăng timeout của OkHttpClient (`connectTimeout = 15s`, `readTimeout = 20s`).
- Đảm bảo `fetchTickersWithFallback()`:
  - Tải toàn bộ mảng `ticker/24hr` từ `fapi.binance.com` (> 750 cặp, lọc lấy toàn bộ các cặp hợp đồng vĩnh cửu USDT không chứa dấu gạch dưới `_`).
  - Đồng bộ danh sách tên coin với `exchangeInfo` để có đầy đủ hơn 500 coin với giá thời gian thực.

### Bước 2: Sửa đổi Logic Quét trong `MainViewModel.kt`
- Trong `scanAllFuturesCoins()`:
  - Loại bỏ hoàn toàn điều kiện `if (currentTickers.isEmpty())`.
  - Gọi chủ động `BinanceClient.refreshValidPerpetualSymbols(forceRefresh = true)` và `repository.getTickers()`.
  - Nếu danh sách tickers có ít hơn 100 coin, bắt buộc gọi API lấy danh sách đầy đủ từ Binance Futures.
  - Lấy toàn bộ danh sách coin hợp lệ (> 500 coin altcoin, loại trừ BTC và stablecoins theo quy tắc chiến lược SMA20 D1).
  - Tối ưu kích thước gói quét song song `chunkSize = 12` với `Dispatchers.IO` để quét mượt mà toàn bộ hơn 500 coin trong thời gian ngắn mà không bị nghẽn API rate limit.
- Trong `loadInitialData()` và `refreshTickers()`:
  - Ngay khi mở app, gọi `refreshTickers()` để nạp toàn bộ hơn 500 coin vào `_uiState.tickers` và hiển thị trên màn hình Thị trường: `Xem tất cả (~520)`.

### Bước 3: Dọn dẹp bộ lọc thanh khoản cũ trong Backtest (`CryptoRepository.kt`)
- Trong `fetchHighLiquiditySymbolsFromMainnet()`:
  - Bỏ điều kiện lọc `volumeUsdt >= 5_000_000 USDT`.
  - Lấy toàn bộ danh sách coin USDT-M Perpetual hợp lệ (> 500 coin) để đưa vào kiểm thử, giúp kết quả backtest phản ánh toàn diện thị trường theo đúng chỉ đạo: *"mở toàn bộ tín hiệu thỏa mãn không giới hạn"*.

### Bước 4: Kiểm tra và Biên dịch (`compile_applet`)
- Biên dịch ứng dụng bằng `compile_applet` để xác nhận không có lỗi cú pháp.
- Đảm bảo thanh tiến trình hiển thị chính xác số lượng hơn 500 coin (ví dụ: `Đã quét 120/522 altcoin...`).
