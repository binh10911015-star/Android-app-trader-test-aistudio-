package com.example.ai

import com.example.model.BacktestConfig
import com.example.model.BacktestHistoryRecord
import com.example.model.BasketBacktestReport
import java.util.Locale
import kotlin.math.roundToInt

enum class OptimizationGoal(val title: String, val description: String) {
    BALANCED("Cân bằng", "Tối ưu hài hòa giữa tỷ lệ thắng, lợi nhuận ròng và giải quyết điểm nghẽn"),
    MAX_PROFIT("Tối đa Lợi nhuận", "Nới lỏng biên độ giữ sóng, tối ưu K_TP & K_Trail để bám trọn xu hướng"),
    MIN_DRAWDOWN("An toàn / Giảm Max DD", "Siết chặt lọc thanh khoản, giảm tỷ trọng vốn và kích hoạt van phòng vệ")
}

data class ParameterDiff(
    val name: String,
    val oldValue: String,
    val newValue: String,
    val changeDescription: String
)

data class OptimizationResult(
    val goal: OptimizationGoal,
    val recommendedConfig: BacktestConfig,
    val rationale: String,
    val bottleneckDiagnosis: String,
    val historicalComparison: String,
    val diffs: List<ParameterDiff>
)

object AdaptiveOptimizationEngine {

    fun optimize(
        currentConfig: BacktestConfig,
        currentReport: BasketBacktestReport?,
        history: List<BacktestHistoryRecord>,
        goal: OptimizationGoal = OptimizationGoal.BALANCED
    ): OptimizationResult {
        val totalCycles = currentReport?.totalCycles ?: 0
        val winrate = currentReport?.winratePercent ?: 0.0
        val netProfit = currentReport?.netProfitUsdt ?: 0.0
        val maxDd = currentReport?.maxDrawdownPercent ?: 0.0

        val slHits = currentReport?.cycles?.count { it.exitReason == "SL_HIT" } ?: 0
        val timeoutHits = currentReport?.cycles?.count { it.exitReason.contains("TIMEOUT") } ?: 0
        val trailingHits = currentReport?.cycles?.count { it.exitReason == "TRAILING_STOP_HIT" } ?: 0

        val slRatio = if (totalCycles > 0) slHits.toDouble() / totalCycles else 0.0
        val timeoutRatio = if (totalCycles > 0) timeoutHits.toDouble() / totalCycles else 0.0
        val trailingRatio = if (totalCycles > 0) trailingHits.toDouble() / totalCycles else 0.0

        var newInitialCapital = currentConfig.initialCapital
        var newPerTradePct = currentConfig.perTradePct
        var newMaxAllocation = currentConfig.maxPortfolioAllocation
        var newMinQuoteVol = currentConfig.minQuoteVolume24h
        var newMinVolRatio = currentConfig.minVolRatio
        var newMaxD1Change = currentConfig.maxD1ChangePct
        var newMaxAtrPct = currentConfig.maxAtrPercent
        var newBtcPanic = currentConfig.btcPanicCircuitBreaker
        var newSlotMode = currentConfig.slotScanMode
        var newKTp = currentConfig.kTp
        var newKTrail = currentConfig.kTrail
        var newKSl = currentConfig.kSl
        var newMaxHoldingHours = currentConfig.maxHoldingHours
        var newEnableTrailing = currentConfig.enableTrailingStop
        var newEnableFloorLock = currentConfig.enableFloorLock
        var newEnableStopLoss = currentConfig.enableStopLoss
        var newEnableTimeout = currentConfig.enableTimeout

        val diffs = mutableListOf<ParameterDiff>()
        val diagnosisSb = StringBuilder()

        when (goal) {
            OptimizationGoal.MAX_PROFIT -> {
                diagnosisSb.append("Mục tiêu **Tối đa Lợi nhuận** tập trung vào việc kéo dài thời gian gồng lãi của các sóng bứt phá D1 mạnh, đồng thời nới biên độ Trailing để tránh bị giũ hàng non.\n")

                // Tăng holding hours để bắt trọn sóng
                val targetHours = if (currentConfig.maxHoldingHours < 144) 144 else 216
                if (targetHours != currentConfig.maxHoldingHours) {
                    diffs.add(ParameterDiff("Thời gian giữ lệnh (Timeout)", "${currentConfig.maxHoldingHours}h", "${targetHours}h", "Tăng thời gian cho altcoin chạy hết chu kỳ tăng trưởng"))
                    newMaxHoldingHours = targetHours
                }

                // Tăng K_TP để mở rộng ngưỡng kích hoạt Trailing
                val targetKTp = ((currentConfig.kTp + 0.15).coerceIn(0.60, 0.85) * 100).roundToInt() / 100.0
                if (targetKTp != currentConfig.kTp) {
                    diffs.add(ParameterDiff("Hệ số K_TP (Kích hoạt)", "${currentConfig.kTp}", "$targetKTp", "Nâng ngưỡng kích hoạt trailing để bắt các đợt tăng biên độ lớn"))
                    newKTp = targetKTp
                }

                // Nới K_Trail để chịu rung lắc
                val targetKTrail = ((currentConfig.kTrail + 0.10).coerceIn(0.30, 0.45) * 100).roundToInt() / 100.0
                if (targetKTrail != currentConfig.kTrail) {
                    diffs.add(ParameterDiff("Hệ số K_Trail (Giật lùi)", "${currentConfig.kTrail}", "$targetKTrail", "Nới rộng khoảng đệm trailing ATR tránh bị quét khi điều chỉnh ngắn hạn"))
                    newKTrail = targetKTrail
                }

                // Nới trần D1 Change
                val targetD1Change = (currentConfig.maxD1ChangePct + 2.0).coerceIn(12.0, 15.0)
                if (targetD1Change != currentConfig.maxD1ChangePct) {
                    diffs.add(ParameterDiff("Trần tăng nến D1", "${currentConfig.maxD1ChangePct}%", "$targetD1Change%", "Mở rộng bắt các coin có dòng tiền bứt phá mạnh trong ngày"))
                    newMaxD1Change = targetD1Change
                }

                // BTC panic nới lỏng nhẹ
                val targetPanic = -4.5
                if (targetPanic != currentConfig.btcPanicCircuitBreaker) {
                    diffs.add(ParameterDiff("BTC Panic Breaker", "${currentConfig.btcPanicCircuitBreaker}%", "$targetPanic%", "Cho phép giỏ lệnh tiếp tục vận hành khi BTC có biến động điều chỉnh nhẹ"))
                    newBtcPanic = targetPanic
                }

                newEnableTrailing = true
                newEnableFloorLock = true
            }

            OptimizationGoal.MIN_DRAWDOWN -> {
                diagnosisSb.append("Mục tiêu **An toàn & Phòng vệ** ưu tiên giảm thiểu sụt giảm tài khoản (Max Drawdown), nâng cao chất lượng chọn lọc cổ phiếu và siết chặt kỷ luật quản lý rủi ro.\n")

                // Giảm vốn/lệnh
                val targetPerTrade = 0.010 // 1.0%
                if (targetPerTrade != currentConfig.perTradePct) {
                    diffs.add(ParameterDiff("Vốn mỗi lệnh (Per Trade)", "${(currentConfig.perTradePct * 100)}%", "${(targetPerTrade * 100)}%", "Hạ tỷ trọng lệnh xuống 1.0% để kiềm chế tối đa mức sụt giảm danh mục"))
                    newPerTradePct = targetPerTrade
                }

                // Giảm trần danh mục
                val targetAlloc = 0.40 // 40%
                if (targetAlloc != currentConfig.maxPortfolioAllocation) {
                    diffs.add(ParameterDiff("Trần danh mục tối đa", "${(currentConfig.maxPortfolioAllocation * 100).toInt()}%", "${(targetAlloc * 100).toInt()}%", "Giới hạn giải ngân 40% Equity, giữ 60% tiền mặt phòng thủ"))
                    newMaxAllocation = targetAlloc
                }

                // Tăng thanh khoản tối thiểu
                val targetVol = 25_000_000.0
                if (targetVol != currentConfig.minQuoteVolume24h) {
                    diffs.add(ParameterDiff("Thanh khoản 24h tối thiểu", "${(currentConfig.minQuoteVolume24h / 1_000_000).toInt()}M", "${(targetVol / 1_000_000).toInt()}M", "Chỉ chọn top coin thanh khoản cao, giảm thiểu triệt để hiện tượng trượt giá"))
                    newMinQuoteVol = targetVol
                }

                // Tăng ngưỡng RVol
                val targetVolRatio = 2.0
                if (targetVolRatio != currentConfig.minVolRatio) {
                    diffs.add(ParameterDiff("Ngưỡng nổ Volume (RVol)", "${currentConfig.minVolRatio}x", "${targetVolRatio}x", "Đòi hỏi khối lượng giao dịch đột biến gấp đôi SMA20 mới xét vào lệnh"))
                    newMinVolRatio = targetVolRatio
                }

                // Siết trần D1
                val targetD1Change = 8.0
                if (targetD1Change != currentConfig.maxD1ChangePct) {
                    diffs.add(ParameterDiff("Trần tăng nến D1", "${currentConfig.maxD1ChangePct}%", "$targetD1Change%", "Loại bỏ hoàn toàn rủi ro mua đuổi đỉnh những coin đã chạy quá xa"))
                    newMaxD1Change = targetD1Change
                }

                // Siết BTC Panic Breaker
                val targetPanic = -2.5
                if (targetPanic != currentConfig.btcPanicCircuitBreaker) {
                    diffs.add(ParameterDiff("BTC Panic Breaker", "${currentConfig.btcPanicCircuitBreaker}%", "$targetPanic%", "Ngắt mở giỏ khẩn cấp ngay khi nến D1 BTC giảm quá 2.5%"))
                    newBtcPanic = targetPanic
                }

                // Bật SL và FloorLock
                newEnableStopLoss = true
                newKSl = 0.50
                newEnableFloorLock = true
                newEnableTrailing = true
            }

            OptimizationGoal.BALANCED -> {
                diagnosisSb.append("Mục tiêu **Cân bằng** tự động chẩn đoán các điểm nghẽn chính từ kết quả backtest vừa chạy để đưa ra tinh chỉnh tối ưu thích ứng:\n")

                if (totalCycles > 0) {
                    diagnosisSb.append("• Đã thực hiện **$totalCycles chu kỳ**: Tỷ lệ thắng **${String.format(Locale.US, "%.1f", winrate)}%**, Lợi nhuận **${if (netProfit >= 0) "+" else ""}${String.format(Locale.US, "%.1f", netProfit)} USDT**, Max Drawdown **${String.format(Locale.US, "%.2f", maxDd)}%**.\n")
                    diagnosisSb.append("• Phân bố kết thúc: Trailing TP: `$trailingHits` ($${(trailingRatio * 100).toInt()}%) | Cắt lỗ (SL): `$slHits` ($${(slRatio * 100).toInt()}%) | Hết hạn Timeout: `$timeoutHits` ($${(timeoutRatio * 100).toInt()}%).\n")

                    // 1. Chẩn đoán điểm nghẽn Timeout
                    if (timeoutRatio >= 0.30) {
                        diagnosisSb.append("• **Điểm nghẽn 1 (Tỷ lệ Timeout cao):** Có tới ${(timeoutRatio * 100).toInt()}% chu kỳ bị đóng do hết thời gian trước khi kịp chạm ngưỡng Trailing. ")
                        if (currentConfig.maxHoldingHours <= 72) {
                            diffs.add(ParameterDiff("Thời gian giữ lệnh (Timeout)", "${currentConfig.maxHoldingHours}h", "144h", "Tăng thời gian giữ lệnh lên 6 ngày giúp vị thế tích lũy và phát huy xung lực tăng"))
                            newMaxHoldingHours = 144
                        }
                        if (currentConfig.kTp > 0.40) {
                            val lowerKTp = ((currentConfig.kTp - 0.10).coerceAtLeast(0.35) * 100).roundToInt() / 100.0
                            diffs.add(ParameterDiff("Hệ số K_TP", "${currentConfig.kTp}", "$lowerKTp", "Hạ ngưỡng kích hoạt giúp Trailing bắt đầu khóa lãi sớm hơn trước khi cạn thời gian"))
                            newKTp = lowerKTp
                        }
                    }

                    // 2. Chẩn đoán điểm nghẽn Cắt lỗ (SL Hit)
                    if (slRatio >= 0.25) {
                        diagnosisSb.append("• **Điểm nghẽn 2 (Dính Cắt Lỗ sớm):** Tỷ lệ dính SL là ${(slRatio * 100).toInt()}%, cho thấy biên độ Stop Loss đang quá gần râu nến rung giật ngắn hạn. ")
                        if (currentConfig.kSl < 0.75) {
                            diffs.add(ParameterDiff("Hệ số Stop Loss (K_SL)", "${currentConfig.kSl}", "0.75", "Nới rộng bước đệm SL ra 0.75*ATR tránh bị quét bởi râu nến kỹ thuật"))
                            newKSl = 0.75
                        } else if (currentConfig.kSl < 1.0) {
                            diffs.add(ParameterDiff("Hệ số Stop Loss (K_SL)", "${currentConfig.kSl}", "1.00", "Nới lỏng bước đệm SL lên 1.0*ATR để bảo vệ vị thế tốt hơn"))
                            newKSl = 1.00
                        }
                    }

                    // 3. Chẩn đoán Winrate thấp
                    if (winrate < 50.0) {
                        diagnosisSb.append("• **Điểm nghẽn 3 (Winrate < 50%):** Chất lượng điểm vào lệnh cần được tăng cường bằng cách lọc kỹ thanh khoản và xung lực Volume. ")
                        if (currentConfig.minVolRatio < 1.8) {
                            diffs.add(ParameterDiff("Ngưỡng Volume RVol", "${currentConfig.minVolRatio}x", "1.8x", "Tăng điều kiện bùng nổ volume lên 1.8x để loại bỏ các tín hiệu breakout giả"))
                            newMinVolRatio = 1.8
                        }
                        if (currentConfig.minQuoteVolume24h < 20_000_000.0) {
                            diffs.add(ParameterDiff("Thanh khoản 24h", "${(currentConfig.minQuoteVolume24h / 1_000_000).toInt()}M", "20M", "Nâng mức thanh khoản lên 20M USDT để đảm bảo chọn đúng coin có dòng tiền lớn"))
                            newMinQuoteVol = 20_000_000.0
                        }
                    }

                    // 4. Nếu Trailing hoạt động tốt
                    if (trailingRatio >= 0.50 && netProfit > 0) {
                        diagnosisSb.append("• **Tín hiệu tích cực:** Đa số các chu kỳ (${(trailingRatio * 100).toInt()}%) đã chốt lời bằng cơ chế bám đỉnh Trailing Stop. Hệ thống đang đi đúng hướng!")
                        newEnableTrailing = true
                        newEnableFloorLock = true
                    }
                } else {
                    diagnosisSb.append("Chưa có phiên test nào trong bộ nhớ. Đề xuất thông số chuẩn mực Dual Basket Momentum v2.1 với K_TP=0.5, K_Trail=0.25, K_SL=0.75 và Timeout 144h.")
                    newMaxHoldingHours = 144
                    newKSl = 0.75
                    newKTp = 0.50
                    newKTrail = 0.25
                    newEnableTrailing = true
                    newEnableFloorLock = true
                }
            }
        }

        // Tạo phần đối chiếu lịch sử (Historical comparison)
        val historySb = StringBuilder()
        if (history.isNotEmpty()) {
            historySb.append("### Đối Soát Biến Thiên Các Lần Test Trước (${history.size} bản ghi):\n")
            val bestProfit = history.maxByOrNull { it.netProfitUsdt }
            val bestWinrate = history.maxByOrNull { it.winratePercent }
            val lowestDd = history.minByOrNull { it.maxDrawdownPercent }

            history.takeLast(3).reversed().forEachIndexed { idx, rec ->
                val timeStr = java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date(rec.timestamp))
                historySb.append("• **Lần test [$timeStr]:** Lãi ròng **${if (rec.netProfitUsdt >= 0) "+" else ""}${String.format(Locale.US, "%,.1f", rec.netProfitUsdt)} USDT** | Winrate **${String.format(Locale.US, "%.1f", rec.winratePercent)}%** | MaxDD **${String.format(Locale.US, "%.2f", rec.maxDrawdownPercent)}%** (K_TP=${rec.config.kTp}, K_Trail=${rec.config.kTrail}, K_SL=${rec.config.kSl}, Hold=${rec.config.maxHoldingHours}h)\n")
            }

            if (bestProfit != null) {
                historySb.append("=> **Cấu hình lãi cao nhất:** +${String.format(Locale.US, "%,.1f", bestProfit.netProfitUsdt)} USDT (K_TP=${bestProfit.config.kTp}, K_Trail=${bestProfit.config.kTrail})\n")
            }
            if (lowestDd != null) {
                historySb.append("=> **Cấu hình an toàn nhất (MaxDD thấp nhất):** ${String.format(Locale.US, "%.2f", lowestDd.maxDrawdownPercent)}% (K_SL=${lowestDd.config.kSl}, Vốn/lệnh=${(lowestDd.config.perTradePct * 100)}%)\n")
            }
        } else {
            historySb.append("Chưa có lịch sử các lần backtest trước để đối chiếu chuỗi. Bản test này sẽ là cơ sở đầu tiên.\n")
        }

        val recommendedConfig = currentConfig.copy(
            initialCapital = newInitialCapital,
            perTradePct = newPerTradePct,
            maxPortfolioAllocation = newMaxAllocation,
            minQuoteVolume24h = newMinQuoteVol,
            minVolRatio = newMinVolRatio,
            maxD1ChangePct = newMaxD1Change,
            maxAtrPercent = newMaxAtrPct,
            btcPanicCircuitBreaker = newBtcPanic,
            slotScanMode = newSlotMode,
            kTp = newKTp,
            kTrail = newKTrail,
            kSl = newKSl,
            maxHoldingHours = newMaxHoldingHours,
            enableTrailingStop = newEnableTrailing,
            enableFloorLock = newEnableFloorLock,
            enableStopLoss = newEnableStopLoss,
            enableTimeout = newEnableTimeout
        )

        val rationale = when (goal) {
            OptimizationGoal.MAX_PROFIT -> "Mở rộng thời gian giữ lệnh lên ${newMaxHoldingHours}h và nâng K_TP lên $newKTp giúp các vị thế breakout ăn trọn sóng lớn mà không bị thoát non."
            OptimizationGoal.MIN_DRAWDOWN -> "Hạ tỷ trọng vốn xuống ${(newPerTradePct * 100)}%, siết thanh khoản lên ${(newMinQuoteVol / 1_000_000).toInt()}M USDT và khóa trần danh mục 40% giúp tài khoản kiểm soát sụt giảm nghiêm ngặt."
            OptimizationGoal.BALANCED -> "Điều chỉnh thích ứng giải quyết trực tiếp điểm nghẽn (${if (timeoutRatio > 0.3) "giảm timeout" else if (slRatio > 0.25) "giảm SL" else "cân bằng vào/ra"}) để tối ưu tổng thể tỷ lệ thắng và lợi nhuận."
        }

        return OptimizationResult(
            goal = goal,
            recommendedConfig = recommendedConfig,
            rationale = rationale,
            bottleneckDiagnosis = diagnosisSb.toString(),
            historicalComparison = historySb.toString(),
            diffs = diffs
        )
    }
}
