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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.model.BinanceApiConfig
import com.example.model.BinancePortfolio
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun PositionsScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    openPositions: List<TradePosition>,
    closedPositions: List<TradePosition>,
    apiConfig: BinanceApiConfig,
    binancePortfolio: BinancePortfolio,
    livePositionsTpSl: Map<String, Pair<Double?, Double?>>,
    onSelectCoin: (String) -> Unit,
    onPerformWithPinCheck: (() -> Unit) -> Unit,
    onEditTpSlTarget: (String, Boolean, Double?, Double?, Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val paperOpenPositions = openPositions.filter { !it.isLiveBinanceOrder }
    val paperClosedPositions = closedPositions.filter { !it.isLiveBinanceOrder }

    var selectedSubTab by remember { mutableStateOf(0) }
    var showEditTargetDialog by remember { mutableStateOf(false) }
    var showEditBalanceDialog by remember { mutableStateOf(false) }

    val simulatedBalance = uiState.settings.simulatedBalanceUsdt

    var totalInvested = 0.0
    var totalUnrealizedPnl = 0.0
    paperOpenPositions.forEach { pos ->
        val currentPrice = uiState.tickers.find { it.symbol == pos.symbol }?.lastPrice ?: pos.currentPrice
        val pnl = (currentPrice - pos.entryPrice) * pos.amount
        totalInvested += pos.investedUsdt
        totalUnrealizedPnl += pnl
    }

    val currentPortfolioRoi = if (simulatedBalance > 0.0) {
        (totalUnrealizedPnl / simulatedBalance) * 100.0
    } else 0.0

    val targetRoi = if (uiState.settings.portfolioTakeProfitPercent > 0.0) {
        uiState.settings.portfolioTakeProfitPercent
    } else 4.0

    val progress = if (targetRoi > 0.0 && currentPortfolioRoi > 0.0) {
        (currentPortfolioRoi / targetRoi).coerceIn(0.0, 1.0).toFloat()
    } else 0f
    val progressPercent = (progress * 100).toInt()
    val isProfit = totalUnrealizedPnl >= 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
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
                        text = "PAPER TRADING (MAINNET DATA)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Giả lập thời gian thực với giá nến Binance Mainnet",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { viewModel.refreshTickers() },
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Làm mới", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // THẺ SỐ DƯ VÍ ẢO - CÓ NÚT ĐỔI VỐN & SỬA TRỰC TIẾP
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.clickable { showEditBalanceDialog = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Số Dư Ví Ảo (Mô phỏng vốn)", color = TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "✎", color = CryptoGold, fontSize = 11.sp)
                    }
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", simulatedBalance)} USDT",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { showEditBalanceDialog = true },
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        border = BorderStroke(0.8.dp, CryptoGold)
                    ) {
                        Text(text = "Đổi vốn", color = CryptoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.settings.autoTradeEnabled,
                        onCheckedChange = { checked ->
                            onPerformWithPinCheck {
                                viewModel.toggleAutoTrade(checked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CryptoGold,
                            checkedTrackColor = CryptoGold.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // THẺ CHỐT LỜI TOÀN DANH MỤC ẢO
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chốt Lời Toàn Danh Mục Ảo",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showEditTargetDialog = true },
                        color = CryptoGold.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, CryptoGold.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Mục tiêu: ", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "+${String.format(Locale.US, "%.1f", targetRoi)}%",
                                color = CryptoGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "✎", color = CryptoGold, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "ROI Danh Mục: ", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${if (currentPortfolioRoi >= 0) "+" else ""}${String.format(Locale.US, "%.2f", currentPortfolioRoi)}%",
                            color = if (isProfit) CryptoGreen else CryptoRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = " (${if (isProfit) "+$" else "-$"}${String.format(Locale.US, "%.2f", Math.abs(totalUnrealizedPnl))})",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Tiến độ: $progressPercent%",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = CryptoGold,
                    trackColor = DarkSurfaceHighlight
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedSubTab = 0 },
                    color = if (selectedSubTab == 0) CryptoGold else DarkSurfaceHighlight
                ) {
                    Text(
                        text = "Vị thế mở (${paperOpenPositions.size})",
                        color = if (selectedSubTab == 0) Color.Black else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedSubTab = 1 },
                    color = if (selectedSubTab == 1) CryptoGold else DarkSurfaceHighlight
                ) {
                    Text(
                        text = "Lịch sử đóng (${paperClosedPositions.size})",
                        color = if (selectedSubTab == 1) Color.Black else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (selectedSubTab == 0 && paperOpenPositions.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        onPerformWithPinCheck {
                            viewModel.closeAllPositions()
                        }
                    },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, CryptoRed)
                ) {
                    Text(text = "Đóng tất cả", color = CryptoRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (selectedSubTab == 0) {
                if (paperOpenPositions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Chưa có vị thế Paper Trade nào đang mở", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(paperOpenPositions, key = { it.id }) { pos ->
                        val currentPrice = uiState.tickers.find { it.symbol == pos.symbol }?.lastPrice ?: pos.currentPrice
                        val pnl = (currentPrice - pos.entryPrice) * pos.amount
                        val isItemProfit = pnl >= 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCoin(pos.symbol) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                            border = BorderStroke(1.dp, if (isItemProfit) CryptoGreen.copy(alpha = 0.3f) else CryptoRed.copy(alpha = 0.3f))
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
                                                text = "LONG",
                                                color = CryptoGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = pos.symbol, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = "Vốn: $${String.format(Locale.US, "%.1f", pos.investedUsdt)}", color = TextSecondary, fontSize = 10.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onPerformWithPinCheck {
                                                viewModel.manualClosePosition(pos.id)
                                            }
                                        },
                                        modifier = Modifier.height(26.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        border = BorderStroke(1.dp, CryptoRed)
                                    ) {
                                        Text(text = "Đóng", color = CryptoRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(text = "Entry / Mark (Mainnet)", color = TextSecondary, fontSize = 10.sp)
                                        Text(
                                            text = "$${CryptoRepository.formatPrice(pos.entryPrice)} / $${CryptoRepository.formatPrice(currentPrice)}",
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "PnL Ảo", color = TextSecondary, fontSize = 10.sp)
                                        Text(
                                            text = "${if (isItemProfit) "+" else ""}$${String.format(Locale.US, "%.2f", pnl)} (${pos.roiFormatted})",
                                            color = if (isItemProfit) CryptoGreen else CryptoRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (paperClosedPositions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text(text = "Chưa có lịch sử vị thế ảo đóng", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    items(paperClosedPositions, key = { it.id }) { pos ->
                        val pnl = pos.pnlUsdt ?: 0.0
                        val isClosedProfit = pnl >= 0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = pos.symbol, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = pos.closeReason ?: "ĐÃ ĐÓNG",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    text = "${if (isClosedProfit) "+" else ""}$${String.format(Locale.US, "%.2f", pnl)} USDT",
                                    color = if (isClosedProfit) CryptoGreen else CryptoRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditBalanceDialog) {
        EditSimulatedBalanceDialog(
            currentBalance = simulatedBalance,
            onDismiss = { showEditBalanceDialog = false },
            onConfirm = { newBalance ->
                viewModel.updateSimulatedBalance(newBalance)
                showEditBalanceDialog = false
            }
        )
    }

    if (showEditTargetDialog) {
        EditTargetRoiDialog(
            currentTarget = targetRoi,
            onDismiss = { showEditTargetDialog = false },
            onConfirm = { newTarget ->
                viewModel.updatePortfolioTakeProfitPercent(newTarget)
                showEditTargetDialog = false
            }
        )
    }
}

/**
 * Hộp thoại chỉnh sửa số vốn ví ảo (Simulated Balance)
 */
@Composable
fun EditSimulatedBalanceDialog(
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf(String.format(Locale.US, "%.0f", currentBalance)) }
    val presets = listOf(100.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Cài đặt Vốn Ví Ảo (USDT)", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nhập số vốn bạn dự định nạp để chạy mô phỏng. Hệ thống sẽ reset lại các lệnh cũ để tính ROI chuẩn xác.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Text(text = "Chọn nhanh các mức vốn:", color = TextMuted, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(3).forEach { p ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { textValue = p.toInt().toString() },
                            color = if (textValue.toDoubleOrNull() == p) CryptoGold else DarkSurfaceHighlight
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = "$${p.toInt()}",
                                    color = if (textValue.toDoubleOrNull() == p) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.drop(3).forEach { p ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { textValue = p.toInt().toString() },
                            color = if (textValue.toDoubleOrNull() == p) CryptoGold else DarkSurfaceHighlight
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = "$${p.toInt()}",
                                    color = if (textValue.toDoubleOrNull() == p) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Số vốn tùy chỉnh (USDT)") },
                    placeholder = { Text("Ví dụ: 300 hoặc 1500") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textValue.replace(",", ".").toDoubleOrNull()
                    if (parsed != null && parsed >= 10.0) {
                        onConfirm(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
            ) {
                Text(text = "Áp dụng vốn", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}

@Composable
fun EditTargetRoiDialog(
    currentTarget: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf(String.format(Locale.US, "%.1f", currentTarget)) }
    val presets = listOf(2.0, 3.0, 4.0, 5.0, 8.0, 10.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Cài đặt Mục tiêu Chốt lời Ảo", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hệ thống sẽ tự động đóng toàn bộ vị thế ảo khi ROI danh mục chạm mốc này.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Text(text = "Chọn nhanh:", color = TextMuted, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.take(3).forEach { p ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { textValue = p.toString() },
                            color = if (textValue.toDoubleOrNull() == p) CryptoGold else DarkSurfaceHighlight
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = "+${p.toInt()}%",
                                    color = if (textValue.toDoubleOrNull() == p) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.drop(3).forEach { p ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { textValue = p.toString() },
                            color = if (textValue.toDoubleOrNull() == p) CryptoGold else DarkSurfaceHighlight
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = "+${p.toInt()}%",
                                    color = if (textValue.toDoubleOrNull() == p) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Nhập mục tiêu tùy chỉnh (%)") },
                    placeholder = { Text("Ví dụ: 4.5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = textValue.replace(",", ".").toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        onConfirm(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
            ) {
                Text(text = "Lưu mục tiêu", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}