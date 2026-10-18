package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.CandlestickChart
import com.example.ui.components.PinVerificationDialog
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.IndicatorMA20
import com.example.ui.theme.IndicatorSuperTrendDown
import com.example.ui.theme.IndicatorSuperTrendUp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun CoinDetailScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    openPositions: List<TradePosition>,
    onBack: () -> Unit
) {
    val symbol = uiState.selectedSymbol
    val ticker = uiState.tickers.find { it.symbol == symbol }
    val latestPrice = ticker?.lastPrice
        ?: uiState.klineData.lastOrNull()?.candle?.close
        ?: 0.0

    var isLongOrder by remember { mutableStateOf(true) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinActionDesc by remember { mutableStateOf("") }
    var onPinConfirmedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showPinDialog) {
        PinVerificationDialog(
            actionTitle = pinActionDesc.ifEmpty { "vào lệnh $symbol" },
            onVerify = { pin -> viewModel.authorizePin(pin) },
            onSuccess = {
                onPinConfirmedAction?.invoke()
                onPinConfirmedAction = null
                showPinDialog = false
            },
            onDismiss = {
                showPinDialog = false
                onPinConfirmedAction = null
            }
        )
    }

    val latestIndicator = uiState.klineData.lastOrNull()
    val isBullishAlert = latestIndicator?.isMaAboveSuperTrend == true
    val maValue = latestIndicator?.ma20
    val stValue = latestIndicator?.superTrend?.value

    // Check if symbol already has an active open position
    val existingPosition = openPositions.find { it.symbol == symbol }
    val hasOpenOrder = existingPosition != null

    val isProfit = (ticker?.priceChangePercent ?: 0.0) >= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = TextPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = symbol,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = DarkSurfaceHighlight,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Futures USDT-M",
                            color = CryptoGold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "Chỉ báo: MA20 & SuperTrend(20,4)",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${CryptoRepository.formatPrice(latestPrice)}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                if (ticker != null) {
                    Text(
                        text = "${if (isProfit) "+" else ""}${String.format(Locale.US, "%.2f", ticker.priceChangePercent)}%",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Timeframe selection buttons: H4 and D1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Khung thời gian nến:",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.selectedTimeframe == "4h",
                        onClick = { viewModel.setTimeframe("4h") },
                        label = { Text("H4 (4 Giờ)", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("timeframe_4h_chip")
                    )

                    FilterChip(
                        selected = uiState.selectedTimeframe == "1d",
                        onClick = { viewModel.setTimeframe("1d") },
                        label = { Text("D1 (1 Ngày)", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("timeframe_1d_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prominent Bullish Alert Banner (Cảnh báo xu hướng tăng khi MA20 > SuperTrend(20,4))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = if (isBullishAlert) CryptoGreen else DarkBorder,
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBullishAlert) CryptoGreen.copy(alpha = 0.12f) else DarkSurfaceElevated
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isBullishAlert) CryptoGreen.copy(alpha = 0.25f) else DarkSurfaceHighlight,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBullishAlert) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (isBullishAlert) CryptoGreen else CryptoRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isBullishAlert)
                                "⚡ CẢNH BÁO XU HƯỚNG TĂNG: MA20 > SUPERTREND(20,4)"
                            else
                                "XU HƯỚNG GIẢM / CHƯA XÁC NHẬN: MA20 ≤ SUPERTREND",
                            color = if (isBullishAlert) CryptoGreen else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBullishAlert)
                                "Khung ${uiState.selectedTimeframe.uppercase()}: MA20 ($${maValue?.let { CryptoRepository.formatPrice(it) } ?: "--"}) vượt lên trên SuperTrend ($${stValue?.let { CryptoRepository.formatPrice(it) } ?: "--"}). Đủ điều kiện mở lệnh mua!"
                            else
                                "Khung ${uiState.selectedTimeframe.uppercase()}: MA20 ($${maValue?.let { CryptoRepository.formatPrice(it) } ?: "--"}) đang nằm dưới SuperTrend ($${stValue?.let { CryptoRepository.formatPrice(it) } ?: "--"}).",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Candlestick Chart
            if (uiState.isKlineLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(DarkSurface, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CryptoGold, strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Đang tải dữ liệu nến Binance ${symbol} (${uiState.selectedTimeframe})...",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val initialMode = if (uiState.settings.selectedStrategy == com.example.model.TradingStrategyType.D1_SMA20_VOLUME)
                    com.example.ui.components.ChartStrategyMode.SMA_VOLUME
                else
                    com.example.ui.components.ChartStrategyMode.SMA_SUPERTREND
                CandlestickChart(
                    indicatorData = uiState.klineData,
                    timeframe = uiState.selectedTimeframe,
                    initialMode = initialMode
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Simulated Trade Execution Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val currentVol = uiState.settings.calculatePositionVolume()
                    val volFormatted = if (currentVol % 1.0 == 0.0) "${currentVol.toInt()}" else String.format(Locale.US, "%.1f", currentVol)
                    Text(
                        text = "Chiến Lược Tự Động Vào Lệnh (0.5% Số Dư ~ $volFormatted USDT)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Configuration parameters display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Vốn vào lệnh:", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                "$volFormatted USDT (0.5%)",
                                color = CryptoGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text("Chốt Lời (TP):", color = TextSecondary, fontSize = 11.sp)
                            val tpPercent = uiState.settings.takeProfitPercent
                            if (tpPercent != null) {
                                val tpPrice = latestPrice * (1.0 + tpPercent / 100.0)
                                Text(
                                    "+$tpPercent% ($${CryptoRepository.formatPrice(tpPrice)})",
                                    color = CryptoGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    "Không thiết lập",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Column {
                            Text("Cắt Lỗ (SL):", color = TextSecondary, fontSize = 11.sp)
                            val slPercent = uiState.settings.stopLossPercent
                            if (slPercent != null) {
                                val slPrice = latestPrice * (1.0 - slPercent / 100.0)
                                Text(
                                    "-$slPercent% ($${CryptoRepository.formatPrice(slPrice)})",
                                    color = CryptoRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    "Không thiết lập",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Condition: "Nếu danh sách vào lệnh đã có thì không tự động mở thêm lệnh mới cho coin đó."
                    if (hasOpenOrder && existingPosition != null) {
                        // Already has open order
                        Surface(
                            color = CryptoGold.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = CryptoGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Coin này đã có 1 lệnh đang mở",
                                        color = CryptoGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Theo quy tắc: Không mở thêm lệnh trùng lặp cho $symbol. Đang theo dõi TP/SL.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val unrealizedPnl = existingPosition.unrealizedPnlUsdt
                                val unrealizedPercent = existingPosition.unrealizedPnlPercent
                                val isPosProfit = unrealizedPnl >= 0

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("PnL tạm tính:", color = TextSecondary, fontSize = 11.sp)
                                        Text(
                                            text = "${if (isPosProfit) "+" else ""}$${String.format(Locale.US, "%.2f", unrealizedPnl)} (${if (isPosProfit) "+" else ""}${String.format(Locale.US, "%.2f", unrealizedPercent)}%)",
                                            color = if (isPosProfit) CryptoGreen else CryptoRed,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            pinActionDesc = "đóng vị thế $symbol"
                                            onPinConfirmedAction = {
                                                viewModel.manualClosePosition(existingPosition.id)
                                            }
                                            showPinDialog = true
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                                        modifier = Modifier.testTag("manual_close_button")
                                    ) {
                                        Text("Đóng Lệnh", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Cài đặt đòn bẩy giao dịch (1x - 50x)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Đòn bẩy giao dịch:", color = TextSecondary, fontSize = 11.sp)
                                Surface(
                                    color = CryptoGold.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${uiState.settings.leverage}x",
                                        color = CryptoGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(5, 10, 20, 50).forEach { lev ->
                                    val isSelected = uiState.settings.leverage == lev
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(30.dp)
                                            .clickable { viewModel.updateLeverage(symbol, lev) }
                                            .testTag("leverage_chip_$lev"),
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) CryptoGold else DarkSurfaceHighlight,
                                        border = if (isSelected) null else BorderStroke(1.dp, DarkBorder)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${lev}x",
                                                color = if (isSelected) DarkBg else TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val currentVol = uiState.settings.calculatePositionVolume()
                        val volFormatted = if (currentVol % 1.0 == 0.0) "${currentVol.toInt()}" else String.format(Locale.US, "%.1f", currentVol)

                        // 2 Nút mở vị thế: [BUY / LONG] và [SELL / SHORT]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Nút BUY / LONG
                            Button(
                                onClick = {
                                    isLongOrder = true
                                    pinActionDesc = "vào lệnh BUY / LONG $symbol (${uiState.settings.leverage}x, $volFormatted USDT)"
                                    onPinConfirmedAction = {
                                        viewModel.executeOrder(symbol = symbol, isLong = true)
                                    }
                                    showPinDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CryptoGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("execute_buy_long_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("BUY / LONG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("$volFormatted USDT", fontSize = 9.sp)
                                }
                            }

                            // Nút SELL / SHORT
                            Button(
                                onClick = {
                                    isLongOrder = false
                                    pinActionDesc = "vào lệnh SELL / SHORT $symbol (${uiState.settings.leverage}x, $volFormatted USDT)"
                                    onPinConfirmedAction = {
                                        viewModel.executeOrder(symbol = symbol, isLong = false)
                                    }
                                    showPinDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CryptoRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("execute_sell_short_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SELL / SHORT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("$volFormatted USDT", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
