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
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

    // Dialog state for Paper TP/SL
    var paperTpSlTarget by remember { mutableStateOf<TradePosition?>(null) }
    // Dialog state for Live Binance TP/SL
    var liveTpSlTarget by remember { mutableStateOf<BinanceLivePosition?>(null) }

    val livePositions = binancePortfolio?.positions ?: emptyList()
    val totalPaperPnl = openPositions.sumOf { it.unrealizedPnlUsdt }
    val totalLivePnl = livePositions.sumOf { it.unrealizedProfit }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    // Confirmation dialog for closing all positions
    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = CryptoRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đóng Tất Cả Vị Thế?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn đóng toàn bộ ${openPositions.size} vị thế Paper Trade${if (livePositions.isNotEmpty()) " và ${livePositions.size} vị thế Live Binance" else ""} không?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseAllDialog = false
                        onPerformWithPinCheck {
                            viewModel.closeAllPositions()
                            if (livePositions.isNotEmpty()) {
                                viewModel.closeAllLiveBinancePositions()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed)
                ) {
                    Text("Đóng hết ngay", color = Color.White, fontWeight = FontWeight.Bold)
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

    // TP/SL Edit Dialog for Paper Position
    paperTpSlTarget?.let { pos ->
        val isLong = pos.positionSide.uppercase() != "SHORT"
        TpSlDialog(
            symbol = pos.symbol,
            isLong = isLong,
            entryPrice = pos.entryPrice,
            markPrice = pos.currentPrice,
            leverage = pos.leverage,
            initialTpPrice = pos.takeProfitPrice,
            initialSlPrice = pos.stopLossPrice,
            onDismiss = { paperTpSlTarget = null },
            onConfirm = { tp, sl ->
                paperTpSlTarget = null
                onEditTpSlTarget(pos.symbol, isLong, tp, sl, pos.id)
            }
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
        // TOP SUMMARY CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SỐ DƯ PAPER TRADE", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", uiState.settings.simulatedBalanceUsdt)} USDT",
                            color = CryptoGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (openPositions.isNotEmpty() || livePositions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showCloseAllDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                            border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_close_all_positions")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đóng tất cả", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Unrealized PnL (Paper)", color = TextMuted, fontSize = 10.sp)
                        val pnlSign = if (totalPaperPnl >= 0) "+" else ""
                        Text(
                            text = "$pnlSign$${String.format(Locale.US, "%.2f", totalPaperPnl)}",
                            color = if (totalPaperPnl >= 0) CryptoGreen else CryptoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (apiConfig.isConnected) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ví Binance Live", color = TextMuted, fontSize = 10.sp)
                            val liveSign = if (totalLivePnl >= 0) "+" else ""
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", binancePortfolio?.totalWalletBalance ?: 0.0)} ($liveSign$${String.format(Locale.US, "%.2f", totalLivePnl)})",
                                color = if (totalLivePnl >= 0) CryptoGreen else CryptoRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Trạng thái", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "Mô phỏng (Testnet)",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // BẢNG CHI TIẾT UNREALIZED PNL & TỔNG VOLUME CỦA TỪNG GIỎ
                val isLiveActive = apiConfig.isConnected && livePositions.isNotEmpty()
                val priPositionsCount: Int
                val priPnl: Double
                val priMargin: Double
                val priRoi: Double

                val secPositionsCount: Int
                val secPnl: Double
                val secMargin: Double
                val secRoi: Double

                if (isLiveActive) {
                    val priList = livePositions.filter { it.basketSlot.uppercase() == "PRIMARY" || it.basketSlot.isEmpty() }
                    val secList = livePositions.filter { it.basketSlot.uppercase() == "SECONDARY" }
                    priPositionsCount = priList.size
                    priPnl = priList.sumOf { it.unrealizedProfit }
                    priMargin = priList.sumOf { it.initialMargin }
                    priRoi = if (priMargin > 0.0) (priPnl / priMargin) * 100.0 else 0.0

                    secPositionsCount = secList.size
                    secPnl = secList.sumOf { it.unrealizedProfit }
                    secMargin = secList.sumOf { it.initialMargin }
                    secRoi = if (secMargin > 0.0) (secPnl / secMargin) * 100.0 else 0.0
                } else {
                    val priList = openPositions.filter { it.basketSlot.uppercase() == "PRIMARY" || it.basketSlot.isEmpty() }
                    val secList = openPositions.filter { it.basketSlot.uppercase() == "SECONDARY" }
                    priPositionsCount = priList.size
                    priPnl = priList.sumOf { it.unrealizedPnlUsdt }
                    priMargin = priList.sumOf { it.investedUsdt }
                    priRoi = if (priMargin > 0.0) (priPnl / priMargin) * 100.0 else 0.0

                    secPositionsCount = secList.size
                    secPnl = secList.sumOf { it.unrealizedPnlUsdt }
                    secMargin = secList.sumOf { it.investedUsdt }
                    secRoi = if (secMargin > 0.0) (secPnl / secMargin) * 100.0 else 0.0
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // GIỎ CHÍNH
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceHighlight.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CryptoGold)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GIỎ CHÍNH", color = CryptoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val priPnlSign = if (priPnl >= 0) "+" else ""
                            Text(
                                text = "$priPnlSign$${String.format(Locale.US, "%.2f", priPnl)} ($priPnlSign${String.format(Locale.US, "%.2f", priRoi)}%)",
                                color = if (priPnl >= 0) CryptoGreen else CryptoRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$priPositionsCount vị thế • Vol: $${String.format(Locale.US, "%.1f", priMargin)} USDT",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // GIỎ PHỤ
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceHighlight.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GIỎ PHỤ", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val secPnlSign = if (secPnl >= 0) "+" else ""
                            Text(
                                text = "$secPnlSign$${String.format(Locale.US, "%.2f", secPnl)} ($secPnlSign${String.format(Locale.US, "%.2f", secRoi)}%)",
                                color = if (secPnl >= 0) CryptoGreen else CryptoRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$secPositionsCount vị thế • Vol: $${String.format(Locale.US, "%.1f", secMargin)} USDT",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }

        // TABS: Đang mở vs Lịch sử
        val openCount = openPositions.size + livePositions.size
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
                        text = "Vị Thế Mở ($openCount)",
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
                        text = "Lịch Sử (${closedPositions.size})",
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
                // TAB VỊ THẾ ĐANG MỞ
                if (openPositions.isEmpty() && livePositions.isEmpty()) {
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
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Hiện không có vị thế nào đang mở",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Chọn coin từ danh sách Thị Trường để mở lệnh Paper Trade hoặc quét tín hiệu tự động.",
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Vị thế Live Binance
                        if (livePositions.isNotEmpty()) {
                            item {
                                Text(
                                    text = "VỊ THẾ LIVE BINANCE (${livePositions.size})",
                                    color = CryptoGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(livePositions, key = { "live_${it.symbol}_${it.positionSide}" }) { livePos ->
                                LivePositionItem(
                                    position = livePos,
                                    savedTpSl = livePositionsTpSl[livePos.symbol],
                                    onSelectCoin = onSelectCoin,
                                    onEditTpSl = { liveTpSlTarget = livePos },
                                    onClosePosition = {
                                        onPerformWithPinCheck {
                                            viewModel.closeLiveBinancePosition(livePos)
                                        }
                                    }
                                )
                            }
                        }

                        // Vị thế Paper Trade
                        if (openPositions.isNotEmpty()) {
                            if (livePositions.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "VỊ THẾ PAPER TRADE (${openPositions.size})",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            items(openPositions, key = { "paper_${it.id}" }) { pos ->
                                PaperPositionItem(
                                    position = pos,
                                    onSelectCoin = onSelectCoin,
                                    onEditTpSl = { paperTpSlTarget = pos },
                                    onClosePosition = {
                                        onPerformWithPinCheck {
                                            viewModel.manualClosePosition(pos.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB LỊCH SỬ CHỐT LỆNH
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
                                text = "Các vị thế khi chốt lời/cắt lỗ hoặc đóng thủ công sẽ xuất hiện tại đây.",
                                color = TextMuted,
                                fontSize = 12.sp
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
private fun PaperPositionItem(
    position: TradePosition,
    onSelectCoin: (String) -> Unit,
    onEditTpSl: () -> Unit,
    onClosePosition: () -> Unit
) {
    val isLong = position.positionSide.uppercase() != "SHORT"
    val pnl = position.unrealizedPnlUsdt
    val roi = position.unrealizedPnlPercent
    val isProfit = pnl >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCoin(position.symbol) }
            .testTag("position_card_${position.symbol}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, if (isProfit) CryptoGreen.copy(alpha = 0.25f) else CryptoRed.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: Symbol, Side badge, Leverage, Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.symbol,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isLong) CryptoGreen.copy(alpha = 0.2f) else CryptoRed.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isLong) "LONG ${position.leverage}x" else "SHORT ${position.leverage}x",
                            color = if (isLong) CryptoGreen else CryptoRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DarkSurfaceHighlight
                    ) {
                        Text(
                            text = "Paper",
                            color = TextMuted,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    val isPrimaryPaper = position.basketSlot.uppercase() == "PRIMARY" || position.basketSlot.isEmpty()
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isPrimaryPaper) CryptoGold.copy(alpha = 0.18f) else Color(0xFF00E5FF).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, if (isPrimaryPaper) CryptoGold.copy(alpha = 0.5f) else Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isPrimaryPaper) "Giỏ Chính" else "Giỏ Phụ",
                            color = if (isPrimaryPaper) CryptoGold else Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // PnL display
                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isProfit) "+" else ""
                    Text(
                        text = "$sign$${String.format(Locale.US, "%.2f", pnl)}",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$sign${String.format(Locale.US, "%.2f", roi)}%",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details row: Entry price, Mark price, Margin / Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Giá vào (Entry)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.entryPrice)}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Giá hiện tại", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.currentPrice)}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Ký quỹ (Margin)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%.1f", position.investedUsdt)} USDT",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TP/SL Info and Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TP/SL Targets text
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (position.takeProfitPrice != null && position.takeProfitPrice > 0) {
                        Text("TP: $${CryptoRepository.formatPrice(position.takeProfitPrice)}", color = CryptoGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (position.stopLossPrice != null && position.stopLossPrice > 0) {
                        Text("SL: $${CryptoRepository.formatPrice(position.stopLossPrice)}", color = CryptoRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    if ((position.takeProfitPrice == null || position.takeProfitPrice <= 0) &&
                        (position.stopLossPrice == null || position.stopLossPrice <= 0)
                    ) {
                        Text("Chưa cài TP/SL", color = TextMuted, fontSize = 10.sp)
                    }
                }

                Row {
                    OutlinedButton(
                        onClick = onEditTpSl,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("TP/SL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onClosePosition,
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoRed.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Đóng", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.35f))
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isLong) CryptoGreen.copy(alpha = 0.2f) else CryptoRed.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isLong) "LONG ${position.leverage}x" else "SHORT ${position.leverage}x",
                            color = if (isLong) CryptoGreen else CryptoRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CryptoGold.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "LIVE",
                            color = CryptoGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    val isPrimaryLive = position.basketSlot.uppercase() == "PRIMARY" || position.basketSlot.isEmpty()
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isPrimaryLive) CryptoGold.copy(alpha = 0.18f) else Color(0xFF00E5FF).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, if (isPrimaryLive) CryptoGold.copy(alpha = 0.5f) else Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isPrimaryLive) "Giỏ Chính" else "Giỏ Phụ",
                            color = if (isPrimaryLive) CryptoGold else Color(0xFF00E5FF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val sign = if (isProfit) "+" else ""
                    Text(
                        text = "$sign$${String.format(Locale.US, "%.2f", pnl)}",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$sign${String.format(Locale.US, "%.2f", roi)}%",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Giá vào (Entry)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.entryPrice)}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Giá Mark", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.markPrice)}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Ký quỹ (Margin)", color = TextMuted, fontSize = 10.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%.1f", position.initialMargin)} USDT",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tp = savedTpSl?.first ?: position.takeProfitPrice
                    val sl = savedTpSl?.second ?: position.stopLossPrice
                    if (tp != null && tp > 0) {
                        Text("TP: $${CryptoRepository.formatPrice(tp)}", color = CryptoGreen, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (sl != null && sl > 0) {
                        Text("SL: $${CryptoRepository.formatPrice(sl)}", color = CryptoRed, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    if ((tp == null || tp <= 0) && (sl == null || sl <= 0)) {
                        Text("Chưa cài TP/SL", color = TextMuted, fontSize = 10.sp)
                    }
                }

                Row {
                    OutlinedButton(
                        onClick = onEditTpSl,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("TP/SL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onClosePosition,
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoRed.copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Đóng", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    val isLong = position.positionSide.uppercase() != "SHORT"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCoin(position.symbol) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(0.5.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
                    Spacer(modifier = Modifier.width(4.dp))
                    val isPrimaryClosed = position.basketSlot.uppercase() == "PRIMARY" || position.basketSlot.isEmpty()
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = if (isPrimaryClosed) CryptoGold.copy(alpha = 0.15f) else Color(0xFF00E5FF).copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, if (isPrimaryClosed) CryptoGold.copy(alpha = 0.4f) else Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isPrimaryClosed) "Giỏ Chính" else "Giỏ Phụ",
                            color = if (isPrimaryClosed) CryptoGold else Color(0xFF00E5FF),
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

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$${CryptoRepository.formatPrice(position.entryPrice)} ➔ $${CryptoRepository.formatPrice(position.exitPrice ?: position.currentPrice)}",
                    color = TextSecondary,
                    fontSize = 10.sp,
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
