package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.TpSlDialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceAssetBalance
import com.example.model.BinanceLivePosition
import com.example.model.BinanceOpenOrderItem
import com.example.model.BinanceOrderVerificationDetail
import com.example.model.RoiSortOrder
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
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

data class TpSlTarget(
    val symbol: String,
    val isLong: Boolean,
    val entryPrice: Double,
    val markPrice: Double,
    val leverage: Int,
    val tpPrice: Double? = null,
    val slPrice: Double? = null,
    val positionId: Long? = null
)

@Composable
fun PositionsScreen(
    viewModel: MainViewModel,
    openPositions: List<TradePosition>,
    closedPositions: List<TradePosition>,
    onSelectCoin: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val binancePortfolio by viewModel.binancePortfolio.collectAsState()
    val isRefreshingPortfolio by viewModel.isRefreshingPortfolio.collectAsState()
    val orderVerificationDetail by viewModel.orderVerificationDetail.collectAsState()
    val isVerifyingOrder by viewModel.isVerifyingOrder.collectAsState()

    val sortOrder = uiState.roiSortOrder

    // Phân tách 2 nhóm vị thế độc lập
    val paperOpenPositions = remember(openPositions, sortOrder) {
        val list = openPositions.filter { !it.isLiveBinanceOrder }
        when (sortOrder) {
            RoiSortOrder.DESCENDING -> list.sortedByDescending { it.unrealizedPnlPercent }
            RoiSortOrder.ASCENDING -> list.sortedBy { it.unrealizedPnlPercent }
            RoiSortOrder.DEFAULT -> list.sortedByDescending { it.entryTime }
        }
    }
    val paperClosedPositions = remember(closedPositions) {
        closedPositions.filter { !it.isLiveBinanceOrder }
    }

    val binanceExecutedOpenPositions = remember(openPositions, sortOrder) {
        val list = openPositions.filter { it.isLiveBinanceOrder }
        when (sortOrder) {
            RoiSortOrder.DESCENDING -> list.sortedByDescending { it.unrealizedPnlPercent }
            RoiSortOrder.ASCENDING -> list.sortedBy { it.unrealizedPnlPercent }
            RoiSortOrder.DEFAULT -> list.sortedByDescending { it.entryTime }
        }
    }
    val binanceClosedPositions = remember(closedPositions) {
        closedPositions.filter { it.isLiveBinanceOrder }
    }

    val livePositionsTpSl by viewModel.livePositionsTpSl.collectAsState()

    // Sắp xếp tự động danh sách vị thế Binance Live giảm dần theo % ROI
    val liveSortedPositions = remember(binancePortfolio.positions, sortOrder) {
        when (sortOrder) {
            RoiSortOrder.ASCENDING -> binancePortfolio.positions.sortedBy { it.roiPercent }
            else -> binancePortfolio.positions.sortedByDescending { it.roiPercent }
        }
    }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinActionTitle by remember { mutableStateOf("Xác nhận thao tác") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val performWithPinCheck: (String, () -> Unit) -> Unit = { title, action ->
        pinActionTitle = title
        pendingAction = action
        showPinDialog = true
    }

    var editingTpSlTarget by remember { mutableStateOf<TpSlTarget?>(null) }

    // 2 Tab chính phân biệt rõ ràng: 0 = Paper Trading, 1 = Binance Thực Tế
    var primaryTab by remember { mutableStateOf(if (apiConfig.isConnected) 1 else 0) }
    var paperSubTab by remember { mutableStateOf(0) }
    var binanceSubTab by remember { mutableStateOf(0) }

    // Modal PIN 4 số bảo mật
    if (showPinDialog) {
        PinVerificationDialog(
            actionTitle = pinActionTitle,
            onVerify = { pin -> viewModel.authorizePin(pin) },
            onSuccess = {
                pendingAction?.invoke()
                pendingAction = null
            },
            onDismiss = {
                showPinDialog = false
                pendingAction = null
            }
        )
    }

    // Modal Cài Đặt TP/SL
    if (editingTpSlTarget != null) {
        val target = editingTpSlTarget!!
        TpSlDialog(
            symbol = target.symbol,
            isLong = target.isLong,
            entryPrice = target.entryPrice,
            markPrice = target.markPrice,
            leverage = target.leverage,
            initialTpPrice = target.tpPrice,
            initialSlPrice = target.slPrice,
            onDismiss = { editingTpSlTarget = null },
            onConfirm = { tp, sl ->
                editingTpSlTarget = null
                performWithPinCheck("Cài đặt TP/SL cho ${target.symbol}") {
                    viewModel.setPositionTpSl(
                        symbol = target.symbol,
                        isLong = target.isLong,
                        tpPrice = tp,
                        slPrice = sl,
                        positionId = target.positionId
                    )
                }
            }
        )
    }

    // Order Verification Dialog
    if (isVerifyingOrder || orderVerificationDetail != null) {
        OrderVerificationModalDialog(
            detail = orderVerificationDetail,
            isLoading = isVerifyingOrder,
            onDismiss = { viewModel.dismissOrderVerificationDetail() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Quản Lý Vị Thế & Danh Mục",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (apiConfig.isConnected) "Đã kết nối Binance Futures API (${if (apiConfig.isTestnet) "Testnet Demo" else "Mainnet"})" else "Chưa kết nối API | Chế độ Paper Trading",
                    color = if (apiConfig.isConnected) CryptoGreen else TextSecondary,
                    fontSize = 11.sp
                )
            }

            if (apiConfig.isConnected && primaryTab == 1) {
                IconButton(
                    onClick = { viewModel.refreshBinancePortfolio() },
                    enabled = !isRefreshingPortfolio
                ) {
                    if (isRefreshingPortfolio) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CryptoGold,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Làm mới danh mục",
                            tint = CryptoGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        val allActivePositions = remember(openPositions) { openPositions.filter { it.status == com.example.model.TradePosition.STATUS_OPEN } }
        val totalInvested = remember(allActivePositions) { allActivePositions.sumOf { it.investedUsdt } }
        val totalUnrealizedPnl = remember(allActivePositions) { allActivePositions.sumOf { it.unrealizedPnlUsdt } }
        val portfolioRoi = remember(totalInvested, totalUnrealizedPnl) {
            if (totalInvested > 0.0) (totalUnrealizedPnl / totalInvested) * 100.0 else 0.0
        }
        val targetTpPercent = uiState.settings.portfolioTakeProfitPercent
        val progressFraction = remember(portfolioRoi, targetTpPercent) {
            if (targetTpPercent > 0.0) (portfolioRoi / targetTpPercent).toFloat().coerceIn(0f, 1f) else 0f
        }

        // Portfolio Take-Profit (Chốt Lời Toàn Danh Mục) Banner Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CryptoGold.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = CryptoGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Chốt Lời Toàn Danh Mục",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CryptoGold.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "TỰ ĐỘNG ĐÓNG ALL",
                                        color = CryptoGold,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Lưu ý: Chốt lời trên toàn danh mục (không chốt lời từng vị thế)",
                                color = TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Button Đóng Tất Cả Vị Thế
                    if (allActivePositions.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CryptoRed.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.6f)),
                            modifier = Modifier.clickable {
                                performWithPinCheck("Đóng toàn bộ ${allActivePositions.size} vị thế") {
                                    viewModel.closeAllPositions()
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = CryptoRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Đóng All (${allActivePositions.size})",
                                    color = CryptoRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stats Row: Total ROI vs Target TP %
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ROI Danh Mục: ",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        val isPositive = portfolioRoi >= 0.0
                        Text(
                            text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.2f", portfolioRoi)}%",
                            color = if (isPositive) CryptoGreen else CryptoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = " (${if (totalUnrealizedPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", totalUnrealizedPnl)})",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Mục tiêu: ",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "+${String.format(Locale.US, "%.1f", targetTpPercent)}%",
                            color = CryptoGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Linear Progress Indicator
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (progressFraction >= 1f) CryptoGreen else CryptoGold,
                    trackColor = DarkSurfaceHighlight
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tiến độ: ${(progressFraction * 100).toInt()}%",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "${allActivePositions.size} vị thế đang mở",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // BỘ CHỌN 2 TAB CHÍNH TÁCH BIỆT: Paper Trading vs Binance Thực Tế
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .background(DarkSurface, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            // Tab 1: Paper Trading (Mô Phỏng)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (primaryTab == 0) DarkSurfaceElevated else Color.Transparent)
                    .clickable { primaryTab = 0 }
                    .padding(vertical = 10.dp)
                    .testTag("primary_tab_paper_trading"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ListAlt,
                        contentDescription = null,
                        tint = if (primaryTab == 0) CryptoGold else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Paper Trading",
                        color = if (primaryTab == 0) CryptoGold else TextSecondary,
                        fontWeight = if (primaryTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                    if (paperOpenPositions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (primaryTab == 0) CryptoGold.copy(alpha = 0.2f) else DarkSurfaceHighlight
                        ) {
                            Text(
                                text = "${paperOpenPositions.size}",
                                color = if (primaryTab == 0) CryptoGold else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Tab 2: Binance Thực Tế (Testnet Demo / Mainnet)
            val binanceActiveCount = binanceExecutedOpenPositions.size + binancePortfolio.positions.size
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (primaryTab == 1) DarkSurfaceElevated else Color.Transparent)
                    .clickable {
                        primaryTab = 1
                        if (apiConfig.isConnected && binancePortfolio.positions.isEmpty()) {
                            viewModel.refreshBinancePortfolio()
                        }
                    }
                    .padding(vertical = 10.dp)
                    .testTag("primary_tab_binance_real"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = if (primaryTab == 1) CryptoGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Binance Thực Tế",
                        color = if (primaryTab == 1) CryptoGreen else TextSecondary,
                        fontWeight = if (primaryTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                    if (binanceActiveCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (primaryTab == 1) CryptoGreen.copy(alpha = 0.2f) else DarkSurfaceHighlight
                        ) {
                            Text(
                                text = "$binanceActiveCount",
                                color = if (primaryTab == 1) CryptoGreen else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // NỘI DUNG TƯƠNG ỨNG CỦA 2 TAB CHÍNH
        if (primaryTab == 0) {
            // ==================== TAB CHÍNH 1: PAPER TRADING ====================
            PaperTradingTabView(
                viewModel = viewModel,
                paperOpenPositions = paperOpenPositions,
                paperClosedPositions = paperClosedPositions,
                simulatedBalance = uiState.settings.simulatedBalanceUsdt,
                sortOrder = sortOrder,
                selectedSubTab = paperSubTab,
                onSubTabChange = { paperSubTab = it },
                onSelectCoin = onSelectCoin,
                isOpeningPositions = uiState.isOpeningPositions,
                openingPositionsProgress = uiState.openingPositionsProgress
            )
        } else {
            // ==================== TAB CHÍNH 2: BINANCE THỰC TẾ ====================
            BinanceRealTradingTabView(
                viewModel = viewModel,
                uiState = uiState,
                apiConfig = apiConfig,
                binancePortfolio = binancePortfolio,
                binanceExecutedOpenPositions = binanceExecutedOpenPositions,
                binanceClosedPositions = binanceClosedPositions,
                livePositionsTpSl = livePositionsTpSl,
                sortOrder = sortOrder,
                selectedSubTab = binanceSubTab,
                onSubTabChange = { binanceSubTab = it },
                onSelectCoin = onSelectCoin,
                isOpeningPositions = uiState.isOpeningPositions,
                openingPositionsProgress = uiState.openingPositionsProgress,
                onPerformWithPinCheck = performWithPinCheck,
                onEditTpSlTarget = { editingTpSlTarget = it }
            )
        }
    }
}

/**
 * Giao diện chuyên biệt cho TAB 1: PAPER TRADING (MÔ PHỎNG)
 */
@Composable
fun PaperTradingTabView(
    viewModel: MainViewModel,
    paperOpenPositions: List<TradePosition>,
    paperClosedPositions: List<TradePosition>,
    simulatedBalance: Double,
    sortOrder: RoiSortOrder,
    selectedSubTab: Int,
    onSubTabChange: (Int) -> Unit,
    onSelectCoin: (String) -> Unit,
    isOpeningPositions: Boolean,
    openingPositionsProgress: String
) {
    val totalUnrealizedPnl = paperOpenPositions.sumOf { it.unrealizedPnlUsdt }
    val totalRealizedPnl = paperClosedPositions.sumOf { it.pnlUsdt ?: 0.0 }
    val isUnrealizedProfit = totalUnrealizedPnl >= 0
    val isRealizedProfit = totalRealizedPnl >= 0

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Card: Paper Trading
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Số Dư Paper Trading (Ảo)", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CryptoGold.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "MÔ PHỎNG",
                                    color = CryptoGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", simulatedBalance)} USDT",
                            color = CryptoGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Volume Mỗi Lệnh (0.5%)", color = TextSecondary, fontSize = 11.sp)
                        val vol = simulatedBalance * 0.005
                        Text(
                            text = "~$${String.format(Locale.US, "%.1f", if (vol > 0) vol else 50.0)} USDT",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("PnL Chưa Chốt (Paper)", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${if (isUnrealizedProfit) "+" else ""}$${String.format(Locale.US, "%.2f", totalUnrealizedPnl)}",
                            color = if (isUnrealizedProfit) CryptoGreen else CryptoRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${paperOpenPositions.size} vị thế mở",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Lợi Nhuận Đã Chốt", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${if (isRealizedProfit) "+" else ""}$${String.format(Locale.US, "%.2f", totalRealizedPnl)}",
                            color = if (isRealizedProfit) CryptoGreen else CryptoRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${paperClosedPositions.size} lệnh hoàn tất",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2 Sub-Tabs cho Paper Trading: Vị Thế Mở | Lịch Sử Lệnh
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DarkSurface,
            contentColor = CryptoGold,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                if (selectedSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = CryptoGold
                    )
                }
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { onSubTabChange(0) },
                text = {
                    Text(
                        text = "Vị Thế Đang Mở (${paperOpenPositions.size})",
                        fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { onSubTabChange(1) },
                text = {
                    Text(
                        text = "Lịch Sử Lệnh (${paperClosedPositions.size})",
                        fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
        }

        when (selectedSubTab) {
            0 -> {
                // Vị Thế Đang Mở Paper Trading
                Column(modifier = Modifier.fillMaxSize()) {
                    // Thanh công cụ Sắp xếp & Đóng tất cả
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "Sắp xếp:", color = TextSecondary, fontSize = 11.sp)

                            Surface(
                                onClick = { viewModel.setRoiSortOrder(RoiSortOrder.DEFAULT) },
                                color = if (sortOrder == RoiSortOrder.DEFAULT) DarkSurfaceHighlight else DarkSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (sortOrder == RoiSortOrder.DEFAULT) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = "Mới nhất",
                                    color = if (sortOrder == RoiSortOrder.DEFAULT) CryptoGold else TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                onClick = { viewModel.setRoiSortOrder(RoiSortOrder.DESCENDING) },
                                color = if (sortOrder == RoiSortOrder.DESCENDING) CryptoGreen.copy(alpha = 0.2f) else DarkSurface,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (sortOrder == RoiSortOrder.DESCENDING) CryptoGreen else DarkBorder)
                            ) {
                                Text(
                                    text = "ROI 🔽",
                                    color = if (sortOrder == RoiSortOrder.DESCENDING) CryptoGreen else TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (paperOpenPositions.isNotEmpty()) {
                                Surface(
                                    onClick = { viewModel.closeAllPositions() },
                                    color = CryptoRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CryptoRed.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "Đóng tất cả",
                                        color = CryptoRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Surface(
                                onClick = { viewModel.resetAccountBalance() },
                                color = DarkSurfaceElevated,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                            ) {
                                Text(
                                    text = "Reset 10k",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (isOpeningPositions && openingPositionsProgress.isNotEmpty()) {
                        Surface(
                            color = CryptoGold.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGold.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = CryptoGold, modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = openingPositionsProgress, color = CryptoGold, fontSize = 11.sp)
                            }
                        }
                    }

                    if (paperOpenPositions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ListAlt, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Chưa có vị thế Paper Trading nào", color = TextSecondary, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Bật Auto-trade hoặc vào danh sách coin để mở lệnh mô phỏng với 0.5% số dư.",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(paperOpenPositions, key = { it.id }) { pos ->
                                OpenPositionCard(
                                    position = pos,
                                    onClose = { viewModel.manualClosePosition(pos.id) },
                                    onVerify = null,
                                    onClick = { onSelectCoin(pos.symbol) }
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Lịch Sử Lệnh Paper Trading
                if (paperClosedPositions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có lịch sử lệnh Paper Trading đã đóng", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(paperClosedPositions, key = { it.id }) { pos ->
                            ClosedPositionCard(
                                position = pos,
                                onVerify = null,
                                onClick = { onSelectCoin(pos.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Giao diện chuyên biệt cho TAB 2: BINANCE THỰC TẾ (DEMO / MAINNET)
 */
@Composable
fun BinanceRealTradingTabView(
    viewModel: MainViewModel,
    uiState: com.example.ui.UiState,
    apiConfig: com.example.model.BinanceApiConfig,
    binancePortfolio: com.example.model.BinancePortfolio,
    binanceExecutedOpenPositions: List<TradePosition>,
    binanceClosedPositions: List<TradePosition>,
    livePositionsTpSl: Map<String, Pair<Double?, Double?>>,
    sortOrder: RoiSortOrder,
    selectedSubTab: Int,
    onSubTabChange: (Int) -> Unit,
    onSelectCoin: (String) -> Unit,
    isOpeningPositions: Boolean,
    openingPositionsProgress: String,
    onPerformWithPinCheck: (String, () -> Unit) -> Unit,
    onEditTpSlTarget: (TpSlTarget) -> Unit
) {
    // Sắp xếp danh sách vị thế Binance Live giảm dần theo % ROI
    val liveSortedPositions = remember(binancePortfolio.positions, sortOrder) {
        when (sortOrder) {
            RoiSortOrder.ASCENDING -> binancePortfolio.positions.sortedBy { it.roiPercent }
            else -> binancePortfolio.positions.sortedByDescending { it.roiPercent }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!apiConfig.isConnected) {
            // Khi chưa kết nối API
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Chưa Kết Nối Binance Futures API",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vào màn hình Cài Đặt API để quản lý API Key & Secret Key. Hệ thống hỗ trợ gửi lệnh thẳng lên Binance Mainnet (kiểm thử) hoặc Testnet Demo và tự động sửa lỗi, kiểm tra trạng thái.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.autoLoginMainnet() },
                            colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
                        ) {
                            Text("Kết Nối Binance Mainnet", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.autoLoginTestnet() },
                            border = BorderStroke(1.dp, CryptoGold)
                        ) {
                            Text("Testnet Demo", color = CryptoGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
            return
        }

        // Summary Card: Binance Thực Tế
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Số Dư Ví Binance (${if (apiConfig.isTestnet) "Testnet Demo" else "Mainnet"})", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CryptoGreen))
                        }
                        val bal = if (binancePortfolio.totalWalletBalance > 0.0) binancePortfolio.totalWalletBalance else (apiConfig.totalWalletBalance ?: 0.0)
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", bal)} USDT",
                            color = CryptoGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Ký Quỹ Khả Dụng", color = TextSecondary, fontSize = 11.sp)
                        val avail = if (binancePortfolio.availableBalance > 0.0) binancePortfolio.availableBalance else (apiConfig.availableBalance ?: 0.0)
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", avail)} USDT",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PnL Chưa Chốt Binance Demo", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CryptoGreen.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "ĐỒNG BỘ 100%",
                                    color = CryptoGreen,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        val unPnl = binancePortfolio.totalUnrealizedProfit
                        val isPos = unPnl >= 0
                        Text(
                            text = "${if (isPos) "+" else ""}$${String.format(Locale.US, "%.2f", unPnl)} USDT",
                            color = if (isPos) CryptoGreen else CryptoRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${binancePortfolio.positions.size} vị thế khớp hoàn toàn với Binance Demo",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Lệnh Chờ Trên Sàn", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            text = "${binancePortfolio.openOrders.size} lệnh",
                            color = CryptoGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${binancePortfolio.assets.size} loại tài sản",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4 Sub-Tabs: Vị Thế Binance | Lệnh Chờ | Số Dư & Ví | Lịch Sử Binance
        val livePosCount = binancePortfolio.positions.size + binanceExecutedOpenPositions.size
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = DarkSurface,
            contentColor = CryptoGreen,
            edgePadding = 12.dp,
            indicator = { tabPositions ->
                if (selectedSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = CryptoGreen
                    )
                }
            }
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { onSubTabChange(0) },
                text = {
                    Text(
                        text = "Vị Thế Binance ($livePosCount)",
                        fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = {
                    onSubTabChange(1)
                    if (binancePortfolio.openOrders.isEmpty()) viewModel.refreshBinancePortfolio()
                },
                text = {
                    Text(
                        text = "Lệnh Chờ (${binancePortfolio.openOrders.size})",
                        fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 2,
                onClick = {
                    onSubTabChange(2)
                    if (binancePortfolio.assets.isEmpty()) viewModel.refreshBinancePortfolio()
                },
                text = {
                    Text(
                        text = "Số Dư & Ví (${binancePortfolio.assets.size})",
                        fontWeight = if (selectedSubTab == 2) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
            Tab(
                selected = selectedSubTab == 3,
                onClick = { onSubTabChange(3) },
                text = {
                    Text(
                        text = "Lịch Sử Binance (${binanceClosedPositions.size})",
                        fontWeight = if (selectedSubTab == 3) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
        }

        when (selectedSubTab) {
            0 -> {
                // Tab 0: Vị thế Binance
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isOpeningPositions && openingPositionsProgress.isNotEmpty()) {
                        Surface(
                            color = CryptoGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = CryptoGreen, modifier = Modifier.size(13.dp), strokeWidth = 1.5.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = openingPositionsProgress, color = CryptoGreen, fontSize = 11.sp)
                            }
                        }
                    }

                    val hasAnyLivePos = binancePortfolio.positions.isNotEmpty() || binanceExecutedOpenPositions.isNotEmpty()
                    if (!hasAnyLivePos) {
                        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Chưa có vị thế mở trên Binance Demo", color = TextSecondary, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mọi lệnh mở khi đã kết nối API sẽ được gửi trực tiếp lên Binance Demo và hiển thị tại đây.", color = TextMuted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 1. Live Contract Positions direct from Binance (Sắp xếp giảm dần theo % ROI)
                            if (liveSortedPositions.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Verified, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "HỢP ĐỒNG LIVE BINANCE (${liveSortedPositions.size})",
                                                color = CryptoGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                         Button(
                                            onClick = {
                                                onPerformWithPinCheck("Đóng TOÀN BỘ ${liveSortedPositions.size} vị thế trên sàn Binance") {
                                                    viewModel.closeAllLiveBinancePositions()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CryptoRed, contentColor = Color.White),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp).testTag("close_all_live_positions_button")
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Đóng Tất Cả", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                items(liveSortedPositions, key = { "binance_${it.symbol}" }) { livePos ->
                                    BinanceLivePositionCard(
                                        position = livePos,
                                        configuredTp = livePositionsTpSl[livePos.symbol]?.first,
                                        configuredSl = livePositionsTpSl[livePos.symbol]?.second,
                                        onSelectCoin = { onSelectCoin(livePos.symbol) },
                                        onEditTpSl = {
                                            onEditTpSlTarget(
                                                TpSlTarget(
                                                    symbol = livePos.symbol,
                                                    isLong = livePos.isLong,
                                                    entryPrice = livePos.entryPrice,
                                                    markPrice = if (livePos.markPrice > 0) livePos.markPrice else livePos.entryPrice,
                                                    leverage = livePos.leverage,
                                                    tpPrice = livePositionsTpSl[livePos.symbol]?.first,
                                                    slPrice = livePositionsTpSl[livePos.symbol]?.second
                                                )
                                            )
                                        },
                                        onClose = {
                                            onPerformWithPinCheck("Đóng vị thế ${livePos.symbol} trên sàn Binance") {
                                                viewModel.closeLiveBinancePosition(livePos)
                                            }
                                        }
                                    )
                                }
                            }

                            // 2. Positions executed via API with Binance Order ID & Verification
                            if (binanceExecutedOpenPositions.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "LỆNH BOT ĐÃ GỬI LÊN BINANCE DEMO (${binanceExecutedOpenPositions.size})",
                                            color = CryptoGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                items(binanceExecutedOpenPositions, key = { it.id }) { pos ->
                                    OpenPositionCard(
                                        position = pos,
                                        onClose = {
                                            onPerformWithPinCheck("Đóng vị thế ${pos.symbol}") {
                                                viewModel.manualClosePosition(pos.id)
                                            }
                                        },
                                        onEditTpSl = {
                                            val currentPrice = uiState.tickers.find { it.symbol == pos.symbol }?.lastPrice ?: pos.currentPrice
                                            onEditTpSlTarget(
                                                TpSlTarget(
                                                    symbol = pos.symbol,
                                                    isLong = true,
                                                    entryPrice = pos.entryPrice,
                                                    markPrice = currentPrice,
                                                    leverage = uiState.settings.leverage,
                                                    tpPrice = pos.takeProfitPrice,
                                                    slPrice = pos.stopLossPrice,
                                                    positionId = pos.id
                                                )
                                            )
                                        },
                                        onVerify = if (pos.binanceOrderId != null) {
                                            { viewModel.verifyOrderOnBinance(pos.symbol, pos.binanceOrderId) }
                                        } else null,
                                        onClick = { onSelectCoin(pos.symbol) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Tab 1: Lệnh Chờ Binance
                if (binancePortfolio.openOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Không có lệnh chờ nào trên sàn", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.refreshBinancePortfolio() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Làm mới sổ lệnh")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(binancePortfolio.openOrders, key = { it.orderId }) { order ->
                            BinanceOpenOrderCard(
                                order = order,
                                onVerify = { viewModel.verifyOrderOnBinance(order.symbol, order.orderId) }
                            )
                        }
                    }
                }
            }

            2 -> {
                // Tab 2: Số Dư & Ví Binance
                if (binancePortfolio.assets.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Đang tải dữ liệu số dư từ Binance...", color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(binancePortfolio.assets, key = { it.asset }) { asset ->
                            BinanceAssetCard(asset = asset)
                        }
                    }
                }
            }

            3 -> {
                // Tab 3: Lịch Sử Lệnh Binance
                if (binanceClosedPositions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Chưa có lịch sử lệnh Binance đã đóng", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(binanceClosedPositions, key = { it.id }) { pos ->
                            ClosedPositionCard(
                                position = pos,
                                onVerify = if (pos.binanceOrderId != null) {
                                    { viewModel.verifyOrderOnBinance(pos.symbol, pos.binanceOrderId) }
                                } else null,
                                onClick = { onSelectCoin(pos.symbol) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card hiển thị vị thế hợp đồng Binance thực tế (GET /fapi/v2/account & positionRisk).
 * Hiển thị đầy đủ: Symbol, Chiều (LONG/SHORT), Đòn bẩy, Ký quỹ (Margin USDT), Entry Price, Mark Price, Liq Price,
 * Unrealized PnL (USDT & % ROI có màu sắc trực quan), nút [Sửa TP/SL] và nút [Đóng Vị Thế].
 */
@Composable
fun BinanceLivePositionCard(
    position: BinanceLivePosition,
    configuredTp: Double? = null,
    configuredSl: Double? = null,
    onSelectCoin: () -> Unit,
    onEditTpSl: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val isProfit = position.unrealizedProfit >= 0
    val sign = if (isProfit) "+" else ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectCoin() }
            .border(
                1.dp,
                if (isProfit) CryptoGreen.copy(alpha = 0.5f) else CryptoRed.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            ),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Symbol, LONG/SHORT, Leverage & Live Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (position.isLong) CryptoGreen.copy(alpha = 0.18f) else CryptoRed.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (position.isLong) "LONG ${position.leverage}x" else "SHORT ${position.leverage}x",
                            color = if (position.isLong) CryptoGreen else CryptoRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = position.symbol,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Huy hiệu Binance Live
                Surface(
                    color = CryptoGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "BINANCE LIVE",
                            color = CryptoGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details: Khối lượng, PnL Chưa Thực Hiện & ROI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Khối lượng hợp đồng:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "${String.format(Locale.US, "%.4f", position.absAmount)} ${position.symbol.replace("USDT", "")}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Ký quỹ: $${String.format(Locale.US, "%.2f", position.initialMargin)} USDT",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$sign$${String.format(Locale.US, "%.2f", position.unrealizedProfit)} USDT",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ROI: $sign${String.format(Locale.US, "%.2f", position.roiPercent)}%",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Giá vào, Giá Mark & Giá thanh lý (Liq Price)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Giá vào (Entry):", color = TextSecondary, fontSize = 9.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.entryPrice)}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column {
                    Text("Giá Mark:", color = TextSecondary, fontSize = 9.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.markPrice)}",
                        color = CryptoGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Giá thanh lý (Liq):", color = TextSecondary, fontSize = 9.sp)
                    Text(
                        text = if (position.liquidationPrice > 0.0) "$${CryptoRepository.formatPrice(position.liquidationPrice)}" else "--",
                        color = if (position.liquidationPrice > 0.0) CryptoRed else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Hiển thị mốc TP/SL đã cài đặt nếu có
            if (configuredTp != null || configuredSl != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceElevated, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TP/SL Đã Cài:",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (configuredTp != null) {
                            Text(
                                text = "TP: $${CryptoRepository.formatPrice(configuredTp)}",
                                color = CryptoGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        if (configuredTp != null && configuredSl != null) {
                            Text(" | ", color = TextMuted, fontSize = 10.sp)
                        }
                        if (configuredSl != null) {
                            Text(
                                text = "SL: $${CryptoRepository.formatPrice(configuredSl)}",
                                color = CryptoRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: [Cài đặt TP / SL] và [Đóng Vị Thế]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditTpSl,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("edit_tpsl_button_${position.symbol}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cài đặt TP / SL",
                        color = CryptoGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("close_position_button_${position.symbol}"),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Đóng Vị Thế",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Card hiển thị lệnh chờ trên sàn Binance Futures (Open Orders).
 */
@Composable
fun BinanceOpenOrderCard(
    order: BinanceOpenOrderItem,
    onVerify: () -> Unit
) {
    val isBuy = order.side.equals("BUY", ignoreCase = true)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isBuy) CryptoGreen.copy(alpha = 0.18f) else CryptoRed.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${order.side} ${order.type}",
                            color = if (isBuy) CryptoGreen else CryptoRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = order.symbol,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Trạng thái lệnh
                Surface(
                    color = CryptoGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGold.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = order.status,
                        color = CryptoGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Giá đặt:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(order.price)}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column {
                    Text("Khối lượng:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "${order.origQty}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Đã khớp:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "${order.executedQty}",
                        color = CryptoGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Binance Order ID và Nút đối soát
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: #${order.orderId}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                TextButton(
                    onClick = onVerify,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kiểm tra trên Binance", color = CryptoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Card hiển thị số dư từng đồng coin trong ví Futures.
 */
@Composable
fun BinanceAssetCard(asset: BinanceAssetBalance) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CryptoGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = asset.asset.take(3),
                        color = CryptoGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = asset.asset,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Khả dụng: ${String.format(Locale.US, "%.4f", asset.availableBalance)}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format(Locale.US, "%.4f", asset.walletBalance)}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (asset.unrealizedProfit != 0.0) {
                    val isP = asset.unrealizedProfit >= 0
                    Text(
                        text = "${if (isP) "+" else ""}${String.format(Locale.US, "%.2f", asset.unrealizedProfit)} USDT",
                        color = if (isP) CryptoGreen else CryptoRed,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Card hiển thị vị thế mở của bot / người dùng có huy hiệu Binance Order ID.
 */
@Composable
fun OpenPositionCard(
    position: TradePosition,
    onClose: () -> Unit,
    onEditTpSl: (() -> Unit)? = null,
    onVerify: (() -> Unit)?,
    onClick: () -> Unit
) {
    val pnl = position.unrealizedPnlUsdt
    val isProfit = pnl >= 0
    val sign = if (isProfit) "+" else ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, if (isProfit) CryptoGreen.copy(alpha = 0.4f) else CryptoRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Symbol, Tag, and PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = CryptoGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "LONG",
                            color = CryptoGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = position.symbol,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Unrealized PnL & ROI
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$sign$${String.format(Locale.US, "%.2f", pnl)} USDT",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "ROI: ${position.roiFormatted}",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Huy hiệu xác thực Binance Order ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (position.binanceOrderId != null) {
                    Surface(
                        color = CryptoGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ĐÃ ĐƯA LÊN BINANCE #${position.binanceOrderId}",
                                color = CryptoGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    Surface(
                        color = CryptoGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGold.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "LỆNH MÔ PHỎNG",
                            color = CryptoGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (onVerify != null) {
                    Text(
                        text = "Kiểm tra lệnh 🔍",
                        color = CryptoGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onVerify() }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Giá vào:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.entryPrice)}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column {
                    Text("Giá hiện tại:", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.currentPrice)}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column {
                    Text("Vốn (0.5%):", color = TextSecondary, fontSize = 10.sp)
                    val volText = if (position.investedUsdt % 1.0 == 0.0) "${position.investedUsdt.toInt()} USDT" else "${String.format(Locale.US, "%.1f", position.investedUsdt)} USDT"
                    Text(
                        text = volText,
                        color = CryptoGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // TP & SL targets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceHighlight, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TP (Chốt lời): ", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = position.takeProfitPrice?.let { "$${CryptoRepository.formatPrice(it)}" } ?: "Không thiết lập",
                        color = if (position.takeProfitPrice != null) CryptoGreen else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SL (Cắt lỗ): ", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = position.stopLossPrice?.let { "$${CryptoRepository.formatPrice(it)}" } ?: "Không thiết lập",
                        color = if (position.stopLossPrice != null) CryptoRed else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onEditTpSl != null) {
                    OutlinedButton(
                        onClick = onEditTpSl,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cài đặt TP / SL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .then(if (onEditTpSl != null) Modifier.weight(1f) else Modifier)
                        .height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Đóng Vị Thế", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Card hiển thị lịch sử vị thế đã đóng.
 */
@Composable
fun ClosedPositionCard(
    position: TradePosition,
    onVerify: (() -> Unit)?,
    onClick: () -> Unit
) {
    val pnl = position.pnlUsdt ?: 0.0
    val pnlPercent = position.pnlPercent ?: 0.0
    val isProfit = pnl >= 0
    val sign = if (isProfit) "+" else ""

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (position.closeReason) {
                            TradePosition.REASON_TAKE_PROFIT -> CryptoGreen.copy(alpha = 0.15f)
                            TradePosition.REASON_STOP_LOSS -> CryptoRed.copy(alpha = 0.15f)
                            else -> DarkSurfaceHighlight
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = when (position.closeReason) {
                                TradePosition.REASON_TAKE_PROFIT -> "CHỐT LỜI (TP)"
                                TradePosition.REASON_STOP_LOSS -> "CẮT LỖ (SL)"
                                else -> "ĐÓNG THỦ CÔNG"
                            },
                            color = when (position.closeReason) {
                                TradePosition.REASON_TAKE_PROFIT -> CryptoGreen
                                TradePosition.REASON_STOP_LOSS -> CryptoRed
                                else -> TextSecondary
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = position.symbol,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "$sign$${String.format(Locale.US, "%.2f", pnl)} USDT (${sign}${String.format(Locale.US, "%.2f", pnlPercent)}%)",
                    color = if (isProfit) CryptoGreen else CryptoRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Binance Order ID nếu có
            if (position.binanceOrderId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = CryptoGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BINANCE LIVE #${position.binanceOrderId}",
                            color = CryptoGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (onVerify != null) {
                        Text(
                            text = "Đối soát lệnh 🔍",
                            color = CryptoGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onVerify() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vào: $${CryptoRepository.formatPrice(position.entryPrice)} → Ra: $${CryptoRepository.formatPrice(position.exitPrice ?: position.currentPrice)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = position.exitTime?.let { dateFormat.format(Date(it)) } ?: "",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Hộp thoại đối soát lệnh thực tế trên Binance Futures (GET /fapi/v1/order).
 */
@Composable
fun OrderVerificationModalDialog(
    detail: BinanceOrderVerificationDetail?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (detail?.isVerified == true) Icons.Default.Verified else Icons.Default.Shield,
                contentDescription = null,
                tint = if (detail?.isVerified == true) CryptoGreen else CryptoGold,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Đối Soát Lệnh Thực Tế Binance",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = CryptoGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Đang kết nối API máy chủ Binance...", color = TextSecondary, fontSize = 12.sp)
                    }
                } else if (detail != null) {
                    // Status Badge
                    Surface(
                        color = if (detail.isVerified) CryptoGreen.copy(alpha = 0.18f) else CryptoRed.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (detail.isVerified) CryptoGreen else CryptoRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (detail.isVerified) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (detail.isVerified) CryptoGreen else CryptoRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (detail.isVerified) "XÁC NHẬN ĐÃ ĐƯA LÊN BINANCE" else "CHƯA XÁC NHẬN ĐƯỢC",
                                color = if (detail.isVerified) CryptoGreen else CryptoRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Detail rows
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceHighlight,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            VerificationItemRow("Binance Order ID:", "#${detail.orderId}", isMono = true, isHighlight = true)
                            if (detail.clientOrderId.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                VerificationItemRow("Client Order ID:", detail.clientOrderId, isMono = true)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            VerificationItemRow("Cặp giao dịch:", detail.symbol)
                            Spacer(modifier = Modifier.height(4.dp))
                            VerificationItemRow("Trạng thái lệnh:", detail.status, isStatus = true)
                            Spacer(modifier = Modifier.height(4.dp))
                            VerificationItemRow("Loại lệnh:", "${detail.side} ${detail.type}")
                            if (detail.executedQty > 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                VerificationItemRow("Khối lượng khớp:", "${detail.executedQty}", isMono = true)
                            }
                            if (detail.avgPrice > 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                VerificationItemRow("Giá khớp trung bình:", "$${CryptoRepository.formatPrice(detail.avgPrice)}", isMono = true)
                            }
                            if (detail.updateTime > 0L) {
                                Spacer(modifier = Modifier.height(4.dp))
                                VerificationItemRow("Thời gian máy chủ:", dateFormat.format(Date(detail.updateTime)))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = detail.message,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Đóng", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun VerificationItemRow(
    label: String,
    value: String,
    isMono: Boolean = false,
    isHighlight: Boolean = false,
    isStatus: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = when {
                isStatus && (value == "FILLED" || value == "NEW") -> CryptoGreen
                isStatus -> CryptoRed
                isHighlight -> CryptoGold
                else -> TextPrimary
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
        )
    }
}
