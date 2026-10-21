package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.TradePosition
import com.example.ui.MainViewModel
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    closedPositions: List<TradePosition>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDays by remember { mutableStateOf(180) }

    // Các biến trạng thái lưu giá trị tùy chỉnh thủ công
    var capitalInput by remember { mutableStateOf("10000") }
    var perTradePctInput by remember { mutableStateOf("1.5") }
    var maxAllocationPctInput by remember { mutableStateOf("60") }
    var targetRoiPctInput by remember { mutableStateOf("1.0") }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // TIÊU ĐỀ
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CryptoGold))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BACKTEST BASKET D1 MOMENTUM",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Tùy chỉnh thông số quản trị vốn • Nến Binance Mainnet",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // BẢNG ĐIỀU CHỈNH THÔNG SỐ QUẢN TRỊ VỐN THỦ CÔNG
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Cấu Hình Quản Trị Vốn & Mở Giỏ Lệnh", color = CryptoGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // HÀNG 1: VỐN BAN ĐẦU & KHUNG THỜI GIAN
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = capitalInput,
                            onValueChange = { capitalInput = it },
                            label = { Text("Vốn gốc (USDT)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedLabelColor = CryptoGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Khung ngày test", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(90, 180).forEach { days ->
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedDays = days },
                                        color = if (selectedDays == days) CryptoGold else DarkSurfaceHighlight
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$days ngày",
                                                color = if (selectedDays == days) Color.Black else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // HÀNG 2: 3 THÔNG SỐ VỐN/LỆNH - TRẦN GIẢI NGÂN - CHỐT LỜI
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = perTradePctInput,
                            onValueChange = { perTradePctInput = it },
                            label = { Text("Vốn/lệnh (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedLabelColor = CryptoGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = maxAllocationPctInput,
                            onValueChange = { maxAllocationPctInput = it },
                            label = { Text("Trần vốn (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedLabelColor = CryptoGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        OutlinedTextField(
                            value = targetRoiPctInput,
                            onValueChange = { targetRoiPctInput = it },
                            label = { Text("Chốt giỏ (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedLabelColor = CryptoGold,
                                unfocusedLabelColor = TextMuted
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // NÚT BẮT ĐẦU CHẠY BACKTEST THEO CONFIG TÙY CHỌN
                    Button(
                        onClick = {
                            val cap = capitalInput.replace(",", ".").toDoubleOrNull() ?: 10000.0
                            val perTrade = (perTradePctInput.replace(",", ".").toDoubleOrNull() ?: 1.5) / 100.0
                            val maxAlloc = (maxAllocationPctInput.replace(",", ".").toDoubleOrNull() ?: 60.0) / 100.0
                            val targetRoi = (targetRoiPctInput.replace(",", ".").toDoubleOrNull() ?: 1.0) / 100.0

                            val customConfig = BacktestConfig(
                                initialCapital = cap,
                                perTradePct = perTrade,
                                maxPortfolioAllocation = maxAlloc,
                                targetPortfolioRoi = targetRoi
                            )
                            viewModel.runBasketStrategyBacktest(days = selectedDays, config = customConfig)
                        },
                        enabled = !uiState.isBasketBacktesting,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoGold),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isBasketBacktesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Đang quét nến Mainnet...", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Chạy Backtest Giỏ Lệnh ($selectedDays ngày)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // BÁO CÁO KẾT QUẢ ĐẦU RA (OUTPUT METRICS)
        uiState.basketReport?.let { report ->
            item {
                if (report.errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = BorderStroke(1.dp, CryptoRed)
                    ) {
                        Text(
                            text = report.errorMessage,
                            color = CryptoRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = BorderStroke(1.dp, CryptoGold)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BÁO CÁO HIỆU SUẤT CHIẾN LƯỢC",
                                    color = CryptoGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Vốn gốc: $${String.format(Locale.US, "%,.0f", report.initialCapital)}",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Tổng Chu Kỳ (Cycles)", color = TextMuted, fontSize = 10.sp)
                                    Text("${report.totalCycles} giỏ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Winrate Giỏ", color = TextMuted, fontSize = 10.sp)
                                    Text("${report.winratePercent}%", color = CryptoGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Thời gian giữ TB", color = TextMuted, fontSize = 10.sp)
                                    Text("${String.format(Locale.US, "%.1f", report.averageHoldingDays)} ngày", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = DarkBorder)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Lợi Nhuận Ròng (Net Profit)", color = TextMuted, fontSize = 10.sp)
                                    Text(
                                        text = "+$${String.format(Locale.US, "%.2f", report.netProfitUsdt)} (+${String.format(Locale.US, "%.2f", report.netProfitPercent)}%)",
                                        color = CryptoGreen,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Max Drawdown", color = TextMuted, fontSize = 10.sp)
                                    Text(
                                        text = "-${String.format(Locale.US, "%.2f", report.maxDrawdownPercent)}%",
                                        color = if (report.maxDrawdownPercent > 10.0) CryptoRed else CryptoGold,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Số dư ví kết thúc: $${String.format(Locale.US, "%.2f", report.finalEquity)} USDT",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // LỊCH SỬ TỪNG GIỎ ĐÃ CHỐT
            if (report.cycles.isNotEmpty()) {
                item {
                    Text(
                        text = "LỊCH SỬ ${report.cycles.size} CHU KỲ GIỎ ĐÃ CHỐT THÀNH CÔNG",
                        color = CryptoGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                items(report.cycles, key = { it.cycleIndex }) { cycle ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CryptoGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "GIỎ #${cycle.cycleIndex}",
                                            color = CryptoGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${cycle.coinsCount} coins",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "+$${String.format(Locale.US, "%.2f", cycle.realizedProfitUsdt)} (+${String.format(Locale.US, "%.1f", cycle.basketRoiPercent)}%)",
                                    color = CryptoGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${dateFormat.format(Date(cycle.openTime))} ➔ ${dateFormat.format(Date(cycle.closeTime))}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "Giữ: ${String.format(Locale.US, "%.1f", cycle.holdingDays)} ngày",
                                    color = CryptoGold,
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Danh sách: " + cycle.symbolsList.take(8).joinToString(", ") { it.removeSuffix("USDT") } + (if (cycle.symbolsList.size > 8) "..." else ""),
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}