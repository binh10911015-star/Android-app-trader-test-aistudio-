# Kế Hoạch Triển Khai: Gửi Lệnh Trực Tiếp Binance Demo, Tự Động Sửa Lỗi & Tách Biệt Hai Tab Vị Thế

## Tóm Tắt Mục Tiêu
Nâng cấp hệ thống giao dịch Binance Futures: Khi đã nhập API, mọi thao tác đặt lệnh (thủ công hoặc tự động qua Auto-Trade) sẽ được gửi thẳng lên Binance Demo/Futures API bằng lệnh Thị Trường (MARKET). Hệ thống tích hợp thuật toán **tự động phát hiện và sửa lỗi** (đồng bộ timestamp lệch giờ, chuẩn hóa bước giá/khối lượng tối thiểu theo bộ lọc sàn, tự bù ký quỹ hoặc số lượng), tự động thực hiện thao tác kiểm tra đối soát lệnh trực tiếp trên sàn Binance Demo ngay sau khi gửi. Đồng thời, cấu trúc lại toàn bộ giao diện thành **2 Tab chính tách biệt rõ ràng**: Tab **Paper Trading** (Mô Phỏng Ảo) và Tab **Binance Thực Tế** (API Demo / Live).

---

### Phản Hồi Người Dùng & Quyết Định Cốt Lõi (Confirmed Decisions)

> [!IMPORTANT]
> Dựa trên lựa chọn của bạn trong phiên phỏng vấn làm rõ:
> - **Cơ chế tự động sửa lỗi khi gửi lệnh Binance Demo**: Tự động lấy quy cách sàn (`exchangeInfo`), chuẩn hóa bước giá (`tickSize`), làm tròn khối lượng (`stepSize`), đảm bảo khối lượng tối thiểu (`minNotional`), và tự động lấy `serverTime` bù trừ độ lệch đồng hồ (`timestamp offset` sửa lỗi mã -1021) khi gửi lại lệnh.
> - **Cấu trúc phân biệt vị thế**: Thiết kế **2 Tab chính tách biệt hoàn toàn**:
>   1. **Tab Paper Trading**: Chuyên quản lý các vị thế mô phỏng nội bộ, số dư ảo ban đầu, lợi nhuận giả lập không rủi ro.
>   2. **Tab Binance Thực Tế**: Chuyên quản lý các vị thế thực tế mở trực tiếp trên Binance Futures (Demo / Mainnet), số dư ví USDT thực, lệnh chờ khớp trên sàn và lịch sử lệnh kèm mã `Binance Order ID`.
> - **Loại lệnh ưu tiên khi gửi Binance Demo**: Sử dụng lệnh Thị Trường (**MARKET**) để lệnh được gửi và khớp ngay lập tức trên sàn Binance Demo.

---

### 1. Tổng Quan & Trải Nghiệm Người Dùng (Overview & Core Concept)

- **Quy trình gửi lệnh & Tự động sửa lỗi**:
  1. Người dùng bấm "Mở Vị Thế" hoặc Bot quét tín hiệu và kích hoạt "Auto-Trade".
  2. Nếu API đã kết nối: Thay vì chỉ lưu cục bộ, hệ thống chuẩn bị payload `POST /fapi/v1/order` (symbol, side=BUY, type=MARKET, quantity, timestamp).
  3. **Khối Xử Lý Lỗi Tự Động (Auto Error-Fix Engine)**:
     - Nếu gặp lỗi `-1021 Timestamp out of recvWindow`: Hệ thống gọi `GET /fapi/v1/time`, tính độ chênh lệch thời gian `offset = serverTime - localTime`, tự điều chỉnh và gửi lại lệnh ngay tức thì.
     - Nếu gặp lỗi `-1111 Precision / -1013 LOT_SIZE`: Hệ thống tự truy vấn `exchangeInfo` của cặp giao dịch, định dạng lại số chữ số thập phân của số lượng theo `stepSize` và `minQty` rồi tự động đặt lại lệnh.
     - Nếu gặp lỗi `-4164 / MIN_NOTIONAL` (khối lượng lệnh nhỏ hơn mức tối thiểu 5 USDT): Hệ thống tự làm tròn lên mức hợp lệ tối thiểu để đảm bảo lệnh được sàn chấp nhận.
  4. **Kiểm tra đối soát tức thì (Immediate Verification)**:
     - Ngay sau khi Binance phản hồi thành công, ứng dụng nhận về `orderId`, `clientOrderId`, `executedQty`, `avgPrice`, `status`.
     - Ứng dụng tự động gọi hàm xác thực đối soát `GET /fapi/v1/order` để xác nhận trạng thái lệnh trên Binance Demo và hiển thị thông báo thành công kèm huy hiệu xanh lá `[BINANCE DEMO #ID]`.
     - Nếu có sự cố bất khả kháng (ví dụ mạng đứt hoặc tài khoản hết số dư Demo), hệ thống đưa ra thông báo rõ ràng kèm phương án khắc phục.

- **Tách biệt 2 Tab Vị Thế Chuyên Biệt**:
  - **Tab 1: Paper Trading (Mô Phỏng)**:
    - Hiển thị danh sách vị thế mô phỏng với đòn bẩy ảo, PnL tính toán theo giá thị trường thời gian thực.
    - Cho phép đóng vị thế, chốt lời/cắt lỗ mô phỏng, xóa toàn bộ lịch sử ảo.
  - **Tab 2: Binance Thực Tế (API Demo / Live)**:
    - Hiển thị số dư ví Binance Futures thực tế (Wallet Balance, Margin Balance, Available Balance).
    - Danh sách các vị thế hợp đồng đang mở trên tài khoản Binance Demo (`positionAmt != 0`).
    - Danh sách lệnh chờ khớp (`openOrders`).
    - Lịch sử các lệnh đã gửi thành công lên Binance Demo kèm nút "Kiểm Tra Trạng Thái Lệnh" trên máy chủ Binance.

---

### 2. Thiết Kế Giao Diện & Tương Tác (UX & Visual Design)

- **Thanh chọn 2 Tab Chính Nổi Bật**:
  - Đặt ở vị trí cao nhất của màn hình Vị Thế:
    - Nút 1: `📊 Paper Trading (Mô Phỏng)` (Màu sắc hiện đại, viền sắc nét).
    - Nút 2: `⚡ Binance Thực Tế (API Demo)` (Có chấm xanh sáng báo hiệu kết nối API hoạt động).
- **Trải nghiệm khi ở Tab Paper Trading**:
  - Thẻ tóm tắt số dư ảo, tổng PnL chưa chốt và đã chốt.
  - Danh sách thẻ vị thế mô phỏng với màu viền Amber/Gold đặc trưng.
  - Các nút thao tác nhanh: Đóng vị thế, sửa đòn bẩy mô phỏng.
- **Trải nghiệm khi ở Tab Binance Thực Tế**:
  - Thanh trạng thái kết nối Binance Testnet với nút "Làm mới danh mục" tức thì.
  - Thẻ hiển thị số dư thực tế từ Binance (USDT khả dụng, ký quỹ duy trì, PnL chưa thực hiện).
  - 3 nhóm nội dung con rõ ràng:
    - **Vị Thế Mở Trên Binance**: Hiển thị từng vị thế thực tế với Size, Entry Price, Mark Price, Liq. Price, PnL% và nút Đóng Lệnh trên sàn.
    - **Lệnh Chờ Sàn (Open Orders)**: Hiển thị các lệnh LIMIT/STOP đang chờ khớp trên sàn.
    - **Lệnh Đã Gửi & Đối Soát**: Danh sách các lệnh đã được gửi thành công, gắn mã `Binance Order ID`, trạng thái `FILLED`, kèm nút "Đối soát trên Binance" để mở modal tra cứu trực tiếp.
- **Modal Kiểm Tra & Đối Soát Lệnh Trực Tiếp (Order Verification Dialog)**:
  - Hiển thị thông số từ server Binance: `Mã lệnh Binance (orderId)`, `Mã tham chiếu (clientOrderId)`, `Thời gian ghi nhận`, `Giá khớp trung bình`, `Số lượng đã khớp`, `Tình trạng: ĐÃ KHỚP HOÀN TOÀN (FILLED)`.

---

### 3. Kiến Trúc Kỹ Thuật & Tự Động Sửa Lỗi (Technical Architecture)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           GIAO DIỆN VỊ THẾ                              │
│       ┌──────────────────────────────┬──────────────────────────────┐   │
│       │  TAB 1: PAPER TRADING        │  TAB 2: BINANCE THỰC TẾ      │   │
│       │  - Vị thế mô phỏng nội bộ    │  - Vị thế mở Binance Demo    │   │
│       │  - PnL ảo & Quản lý vốn ảo   │  - Số dư ký quỹ thật Binance │   │
│       │                              │  - Lệnh chờ (Open Orders)    │   │
│       │                              │  - Lệnh đã khớp & Đối soát   │   │
│       └──────────────────────────────┴──────────────────────────────┘   │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                             MAIN VIEWMODEL                              │
│  - executeBuy() -> Tự động phân luồng (Paper vs Binance Demo)           │
│  - postOrderToBinanceWithAutoFix()                                      │
│  - verifyRemoteOrderStatus(symbol, orderId)                             │
│  - refreshBinancePortfolio()                                            │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               BINANCE FUTURES API VERIFIER & ORDER ENGINE               │
│                                                                         │
│   POST /fapi/v1/order (Lệnh MARKET)                                     │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                      AUTO-FIX ENGINE                            │   │
│   │  [Lỗi -1021 Lệch giờ]     -> Đồng bộ GET /fapi/v1/time + Retry  │   │
│   │  [Lỗi -1111/LOT_SIZE]     -> Sửa precision theo exchangeInfo    │   │
│   │  [Lỗi -4164 MIN_NOTIONAL] -> Tự bù khối lượng lên >= 5 USDT     │   │
│   │  [Lỗi Mạng/Timeout]       -> Retry lũy tiến (tối đa 2 lần)      │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│                                     │                                   │
│                                     ▼                                   │
│   GET /fapi/v1/order (Kiểm tra đối soát lệnh đã khớp hay chưa)          │
│   GET /fapi/v2/account & /fapi/v1/openOrders (Đồng bộ danh mục)         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

### Kế Hoạch Các Bước Thực Hiện Chi Tiết

1. **Bước 1: Nâng cấp `BinanceFuturesApiVerifier`**:
   - Thêm phương thức `syncServerTimeOffset()` để tự động tính độ lệch đồng hồ máy chủ Binance.
   - Thêm phương thức `getSymbolLotSizeInfo()` để tra cứu bước giá, số lượng tối thiểu và precision của từng cặp coin.
   - Viết phương thức `postMarketOrderWithAutoFix(symbol, side, usdtAmount, apiKey, apiSecret, isTestnet)` gửi lệnh MARKET lên Binance Demo với chu trình tự động sửa lỗi (-1021 timestamp, -1111 lot size, -4164 min notional) và trả về thông tin chi tiết của lệnh.
   - Kết nối với hàm `checkOrderStatus` hiện có để kiểm tra ngay lập tức trạng thái khớp lệnh.

2. **Bước 2: Cập nhật `MainViewModel`**:
   - Mở rộng hàm `executeBuy` và `openAllScannedPositions`: Nếu API Binance đã kết nối, kích hoạt `postMarketOrderWithAutoFix` gửi thẳng lên Binance Demo.
   - Lưu trữ mã `binanceOrderId`, `binanceStatus` vào cơ sở dữ liệu `TradePosition` để theo dõi.
   - Tự động làm mới `binancePortfolio` ngay sau khi đặt lệnh để vị thế mới xuất hiện lập tức.
   - Bổ sung hàm tiện ích chuyển đổi tab và kích hoạt kiểm tra đối soát.

3. **Bước 3: Tái cấu trúc giao diện `PositionsScreen.kt`**:
   - Xây dựng 2 Tab chính phân biệt rõ ràng: **Paper Trading** và **Binance Thực Tế**.
   - Tab Paper Trading: Giữ nguyên trải nghiệm mô phỏng, trực quan và an toàn.
   - Tab Binance Thực Tế: Hiển thị trọn vẹn danh mục thực của Binance Demo (Số dư ví ký quỹ, các vị thế hợp đồng đang mở, danh sách lệnh chờ khớp, và các lệnh đã đặt kèm huy hiệu Binance Order ID và nút đối soát trực tiếp).

4. **Bước 4: Kiểm tra và Biên dịch**:
   - Chạy `compile_applet` để xác thực toàn bộ mã nguồn Kotlin/Compose biên dịch sạch sẽ, không có lỗi.
