package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BacktestConfig
import com.example.model.BacktestHistoryRecord
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatisticsScreen(
    viewModel: MainViewModel,
    onNavigateToBacktest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val history by viewModel.backtestHistory.collectAsState()
    val optimalConfig = remember(history) { viewModel.getOptimalAiConfig() }

    val totalRuns = history.size
    val avgWinrate = if (history.isNotEmpty()) history.map { it.winratePercent }.average() else 0.0
    val avgRoi = if (history.isNotEmpty()) history.map { it.netProfitPercent }.average() else 0.0

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 14.dp)
            .testTag("statistics_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
    ) {
        // --- TIÊU ĐỀ THẺ THỐNG KÊ ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Thống Kê & Đề Xuất AI",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "Ghi nhận kết quả các lần backtest • AI phân tích bộ thông số tối ưu",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CryptoGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "AI Optimizer",
                        color = CryptoGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ==========================================
        // KHUNG VIP: AI ĐỀ XUẤT BỘ THÔNG SỐ HIỆU QUẢ
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("ai_optimal_config_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.5.dp, CryptoGold)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BỘ THÔNG SỐ TỐI ƯU TỪ AI",
                                color = CryptoGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CryptoGold.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, CryptoGold)
                        ) {
                            Text(
                                text = "TOP PERFORMANCE",
                                color = CryptoGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Thuật toán AI tự động đánh giá các kịch bản backtest đã thực hiện (ưu tiên Winrate cao, Drawdown thấp và lợi nhuận ròng ổn định):",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Lưới thông số tối ưu
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• Chu kỳ SMA nến D1:", fontSize = 11.sp, color = TextMuted)
                                Text("${optimalConfig.smaPeriod} (Nến ngày)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CryptoGold, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• Tỷ lệ Chốt Lời RR:", fontSize = 11.sp, color = TextMuted)
                                Text("1:${optimalConfig.rrRatio.toInt()} (Cố định 3R)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CryptoGreen, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• Cắt Lỗ Stop Loss (SL):", fontSize = 11.sp, color = TextMuted)
                                Text("Đáy nến t-1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CryptoRed, fontFamily = FontFamily.Monospace)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• Quy mô & Phân bổ:", fontSize = 11.sp, color = TextMuted)
                                Text("5.1 USDT/lệnh (Không giới hạn trần)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.applyRecommendedConfig(optimalConfig)
                            onNavigateToBacktest()
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp).testTag("btn_apply_ai_config"),
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Nạp Bộ Thông Số Này Vào Bot & Backtest",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ==========================================
        // TỔNG HỢP HIỆU SUẤT CÁC LẦN BACKTEST
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: Số lần test
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Số lần Backtest", fontSize = 10.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$totalRuns Lượt", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Card 2: Winrate TB
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Winrate TB", fontSize = 10.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${"%.1f".format(avgWinrate)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CryptoGold)
                    }
                }

                // Card 3: ROI TB
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("ROI TB", fontSize = 10.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${if (avgRoi >= 0) "+" else ""}${"%.1f".format(avgRoi)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CryptoGreen)
                    }
                }
            }
        }

        // ==========================================
        // DANH SÁCH LỊCH SỬ CÁC LẦN BACKTEST
        // ==========================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch Sử Các Lần Backtest (${history.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Sắp xếp: Mới nhất",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Chưa có lượt backtest nào. Hãy sang thẻ 'Backtest' và nhấn Chạy Thử Nghiệm!",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(history, key = { it.id }) { item ->
                BacktestHistoryItemCard(
                    record = item,
                    dateFormat = dateFormat,
                    onApplyConfig = {
                        viewModel.updateBacktestConfig(item.config, item.days)
                        onNavigateToBacktest()
                    }
                )
            }
        }
    }
}

@Composable
fun BacktestHistoryItemCard(
    record: BacktestHistoryRecord,
    dateFormat: SimpleDateFormat,
    onApplyConfig: () -> Unit
) {
    val isProfit = record.netProfitUsdt >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, if (isProfit) CryptoGreen.copy(alpha = 0.4f) else CryptoRed.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = dateFormat.format(Date(record.timestamp)),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isProfit) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${if (isProfit) "+" else ""}${"%.2f".format(record.netProfitPercent)}% ROI",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Lợi nhuận ròng", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "${if (isProfit) "+" else ""}${"%.2f".format(record.netProfitUsdt)} USDT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Winrate", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "${"%.1f".format(record.winratePercent)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CryptoGold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Max Drawdown", fontSize = 10.sp, color = TextMuted)
                    Text(
                        text = "-${"%.2f".format(record.maxDrawdownPercent)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CryptoRed,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.days} Ngày • ${record.totalCycles} Lệnh (${record.winCycles} Thắng, ${record.lossCycles} Thua)",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                TextButton(
                    onClick = onApplyConfig,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Dùng bộ thông số này", fontSize = 10.sp, color = CryptoGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
