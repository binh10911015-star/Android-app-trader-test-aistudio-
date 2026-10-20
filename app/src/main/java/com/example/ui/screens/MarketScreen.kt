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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.ui.components.SettingsDialog
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
    var showSettingsDialog by remember { mutableStateOf(false) }

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
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(CryptoGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Binance Futures",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (uiState.settings.selectedStrategy == TradingStrategyType.D1_SMA20_VOLUME)
                        "D1: SMA20 Breakout & Vol >= 2x" else "4H: SuperTrend & MA20",
                    color = CryptoGold,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.clip(CircleShape).clickable { viewModel.refreshTickers() },
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Làm mới",
                        tint = TextSecondary,
                        modifier = Modifier.padding(8.dp).size(16.dp)
                    )
                }

                Surface(
                    modifier = Modifier.clip(CircleShape).clickable { showSettingsDialog = true },
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt",
                        tint = TextSecondary,
                        modifier = Modifier.padding(8.dp).size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // THẺ SỐ DƯ & CÔNG TẮC AUTO-TRADE
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
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Số Dư Binance Futures", color = TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (apiConfig.isTestnet) CryptoGold.copy(alpha = 0.2f) else CryptoGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (apiConfig.isTestnet) "Binance Demo" else "Mainnet",
                                color = if (apiConfig.isTestnet) CryptoGold else CryptoGreen,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", binancePortfolio.totalWalletBalance)} USDT",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (uiState.settings.autoTradeEnabled) "AUTO-TRADE: BẬT" else "AUTO-TRADE: TẮT",
                            color = if (uiState.settings.autoTradeEnabled) CryptoGreen else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Volume 0.5%/lệnh", color = TextMuted, fontSize = 9.sp)
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
                            checkedThumbColor = CryptoGreen,
                            checkedTrackColor = CryptoGreen.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // HÀNG QUÉT TOÀN BỘ SÀN
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = DarkSurfaceHighlight
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Quét 4H (7:00 UTC+7)", color = TextMuted, fontSize = 11.sp)
                    Text(text = uiState.countdownText, color = CryptoGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.scanAllFuturesCoins() },
                enabled = !uiState.isScanning,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
            ) {
                if (uiState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Text(text = "Quét ngay (${uiState.tickers.size})", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Thẻ 1: D1 SMA20 Breakout & Bùng Nổ Vol
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectSignalTab(0) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.selectedSignalTab == 0 && uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                        DarkSurfaceElevated else DarkSurfaceHighlight
                ),
                border = BorderStroke(
                    1.dp,
                    if (uiState.selectedSignalTab == 0 && uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                        CryptoGold else Color.Transparent
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "D1 BREAKOUT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.selectedSignalTab == 0 && uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                                CryptoGold else TextPrimary
                        )
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
                        text = "Giá >= SMA20 & Vol >= 2x (từ 7h)",
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Thẻ 2: 4H SuperTrend & MA20
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.selectSignalTab(1) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.selectedSignalTab == 1 && uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                        DarkSurfaceElevated else DarkSurfaceHighlight
                ),
                border = BorderStroke(
                    1.dp,
                    if (uiState.selectedSignalTab == 1 && uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                        CryptoGreen else Color.Transparent
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "4H SUPERTREND",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.selectedSignalTab == 1 && uiState.filterMode == MarketFilter.BULLISH_SIGNAL)
                                CryptoGreen else TextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CryptoGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${uiState.superTrendBullishCount} coin",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CryptoGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "MA20 > SuperTrend (20,4)",
                        fontSize = 9.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
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
                    text = "Mở toàn bộ (${if (uiState.selectedSignalTab == 0) uiState.d1BullishCount else uiState.superTrendBullishCount})",
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
            items(uiState.filteredTickers, key = { it.symbol }) { ticker ->
                CoinItemCard(
                    ticker = ticker,
                    onSelect = { onSelectCoin(ticker.symbol) },
                    onBuyClick = {
                        onPerformWithPinCheck {
                            viewModel.executeBuy(ticker.symbol)
                        }
                    }
                )
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = uiState.settings,
            apiConfig = apiConfig,
            onDismiss = { showSettingsDialog = false },
            onSave = { updated ->
                viewModel.updateSettings(updated)
                showSettingsDialog = false
            },
            onResetAccount = { viewModel.resetAccountBalance() },
            onSaveApiCredentials = { key, sec, pin, testnet, live, onResult ->
                viewModel.saveAndVerifyApiCredentials(key, sec, pin, testnet, live, onResult)
            },
            onOpenApiConfig = onOpenApiConfig ?: {}
        )
    }
}

@Composable
fun CoinItemCard(
    ticker: BinanceTicker,
    onSelect: () -> Unit,
    onBuyClick: () -> Unit
) {
    val isUp = ticker.priceChangePercent >= 0

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = ticker.symbol, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "$${CryptoRepository.formatPrice(ticker.lastPrice)}",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
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