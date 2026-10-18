# Kế Hoạch Sửa Lỗi & Nâng Cấp Giao Diện Ứng Dụng CryptoTrader

Tài liệu này chi tiết hóa phương án khắc phục toàn diện 4 vấn đề tồn đọng được người dùng phản hồi, mang lại trải nghiệm phân tích thị trường, biểu đồ kỹ thuật, thống kê hiệu quả đầu tư và quản lý kết nối Binance Futures chuyên nghiệp.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Toàn bộ các yêu cầu đã được làm rõ thông qua quá trình phỏng vấn người dùng và thống nhất triển khai như sau:

- **Confirmed Decision 1 (Thẻ Biểu đồ)**: Giới hạn biểu đồ hiển thị 30 nến gần nhất (`takeLast(30)`), tính toán khoảng cách và tỷ lệ co dãn tối ưu để các thanh nến không bị dính sát nhau. Tích hợp thanh nút chuyển đổi linh hoạt giữa 2 chế độ:
  1. *SMA + SuperTrend*: Vẽ đường MA20 vàng và dải SuperTrend (xanh lá khi UP, đỏ khi DOWN) theo dữ liệu kỹ thuật thực tế.
  2. *SMA + Khối lượng*: Vẽ đường MA20 kết hợp biểu đồ thanh khối lượng (Volume bars) bên dưới cùng vạch mốc bùng nổ 2x.
- **Confirmed Decision 2 (Thẻ Thống kê)**: Thay thế hoàn toàn stub 2 dòng chữ bằng bảng điều khiển Analytics đầy đủ: Thẻ tổng quan PnL ròng, Tỷ lệ thắng (Win Rate %), Profit Factor, Biểu đồ đường cong lợi nhuận tích lũy (Cumulative PnL Chart) vẽ bằng Canvas mượt mà, và Danh sách lịch sử lệnh đã đóng.
- **Confirmed Decision 3 (Thẻ API Key)**: Xây dựng màn hình API Config hoàn chỉnh gồm Form nhập API Key, Secret Key, Mã PIN bảo vệ, công tắc Testnet/Mainnet và nút "Kiểm Tra Kết Nối Trực Tiếp Binance" hiển thị ngay số dư ví USDT và trạng thái tài khoản.
- **Confirmed Decision 4 (Thẻ Thị trường)**: Tái cấu trúc phân cấp giao diện cho phép cuộn toàn bộ trang hoặc cuộn danh sách ticker mượt mà, không bị kẹt; thẻ coin hiển thị chi tiết các thông số của từng tín hiệu (Giá mở cửa SMA20 D1, Tỷ lệ bùng nổ khối lượng, Tín hiệu SuperTrend).

---

## 1. Overview & Core Concept

- **Mục tiêu chính**: Khắc phục triệt để lỗi không thể tương tác ở 4 tab (Thị trường, Biểu đồ, Thống kê, API Key) do các thành phần UI bị thiếu hoặc đặt kích thước không phù hợp trong Jetpack Compose.
- **Đối tượng sử dụng**: Nhà giao dịch Binance Futures cần một công cụ theo dõi tín hiệu tự động, kiểm tra nến kỹ thuật rõ ràng và quản lý tài khoản bảo mật trực tiếp trên thiết bị di động.
- **Giá trị cốt lõi**: Trực quan hóa dữ liệu nến và chỉ báo chính xác, minh bạch hóa lịch sử lãi lỗ và cung cấp công cụ xác thực API Binance an toàn, tin cậy.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Flow 1: Khám phá Thị Trường & Tín Hiệu (Tab Thị trường)**
   - Người dùng mở tab Thị trường: Toàn bộ màn hình cuộn mượt mà.
   - Thẻ mỗi coin hiển thị đầy đủ chi tiết: Giá hiện tại, tỷ lệ thay đổi 24h, Giá SMA20 D1 Open, Tỷ lệ khối lượng hiện tại so với trung bình (ví dụ: `2.4x / 2.0x`), và nhãn trạng thái đạt chuẩn chiến lược.
   - Người dùng có thể nhấn "Quét ngay" hoặc lọc theo "Tín hiệu tăng" để xem danh sách thu gọn.
2. **Flow 2: Phân Tích Biểu Đồ & Chuyển Đổi Chiến Lược (Tab Biểu đồ)**
   - Người dùng bấm vào một coin hoặc tab Biểu đồ: 30 nến gần nhất hiển thị thoáng đãng, rõ nét từng thân nến và râu nến.
   - Người dùng bấm nút chuyển đổi:
     - Chế độ **"SMA + SuperTrend"**: Nổi bật đường MA20 màu vàng óng và các đoạn dải SuperTrend xanh lá (mua) / đỏ (bán).
     - Chế độ **"SMA + Khối Lượng"**: Hiển thị đường MA20 trên nến và khung phụ Volume phía dưới với các cột khối lượng và đường kẻ chuẩn bùng nổ 2x.
3. **Flow 3: Theo Dõi Hiệu Quả Đầu Tư (Tab Thống kê)**
   - Xem tổng PnL (USDT), tỷ lệ thắng (Win Rate %), số lệnh thắng/thua.
   - Biểu đồ tăng trưởng PnL vẽ trực tiếp bằng Canvas thể hiện xu hướng tài khoản.
   - Bảng lịch sử lệnh đóng thể hiện rõ thời gian, giá vào/ra, lý do chốt (TP/SL/Thủ công).
4. **Flow 4: Cấu Hình & Kiểm Tra API (Tab API Key)**
   - Người dùng nhập API Key, API Secret và mã PIN truy cập.
   - Nhấn nút **"Kiểm Tra Kết Nối Trực Tiếp Binance"**: Hệ thống gửi yêu cầu xác thực tới máy chủ Binance Futures và hiển thị hộp thoại kết quả thành công với số dư ví USDT thực tế hoặc thông báo lỗi cụ thể.

### Visual Identity & Theme Tokens
- **Bảng màu chủ đạo**:
  - `DarkBg` (`0xFF121418`): Nền tối sang trọng, giảm mỏi mắt cho trader.
  - `DarkSurface` (`0xFF1E2329`): Màu bề mặt thẻ theo phong cách Binance Pro.
  - `CryptoGold` (`0xFFF0B90B`): Màu nhấn thương hiệu Binance cho các hành động quan trọng và đường MA20.
  - `CryptoGreen` (`0xFF0ECB81`): Nến tăng, tín hiệu Buy, trạng thái có lãi.
  - `CryptoRed` (`0xFFF6465D`): Nến giảm, trạng thái lỗ, cảnh báo rủi ro.
- **Typography & Font**: Sử dụng `FontFamily.Monospace` cho các thông số giá, khối lượng và tỷ lệ % nhằm đảm bảo các con số luôn thẳng hàng và dễ đọc.

---

## 3. Key Product Decisions & Trade-Offs

- **Quyết định 1: Giới hạn 30 nến gần nhất trong CandlestickChart**
  - *Phương án chọn*: Lấy đúng 30 nến gần nhất (`candles.takeLast(30)`) và tính toán chiều rộng từng nến tỷ lệ với bề ngang màn hình (`availableWidth / 30`).
  - *Lý do*: Giúp nến có độ rộng từ 6dp - 10dp kèm khoảng cách 3dp - 4dp, trader quan sát rõ hình thái nến thay vì hàng trăm nến mỏng đặc như sợi chỉ.
- **Quyết định 2: Hai chế độ xem biểu đồ chuyên biệt (Toggle Mode)**
  - *Phương án chọn*: Đặt 2 nút chip "SMA + SuperTrend" và "SMA + Khối Lượng" ngay trên đầu biểu đồ.
  - *Lý do*: Tránh nhồi nhét quá nhiều đường vẽ vào một màn hình điện thoại nhỏ, giúp người dùng tập trung đánh giá đúng tiêu chí của chiến lược mình đang theo dõi.
- **Quyết định 3: Cấu trúc cuộn trong Tab Thị trường**
  - *Phương án chọn*: Đặt các phần Header (Tài khoản, Bộ lọc, Tín hiệu đã quét) vào các `item` của `LazyColumn` hoặc sử dụng `weight(1f)` chuẩn Compose.
  - *Lý do*: Khắc phục triệt để hiện tượng xung đột cuộn khiến danh sách không thể vuốt xuống được.

---

## 4. Technical Architecture & Data Strategy

### Component Hierarchy Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                          MainActivity                           │
│  ┌───────────────────────┐   ┌───────────────────────────────┐  │
│  │ TradingModeHeader     │   │ BottomNavigationBar (5 Tabs)  │  │
│  └───────────────────────┘   └───────────────────────────────┘  │
└────────────────────────────────┬────────────────────────────────┘
                                 │ Crossfade Navigation
         ┌───────────────────────┼─────────────────────────┐
         ▼                       ▼                         ▼
┌──────────────────┐   ┌──────────────────┐      ┌──────────────────┐
│   MarketScreen   │   │ CoinDetailScreen │      │ AnalyticsScreen  │
│ ┌──────────────┐ │   │ ┌──────────────┐ │      │ ┌──────────────┐ │
│ │ AccountCard  │ │   │ │TimeframeChip │ │      │ │ PnL Summary  │ │
│ ├──────────────┤ │   │ ├──────────────┤ │      │ ├──────────────┤ │
│ │ SignalBreak- │ │   │ │StrategyToggle│ │      │ │ Win Rate Win/│ │
│ │ down Cards   │ │   │ ├──────────────┤ │      │ │ Loss Stats   │ │
│ ├──────────────┤ │   │ │Candlestick-  │ │      │ ├──────────────┤ │
│ │ LazyColumn   │ │   │ │Chart(30 nến) │ │      │ │Canvas Profit │ │
│ │ (Full Scroll)│ │   │ ├──────────────┤ │      │ │ Curve Chart  │ │
│ └──────────────┘ │   │ │Order Box     │ │      │ ├──────────────┤ │
└──────────────────┘   │ └──────────────┘ │      │ │ Closed Pos.  │ │
                       └──────────────────┘      │ │ LazyColumn   │ │
                                                 │ └──────────────┘ │
                                                 └──────────────────┘
                                                           ▲
                                 ┌─────────────────────────┘
                                 ▼
                       ┌──────────────────┐
                       │ ApiConfigScreen  │
                       │ ┌──────────────┐ │
                       │ │ Key & Secret │ │
                       │ ├──────────────┤ │
                       │ │ Testnet/Live │ │
                       │ ├──────────────┤ │
                       │ │ Test Button  │ │
                       │ │ & Live Check │ │
                       │ └──────────────┘ │
                       └──────────────────┘
```

### Chi Tiết Cải Tiến Từng File

1. **`app/src/main/java/com/example/ui/screens/MarketScreen.kt`**:
   - Sắp xếp lại container: sử dụng `LazyColumn` làm gốc hoặc gán `Modifier.weight(1f)` cho danh sách ticker để cuộn tự nhiên.
   - Nâng cấp `CoinCardItem` và `ScannedSignalCard`: hiển thị tường minh các chỉ số kỹ thuật:
     - Giá đóng cửa vs SMA20 Open D1 (`$lastPrice` vs `$sma20OpenD1` chênh lệch % cụ thể).
     - Tỷ lệ khối lượng D1 (`volumeRatioD1` kèm icon bùng nổ khi >= 2.0x).
     - Trạng thái điều kiện nến đáp ứng chuẩn breakout.
2. **`app/src/main/java/com/example/ui/components/CandlestickChart.kt`**:
   - Nhận danh sách `candles` và `indicatorData`, lọc 30 nến gần nhất (`takeLast(30)`).
   - Thêm tham số `chartMode` (`SMA_SUPERTREND` vs `SMA_VOLUME`).
   - Tính toán `minPrice` và `maxPrice` chính xác dựa trên giá High/Low của 30 nến và các đường chỉ báo để scale nến chuẩn mực.
   - Vẽ nến: Thân nến đặc và râu nến bằng màu chuẩn crypto.
   - Vẽ đường MA20 cong mượt nối các điểm MA20 của 30 nến.
   - Nếu ở chế độ `SMA_SUPERTREND`: Vẽ các đoạn SuperTrend bằng đường kẻ dày hoặc điểm chấm màu xanh lá/đỏ tương ứng xu hướng.
   - Nếu ở chế độ `SMA_VOLUME`: Vẽ các cột thể tích ở 25% chiều cao phía dưới, kèm đường đứt nét ngưỡng 2x Volume.
3. **`app/src/main/java/com/example/ui/screens/CoinDetailScreen.kt`**:
   - Bổ sung bộ nút chuyển đổi chế độ biểu đồ: "SMA + SuperTrend" vs "SMA + Khối Lượng".
   - Liên kết dữ liệu nến thực tế từ Binance vào biểu đồ.
4. **`app/src/main/java/com/example/ui/screens/AnalyticsScreen.kt`**:
   - Xây dựng giao diện Analytics chuyên nghiệp:
     - **Thẻ Thống Kê Tổng Quan**: Tổng PnL USDT (hiển thị màu xanh nếu lãi, đỏ nếu lỗ), ROI %, Tỷ Lệ Thắng (Win Rate %), Hệ Số Lợi Nhuận (Profit Factor).
     - **Biểu Đồ Lợi Nhuận Tích Lũy (PnL Growth Chart)**: Vẽ đồ thị đường Canvas trực quan thể hiện đường vốn qua từng giao dịch đã đóng.
     - **Lịch Sử Giao Dịch (Closed Positions)**: Hiển thị danh sách các lệnh đã đóng với huy hiệu Take Profit (xanh), Stop Loss (đỏ), thời gian vào/ra, giá khớp và PnL thực tế.
5. **`app/src/main/java/com/example/ui/screens/ApiConfigScreen.kt`**:
   - Xây dựng form nhập liệu đầy đủ:
     - Binance API Key (Text field có nút sao chép/dán).
     - Binance API Secret (Password field có icon ẩn/hiện mắt).
     - Access PIN (Mã PIN bảo vệ xác thực khi vào lệnh thực).
     - Switch chọn môi trường: Binance Futures Testnet vs Binance Futures Live Mainnet.
     - Nút **"Test Kết Nối Trực Tiếp Binance"**: Gọi hàm `viewModel.saveAndVerifyApiCredentials` / `viewModel.testApiConnection`, hiển thị spinner khi đang kiểm tra và card trạng thái chi tiết (Số dư ví USDT khả dụng, quyền Trade, quyền Đọc dữ liệu).
     - Nút "Lưu Cấu Hình" và hiển thị hướng dẫn tạo API Key an toàn trên Binance (chỉ cấp quyền Futures, không cấp quyền Rút tiền).
