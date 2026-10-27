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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceApiConfig
import com.example.model.BinancePortfolio
import com.example.model.BinanceTicker
import com.example.model.TradePosition
import com.example.model.TradingStrategyType
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import com.example.ui.MarketFilter
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
fun MarketScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    openPositions: List<TradePosition> = emptyList(),
    apiConfig: BinanceApiConfig = BinanceApiConfig(),
    binancePortfolio: BinancePortfolio = BinancePortfolio(),
    onSelectCoin: (String) -> Unit,
    onPerformWithPinCheck: (() -> Unit) -> Unit = { it() },
    onOpenApiConfig: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pendingBuySymbol by remember { mutableStateOf<String?>(null) }
    var showBotWarningDialog by remember { mutableStateOf(false) }

    if (showBotWarningDialog) {
        AlertDialog(
            onDismissRequest = {
                showBotWarningDialog = false
                pendingBuySymbol = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = CryptoGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bot Đang Hoạt Động", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "Bot giao dịch tự động đang hoạt động theo cơ chế Single Position ATR Trailing và phân bổ vốn cuốn chiếu (5.1 USDT/vị thế, tối đa 60% Equity). Bạn không nên mở lệnh thủ công để tránh xung đột vốn.\n\nBạn có muốn tắt Bot để tiếp tục mở lệnh?",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBotWarningDialog = false
                        val sym = pendingBuySymbol
                        pendingBuySymbol = null
                        if (sym != null) {
                            viewModel.toggleAutoTrade(false)
                            onPerformWithPinCheck {
                                viewModel.executeBuy(sym, isAuto = false, basketSlot = "PRIMARY")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
                ) {
                    Text("Tắt Bot & Mở Lệnh", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBotWarningDialog = false
                        pendingBuySymbol = null
                    }
                ) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(12.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // HÀNG HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.settings.autoTradeEnabled) CryptoGreen else CryptoRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Binance Futures USDT-M",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.settings.autoTradeEnabled) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (uiState.settings.autoTradeEnabled) CryptoGreen.copy(alpha = 0.5f) else CryptoRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (uiState.settings.autoTradeEnabled) "Bot: Đang chạy" else "Bot: Đang dừng",
                            color = if (uiState.settings.autoTradeEnabled) CryptoGreen else CryptoRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Chiến lược: SMA20 D1 (RR 1:3)",
                    color = CryptoGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { viewModel.scanAllFuturesCoins() },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGold),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Quét thủ công",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Quét", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // HÀNG ĐẾM NGƯỢC QUÉT TÍN HIỆU & NÚT KÍCH HOẠT BOT
        val nextScanLabel = com.example.util.ScanScheduler.getNextScanHourText(uiState.nextScanTimestamp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = DarkSurfaceHighlight,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Quét: $nextScanLabel",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Đếm ngược phiên quét",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                    Text(
                        text = if (uiState.countdownText.isNotEmpty()) uiState.countdownText else "00:00:00",
                        color = CryptoGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (!uiState.settings.autoTradeEnabled) {
                Button(
                    onClick = { viewModel.toggleAutoTrade(true) },
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGold),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Kích hoạt Bot",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.toggleAutoTrade(false) },
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CryptoGreen.copy(alpha = 0.15f),
                        contentColor = CryptoGreen
                    ),
                    border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CryptoGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bot đang chạy",
                        color = CryptoGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (uiState.isScanning) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { uiState.scanPercent },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = CryptoGold,
                trackColor = DarkSurfaceHighlight
            )
            Text(text = uiState.scanProgress, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }

        // Ô TÌM KIẾM COIN
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Nhập tên coin (ví dụ: BTC, ETH, SOL...)", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated,
                focusedBorderColor = CryptoGold,
                unfocusedBorderColor = DarkBorder
            )
        )

        // 2 THẺ TÍN HIỆU ĐỘC LẬP
        // THẺ CHIẾN LƯỢC: D1 SMA20 Breakout & Bùng Nổ Vol (Quét nến ngày 07:00 AM)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    viewModel.selectSignalTab(0)
                    viewModel.setFilterMode(
                        if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL) MarketFilter.ALL else MarketFilter.BULLISH_SIGNAL
                    )
                },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                    DarkSurfaceElevated else DarkSurfaceHighlight
            ),
            border = BorderStroke(
                1.dp,
                if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                    CryptoGold else DarkBorder
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TÍN HIỆU SMA20 D1 (RR 1:3)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                                CryptoGold else TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(07:00 AM UTC+7)",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CryptoGold.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${uiState.d1BullishCount} coin",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CryptoGold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Điều kiện: Nến t-1 tăng (Close > Open), Close > SMA20 và Low <= SMA20. SL = Đáy t-1, TP = 3R (5.1 USDT/lệnh). Ưu tiên: Coin có % thay đổi nến t-1 nhỏ nhất vào trước.",
                    fontSize = 9.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // HÀNG XEM TẤT CẢ & MỞ TOÀN BỘ
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { viewModel.setFilterMode(MarketFilter.ALL) },
                color = if (uiState.filterMode == MarketFilter.ALL) CryptoGold.copy(alpha = 0.2f) else DarkSurfaceHighlight
            ) {
                Text(
                    text = "Xem tất cả (${uiState.tickers.size})",
                    color = if (uiState.filterMode == MarketFilter.ALL) CryptoGold else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            OutlinedButton(
                onClick = {
                    onPerformWithPinCheck {
                        viewModel.openAllScannedPositions(isAuto = false)
                    }
                },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, CryptoGreen)
            ) {
                Text(
                    text = "Mở toàn bộ (${uiState.d1BullishCount})",
                    color = CryptoGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // DANH SÁCH COIN
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(uiState.filteredTickers, key = { _, it -> it.symbol }) { index, ticker ->
                val priorityIndex = if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL) index + 1 else null
                CoinItemCard(
                    ticker = ticker,
                    priorityIndex = priorityIndex,
                    onSelect = { onSelectCoin(ticker.symbol) },
                    onBuyClick = {
                        if (uiState.settings.autoTradeEnabled) {
                            pendingBuySymbol = ticker.symbol
                            showBotWarningDialog = true
                        } else {
                            onPerformWithPinCheck {
                                viewModel.executeBuy(ticker.symbol, isAuto = false, basketSlot = "PRIMARY")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CoinItemCard(
    ticker: BinanceTicker,
    priorityIndex: Int? = null,
    onSelect: () -> Unit,
    onBuyClick: () -> Unit
) {
    val isUp = ticker.priceChangePercent >= 0

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(
            1.dp,
            if (priorityIndex == 1) CryptoGold.copy(alpha = 0.8f) else DarkBorder
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (priorityIndex != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (priorityIndex == 1) CryptoGold.copy(alpha = 0.25f) else DarkSurfaceHighlight,
                            border = BorderStroke(1.dp, if (priorityIndex == 1) CryptoGold else DarkBorder),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "#$priorityIndex",
                                color = if (priorityIndex == 1) CryptoGold else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(text = ticker.symbol, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "$${CryptoRepository.formatPrice(ticker.lastPrice)}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (priorityIndex != null) {
                    Text(
                        text = "Nến t-1: +${String.format(Locale.US, "%.2f", ticker.score)}%",
                        color = CryptoGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isUp) CryptoGreen.copy(alpha = 0.2f) else CryptoRed.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "${if (isUp) "+" else ""}${String.format(Locale.US, "%.2f", ticker.priceChangePercent)}%",
                    color = if (isUp) CryptoGreen else CryptoRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onBuyClick,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    border = BorderStroke(1.dp, CryptoGreen)
                ) {
                    Text(text = "+ Mở vị thế", color = CryptoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSelect,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
                ) {
                    Text(text = "Biểu đồ", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}