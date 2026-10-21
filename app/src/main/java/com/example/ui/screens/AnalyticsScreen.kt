package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BacktestConfig
import com.example.model.BasketBacktestReport
import com.example.model.BasketCycleResult
import com.example.model.TradePosition
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    closedPositions: List<TradePosition> = emptyList(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning = uiState.isBasketBacktesting
    val report = uiState.basketReport

    // 1. Số ngày kiểm tra tùy chỉnh
    var daysText by remember { mutableStateOf("180") }

    // 2. Các thông số cấu hình giỏ lệnh & quản trị vốn
    var initialCapitalText by remember { mutableStateOf("10000") }
    var perTradePctText by remember { mutableStateOf("1.5") }
    var maxAllocationPctText by remember { mutableStateOf("60") }
    var targetRoiPctText by remember { mutableStateOf("1.0") }

    // 3. Hai thông số van an toàn & bộ lọc thanh khoản
    var maxD1ChangePctText by remember { mutableStateOf("17.5") }
    var minQuoteVolumeText by remember { mutableStateOf("5000000") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        // --- TIÊU ĐỀ MÀN HÌNH ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CryptoGold.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Backtest Basket D1 Momentum",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quét 07:00 AM (UTC+7) • Long Cross 1x • Binance Mainnet",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // --- BẢNG CẤU HÌNH THÔNG SỐ BACKTEST (6 THÔNG SỐ ĐẦY ĐỦ) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cấu Hình & Quản Trị Vốn",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // HUY HIỆU XÁC NHẬN CHẾ ĐỘ AN TOÀN CROSS 1X
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CryptoGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, CryptoGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = CryptoGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Margin: Cross | Leverage: 1x",
                                    color = CryptoGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HÀNG 1: SỐ NGÀY BACKTEST TÙY CHỈNH & CÁC NÚT CHỌN NHANH
                    Text(text = "Số ngày kiểm tra (Klines D1)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { daysText = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.weight(1.2f).height(50.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        )

                        listOf("60", "90", "180", "360").forEach { d ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { daysText = d },
                                color = if (daysText == d) CryptoGold else DarkSurfaceHighlight,
                                border = BorderStroke(0.5.dp, if (daysText == d) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = "${d}d",
                                    color = if (daysText == d) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HÀNG 2: VỐN BAN ĐẦU & TỶ LỆ VỐN/LỆNH
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Vốn ban đầu (USDT)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = initialCapitalText,
                                onValueChange = { initialCapitalText = it },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoGold,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Vốn/Lệnh (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = perTradePctText,
                                onValueChange = { perTradePctText = it },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoGold,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HÀNG 3: TRẦN GIẢI NGÂN & MỤC TIÊU TP GIỎ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Trần giải ngân (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maxAllocationPctText,
                                onValueChange = { maxAllocationPctText = it },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoGold,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Mục tiêu TP Giỏ (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = targetRoiPctText,
                                onValueChange = { targetRoiPctText = it },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoGold,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HÀNG 4: NGƯỠNG CHẶN TRÊN NẾN D1 & LỌC THANH KHOẢN 24H
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Chặn trên nến D1 (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maxD1ChangePctText,
                                onValueChange = { maxD1ChangePctText = it },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoGold,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Lọc Vol 24h (USDT)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = minQuoteVolumeText,
                                onValueChange = { minQuoteVolumeText = it },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoGold,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // NÚT BẤM CHẠY BACKTEST
                    Button(
                        onClick = {
                            val days = daysText.toIntOrNull() ?: 180
                            val initCap = initialCapitalText.toDoubleOrNull() ?: 10000.0
                            val perTrade = (perTradePctText.toDoubleOrNull() ?: 1.5) / 100.0
                            val maxAlloc = (maxAllocationPctText.toDoubleOrNull() ?: 60.0) / 100.0
                            val targetRoi = (targetRoiPctText.toDoubleOrNull() ?: 1.0) / 100.0
                            val maxD1 = maxD1ChangePctText.toDoubleOrNull() ?: 17.5
                            val minVol = minQuoteVolumeText.toDoubleOrNull() ?: 5000000.0

                            val customConfig = BacktestConfig(
                                initialCapital = initCap,
                                perTradePct = perTrade,
                                maxPortfolioAllocation = maxAlloc,
                                targetPortfolioRoi = targetRoi,
                                maxD1ChangePct = maxD1,
                                minQuoteVolume24h = minVol
                            )

                            viewModel.runBasketStrategyBacktest(days = days, config = customConfig)
                        },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CryptoGold,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Đang tải nến & tính toán...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Bắt Đầu Chạy Backtest", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- KẾT QUẢ BÁO CÁO (NẾU CÓ) ---
        if (report != null) {
            item {
                BasketReportOverviewCard(report = report)
            }

            if (report.cycles.isNotEmpty()) {
                item {
                    Text(
                        text = "Lịch Sử Chu Kỳ Giỏ Lệnh (${report.cycles.size} chu kỳ)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(report.cycles, key = { it.cycleIndex }) { cycle ->
                    BasketCycleItemCard(cycle = cycle)
                }
            }
        }
    }
}

@Composable
fun BasketReportOverviewCard(report: BasketBacktestReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "KẾT QUẢ HIỆU SUẤT BACKTEST",
                color = CryptoGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Tổng chu kỳ", color = TextMuted, fontSize = 10.sp)
                    Text("${report.totalCycles} giỏ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tỷ lệ thắng", color = TextMuted, fontSize = 10.sp)
                    Text("${String.format(Locale.US, "%.1f", report.winratePercent)}%", color = CryptoGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Giữ trung bình", color = TextMuted, fontSize = 10.sp)
                    Text("${String.format(Locale.US, "%.1f", report.averageHoldingDays)} ngày", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Lợi nhuận ròng", color = TextMuted, fontSize = 10.sp)
                    val isProfit = report.netProfitUsdt >= 0
                    Text(
                        text = "${if (isProfit) "+" else ""}$${String.format(Locale.US, "%,.2f", report.netProfitUsdt)} (${if (isProfit) "+" else ""}${String.format(Locale.US, "%.1f", report.netProfitPercent)}%)",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Max Drawdown", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "-${String.format(Locale.US, "%.1f", report.maxDrawdownPercent)}%",
                        color = CryptoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun BasketCycleItemCard(cycle: BasketCycleResult) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val openDateStr = sdf.format(Date(cycle.openTime))
    val closeDateStr = sdf.format(Date(cycle.closeTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceHighlight),
        border = BorderStroke(0.5.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Giỏ #${cycle.cycleIndex} • ${cycle.coinsCount} coins",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+$${String.format(Locale.US, "%.2f", cycle.realizedProfitUsdt)} (+${String.format(Locale.US, "%.1f", cycle.basketRoiPercent)}%)",
                    color = CryptoGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$openDateStr ➔ $closeDateStr (${String.format(Locale.US, "%.1f", cycle.holdingDays)} ngày)",
                    color = TextMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "Vốn: $${String.format(Locale.US, "%,.0f", cycle.totalInvested)}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}