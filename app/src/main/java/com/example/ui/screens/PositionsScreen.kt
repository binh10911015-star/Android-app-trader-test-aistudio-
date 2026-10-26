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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceApiConfig
import com.example.model.BinanceLivePosition
import com.example.model.BinancePortfolio
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.TpSlDialog
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PositionsScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    openPositions: List<TradePosition>,
    closedPositions: List<TradePosition>,
    apiConfig: BinanceApiConfig,
    binancePortfolio: BinancePortfolio?,
    livePositionsTpSl: Map<String, Pair<Double?, Double?>>,
    onSelectCoin: (String) -> Unit,
    onPerformWithPinCheck: (() -> Unit) -> Unit = { it() },
    onEditTpSlTarget: (sym: String, isLong: Boolean, tp: Double?, sl: Double?, id: Long?) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCloseAllDialog by remember { mutableStateOf(false) }

    // Dialog state for Live Binance TP/SL
    var liveTpSlTarget by remember { mutableStateOf<BinanceLivePosition?>(null) }

    val livePositions = binancePortfolio?.positions ?: emptyList()
    val totalLivePnl = livePositions.sumOf { it.unrealizedProfit }
    val totalWalletBalance = binancePortfolio?.totalWalletBalance ?: apiConfig.totalWalletBalance
    val availableBalance = binancePortfolio?.availableBalance ?: apiConfig.availableBalance
    val totalMarginInPositions = livePositions.sumOf { it.initialMargin }
    val maxPortfolioCap = totalWalletBalance * uiState.settings.maxPortfolioAllocation
    val portfolioExposureRatio = if (maxPortfolioCap > 0.0) (totalMarginInPositions / maxPortfolioCap).coerceIn(0.0, 1.0) else 0.0

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    // Confirmation dialog for closing all positions on Binance Futures Mainnet
    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = CryptoRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đóng Tất Cả Vị Thế Live?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn đóng toàn bộ ${livePositions.size} vị thế đang mở trên sàn Binance Futures Mainnet bằng lệnh thị trường ngay lập tức không?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseAllDialog = false
                        viewModel.closeAllLiveBinancePositions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed)
                ) {
                    Text("Đóng hết trên sàn ngay", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseAllDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // TP/SL Edit Dialog for Live Position
    liveTpSlTarget?.let { pos ->
        val savedTpSl = livePositionsTpSl[pos.symbol]
        TpSlDialog(
            symbol = pos.symbol,
            isLong = pos.isLong,
            entryPrice = pos.entryPrice,
            markPrice = pos.markPrice,
            leverage = pos.leverage,
            initialTpPrice = savedTpSl?.first ?: pos.takeProfitPrice,
            initialSlPrice = savedTpSl?.second ?: pos.stopLossPrice,
            onDismiss = { liveTpSlTarget = null },
            onConfirm = { tp, sl ->
                liveTpSlTarget = null
                onEditTpSlTarget(pos.symbol, pos.isLong, tp, sl, null)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // TOP SUMMARY CARD: 100% BINANCE FUTURES MAINNET TIỀN THẬT
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, if (apiConfig.isConnected) CryptoGold.copy(alpha = 0.5f) else DarkBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Row 1: Header + Badges + Close all button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (apiConfig.isConnected) CryptoGreen else CryptoRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VÍ BINANCE FUTURES MAINNET",
                            color = CryptoGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CryptoGreen.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, CryptoGreen.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "TIỀN THẬT 1X CROSS",
                                color = CryptoGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.refreshBinancePortfolio() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới ví", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }

                        if (livePositions.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            OutlinedButton(
                                onClick = { showCloseAllDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                                border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("btn_close_all_positions")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Đóng hết (${livePositions.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (apiConfig.isConnected) {
                    // Balance info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("Tổng Số Dư Ví (Equity)", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", totalWalletBalance)} USDT",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Lãi/Lỗ Chưa Chốt (PnL)", color = TextMuted, fontSize = 10.sp)
                            val liveSign = if (totalLivePnl >= 0) "+" else ""
                            val totalRoi = if (totalMarginInPositions > 0.0) (totalLivePnl / totalMarginInPositions) * 100.0 else 0.0
                            Text(
                                text = "$liveSign$${String.format(Locale.US, "%.2f", totalLivePnl)} ($liveSign${String.format(Locale.US, "%.2f", totalRoi)}%)",
                                color = if (totalLivePnl >= 0) CryptoGreen else CryptoRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Row: Available Balance + Allocated Margin + Portfolio Cap 60%
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Khả dụng (Available)", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", availableBalance)} USDT",
                                color = CryptoGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Đang giải ngân", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%.1f", totalMarginInPositions)} USDT (${livePositions.size} lệnh)",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Trần Danh Mục (60%)", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", maxPortfolioCap)} USDT",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    // Exposure progress bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tỷ lệ giải ngân / Trần 60%", color = TextMuted, fontSize = 9.sp)
                            Text("${(portfolioExposureRatio * 100).toInt()}%", color = if (portfolioExposureRatio > 0.9) CryptoRed else CryptoGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        LinearProgressIndicator(
                            progress = { portfolioExposureRatio.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (portfolioExposureRatio > 0.9) CryptoRed else CryptoGold,
                            trackColor = DarkSurfaceHighlight
                        )
                    }
                } else {
                    // Not connected message
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = CryptoRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Chưa kết nối API Binance Futures Mainnet",
                                color = CryptoRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Vui lòng chuyển sang tab 'Kết Nối API' để nạp API Key / Secret Key và bắt đầu giao dịch tiền thật.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // TABS: VỊ THẾ MAINNET VS LỊCH SỬ LỆNH
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = CryptoGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CryptoGold,
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Vị Thế Mainnet (${livePositions.size})",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 0) CryptoGold else TextSecondary
                    )
                },
                modifier = Modifier.testTag("tab_open_positions")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Lịch Sử Lệnh (${closedPositions.size})",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 1) CryptoGold else TextSecondary
                    )
                },
                modifier = Modifier.testTag("tab_closed_positions")
            )
        }

        // CONTENT
        when (selectedTab) {
            0 -> {
                // TAB VỊ THẾ MAINNET ĐANG MỞ
                if (!apiConfig.isConnected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Chưa kết nối Binance Futures Mainnet",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Để nạp dữ liệu vị thế thật và quản lý lệnh bằng tiền thật, hãy nhập API Key và Secret Key tại tab 'Kết Nối API'.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (livePositions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Không có vị thế nào đang mở trên Binance Futures",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hệ thống sẽ tự động quét và giải ngân theo 2 phiên (07:00 AM & 19:00 PM UTC+7), hoặc bạn có thể mở lệnh trực tiếp từ danh sách Tín Hiệu Thị Trường.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.scanAllFuturesCoins() },
                                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Quét Tín Hiệu Ngay", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "DANH SÁCH VỊ THẾ LIVE (${livePositions.size})",
                                    color = CryptoGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Đòn bẩy: Long Cross 1x",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        items(livePositions, key = { "live_${it.symbol}_${it.positionSide}" }) { livePos ->
                            LivePositionItem(
                                position = livePos,
                                savedTpSl = livePositionsTpSl[livePos.symbol],
                                onSelectCoin = onSelectCoin,
                                onEditTpSl = { liveTpSlTarget = livePos },
                                onClosePosition = {
                                    viewModel.closeLiveBinancePosition(livePos)
                                }
                            )
                        }
                    }
                }
            }

            1 -> {
                // TAB LỊCH SỬ LỆNH
                if (closedPositions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Chưa có lịch sử giao dịch",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Các vị thế khi chốt lời Trailing ATR, cắt Timeout 7 ngày hoặc đóng trên sàn Binance sẽ được ghi nhận tại đây.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(closedPositions, key = { "closed_${it.id}" }) { pos ->
                            ClosedPositionItem(
                                position = pos,
                                dateFormat = dateFormat,
                                onSelectCoin = onSelectCoin
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePositionItem(
    position: BinanceLivePosition,
    savedTpSl: Pair<Double?, Double?>?,
    onSelectCoin: (String) -> Unit,
    onEditTpSl: () -> Unit,
    onClosePosition: () -> Unit
) {
    val isLong = position.isLong
    val pnl = position.unrealizedProfit
    val roi = position.roiPercent
    val isProfit = pnl >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCoin(position.symbol) }
            .testTag("live_position_${position.symbol}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, if (isProfit) CryptoGreen.copy(alpha = 0.35f) else CryptoRed.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol, Badges, PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.symbol,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isLong) CryptoGreen.copy(alpha = 0.2f) else CryptoRed.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, if (isLong) CryptoGreen.copy(alpha = 0.6f) else CryptoRed.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (isLong) "LONG ${position.leverage}X" else "SHORT ${position.leverage}X",
                            color = if (isLong) CryptoGreen else CryptoRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CryptoGold.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, CryptoGold.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "MAINNET",
                            color = CryptoGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // PnL & ROI
                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isProfit) "+" else ""
                    Text(
                        text = "$sign$${String.format(Locale.US, "%.2f", pnl)} USDT",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$sign${String.format(Locale.US, "%.2f", roi)}%",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pricing & Margin Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Giá vào (Entry)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.entryPrice)}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Giá Mark (Sàn)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.markPrice)}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Ký Quỹ (Margin)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", position.initialMargin)} USDT",
                        color = CryptoGold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Liquidation & Quantity Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Khối lượng: ", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "${String.format(Locale.US, "%.4f", position.absAmount)} ${position.symbol.removeSuffix("USDT")}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (position.liquidationPrice > 0.0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Giá Thanh Lý: ", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "$${CryptoRepository.formatPrice(position.liquidationPrice)}",
                            color = CryptoRed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text("Cross 1x: Thanh lý an toàn", color = CryptoGreen, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Exit Engine Status & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit rules status (ATR Trailing + 7D timeout)
                Column(modifier = Modifier.weight(1f)) {
                    val tp = savedTpSl?.first ?: position.takeProfitPrice
                    val sl = savedTpSl?.second ?: position.stopLossPrice

                    if (tp != null && tp > 0) {
                        Text("TP: $${CryptoRepository.formatPrice(tp)}", color = CryptoGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("ATR Trailing: Kích hoạt khi ROI ≥ 1.5%", color = CryptoGold, fontSize = 9.sp)
                    }

                    if (sl != null && sl > 0) {
                        Text("SL: $${CryptoRepository.formatPrice(sl)}", color = CryptoRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    } else {
                        Text("Khóa lãi: +0.5% | Timeout: 7 ngày", color = TextMuted, fontSize = 9.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onEditTpSl,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("TP/SL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onClosePosition,
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoRed),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Đóng Vị Thế", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosedPositionItem(
    position: TradePosition,
    dateFormat: SimpleDateFormat,
    onSelectCoin: (String) -> Unit
) {
    val pnl = position.pnlUsdt ?: 0.0
    val roi = position.pnlPercent ?: 0.0
    val isProfit = pnl >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCoin(position.symbol) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(0.5.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.symbol,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = CryptoGreen.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, CryptoGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "LONG 1X",
                            color = CryptoGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    position.closeReason?.let { reason ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = DarkSurfaceHighlight
                        ) {
                            Text(
                                text = reason,
                                color = TextMuted,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                val sign = if (isProfit) "+" else ""
                Text(
                    text = "$sign$${String.format(Locale.US, "%.2f", pnl)} ($sign${String.format(Locale.US, "%.2f", roi)}%)",
                    color = if (isProfit) CryptoGreen else CryptoRed,
                    fontSize = 12.sp,
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
                    text = "$${CryptoRepository.formatPrice(position.entryPrice)} ➔ $${CryptoRepository.formatPrice(position.exitPrice ?: position.currentPrice)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                position.exitTime?.let { time ->
                    Text(
                        text = dateFormat.format(Date(time)),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
