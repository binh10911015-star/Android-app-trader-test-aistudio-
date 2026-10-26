# Kế hoạch Triển khai: Single-Position ATR Engine (2 Khung Quét 07h/19h, Vốn 5.1$, Xóa Bỏ Binance Demo & Hiển Thị IP Whitelist)

Kế hoạch hoàn thiện hệ thống theo phản hồi của bạn:
1. **Xóa bỏ hoàn toàn chế độ Binance Demo (Testnet)** trong toàn bộ ứng dụng để chỉ giữ kết nối Binance Futures Mainnet thật (tránh nhầm lẫn tài khoản).
2. **Tích hợp 2 mốc quét tín hiệu cuốn chiếu mỗi ngày**: **07:00 AM** và **07:00 PM (19:00) UTC+7**.
3. **Mô hình Single-Position ATR Engine**: Quản lý từng coin độc lập, vốn vào lệnh chuẩn **5.1 USDT/lệnh**, trần danh mục 60% Equity, máy trạng thái Trailing ATR riêng biệt kèm Timeout 7 ngày.
4. **Thẻ hiển thị Outbound Public IP**: Phục vụ Whitelist IP trên sàn Binance.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Các nội dung điều chỉnh cốt lõi theo phản hồi mới nhất của bạn:
> - **Xóa bỏ hoàn toàn Binance Demo / Testnet**: Loại bỏ toàn bộ nút chuyển Testnet, API key demo và các nhãn liên quan. Ứng dụng chỉ sử dụng duy nhất **Binance Futures Mainnet thật (`https://fapi.binance.com`)**.
> - **Bổ sung lần quét 07:00 PM (19:00 UTC+7)**: Bot thực hiện 2 phiên quét tín hiệu cố định mỗi ngày (07:00 sáng và 07:00 tối). Tại mỗi phiên, nếu tổng vốn mở $< 60\%$ Equity, Bot quét và nạp thêm các coin đạt chuẩn bằng vốn còn trống.
> - **Quy mô vốn cố định**: Mặc định **5.1 USDT / lệnh** (phù hợp với quy định tối thiểu của Binance Futures).
> - **Thoát lệnh độc lập**: Từng coin tự kích hoạt Trailing Stop theo $\%ATR$ của chính coin đó, bảo vệ lãi ròng $+0.5\%$ net, và tự động tất toán sau **7 ngày (168 giờ)** nếu chưa chốt lời.

---

## 1. Sơ Đồ Vận Hành & Luồng Dữ Liệu Chi Tiết

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                 LỊCH QUÉT TÍN HIỆU 2 PHIÊN MỖI NGÀY (UTC+7)                 │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ PHIÊN 1: 07:00:00 AM UTC+7           │ PHIÊN 2: 07:00:00 PM (19:00) UTC+7   │
│ (Khi nến ngày D1 chính thức đóng)    │ (Đầu phiên giao dịch Mỹ)             │
├──────────────────────────────────────┴──────────────────────────────────────┤
│ BƯỚC 1: KIỂM TRA HẠN MỨC NGÂN SÁCH                                          │
│ • Tổng vốn đang mở = Tổng tiền ký quỹ các vị thế đang chạy                 │
│ • Trần danh mục = 60% tổng Equity tài khoản                                │
│ • Ngân sách khả dụng = (Equity * 60%) - Tổng vốn đang mở                    │
│   ↳ Nếu Ngân sách < 5.1 USDT: Bỏ qua phiên quét (đã đủ trần)                │
│   ↳ Nếu Ngân sách >= 5.1 USDT: Tính số slot trống = Ngân sách / 5.1 USDT    │
├─────────────────────────────────────────────────────────────────────────────┤
│ BƯỚC 2: QUÉT & CHẤM ĐIỂM DANH SÁCH COIN TRÊN BINANCE MAINNET                │
│ • Lọc thanh khoản: QuoteVolume 24h >= 5,000,000 USDT                        │
│ • Lọc xu hướng D1: Close hôm qua > SMA(20) D1                              │
│ • Bùng nổ khối lượng: Volume hôm qua >= 2.0x Volume hôm kia                 │
│ • Biên độ nến D1: [2.0% đến 17.5%]                                          │
│ • Lọc biến động: %ATR(14) <= 12%                                            │
│ • Chấm điểm Score: (Vol_t-1 / Vol_t-2) * (1 + (Close - Open)/Range)         │
│   ↳ Sắp xếp giảm dần theo Score, chọn Top N coin cho các slot trống        │
├─────────────────────────────────────────────────────────────────────────────┤
│ BƯỚC 3: MỞ LỆNH TỰ ĐỘNG                                                     │
│ • Chế độ: Long Cross 1x trên Binance Futures Mainnet                        │
│ • Vốn mở: Đúng 5.1 USDT cho mỗi lệnh                                        │
│ • Lưu lại %ATR(14) riêng của từng coin tại thời điểm vào lệnh              │
├─────────────────────────────────────────────────────────────────────────────┤
│ BƯỚC 4: MÁY TRẠNG THÁI THOÁT LỆNH ĐỘC LẬP TỪNG COIN                        │
│                                                                             │
│ [Coin i đang chạy lệnh]                                                     │
│   │                                                                         │
│   ├──> THỜI GIAN GIỮ >= 7 NGÀY (168H) ──────────> ĐÓNG LỆNH (TIMEOUT 7D)    │
│   │                                               Thu hồi 5.1$ vốn          │
│   │                                                                         │
│   └──> ROI_i >= max(1.5%, 0.50 * %ATR_i)                                    │
│          │                                                                  │
│          ▼                                                                  │
│       KÍCH HOẠT TRAILING STOP ĐỘC LẬP:                                      │
│       • Bước lùi: Δ_trail = max(0.8%, 0.25 * %ATR_i)                        │
│       • Sàn bảo hiểm lãi: +0.5% net                                         │
│       • Chốt lời khi: ROI tụt chạm Trailing Stop ──> ĐÓNG LỆNH CHỐT LỜI     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Chi Tiết Kỹ Thuật Các Hạng Mục Cần Thực Hiện

### A. Xóa Bỏ Hoàn Toàn Binance Demo (Testnet)
1. **Loại bỏ khỏi Giao diện (`ApiConfigScreen.kt`)**:
   - Xóa bỏ nút gạt / thẻ chọn `Testnet vs Mainnet`.
   - Xóa bỏ các nhãn `[TESTNET]`, `[DEMO]`, `Tài Khoản Testnet Demo Binance`.
   - Giao diện kết nối chỉ còn một mục duy nhất: **API Key & Secret Key của Binance Mainnet**.
2. **Loại bỏ khỏi Mã nguồn & Mô hình dữ liệu**:
   - Gỡ bỏ thuộc tính `isTestnet` trong `BinanceApiConfig`, mặc định cứng endpoint là `https://fapi.binance.com`.
   - Xóa bỏ các hàm gọi testnet trong `BinanceClient.kt` và `BinanceFuturesApiVerifier.kt`.
   - Giữ lại chế độ **Paper Trading** nội bộ (mô phỏng chạy trên dữ liệu giá thật của Mainnet) để người dùng có thể test an toàn trước khi kích hoạt Live bot.

### B. Bổ Sung Phiên Quét 07:00 PM (19:00 UTC+7) Hàng Ngày
1. **Bộ điều phối lịch quét (`ScanScheduler.kt` & `MainViewModel.kt`)**:
   - Lập trình 2 mốc cố định mỗi ngày: **07:00:00 AM** và **19:00:00 PM** (07:00 PM) UTC+7.
   - Đồng hồ đếm ngược hiển thị chính xác còn bao nhiêu thời gian nữa đến mốc quét tiếp theo (ví dụ: còn 4h 15m đến phiên 19:00).
   - Cơ chế cuốn chiếu: Phiên nào cũng kiểm tra ngân sách $< 60\%$ Equity. Nếu phiên sáng đã mở 4 coin (20.4$), phiên tối còn trống hạn mức thì tiếp tục mở thêm coin mới.

### C. Single-Position ATR Engine (Engine Backtest & Vận Hành)
1. **Engine Backtest (`CryptoRepository.kt`)**:
   - Viết hàm `runSinglePositionAtrBacktest`:
     - Nhập số ngày kiểm thử (180 ngày, 365 ngày ~ 1 năm, 730 ngày ~ 2 năm).
     - Mô phỏng chính xác 2 phiên quét mỗi ngày hoặc theo nến D1.
     - Vốn mỗi lệnh: **5.1 USDT**. Trần danh mục: **60%**.
     - Máy trạng thái Trailing ATR riêng biệt cho từng coin:
       - $\text{Activation ROI}_i = \max(1.5\%, 0.50 \times \%ATR_i)$
       - $\Delta_{\text{trail}, i} = \max(0.8\%, 0.25 \times \%ATR_i)$
       - Sàn khóa lãi ròng: $+0.5\%$ net.
       - Tự động tất toán khi thời gian giữ chạm **7 ngày** (`TIMEOUT_7D`).
2. **Màn hình Backtest (`BacktestScreen.kt` & `AnalyticsScreen.kt`)**:
   - Đồng bộ hiển thị bộ thông số Single-Position: Vốn/lệnh 5.1 USDT, Trần 60%, Timeout 7 ngày, Trailing ATR.
   - Thống kê chi tiết: Tỷ lệ thắng, Net Profit, Max Drawdown, Số lệnh chốt lời Trailing, Số lệnh cắt Timeout 7 ngày.

### D. Thẻ Hiển Thị IP Whitelist Binance (`ApiConfigScreen.kt`)
- Trong Tab **Kết Nối API Binance**:
  - Tự động truy vấn IP mạng công cộng qua API `https://api.ipify.org?format=json` (fallback `icanhazip.com`).
  - Thẻ card nổi bật:
    - Địa chỉ IP hiển thị to, rõ ràng theo phông chữ Monospace.
    - Nút **"Sao Chép IP"** (Copy vào clipboard với thông báo "Đã sao chép").
    - Nút **"Làm Mới IP"** (Refresh).
    - Hướng dẫn nhanh người dùng dán vào mục *"Restrict access to trusted IPs only"* trên trang tạo API Key Binance.

---

## 3. Các Tệp Mã Nguồn Cần Chỉnh Sửa

1. **`app/src/main/java/com/example/api/BinanceClient.kt` & `BinanceFuturesApiVerifier.kt`**:
   - Loại bỏ toàn bộ cờ `isTestnet` và URL testnet. Mặc định 100% sử dụng `https://fapi.binance.com`.
2. **`app/src/main/java/com/example/model/TradeModels.kt`**:
   - Xóa bỏ `isTestnet` khỏi `BinanceApiConfig`.
   - Thiết lập mặc định `capitalPerTradeUsdt = 5.1`, `maxHoldingDays = 7`, `maxPortfolioAllocation = 0.60`.
   - Bổ sung cấu trúc dữ liệu cho Single Position Backtest report.
3. **`app/src/main/java/com/example/repository/CryptoRepository.kt`**:
   - Triển khai `runSinglePositionAtrBacktest` đồng bộ với script Python đặc tả.
4. **`app/src/main/java/com/example/ui/MainViewModel.kt`**:
   - Tích hợp lịch quét 2 mốc 07:00 AM và 19:00 PM (07:00 PM) UTC+7.
   - Thêm hàm `fetchOutboundPublicIp()`.
   - Xóa bỏ logic Testnet trong quá trình xác thực và đặt lệnh.
5. **`app/src/main/java/com/example/ui/screens/ApiConfigScreen.kt`**:
   - Xóa toàn bộ giao diện chọn Testnet / Demo.
   - Bổ sung Thẻ IP Whitelist Binance.
   - Cài đặt chiến lược: Đổi vốn/lệnh thành ô nhập 5.1 USDT.
6. **`app/src/main/java/com/example/ui/screens/BacktestScreen.kt` & `AnalyticsScreen.kt`**:
   - Cập nhật giao diện Backtest theo mô hình Single-Position ATR Engine.

---

## 4. Kế Hoạch Xác Minh & Kiểm Thử
- Chạy biên dịch dự án (`compile_applet`) đảm bảo 0 lỗi biên dịch.
- Xác nhận không còn bất kỳ dấu vết hay tùy chọn Testnet/Demo Binance nào trong app.
- Xác nhận IP Whitelist hiển thị đúng và sao chép mượt mà.
- Kiểm tra Backtest 365 ngày (1 năm) và 730 ngày (2 năm) chạy trơn tru với vốn 5.1 USDT/lệnh và 2 phiên quét 07h/19h.
