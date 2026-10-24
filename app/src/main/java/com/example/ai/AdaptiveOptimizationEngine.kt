package com.example.ai

import com.example.model.BacktestConfig
import com.example.model.BacktestHistoryRecord
import com.example.model.BasketBacktestReport
import java.util.Locale
import kotlin.math.roundToInt

enum class OptimizationGoal(val title: String, val description: String) {
    BALANCED("Cân bằng", "Tối ưu hài hòa giữa tỷ lệ thắng, lợi nhuận ròng và giải quyết điểm nghẽn"),
    MAX_PROFIT("Tối đa Lợi nhuận", "Nới lỏng biên độ giữ sóng, tối ưu K_Trail để bám trọn xu hướng"),
    MIN_DRAWDOWN("An toàn / Giảm Max DD", "Siết chặt lọc thanh khoản, giảm tỷ trọng vốn và tối ưu bước đệm Trailing")
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

        val trailingHits = currentReport?.cycles?.count { it.exitReason == "TRAILING_STOP_HIT" } ?: 0
        val trailingRatio = if (totalCycles > 0) trailingHits.toDouble() / totalCycles else 0.0

        var newInitialCapital = currentConfig.initialCapital
        var newPerTradePct = currentConfig.perTradePct
        var newMaxAllocation = currentConfig.maxPortfolioAllocation
        var newMinQuoteVol = currentConfig.minQuoteVolume24h
        var newMinVolRatio = currentConfig.minVolRatio
        var newMinD1Change = currentConfig.minD1ChangePct
        var newMaxD1Change = currentConfig.maxD1ChangePct
        var newMaxAtrPct = currentConfig.maxAtrPercent
        var newSlotMode = currentConfig.slotScanMode
        var newKTrail = currentConfig.kTrail
        var newEnableTrailing = currentConfig.enableTrailingStop
        var newEnableFloorLock = currentConfig.enableFloorLock
        var newFloorLockMinRoi = currentConfig.floorLockMinRoi

        val diffs = mutableListOf<ParameterDiff>()
        val diagnosisSb = StringBuilder()

        when (goal) {
            OptimizationGoal.MAX_PROFIT -> {
                diagnosisSb.append("Mục tiêu **Tối đa Lợi nhuận** tập trung vào việc bám trọn các sóng tăng D1 lớn, nới rộng bước đệm Trailing K_Trail để không bị thoát non khi thị trường rung lắc kỹ thuật nhẹ.\n")

                // Nới K_Trail để chịu rung lắc
                val targetKTrail = ((currentConfig.kTrail + 0.10).coerceIn(0.30, 0.45) * 100).roundToInt() / 100.0
                if (targetKTrail != currentConfig.kTrail) {
                    diffs.add(ParameterDiff("Hệ số K_Trail (Trượt)", "${currentConfig.kTrail}", "$targetKTrail", "Nới rộng khoảng đệm trailing ATR tránh bị quét khi điều chỉnh ngắn hạn"))
                    newKTrail = targetKTrail
                }

                // Nới trần D1 Change
                val targetD1Change = (currentConfig.maxD1ChangePct + 2.0).coerceIn(12.0, 15.0)
                if (targetD1Change != currentConfig.maxD1ChangePct) {
                    diffs.add(ParameterDiff("Trần tăng nến D1", "${currentConfig.maxD1ChangePct}%", "$targetD1Change%", "Mở rộng bắt các coin có dòng tiền bứt phá mạnh trong ngày"))
                    newMaxD1Change = targetD1Change
                }

                newEnableTrailing = true
                newEnableFloorLock = true
            }

            OptimizationGoal.MIN_DRAWDOWN -> {
                diagnosisSb.append("Mục tiêu **An toàn & Phòng vệ** ưu tiên giảm thiểu sụt giảm tài khoản (Max Drawdown), siết chặt chất lượng lọc thanh khoản và rút ngắn bước đệm Trailing để chốt lời sát đỉnh.\n")

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

                // Rút ngắn K_Trail để chốt sát đỉnh
                val targetKTrail = 0.18
                if (targetKTrail != currentConfig.kTrail) {
                    diffs.add(ParameterDiff("Hệ số K_Trail (Trượt)", "${currentConfig.kTrail}", "$targetKTrail", "Rút ngắn bước đệm để bảo toàn lợi nhuận sớm khi nến thoái lui"))
                    newKTrail = targetKTrail
                }

                newEnableFloorLock = true
                newEnableTrailing = true
            }

            OptimizationGoal.BALANCED -> {
                diagnosisSb.append("Mục tiêu **Cân bằng** tự động đối chiếu các điểm nghẽn chính từ kết quả backtest để đưa ra tinh chỉnh tối ưu thích ứng:\n")

                if (totalCycles > 0) {
                    diagnosisSb.append("• Đã thực hiện **$totalCycles chu kỳ**: Tỷ lệ thắng **${String.format(Locale.US, "%.1f", winrate)}%**, Lợi nhuận **${if (netProfit >= 0) "+" else ""}${String.format(Locale.US, "%.1f", netProfit)} USDT**, Max Drawdown **${String.format(Locale.US, "%.2f", maxDd)}%**.\n")
                    diagnosisSb.append("• Cơ chế thoát lệnh: 100% FloorLock & ATR Trailing Stop (`$trailingHits` lần).\n")

                    // 1. Chẩn đoán Winrate thấp
                    if (winrate < 50.0) {
                        diagnosisSb.append("• **Điểm nghẽn (Winrate < 50%):** Chất lượng điểm vào lệnh cần được tăng cường bằng cách lọc kỹ thanh khoản và xung lực Volume. ")
                        if (currentConfig.minVolRatio < 2.0) {
                            diffs.add(ParameterDiff("Ngưỡng Volume RVol", "${currentConfig.minVolRatio}x", "2.0x", "Tăng điều kiện bùng nổ volume lên 2.0x để loại bỏ các tín hiệu breakout giả"))
                            newMinVolRatio = 2.0
                        }
                        if (currentConfig.minQuoteVolume24h < 20_000_000.0) {
                            diffs.add(ParameterDiff("Thanh khoản 24h", "${(currentConfig.minQuoteVolume24h / 1_000_000).toInt()}M", "20M", "Nâng mức thanh khoản lên 20M USDT để đảm bảo chọn đúng coin có dòng tiền lớn"))
                            newMinQuoteVol = 20_000_000.0
                        }
                    }

                    // 2. Chẩn đoán bước đệm K_Trail
                    if (currentConfig.kTrail < 0.20) {
                        diffs.add(ParameterDiff("Bước đệm Trailing K_Trail", "${currentConfig.kTrail}", "0.25", "Nâng K_Trail lên 0.25 để giỏ lệnh có không gian dao động bám sóng"))
                        newKTrail = 0.25
                    }

                    newEnableTrailing = true
                    newEnableFloorLock = true
                } else {
                    diagnosisSb.append("Chưa có phiên test nào trong bộ nhớ. Đề xuất thông số chuẩn mực Dual Basket Momentum (Pure Trailing Stop) với K_Trail=0.25, RVol>=2.0x và FloorLock +0.5% net.")
                    newKTrail = 0.25
                    newMinVolRatio = 2.0
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
                historySb.append("• **Lần test [$timeStr]:** Lãi ròng **${if (rec.netProfitUsdt >= 0) "+" else ""}${String.format(Locale.US, "%,.1f", rec.netProfitUsdt)} USDT** | Winrate **${String.format(Locale.US, "%.1f", rec.winratePercent)}%** | MaxDD **${String.format(Locale.US, "%.2f", rec.maxDrawdownPercent)}%** (K_Trail=${rec.config.kTrail}, RVol=${rec.config.minVolRatio}x)\n")
            }

            if (bestProfit != null) {
                historySb.append("=> **Cấu hình lãi cao nhất:** +${String.format(Locale.US, "%,.1f", bestProfit.netProfitUsdt)} USDT (K_Trail=${bestProfit.config.kTrail})\n")
            }
            if (lowestDd != null) {
                historySb.append("=> **Cấu hình an toàn nhất (MaxDD thấp nhất):** ${String.format(Locale.US, "%.2f", lowestDd.maxDrawdownPercent)}% (Vốn/lệnh=${(lowestDd.config.perTradePct * 100)}%)\n")
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
            minD1ChangePct = newMinD1Change,
            maxD1ChangePct = newMaxD1Change,
            maxAtrPercent = newMaxAtrPct,
            slotScanMode = newSlotMode,
            kTrail = newKTrail,
            enableTrailingStop = newEnableTrailing,
            enableFloorLock = newEnableFloorLock,
            floorLockMinRoi = newFloorLockMinRoi
        )

        val rationale = when (goal) {
            OptimizationGoal.MAX_PROFIT -> "Nâng K_Trail lên $newKTrail giúp các vị thế breakout ăn trọn sóng lớn mà không bị thoát non khi thị trường rung lắc nhẹ."
            OptimizationGoal.MIN_DRAWDOWN -> "Hạ tỷ trọng vốn xuống ${(newPerTradePct * 100)}%, siết thanh khoản lên ${(newMinQuoteVol / 1_000_000).toInt()}M USDT và khóa trần danh mục 40% giúp tài khoản kiểm soát sụt giảm nghiêm ngặt."
            OptimizationGoal.BALANCED -> "Điều chỉnh thích ứng giải quyết trực tiếp điểm nghẽn chất lượng điểm vào (RVol=${newMinVolRatio}x, K_Trail=${newKTrail}) để tối ưu tổng thể tỷ lệ thắng và lợi nhuận."
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
