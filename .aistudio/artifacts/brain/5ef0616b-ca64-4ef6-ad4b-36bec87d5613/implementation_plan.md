# Kế Hoạch Triển Khai: Nâng Cấp Toàn Diện AI Cố Vấn & Động Cơ Tối Ưu Hóa Thông Số Đa Mục Tiêu

## 1. Mục Tiêu & Chẩn Đoán Cốt Lõi
- **Chẩn đoán gốc rễ:**
  1. `AiAssistantViewModel` đang đọc `System.getenv("GEMINI_API_KEY")` (không tồn tại trong môi trường runtime của Android), dẫn đến việc luôn bị rơi vào hàm fallback dự phòng.
  2. Hàm fallback `generateExpertAnalysis()` hiện tại đang hardcode 100% một đoạn text và một bộ thông số JSON cố định duy nhất, không hề tính toán theo dữ liệu backtest thực tế của người dùng.
  3. `ScreenContextBuilder` chưa gửi mảng `backtestHistory` (lịch sử các lần backtest trước), khiến AI không có cơ sở so sánh đối chiếu giữa các phiên chạy với các bộ thông số khác nhau.
- **Mục tiêu giải pháp:**
  1. Xây dựng **Động cơ Tối ưu hóa Thích ứng Dữ liệu Thực (`AdaptiveOptimizationEngine`)** phân tích điểm nghẽn backtest (tỷ lệ SL, Timeout, Trailing, Winrate, Drawdown) để tự động tính toán ra các thông số mới phù hợp theo 3 chế độ: **Tối đa Lợi nhuận**, **An toàn / Giảm Max DD**, **Cân bằng**.
  2. Bổ sung toàn bộ lịch sử các lần backtest trước (`backtestHistory`) vào `ScreenContextBuilder` để AI và động cơ phân tích nhận diện được xu hướng thay đổi hiệu suất qua từng phiên test.
  3. Nâng cấp `AiSystemPrompt` và `AiAssistantViewModel` để kết hợp suy luận mở rộng (Gemini API qua BuildConfig/API key dự phòng) cùng bộ suy luận thích ứng nội tại, đảm bảo không bao giờ bị lặp lại câu trả lời hay thông số khuôn mẫu.
  4. Bổ sung bộ chọn chế độ tối ưu (Mode Selector) và hiển thị bảng so sánh thông số đề xuất vs thông số hiện tại ngay trong giao diện AI Assistant Bottom Sheet.

---

## 2. Thiết Kế Chi Tiết & Các Thành Phần Cần Sửa

### A. Động cơ Phân tích & Tối ưu thích ứng (`com.example.ai.AdaptiveOptimizationEngine`)
- **Phân loại chế độ tối ưu (`OptimizationGoal`):**
  - `MAX_PROFIT`: Tận dụng tối đa sóng tăng, nới thời gian giữ lệnh (144h-216h), nâng $K_{TP}$ (0.6-0.8), mở rộng bước đệm $K_{Trail}$ (0.35-0.45), hạ ngưỡng ngắt BTC panic (-4.0% đến -5.0%).
  - `MIN_DRAWDOWN`: Ưu tiên an toàn vốn, giảm `perTradePct` về 1.0%, hạ trần danh mục 40%, tăng thanh khoản lọc lên 25M USDT, siết BTC panic về -2.5%, khóa sàn lãi ròng FloorLock.
  - `BALANCED`: Giải quyết điểm nghẽn lớn nhất của bản test gần nhất:
    - Nếu `timeoutHits > trailingHits`: Tăng `maxHoldingHours` (từ 72h lên 144h) hoặc hạ $K_{TP}$ (từ 0.5 về 0.35 - 0.40).
    - Nếu `slHits > trailingHits`: Tăng $K_{SL}$ từ 0.5 lên 0.75 - 1.0 hoặc đề xuất tắt Stop Loss cứng nếu người dùng theo trường phái Pure Trailing.
    - Nếu `winrate < 50%`: Tăng ngưỡng thanh khoản `minQuoteVolume24h` và tăng `minVolRatio` (1.8x - 2.2x), siết `maxD1ChangePct` (8% - 10%).
- **So sánh lịch sử đa biến:**
  - Đối chiếu giữa các bản backtest trong lịch sử: cấu hình nào cho Winrate cao nhất, cấu hình nào cho Max Drawdown thấp nhất, cấu hình nào cho Net Profit cao nhất để làm trọng số đề xuất.

### B. Cập nhật Ngữ Cảnh AI (`com.example.ai.ScreenContextBuilder`)
- Bổ sung tham số `backtestHistory: List<BacktestHistoryRecord>` vào hàm `buildDualBasketContext`.
- Đóng gói toàn bộ các bản ghi lịch sử gồm: `id`, `timestamp`, `days`, các thông số cấu hình cũ (`config`), kết quả thực tế (`winrate`, `netProfitUsdt`, `maxDrawdown`, `slHits`, `timeoutHits`, `trailingHits`).
- Gửi kèm thống kê phân phối lệnh thắng/thua để AI có đầy đủ dữ liệu định lượng.

### C. Nâng Cấp ViewModel & Prompt (`AiAssistantViewModel` & `AiSystemPrompt`)
- Hỗ trợ chọn mục tiêu tối ưu hóa trực tiếp (`optimizationGoal: OptimizationGoal`).
- Kiểm tra cấu hình Gemini API qua `BuildConfig.GEMINI_API_KEY` hoặc API key được cấu hình an toàn, kết hợp fallback thông minh sử dụng `AdaptiveOptimizationEngine`.
- Cấu trúc phản hồi theo 4 phần rõ ràng:
  1. **Chẩn đoán điểm nghẽn định lượng:** Đánh giá số liệu thực tế của phiên vừa chạy và so sánh với các phiên trước trong lịch sử.
  2. **Giải pháp tương ứng mục tiêu:** Giải thích lý do tăng/giảm từng thông số cụ thể.
  3. **Bảng so sánh thông số (Trước vs Sau):** Giúp người dùng thấy rõ sự khác biệt của từng chỉ số.
  4. **Khối JSON chuẩn hóa:** Đầy đủ các cờ v2.1 (`enableTrailingStop`, `enableFloorLock`, `enableStopLoss`, `enableTimeout`) để nút bấm áp dụng 1-chạm hoạt động tức thì.

### D. Giao Diện Người Dùng (`AiAssistantUi.kt`)
- Thêm hàng phím chọn chế độ tối ưu nhanh:
  - `[ Cân bằng ]` `[ Tối đa Lợi nhuận ]` `[ An toàn / Giảm Max DD ]`
- Thẻ hiển thị bộ thông số tối ưu thích ứng trực quan:
  - Hiển thị badge chế độ tối ưu.
  - Hiển thị bảng biến thiên thông số (ví dụ: $K_{TP}: 0.50 \rightarrow 0.65$, $MaxHold: 72h \rightarrow 144h$).
  - Nút **"Áp Dụng Bộ Thông Số Này"** với phản hồi thành công tức thì vào màn hình cấu hình Backtest.

---

## 3. Kế Hoạch Kiểm Thử & Xác Minh
1. **Kiểm thử biên dịch:** Chạy `compile_applet` để đảm bảo không có lỗi cú pháp hoặc xung đột dependencies.
2. **Kiểm thử logic tối ưu hóa:** Xác minh rằng khi người dùng đổi các bộ thông số khác nhau trong Backtest, AI Cố Vấn sẽ tính toán ra các bộ thông số đề xuất hoàn toàn khác nhau tương ứng với dữ liệu thực tế, loại bỏ hoàn toàn tình trạng đề xuất cố định một bộ chỉ số.
3. **Kiểm thử nạp thông số:** Nhấn nút áp dụng để đảm bảo toàn bộ các thông số mới (bao gồm các cờ bật/tắt v2.1) được đồng bộ chính xác vào `MainViewModel.backtestConfig`.
