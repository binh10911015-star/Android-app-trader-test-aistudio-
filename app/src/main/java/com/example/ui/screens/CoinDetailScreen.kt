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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BacktestSummaryResult
import com.example.model.BacktestTradeItem
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.CandlestickChart
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CoinDetailScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    openPositions: List<TradePosition>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val symbol = uiState.selectedSymbol

    LaunchedEffect(symbol, uiState.selectedTimeframe) {
        viewModel.loadKlineForSymbol(symbol, uiState.selectedTimeframe)
    }

    val currentTicker = uiState.tickers.find { it.symbol == symbol }
    val currentPrice = currentTicker?.lastPrice
        ?: uiState.klineData.lastOrNull()?.candle?.close
        ?: 0.0
    val isUp = (currentTicker?.priceChangePercent ?: 0.0) >= 0.0

    val existingPosition = openPositions.find { it.symbol == symbol && !it.isLiveBinanceOrder }
    val hasOpenPosition = existingPosition != null

    var showHistoryTrades by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // HÀNG HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onBack() },
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = symbol,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Binance Futures (Mainnet)",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { viewModel.loadKlineForSymbol(symbol, uiState.selectedTimeframe) },
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới",
                    tint = TextSecondary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // THẺ GIÁ REALTIME MAINNET
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Giá Realtime (Mainnet)", color = TextSecondary, fontSize = 11.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(currentPrice)}",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isUp) CryptoGreen.copy(alpha = 0.2f) else CryptoRed.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isUp) CryptoGreen else CryptoRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${if (isUp) "+" else ""}${String.format(Locale.US, "%.2f", currentTicker?.priceChangePercent ?: 0.0)}%",
                            color = if (isUp) CryptoGreen else CryptoRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // HÀNG CHỌN TIMEFRAME
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("15m", "1h", "4h", "1d").forEach { tf ->
                val selected = uiState.selectedTimeframe == tf
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { viewModel.setTimeframe(tf) },
                    color = if (selected) CryptoGold else DarkSurfaceHighlight
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = tf.uppercase(),
                            color = if (selected) Color.Black else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // BIỂU ĐỒ NẾN
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            if (uiState.isKlineLoading && uiState.klineData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CryptoGold, strokeWidth = 2.dp)
                }
            } else if (uiState.klineData.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Đang kết nối luồng nến Binance...", color = TextMuted, fontSize = 12.sp)
                }
            } else {
                CandlestickChart(
                    indicatorData = uiState.klineData,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }

        // THẺ CHẠY BACKTEST LỊCH SỬ D1
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Backtest D1 Breakout (180 Ngày)",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dữ liệu nến lịch sử Binance Mainnet",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Button(
                        onClick = { viewModel.runBacktestForSelectedCoin(candleLimit = 180) },
                        enabled = !uiState.isBacktesting,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
                    ) {
                        if (uiState.isBacktesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Chạy Backtest",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                val backtestResult: BacktestSummaryResult? = uiState.backtestResult
                if (backtestResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (backtestResult.errorMessage != null) {
                        Text(text = backtestResult.errorMessage, color = CryptoRed, fontSize = 11.sp)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Tỷ lệ thắng (Winrate)", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", backtestResult.winRatePercent)}% (${backtestResult.winTrades}/${backtestResult.totalTrades} lệnh)",
                                    color = if (backtestResult.winRatePercent >= 50.0) CryptoGreen else CryptoRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Lợi nhuận ròng", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "${if (backtestResult.netProfitUsdt >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", backtestResult.netProfitUsdt)} (${String.format(Locale.US, "%.1f", backtestResult.totalRoiPercent)}%)",
                                    color = if (backtestResult.netProfitUsdt >= 0) CryptoGreen else CryptoRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Max Drawdown: -${String.format(Locale.US, "%.1f", backtestResult.maxDrawdownPercent)}%",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Profit Factor: ${String.format(Locale.US, "%.2f", backtestResult.profitFactor)}",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        if (backtestResult.tradesHistory.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = DarkBorder)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showHistoryTrades = !showHistoryTrades },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Chi tiết ${backtestResult.tradesHistory.size} lệnh khớp lịch sử",
                                    color = CryptoGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (showHistoryTrades) "Thu gọn ▲" else "Xem thêm ▼",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }

                            if (showHistoryTrades) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    backtestResult.tradesHistory.forEachIndexed { idx, tr ->
                                        BacktestTradeRow(
                                            index = idx + 1,
                                            trade = tr,
                                            dateFormat = dateFormat
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // THẺ THÔNG SỐ VÀO LỆNH PAPER TRADE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val currentVol = uiState.settings.calculatePositionVolume()
                val volFormatted = if (currentVol % 1.0 == 0.0) "${currentVol.toInt()}" else String.format(Locale.US, "%.1f", currentVol)

                Text(
                    text = "Vào Lệnh Paper Trade ($volFormatted USDT ~ 0.5% vốn)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Cơ chế chốt lời", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "FloorLock & Trailing ATR",
                            color = CryptoGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Cắt lỗ (SL)", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "Spot 1x (Không SL)",
                            color = CryptoGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Đòn bẩy", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "${uiState.settings.leverage}x",
                            color = CryptoGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (hasOpenPosition) {
                    OutlinedButton(
                        onClick = { existingPosition?.let { viewModel.manualClosePosition(it.id) } },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        border = BorderStroke(1.dp, CryptoRed)
                    ) {
                        Text(text = "Đóng Vị Thế Hiện Tại", color = CryptoRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.executeBuy(symbol, isAuto = false) },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen)
                    ) {
                        Text(text = "+ Mở Vị Thế Paper Trade", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BacktestTradeRow(
    index: Int,
    trade: BacktestTradeItem,
    dateFormat: SimpleDateFormat
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = DarkSurfaceHighlight,
        border = BorderStroke(0.5.dp, if (trade.isWin) CryptoGreen.copy(alpha = 0.3f) else CryptoRed.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$index ",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = dateFormat.format(Date(trade.entryTime)),
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${trade.exitReason})",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = "Entry: $${CryptoRepository.formatPrice(trade.entryPrice)} ➔ Exit: $${CryptoRepository.formatPrice(trade.exitPrice)}",
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "${if (trade.isWin) "+" else ""}$${String.format(Locale.US, "%.2f", trade.pnlUsdt)} (${String.format(Locale.US, "%.1f", trade.roiPercent)}%)",
                color = if (trade.isWin) CryptoGreen else CryptoRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}