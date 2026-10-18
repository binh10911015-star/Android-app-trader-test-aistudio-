# Kế Hoạch Triển Khai: Chiến Lược Giao Dịch D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)

## Tóm Tắt Mục Tiêu
Nâng cấp bot giao dịch với chiến lược mới trên khung thời gian 1 Ngày (D1): Tự động phát hiện và kích hoạt lệnh MUA khi giá hiện tại cắt lên hoặc vượt trên đường SMA20 (D1 tính theo giá mở cửa - Open price) đồng thời khối lượng giao dịch nến D1 hiện tại đạt từ 2 lần trở lên so với khối lượng nến D1 của ngày hôm trước. Chiến lược được tích hợp thành tùy chọn linh hoạt trong Cài đặt (Settings), áp dụng cơ chế **chốt lời trên toàn bộ danh mục** (tự động đóng toàn bộ các vị thế khi tổng danh mục đạt % lợi nhuận mục tiêu, không chốt lẻ từng vị thế).

---

### Phản Hồi Người Dùng & Quyết Định Cốt Lõi (Confirmed Decisions)

> [!IMPORTANT]
> Dựa trên các phản hồi và yêu cầu điều chỉnh chính xác của bạn:
> - **Cách tính SMA20 D1**: Căn cứ theo **GIÁ MỞ CỬA (Open Price)** của 20 nến D1 gần nhất ($\text{SMA20} = \frac{1}{20} \sum \text{Open}_i$).
> - **Thời điểm xác nhận tín hiệu**: Kiểm tra tức thì khi giá cắt lên SMA20 (Intraday Real-time Check) kết hợp khối lượng lũy kế D1 hiện tại $\ge 2 \times \text{Volume}(D1_{prev})$.
> - **Cơ chế chốt lời TOÀN DANH MỤC (Portfolio Take-Profit)**: 
>   - **Không chốt lời theo từng vị thế đơn lẻ**.
>   - Giám sát tổng lợi nhuận chưa chốt (Total Unrealized PnL %) của toàn bộ danh mục các vị thế đang mở.
>   - Khi tổng lợi nhuận danh mục chạm hoặc vượt tỷ lệ % chốt lời mục tiêu thiết lập trong Cài đặt, bot lập tức kích hoạt lệnh **đóng đồng loạt toàn bộ các vị thế** để hiện thực hóa lợi nhuận.
> - **Tùy chọn chiến lược trong Cài Đặt**: Cho phép linh hoạt chuyển đổi giữa **D1 SMA20 Breakout & Bùng Nổ Khối Lượng** và chiến lược **4H SuperTrend và 4H MA20**.

---

### 1. Tổng Quan & Trải Nghiệm Người Dùng (Overview & Core Concept)

- **Chi tiết Chiến lược D1 SMA20 + Volume Breakout**:
  - **Khung thời gian (Timeframe)**: D1 (Daily - Nến 1 Ngày).
  - **Cơ sở đường SMA20 (D1)**: Tính từ trung bình cộng của **20 giá mở cửa (Open Price)** của các nến ngày gần nhất.
  - **Điều kiện Kỹ thuật (Price)**: Giá giao dịch hiện tại $\ge \text{SMA20}(D1)$ (giá cắt lên hoặc duy trì trên SMA20).
  - **Điều kiện Xác nhận Dòng tiền (Volume)**: Khối lượng giao dịch tại thời điểm nến D1 hiện tại $\ge 2 \times$ Khối lượng giao dịch nến D1 ngày hôm trước ($V_{today} \ge 2 \cdot V_{yesterday}$).
  - **Cơ chế Chốt lời Danh mục (Portfolio Take-Profit)**: Toàn bộ vị thế mở của bot sẽ được giữ song song cho đến khi tổng PnL của cả danh mục đạt ngưỡng mục tiêu (ví dụ: $+5\%$ hoặc $+10\%$ tổng giá trị danh mục). Khi đó, bot sẽ tự động chốt lời toàn bộ danh mục trong 1 lần đóng.
- **Trải nghiệm người dùng**:
  - Người dùng truy cập tab **Cài Đặt (Settings)**, thấy mục **Chiến Lược Giao Dịch Của Bot (Trading Strategy)**:
    1. `D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)` *(Khung D1, SMA20 giá Open)*
    2. `4H SuperTrend và 4H MA20` *(Khung 4H)*
    3. `Đa chiến lược (Chạy song song)`
  - Thiết lập **Mục tiêu Chốt Lời Toàn Danh Mục (% Portfolio TP)**: Người dùng nhập tỷ lệ % mong muốn cho toàn bộ danh mục (mặc định ví dụ $4.0\% - 5.0\%$).
  - Trên màn hình Vị Thế (Positions), hiển thị thanh tiến độ **Tổng Lợi Nhuận Danh Mục** so với mục tiêu chốt lời chung: `Tổng ROI Danh Mục: +3.8% / Mục tiêu Chốt Lời: +4.0% 🎯`.

---

### 2. Thiết Kế Giao Diện & Tương Tác (UX & Visual Design)

- **Khu vực Cài Đặt Chiến Lược & Chốt Lời Danh Mục (Settings UI)**:
  - Thẻ **Chiến Lược Giao Dịch Bot**:
    - Bộ chọn Radio / Segmented Button:
      - Thẻ 1: **D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)** (Chú thích: *SMA20 tính theo giá Open, nến D1*).
      - Thẻ 2: **4H SuperTrend và 4H MA20** (Chú thích: *Giao dịch linh hoạt theo xu hướng 4H*).
  - Thẻ **Cơ Chế Quản Lý Lệnh & Chốt Lời Toàn Danh Mục**:
    - Ô nhập: **Tỷ lệ Chốt Lời Toàn Danh Mục (% Portfolio TP)**.
    - Dòng hướng dẫn trực quan: *"Hệ thống sẽ không chốt lời lẻ tẻ từng lệnh. Khi tổng lãi của tất cả các vị thế đang mở đạt mức % này, bot sẽ tự động chốt lời toàn bộ danh mục cùng lúc."*
- **Giao diện Bảng Quét Tín Hiệu (Market Scanner UI)**:
  - Cột thông số cho từng đồng coin:
    - **Giá vs SMA20 Open (D1)**: Hiển thị giá hiện tại và giá trị SMA20 (Open) nến D1 (Màu xanh khi Giá $\ge$ SMA20).
    - **Tỷ lệ Khối lượng D1 ($V / V_{prev}$)**: Hiển thị nổi bật (ví dụ: `2.4x` màu xanh ngọc kèm biểu tượng ngọn lửa 🔥 khi $\ge 2.0x$).
  - Bộ lọc nhanh: Lọc danh sách các coin đang thỏa mãn đồng thời: Giá $\ge$ SMA20 (D1 Open) và Vol D1 $\ge 2x$.
- **Thanh Tiến Độ Chốt Lời Danh Mục (Portfolio TP Progress Bar)**:
  - Đặt ở đầu màn hình Vị Thế hoặc đầu bảng điều khiển:
    - Hiển thị: Tổng số vốn đầu tư, Tổng PnL chưa chốt ($/%) và thanh đo % tiến tới điểm chốt lời toàn danh mục.
    - Nút thao tác thủ công: "Chốt Lời Toàn Bộ Danh Mục Ngay" (nếu người dùng muốn đóng sớm trước khi bot tự động đóng).

---

### 3. Các Quyết Định Sản Phẩm & Đánh Đổi (Key Product Decisions)

- **Quyết định 1: SMA20 D1 tính căn cứ theo Giá Mở Cửa (Open Price)**:
  - *Phương án chọn*: $\text{SMA20}(D1) = \frac{\sum_{i=1}^{20} \text{Open}_i}{20}$.
  - *Lý do*: Giá mở cửa của các nến ngày là mốc giá cố định, không bị dao động nhiễu trong phiên nến hiện tại, tạo ra đường chuẩn tham chiếu ổn định tuyệt đối khi kiểm tra tức thì giá hiện tại cắt lên SMA20.
- **Quyết định 2: Chốt lời trên toàn danh mục (Portfolio-level TP)**:
  - *Phương án chọn*: Không đặt TP riêng rẽ ở từng vị thế (`takeProfitPrice = null` ở từng lệnh). Thay vào đó, bộ giám sát Portfolio Engine liên tục tính tổng ROI danh mục:
    $$\text{Portfolio ROI} = \frac{\sum \text{Unrealized PnL}}{\sum \text{Invested Margin}} \times 100\%$$
    Khi $\text{Portfolio ROI} \ge \text{Target TP}\%$, bot kích hoạt lệnh đóng toàn bộ vị thế (`closeAllPositions()`).
  - *Lý do*: Đáp ứng chính xác yêu cầu của người dùng, giúp tối ưu hóa tổng danh mục đầu tư, hạn chế tình trạng lệnh thắng bị chốt non trong khi lệnh thua vẫn gồng, và chốt lời đúng lúc toàn bộ danh mục đạt mục tiêu tài chính.
- **Quyết định 3: Danh mục chiến lược độc lập và rõ ràng**:
  - *Phương án chọn*: Phân tách rõ ràng giữa chiến lược mới **D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)** và chiến lược **4H SuperTrend và 4H MA20**.
  - *Lý do*: Giúp người dùng có toàn quyền kiểm soát và dễ dàng so sánh hiệu suất giữa hai trường phái giao dịch.

---

### 4. Kiến Trúc Kỹ Thuật & Luồng Dữ Liệu (Technical Architecture)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           GIAO DIỆN NGƯỜI DÙNG                          │
│   ┌──────────────────────────────┐   ┌──────────────────────────────┐   │
│   │   CÀI ĐẶT (SETTINGS)         │   │   THEO DÕI TOÀN DANH MỤC     │   │
│   │   - Chọn Chiến lược:         │   │   - Tổng PnL Danh mục: +4.2% │   │
│   │     * D1 SMA20 Open + Vol 2x │   │   - Mục tiêu TP: +4.0%       │   │
│   │     * 4H SuperTrend & 4H MA20│   │   - Trạng thái: CHỐT LỜI ALL │   │
│   │   - Mục tiêu TP Danh mục (%) │   │                              │   │
│   └──────────────┬───────────────┘   └──────────────┬───────────────┘   │
└──────────────────┼──────────────────────────────────┼───────────────────┘
                   │                                  │
                   ▼                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      ENGINE PHÂN TÍCH & QUÉT TÍN HIỆU                   │
│                                                                         │
│   1. Lấy chuỗi nến D1 (interval = 1d, limit = 25)                       │
│   2. Trích xuất mảng 20 giá MỞ CỬA (Open) gần nhất                      │
│      => sma20OpenD1 = average(openPrices.takeLast(20))                  │
│   3. Lấy khối lượng D1:                                                 │
│      - V_today = nến D1 hiện tại (đang chạy)                            │
│      - V_yesterday = nến D1 đóng cửa hôm qua                            │
│                                                                         │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │                 ĐIỀU KIỆN KÍCH HOẠT MUA D1                      │   │
│   │   - Giá hiện tại >= sma20OpenD1                                 │   │
│   │   - V_today >= 2.0 * V_yesterday                                │   │
│   │   => Kích hoạt Tín hiệu BUY (D1 SMA20 Open + Vol 2x)            │   │
│   └─────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              ENGINE GIÁM SÁT & CHỐT LỜI TOÀN DANH MỤC                   │
│                                                                         │
│   - Mở vị thế mới: Không gán TP cho riêng từng vị thế                   │
│   - Vòng lặp giám sát danh mục thời gian thực:                          │
│       totalUnrealizedPnl = SUM(pos.unrealizedPnl)                       │
│       totalMargin = SUM(pos.investedMargin)                             │
│       portfolioRoiPercent = (totalUnrealizedPnl / totalMargin) * 100    │
│                                                                         │
│       IF portfolioRoiPercent >= settings.portfolioTakeProfitPercent:    │
│           => THỰC HIỆN ĐÓNG TOÀN BỘ DANH MỤC VỊ THẾ (CLOSE ALL)        │
│           => Gửi thông báo: "Đã chốt lời toàn danh mục thành công!"    │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Quy Trình Tính Toán Tín Hiệu (Logic Code Blueprint):
1. **Lấy dữ liệu nến D1 & Tính SMA20 theo Giá Mở Cửa**:
   - Gọi API klines với khung `1d`.
   - Lấy 20 nến gần nhất: Lấy trường `open` (giá mở cửa) của từng nến.
   - `sma20Open = (open_1 + open_2 + ... + open_20) / 20.0`.
2. **So sánh Khối lượng Nến D1**:
   - `vToday = currentD1Candle.volume` (khối lượng tích lũy của nến hôm nay).
   - `vYesterday = previousD1Candle.volume` (toàn bộ khối lượng nến ngày hôm qua).
   - `volumeRatio = vToday / vYesterday`.
   - Điều kiện mua: `currentPrice >= sma20Open && volumeRatio >= 2.0`.
3. **Cơ chế Chốt Lời Toàn Danh Mục**:
   - Mỗi chu kỳ cập nhật giá (hoặc WebSocket), tính tổng `portfolioRoiPercent`.
   - Nếu `portfolioRoiPercent >= settings.portfolioTakeProfitPercent` (ví dụ $\ge 4.0\%$): Kích hoạt `closeAllPositions()` để đóng tất cả các vị thế đang mở và cập nhật số dư ví.

---

### Kế Hoạch Các Bước Thực Hiện Chi Tiết

1. **Bước 1: Cập nhật Cấu trúc Dữ liệu & Cài Đặt**:
   - Thêm `TradingStrategyType`: `D1_SMA20_VOLUME` và `FOUR_HOUR_SUPERTREND_MA20`.
   - Cập nhật cấu hình: `portfolioTakeProfitPercent` (mục tiêu chốt lời toàn danh mục, ví dụ mặc định 4.0%), `volumeMultiplierThreshold` (mặc định 2.0).
2. **Bước 2: Xây dựng Thuật toán SMA20 D1 (Giá Open) & So Sánh Volume**:
   - Viết hàm phân tích dữ liệu D1 klines: trích xuất 20 giá Open để tính `sma20OpenD1`.
   - Trích xuất $V_{today}$ và $V_{yesterday}$, tính tỷ lệ bùng nổ khối lượng.
   - Đánh giá tín hiệu BUY tức thì khi giá $\ge$ `sma20OpenD1` và $V_{today} \ge 2 \times V_{yesterday}$.
3. **Bước 3: Xây dựng Bộ Giám Sát Chốt Lời Toàn Danh Mục (Portfolio TP Engine)**:
   - Viết hàm kiểm tra tổng lợi nhuận danh mục `checkAndExecutePortfolioTakeProfit()`.
   - Khi tổng ROI danh mục chạm ngưỡng thiết lập, tự động đóng toàn bộ các vị thế đang mở.
4. **Bước 4: Cập nhật Giao diện Cài Đặt & Bảng Quét Tín Hiệu**:
   - Giao diện Cài Đặt: Tùy chọn chuyển đổi giữa `D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)` và `4H SuperTrend và 4H MA20`; ô cài đặt % Chốt Lời Toàn Danh Mục kèm giải thích rõ ràng.
   - Giao diện Quét & Vị Thế: Hiển thị trạng thái SMA20 Open, tỷ lệ Khối lượng D1, và thanh tiến độ chốt lời của toàn danh mục.
5. **Bước 5: Kiểm tra và Xác thực**:
   - Kiểm tra xác thực tính toán SMA20 Open và tỷ lệ Volume 2x.
   - Kiểm tra kịch bản chốt lời toàn danh mục khi tổng ROI đạt mục tiêu, đảm bảo không đóng lẻ tẻ và đóng đồng loạt an toàn.
