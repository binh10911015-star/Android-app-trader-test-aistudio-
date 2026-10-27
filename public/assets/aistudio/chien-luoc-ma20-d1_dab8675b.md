# Chiến lược SMA20 D1 + lọc BTC — tài liệu kỹ thuật

Cập nhật: 27/10/2026 · Script: `api/scripts/ma20-rr-d1.mjs`

## 1. Tóm tắt

Chiến lược mua (long) altcoin trên Binance Futures khung D1. Vào lệnh sau một nến tăng có thân/râu cắt qua SMA20, cắt lỗ cố định ở đáy nến tín hiệu, chốt lời ở 3R. Chỉ giao dịch khi BTC đang nằm trên SMA20 D1.

| | 365 ngày | 730 ngày |
|---|---|---|
| Lệnh đã đóng | 5587 | 10.665 |
| Tỷ lệ thắng (hòa vốn ≈ 25%) | 30,7% | 26,5% |
| Lãi/lỗ ròng (5 USD/lệnh) | +463,62 USD | +210,09 USD |
| R trung bình mỗi lệnh | +0,226R | +0,061R |
| Profit factor | 1,32 | 1,07 |

**Trạng thái: chưa đủ tin cậy để giao dịch thật.** Lợi thế rõ ở năm 10/2025–10/2026 nhưng năm 10/2024–10/2025 lỗ (xem mục 5).

## 2. Quy tắc giao dịch

Ký hiệu: nến `t-1` là nến D1 vừa đóng, nến `t` là nến D1 hiện tại. Mọi chỉ báo tính trên giá đóng cửa D1 (00:00 UTC).

### 2.1. Điều kiện vào lệnh (tất cả phải đúng)

| # | Điều kiện | Công thức |
|---|---|---|
| 1 | Nến `t-1` là nến tăng | `close[t-1] > open[t-1]` |
| 2 | Giá đóng cửa trên SMA20 | `close[t-1] > SMA20[t-1]` |
| 3 | SMA20 nằm trong range nến | `low[t-1] <= SMA20[t-1]` (kết hợp với #2 ⇒ `low ≤ SMA20 < close ≤ high`) |
| 4 | Lọc BTC | `close_BTC[t-1] > SMA20_BTC[t-1]` (BTC/USDT perpetual, D1) |
| 5 | Không có lệnh đang mở trên coin đó | — |

### 2.2. Quản lý lệnh

| Thành phần | Giá trị |
|---|---|
| Giá vào | `open[t]` |
| Cắt lỗ (SL) | `low[t-1]`, cố định suốt lệnh |
| Rủi ro 1R | `R = entry − SL` (bỏ qua tín hiệu nếu `R ≤ 0`) |
| Chốt lời (TP) | `entry + 3R` |
| Thoát lệnh | Chỉ khi chạm SL hoặc TP, không có thoát theo thời gian |

### 2.3. Tham số

| Tham số | Giá trị |
|---|---|
| Khung thời gian | D1 |
| Đường trung bình | SMA 20 (không phải EMA) |
| Risk:Reward | 1:3 |
| Khối lượng | 5.1 USD danh nghĩa mỗi lệnh, không đòn bẩy trong mô phỏng |
| Vốn giả định | 1000 USD |
| Phí | 0,05% mỗi chiều (taker USDT-M) |

## 3. Danh sách coin

Toàn bộ hợp đồng perpetual USDT-M đang giao dịch trên Binance Futures có `underlyingType = COIN` (518 coin tại thời điểm test), loại trừ:

- BTC
- Stablecoin: USDC, FDUSD, TUSD, USDP, DAI, USDE, USD1, BUSD, RLUSD, PYUSD, XUSD, U
- Hợp đồng cổ phiếu, chỉ số, hàng hóa, FX (`underlyingType` khác `COIN`)

Lưu ý: PAXG và XAUT (token vàng) vẫn nằm trong danh sách vì Binance xếp loại COIN.

## 4. Giả định mô phỏng

- Tín hiệu và khớp lệnh dùng nến D1 đã đóng, không nhìn trước dữ liệu tương lai.
- SL/TP được kiểm tra từ chính ngày vào lệnh, dựa trên `high`/`low` của nến D1.
- Nến chạm cả SL lẫn TP trong cùng ngày → tính là **SL** (giả định bất lợi). 365 ngày: 58 trường hợp; 730 ngày: 78.
- Giá mở cửa đã vượt qua SL hoặc TP → khớp tại giá mở cửa.
- Lệnh đang mở cuối kỳ được tính tạm theo giá đóng cửa cuối cùng, không gộp vào thống kê lệnh đã đóng.
- **Chưa tính:** phí funding, trượt giá, giới hạn vốn (đỉnh ~300 lệnh mở cùng lúc ≈ 1500 USD, vượt vốn 1000 USD).

## 5. Kết quả backtest

### 5.1. Theo năm

| Giai đoạn | Lãi/lỗ ròng | Profit factor | Bỏ 1% lệnh lãi nhất |
|---|---|---|---|
| 27/10/2024 – 26/10/2025 | −223,82 USD | 0,86 | −414,19 USD |
| 27/10/2025 – 26/10/2026 | +433,91 USD | 1,29 | +150,07 USD |
| Cả 730 ngày | +210,09 USD | 1,07 | −266,84 USD |

### 5.2. Hiệu quả của bộ lọc BTC

| Giai đoạn | Không lọc BTC | Có lọc BTC |
|---|---|---|
| 365 ngày | +390,77 USD (PF 1,15) | +463,62 USD (PF 1,32) |
| 730 ngày | −2,89 USD (PF 1,00) | +210,09 USD (PF 1,07) |

Bộ lọc chặn được các tháng cả thị trường cùng giảm (11/2025, 2/2026, 6/2026), nhưng **không chặn được các tháng altcoin sập trong khi BTC vẫn trên SMA20**: 1/2025, 3/2025, 8/2025, 10/2025 có tỷ lệ thắng chỉ 4–7% và lỗ tổng khoảng −660 USD.

### 5.3. Đặc điểm lợi nhuận

- Kiểu "lỗ nhỏ đều đặn, lãi lớn theo sóng": phần lớn lợi nhuận đến từ vài tháng altcoin tăng mạnh (11/2024, 4/2025, 7/2025, 4/2026, 8/2026).
- Thời gian giữ lệnh trung vị 3 ngày; khoảng SL trung vị ~6%.

## 6. Các biến thể đã thử và bị loại

Kết quả trên 365 ngày (10/2025–10/2026), cùng điều kiện vào lệnh #1–#3, **không** lọc BTC. So với mốc chuẩn: SMA20 + TP 1:3 + SL cố định = +390,77 USD, PF 1,15.

| Biến thể | Lãi/lỗ ròng | PF | Kết luận |
|---|---|---|---|
| Bỏ điều kiện #3 (SMA20 trong range), RR 1:2 | +25,87 USD | 1,01 | Điều kiện #3 là cần thiết |
| RR 1:2 | +184,91 USD | 1,07 | Kém hơn 1:3 |
| RR 1:4 | +335,85 USD | 1,13 | Kém hơn 1:3, kém ổn định hơn |
| RR 1:5 | +331,15 USD | 1,13 | Kém hơn 1:3, kém ổn định hơn |
| Trailing stop bậc 1R từ +1R (TP 1:3) | +108,13 USD | 1,05 | Cắt mất lệnh thắng lớn |
| Trailing stop bậc 1R từ +2R (TP 1:3) | +286,18 USD | 1,11 | Vẫn kém SL cố định |
| Chốt 50% tại +1R (TP 1:3) | +137,82 USD | 1,07 | Mất 1R mỗi lệnh thắng nhiều hơn phần tiết kiệm |
| EMA20 thay SMA20 | +296,17 USD | 1,11 | SL rộng hơn, lỗ nhiều hơn |
| Khung H8 (SMA20 H8) | −159,33 USD | 0,97 | Nhiễu + phí ăn mòn |
| Khung H4 (SMA20 H4) | −676,14 USD | 0,90 | Nhiễu + phí ăn mòn |

Quy luật chung: với tín hiệu này, can thiệp vào lệnh (dời SL, chốt sớm) hoặc xuống khung nhỏ đều làm kết quả kém đi.

## 7. Rủi ro và hạn chế

1. **Lợi thế không ổn định qua các năm** — năm 2024–2025 lỗ dù có lọc BTC.
2. **Survivorship bias** — chỉ test các coin còn niêm yết hôm nay; coin đã bị hủy niêm yết (thường là coin sập) không có trong dữ liệu, nên kết quả năm cũ có thể còn tệ hơn.
3. **Lợi thế mỏng** — +0,06R mỗi lệnh trên 730 ngày, dễ bị phí funding và trượt giá xóa sạch, nhất là với coin thanh khoản thấp.
4. **Thiếu vốn** — đỉnh ~300 lệnh mở cùng lúc; với vốn 1000 USD sẽ phải bỏ bớt tín hiệu, kết quả thực tế sẽ khác backtest.
5. **Độ phân giải D1** — thứ tự chạm SL/TP trong cùng ngày không xác định được; đã giả định bất lợi.

## 8. Hướng phát triển

- Thay/kết hợp bộ lọc BTC bằng bộ lọc độ rộng thị trường altcoin (tỷ lệ % altcoin đang trên SMA20) để chặn các giai đoạn altcoin yếu khi BTC vẫn khỏe.
- Giới hạn danh sách theo thanh khoản (khối lượng giao dịch 24h).
- Thêm phí funding và trượt giá vào mô phỏng.
- Mô phỏng giới hạn vốn và số lệnh mở tối đa.
- Kiểm tra lại các nến chạm cả SL lẫn TP bằng dữ liệu H1.

## 9. Cách chạy lại

Từ thư mục `api/`:

```bash
# Cấu hình tốt nhất, 365 ngày
node scripts/ma20-rr-d1.mjs futures-alts 365 3 0 0 sma 1d btc

# Cùng cấu hình, 730 ngày
node scripts/ma20-rr-d1.mjs futures-alts 730 3 0 0 sma 1d btc
```

| Vị trí | Tham số | Giá trị cấu hình tốt nhất |
|---|---|---|
| 1 | Danh sách coin (`futures-alts` hoặc `BTC/USDT,ETH/USDT,...` spot) | `futures-alts` |
| 2 | Số ngày test | `365` |
| 3 | RR, có thể nhiều giá trị `2,3,4`; `0` = không TP | `3` |
| 4 | Trailing stop: R bắt đầu dời SL về hòa vốn; `0` = tắt | `0` |
| 5 | Chốt 50% tại +P R; `0` = tắt | `0` |
| 6 | Đường trung bình `sma` / `ema` | `sma` |
| 7 | Khung thời gian `1d` / `8h` / `4h` ... | `1d` |
| 8 | Lọc BTC: `btc` = bật | `btc` |

Kết quả ghi vào `api/ma-cross-results/ma20in-rr3-btc-d1-trades.csv` (chi tiết từng lệnh) và `ma20in-rr3-btc-d1-summary.json` (tổng hợp theo coin). Chạy lại với số ngày khác sẽ ghi đè file cùng tên.
