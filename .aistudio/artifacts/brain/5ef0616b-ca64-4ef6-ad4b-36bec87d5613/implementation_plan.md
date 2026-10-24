# Kế hoạch Triển khai: Phân Định Giỏ Chính & Giỏ Phụ, Chi Tiết PnL Từng Giỏ & Quản Lý Vốn Động

Kế hoạch nâng cấp hệ thống quản lý vị thế và phân bổ vốn nhằm mang lại sự minh bạch tuyệt đối giữa Giỏ Chính (Primary Basket) và Giỏ Phụ (Secondary Basket), đồng thời thực thi nguyên tắc vốn động chính xác trên số dư khả dụng thực tế.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Dựa trên câu trả lời của bạn ở bước trước:
> 1. **Mở vị thế thủ công khi Bot đang chạy**: Hệ thống sẽ hiển thị hộp thoại nhắc nhở / cảnh báo rằng Bot đang hoạt động và ngăn mở thủ công để tránh xung đột phân bổ vốn của 2 giỏ.
> 2. **Vị thế Binance Live đã mở từ trước**: Hệ thống sẽ tự động gán theo thứ tự vào lệnh của Bot (đợt lệnh đầu tiên thuộc Giỏ Chính, đợt tiếp theo thuộc Giỏ Phụ).
> 3. **Vốn động theo %**: Khi quét tín hiệu và mở vị thế, hệ thống luôn lấy **số dư khả dụng hiện có** (số dư chưa bị ký quỹ) để tính toán đúng % vốn cho từng lệnh, đảm bảo đúng quy tắc như ví dụ bạn đưa ra (1.000$ -> vào 5 lệnh 2% = 100$, còn 900$; đợt sau 10 lệnh 2% của 900$ = 180$).

---

## 1. Tổng Quan & Kiến Trúc Tính Năng

```
┌────────────────────────────────────────────────────────────────────────┐
│                        THẺ TỔNG QUAN TÀI SẢN                           │
│  Số Dư: $10,000 USDT (Khả dụng: $8,500 USDT)      [ Đóng tất cả ]      │
│  Tổng Unrealized PnL: +$9.02 (+0.60%)                                  │
├────────────────────────────────────────────────────────────────────────┤
│  ▼ CHI TIẾT THEO TỪNG GIỎ GIAO DỊCH (MỚI)                              │
│  ┌─────────────────────────────────┬─────────────────────────────────┐ │
│  │ 🟡 GIỎ CHÍNH (PRIMARY)          │ 🔵 GIỎ PHỤ (SECONDARY)          │ │
│  │ PnL: +$48.61 (+4.86%)           │ PnL: -$39.59 (-2.20%)           │ │
│  │ Đã mở: 5 vị thế                 │ Đã mở: 10 vị thế                │ │
│  │ Tổng Volume: 1,000.0 USDT       │ Tổng Volume: 1,800.0 USDT       │ │
│  └─────────────────────────────────┴─────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      DANH SÁCH VỊ THẾ ĐANG MỞ                          │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ BIRBUSDT  [LONG 1x] [LIVE] [🟡 GIỎ CHÍNH]        +$48.61 (+97%) │  │
│  │ Entry: $0.0864   Mark: $0.1703   Ký quỹ (Margin): 20.0 USDT      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ BMTUSDT   [LONG 1x] [LIVE] [🔵 GIỎ PHỤ]          +$41.42 (+82%) │  │
│  │ Entry: $0.0214   Mark: $0.0391   Ký quỹ (Margin): 18.0 USDT      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Chi Tiết Thực Hiện

### A. Ghi chú Phân Định Giỏ Trên Từng Vị Thế
1. **Dữ liệu (Data Layer)**:
   - Thêm thuộc tính `basketSlot: String = "PRIMARY"` (với các giá trị `"PRIMARY"` / `"SECONDARY"`) vào `TradePosition` (Room SQLite Entity) và `BinanceLivePosition`.
   - Với các vị thế Binance Live đã mở hoặc đồng bộ về: Hệ thống căn cứ theo `openTime` và ánh xạ với lịch sử quét của Bot để tự động phân định đợt 1 -> Giỏ Chính, đợt 2 -> Giỏ Phụ.
2. **Giao diện thẻ vị thế (UI Layer - `PositionsScreen.kt`)**:
   - Thêm Huy hiệu (Badge) phân định giỏ ngay cạnh nhãn `[LONG 1x]` và `[LIVE] / [Paper]`:
     - **Giỏ Chính**: Badge màu Vàng Kim (`CryptoGold`), nền trong suốt 15%, chữ "Giỏ Chính" / "Primary".
     - **Giỏ Phụ**: Badge màu Xanh Cyan sáng (`#00E5FF`), nền trong suốt 15%, chữ "Giỏ Phụ" / "Secondary".
   - Áp dụng đồng bộ cho cả tab **Vị thế mở** (Live & Paper) và tab **Lịch sử đóng lệnh**.

### B. Bảng Thống Kê Unrealized PnL & Volume Chi Tiết Từng Giỏ
1. **Vị trí**: Nằm ngay bên dưới dòng `Unrealized PnL (Paper)` & `Ví Binance Live` trong thẻ tóm tắt tài sản trên cùng của màn hình Vị thế.
2. **Nội dung hiển thị cho mỗi giỏ**:
   - **Giỏ Chính**:
     - Tiêu đề có icon và màu nhận diện: 🟡 Giỏ Chính (Primary Slot).
     - Unrealized PnL bằng tiền ($) và tỷ lệ ROI (%).
     - Số lượng vị thế đang mở và Tổng ký quỹ/Volume đã giải ngân (USDT).
   - **Giỏ Phụ**:
     - Tiêu đề có icon và màu nhận diện: 🔵 Giỏ Phụ (Secondary Slot).
     - Unrealized PnL bằng tiền ($) và tỷ lệ ROI (%).
     - Số lượng vị thế đang mở và Tổng ký quỹ/Volume đã giải ngân (USDT).

### C. Quản Lý Vốn Động Theo Số Dư Còn Lại Thực Tế (Dynamic Sizing)
1. **Logic tính toán vốn động**:
   - Khi quét tín hiệu và chuẩn bị mở vị thế cho một giỏ (hoặc từng lệnh):
     - **Tài khoản Paper**:
       $$\text{Số dư khả dụng} = \text{simulatedBalanceUsdt} - \sum (\text{Margin của các vị thế đang mở})$$
     - **Tài khoản Binance Live**:
       $$\text{Số dư khả dụng} = \text{availableBalance} \text{ (từ Binance Futures Account Info)}$$
     - **Ký quỹ cho mỗi vị thế**:
       $$\text{Vốn 1 vị thế} = \text{Số dư khả dụng} \times \left(\frac{\text{perTradePct}}{100}\right)$$
   - Đảm bảo đúng chuẩn ví dụ:
     - Khởi điểm $1,000. Giỏ chính mở 5 lệnh $\times$ 2% của $1,000 = $20/lệnh (Tổng $100). Khả dụng còn $900.
     - Lần quét 2, Giỏ phụ mở 10 lệnh $\times$ 2% của $900 = $18/lệnh (Tổng $180). Khả dụng còn $720.

### D. Nhắc Nhở Khi Bấm Mở Lệnh Thủ Công Lúc Bot Đang Chạy
1. **Kiểm tra trạng thái Bot**:
   - Khi người dùng nhấn nút `+ Mở vị thế` tại màn hình Thị Trường hoặc nút `Mua/Long` tại màn hình Chi Tiết Coin:
   - Nếu `isBotRunning == true` (AutoTrade đang BẬT):
     - Hiển thị hộp thoại cảnh báo: *"Bot giao dịch tự động đang hoạt động! Để bảo vệ cơ chế phân bổ vốn động và thoát lệnh theo giỏ (Dual Basket), bạn không nên mở lệnh thủ công trong lúc Bot đang chạy."*
     - Có 2 nút: `Tắt Bot để mở thủ công` hoặc `Đã hiểu (Hủy)`.

---

## 3. Các File Cần Chỉnh Sửa

1. `app/src/main/java/com/example/model/TradeModels.kt`:
   - Thêm trường `basketSlot: String = "PRIMARY"` vào `TradePosition` và `BinanceLivePosition`.
   - Cập nhật hàm tính toán `calculateDynamicPositionVolume(availableBalance: Double)`.
2. `app/src/main/java/com/example/repository/CryptoRepository.kt`:
   - Bổ sung tham số `basketSlot` khi tạo và lưu vị thế `executeSimulatedBuy`.
3. `app/src/main/java/com/example/ui/MainViewModel.kt`:
   - Cập nhật luồng quét và mở lệnh: gán chính xác `PRIMARY` cho giỏ chính và `SECONDARY` cho giỏ phụ.
   - Tính toán vốn động dựa trên số dư khả dụng thực tế của tài khoản (Paper & Binance Live).
   - Tự động phân loại giỏ cho các vị thế Binance Live theo thời gian vào lệnh.
   - Tính toán tổng hợp PnL và Volume cho từng giỏ (Primary vs Secondary).
4. `app/src/main/java/com/example/ui/screens/PositionsScreen.kt`:
   - Thêm thẻ chi tiết PnL, ROI và tổng volume của Giỏ Chính & Giỏ Phụ bên dưới Unrealized PnL.
   - Thêm Badge hiển thị `[Giỏ Chính]` / `[Giỏ Phụ]` cho từng vị thế trong danh sách.
5. `app/src/main/java/com/example/ui/screens/MarketScreen.kt` & `CoinDetailScreen.kt`:
   - Tích hợp kiểm tra cảnh báo khi người dùng nhấn mở lệnh thủ công trong lúc Bot đang chạy.

---

## 4. Kế Hoạch Kiểm Thử & Xác Minh
- Kiểm tra biên dịch sạch (`compile_applet`).
- Kiểm tra tính toán số học vốn động theo đúng công thức tỉ lệ % trên vốn khả dụng còn lại.
- Xác nhận các vị thế hiển thị đầy đủ nhãn Giỏ Chính / Giỏ Phụ rõ ràng, bắt mắt.
