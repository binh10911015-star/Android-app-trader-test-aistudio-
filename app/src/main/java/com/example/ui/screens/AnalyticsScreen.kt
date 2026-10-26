package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BacktestConfig
import com.example.model.BasketBacktestReport
import com.example.model.BasketCycleResult
import com.example.model.BasketSlotId
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

    val currentConfig by viewModel.backtestConfig.collectAsState()
    val currentDays by viewModel.backtestDays.collectAsState()

    // 1. Khung thời gian & Chế độ quét
    var daysText by remember { mutableStateOf(currentDays.toString()) }
    var slotScanMode by remember { mutableStateOf(currentConfig.slotScanMode) }

    // 2. Quản trị vốn & Phân bổ
    var initialCapitalText by remember { mutableStateOf(currentConfig.initialCapital.toInt().toString()) }
    var capitalPerTradeUsdtText by remember { mutableStateOf(if (currentConfig.capitalPerTradeUsdt > 0.0) currentConfig.capitalPerTradeUsdt.toString() else "5.1") }
    var perTradePctText by remember { mutableStateOf((currentConfig.perTradePct * 100).toString()) }
    var maxAllocationPctText by remember { mutableStateOf((currentConfig.maxPortfolioAllocation * 100).toInt().toString()) }

    // 3. Bộ lọc Macro & Chọn Coin
    var minQuoteVolumeText by remember { mutableStateOf(currentConfig.minQuoteVolume24h.toLong().toString()) }
    var minVolRatioText by remember { mutableStateOf(currentConfig.minVolRatio.toString()) }
    var maxD1ChangePctText by remember { mutableStateOf(currentConfig.maxD1ChangePct.toString()) }
    var maxAtrPctText by remember { mutableStateOf((currentConfig.maxAtrPercent * 100).toString()) }

    // 4. Thoát lệnh Pure Trailing & FloorLock
    var kTrailText by remember { mutableStateOf(currentConfig.kTrail.toString()) }
    var floorLockMinRoiText by remember { mutableStateOf((currentConfig.floorLockMinRoi * 100).toString()) }

    // 5. BỘ LỌC BTC MACRO REGIME (v2.2)
    var enableBtcMacroFilter by remember { mutableStateOf(currentConfig.enableBtcMacroFilter) }
    var btcEmaFastText by remember { mutableStateOf(currentConfig.btcEmaFastPeriod.toString()) }
    var btcEmaSlowText by remember { mutableStateOf(currentConfig.btcEmaSlowPeriod.toString()) }
    var enableBtcCircuitBreaker by remember { mutableStateOf(currentConfig.enableBtcCircuitBreaker) }
    var btcCircuitBreakerPctText by remember { mutableStateOf(currentConfig.btcCircuitBreakerPct.toString()) }

    // 6. CƠ CHẾ SMART TIMEOUT (v2.2)
    var enableSmartTimeout by remember { mutableStateOf(currentConfig.enableSmartTimeout) }
    var timeoutDaysText by remember { mutableStateOf(currentConfig.timeoutDays.toString()) }
    var disableTimeoutWhenTrailing by remember { mutableStateOf(currentConfig.disableTimeoutWhenTrailing) }

    // Lắng nghe khi có đề xuất AI 1-chạm được áp dụng
    LaunchedEffect(currentConfig, currentDays) {
        daysText = currentDays.toString()
        slotScanMode = currentConfig.slotScanMode
        initialCapitalText = currentConfig.initialCapital.toInt().toString()
        capitalPerTradeUsdtText = if (currentConfig.capitalPerTradeUsdt > 0.0) currentConfig.capitalPerTradeUsdt.toString() else "5.1"
        perTradePctText = (currentConfig.perTradePct * 100).toString()
        maxAllocationPctText = (currentConfig.maxPortfolioAllocation * 100).toInt().toString()
        minQuoteVolumeText = currentConfig.minQuoteVolume24h.toLong().toString()
        minVolRatioText = currentConfig.minVolRatio.toString()
        maxD1ChangePctText = currentConfig.maxD1ChangePct.toString()
        maxAtrPctText = (currentConfig.maxAtrPercent * 100).toString()
        kTrailText = currentConfig.kTrail.toString()
        floorLockMinRoiText = (currentConfig.floorLockMinRoi * 100).toString()
        enableBtcMacroFilter = currentConfig.enableBtcMacroFilter
        btcEmaFastText = currentConfig.btcEmaFastPeriod.toString()
        btcEmaSlowText = currentConfig.btcEmaSlowPeriod.toString()
        enableBtcCircuitBreaker = currentConfig.enableBtcCircuitBreaker
        btcCircuitBreakerPctText = currentConfig.btcCircuitBreakerPct.toString()
        enableSmartTimeout = currentConfig.enableSmartTimeout
        timeoutDaysText = currentConfig.timeoutDays.toString()
        disableTimeoutWhenTrailing = currentConfig.disableTimeoutWhenTrailing
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp)
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
                        text = "Kiểm Thử Dual Basket v2.0",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "2 Slot 07h/19h • Trailing ATR • Lọc BTC Macro • Binance Mainnet",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // ==========================================
        // NHÓM 1: KHUNG THỜI GIAN & CHẾ ĐỘ QUÉT
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_1_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1. Khung Thời Gian & Chế Độ Quét",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                            modifier = Modifier.weight(1.2f).height(48.dp),
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

                        listOf("180", "365", "730").forEach { d ->
                            val label = when(d) {
                                "180" -> "180d (~6T)"
                                "365" -> "365d (1N)"
                                "730" -> "730d (2N)"
                                else -> "${d}d"
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { daysText = d },
                                color = if (daysText == d) CryptoGold else DarkSurfaceHighlight,
                                border = BorderStroke(0.5.dp, if (daysText == d) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = label,
                                    color = if (daysText == d) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Chế độ slot quét (Execution Slots)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("DUAL", "Dual (07h + 19h)", Icons.Default.Layers),
                            Triple("PRIMARY_ONLY", "Chỉ Primary (07h)", Icons.Default.WbSunny),
                            Triple("SECONDARY_ONLY", "Chỉ Secondary (19h)", Icons.Default.NightsStay)
                        ).forEach { (mode, label, icon) ->
                            val isSelected = slotScanMode == mode
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { slotScanMode = mode },
                                color = if (isSelected) CryptoGold.copy(alpha = 0.2f) else DarkSurfaceHighlight,
                                border = BorderStroke(1.dp, if (isSelected) CryptoGold else DarkBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) CryptoGold else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) CryptoGold else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // NHÓM 2: QUẢN TRỊ VỐN & PHÂN BỔ
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_2_card"),
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
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "2. Quản Trị Vốn & Phân Bổ",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Huy hiệu cố định Cross 1x
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CryptoGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, CryptoGreen.copy(alpha = 0.5f))
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
                                    text = "Margin: Cross | Leverage: 1x (Khóa cứng)",
                                    color = CryptoGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Vốn ban đầu (USDT)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = initialCapitalText,
                        onValueChange = { initialCapitalText = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Vốn vào mỗi lệnh (USDT)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = capitalPerTradeUsdtText,
                                onValueChange = { capitalPerTradeUsdtText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                            Text(text = "Trần danh mục (%) (Tối đa 60%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maxAllocationPctText,
                                onValueChange = { maxAllocationPctText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Chọn nhanh vốn/lệnh (USDT):", color = TextMuted, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5.1 to "5.1$ (Chuẩn)", 10.0 to "10$", 20.0 to "20$", 50.0 to "50$", 100.0 to "100$").forEach { (amt, label) ->
                            val isSelected = capitalPerTradeUsdtText == amt.toString()
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { capitalPerTradeUsdtText = amt.toString() },
                                color = if (isSelected) CryptoGold.copy(alpha = 0.25f) else DarkSurface,
                                border = BorderStroke(0.5.dp, if (isSelected) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) CryptoGold else TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // NHÓM 3: BỘ LỌC MACRO & CHỌN COIN
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_3_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "3. Bộ Lọc Macro & Chọn Coin (v2.0)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Lọc Vol 24h (USDT) (Chuẩn v2.0 >= 20M)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = minQuoteVolumeText,
                            onValueChange = { minQuoteVolumeText = it },
                            modifier = Modifier.weight(1.3f).height(48.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        )

                        listOf("0", "5M", "10M", "20M").forEach { vLabel ->
                            val volVal = when (vLabel) {
                                "0" -> "0"
                                "5M" -> "5000000"
                                "10M" -> "10000000"
                                else -> "20000000"
                            }
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { minQuoteVolumeText = volVal },
                                color = if (minQuoteVolumeText == volVal) CryptoGold else DarkSurfaceHighlight,
                                border = BorderStroke(0.5.dp, if (minQuoteVolumeText == volVal) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = vLabel,
                                    color = if (minQuoteVolumeText == volVal) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ngưỡng nổ RVol(20)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = minVolRatioText,
                                onValueChange = { minVolRatioText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                            Text(text = "Chặn trên nến D1 (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maxD1ChangePctText,
                                onValueChange = { maxD1ChangePctText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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

                    Text(text = "Trần biến động ATR% D1", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = maxAtrPctText,
                        onValueChange = { maxAtrPctText = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
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
        }

        // ==========================================
        // NHÓM 4: CƠ CHẾ CHỐT LỜI PURE TRAILING & FLOORLOCK
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_4_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "4. Cơ Chế Thoát Lệnh (Pure Trailing Stop & FloorLock)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Bước đệm Trailing (K_Trail)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = kTrailText,
                                onValueChange = { kTrailText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                            Text(text = "Sàn khóa lãi FloorLock (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = floorLockMinRoiText,
                                onValueChange = { floorLockMinRoiText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                }
            }
        }

        // ==========================================
        // NHÓM 5: BỘ LỌC BTC MACRO REGIME (v2.2)
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_5_card"),
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
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "5. Bộ Lọc BTC Macro Regime (v2.2)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kích hoạt lọc xu hướng BTC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enableBtcMacroFilter) CryptoGreen else TextMuted)
                            Text("Chỉ mở giỏ khi BTC D1 nằm trên cụm EMA xu hướng tăng", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = enableBtcMacroFilter,
                            onCheckedChange = { enableBtcMacroFilter = it },
                            modifier = Modifier.testTag("toggle_btc_macro_filter"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CryptoGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Chu kỳ EMA Nhanh D1", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = btcEmaFastText,
                                onValueChange = { btcEmaFastText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Chu kỳ EMA Chậm D1", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = btcEmaSlowText,
                                onValueChange = { btcEmaSlowText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ngắt mạch khẩn cấp (Circuit Breaker)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enableBtcCircuitBreaker) CryptoGreen else TextMuted)
                            Text("Hủy mở giỏ nếu nến BTC D1 giảm quá ngưỡng trong 24h", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = enableBtcCircuitBreaker,
                            onCheckedChange = { enableBtcCircuitBreaker = it },
                            modifier = Modifier.testTag("toggle_btc_circuit_breaker"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CryptoGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Ngưỡng giảm BTC 24h kích hoạt ngắt mạch (%)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = btcCircuitBreakerPctText,
                        onValueChange = { btcCircuitBreakerPctText = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }

        // ==========================================
        // NHÓM 6: CƠ CHẾ SMART TIMEOUT (v2.2)
        // ==========================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_6_card"),
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
                            Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "6. Cơ Chế Smart Timeout (v2.2)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kích hoạt Smart Timeout", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enableSmartTimeout) CryptoGreen else TextMuted)
                            Text("Tự động giải phóng vị thế om vốn sau thời gian quy định", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = enableSmartTimeout,
                            onCheckedChange = { enableSmartTimeout = it },
                            modifier = Modifier.testTag("toggle_smart_timeout"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CryptoGreen)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Thời gian nắm giữ tối đa (ngày)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = timeoutDaysText,
                        onValueChange = { timeoutDaysText = it },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Miễn trừ khi đang Trailing lãi", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (disableTimeoutWhenTrailing) CryptoGreen else TextMuted)
                            Text("Không cắt lệnh nếu vị thế đang có lãi và bám đỉnh sóng", fontSize = 10.sp, color = TextMuted)
                        }
                        Switch(
                            checked = disableTimeoutWhenTrailing,
                            onCheckedChange = { disableTimeoutWhenTrailing = it },
                            modifier = Modifier.testTag("toggle_exempt_trailing"),
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CryptoGreen)
                        )
                    }
                }
            }
        }

        // ==========================================
        // NÚT BẤM CHẠY BACKTEST V2.0
        // ==========================================
        item {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull() ?: 180
                    val initCap = initialCapitalText.toDoubleOrNull() ?: 10000.0
                    val capitalPerTrade = capitalPerTradeUsdtText.toDoubleOrNull() ?: 5.1
                    val perTrade = if (initCap > 0) capitalPerTrade / initCap else 0.015
                    val maxAlloc = (maxAllocationPctText.toDoubleOrNull() ?: 60.0) / 100.0
                    val minVol = minQuoteVolumeText.toDoubleOrNull() ?: 5_000_000.0
                    val minRvol = minVolRatioText.toDoubleOrNull() ?: 2.0
                    val maxD1 = maxD1ChangePctText.toDoubleOrNull() ?: 17.5
                    val maxAtr = (maxAtrPctText.toDoubleOrNull() ?: 12.0) / 100.0
                    val kTrail = kTrailText.toDoubleOrNull() ?: 0.25
                    val floorLockRoi = (floorLockMinRoiText.toDoubleOrNull() ?: 0.5) / 100.0

                    val customConfig = BacktestConfig(
                        initialCapital = initCap,
                        capitalPerTradeUsdt = capitalPerTrade,
                        useFixedCapitalPerTrade = true,
                        perTradePct = perTrade,
                        maxPortfolioAllocation = maxAlloc,
                        minQuoteVolume24h = minVol,
                        minVolRatio = minRvol,
                        maxD1ChangePct = maxD1,
                        maxAtrPercent = maxAtr,
                        slotScanMode = slotScanMode,
                        kTrail = kTrail,
                        enableTrailingStop = true,
                        enableFloorLock = true,
                        floorLockMinRoi = floorLockRoi,
                        enableBtcMacroFilter = enableBtcMacroFilter,
                        btcEmaFastPeriod = btcEmaFastText.toIntOrNull() ?: 20,
                        btcEmaSlowPeriod = btcEmaSlowText.toIntOrNull() ?: 50,
                        enableBtcCircuitBreaker = enableBtcCircuitBreaker,
                        btcCircuitBreakerPct = btcCircuitBreakerPctText.toDoubleOrNull() ?: -3.5,
                        enableSmartTimeout = enableSmartTimeout,
                        timeoutDays = timeoutDaysText.toIntOrNull() ?: 7,
                        disableTimeoutWhenTrailing = disableTimeoutWhenTrailing
                    )

                    viewModel.updateBacktestConfig(customConfig, days)
                    viewModel.runBasketStrategyBacktest(days = days, config = customConfig)
                },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("run_backtest_button"),
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
                    Text(text = "Đang tải nến Mainnet & tính toán...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "BẮT ĐẦU CHẠY BACKTEST V2.0", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // KẾT QUẢ BÁO CÁO (NẾU CÓ)
        // ==========================================
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
    val trailingHits = report.cycles.count { it.exitReason == "TRAILING_STOP_HIT" }
    val timeoutHits = report.cycles.count { it.exitReason.contains("TIMEOUT") }
    val slHits = report.cycles.count { it.exitReason == "SL_HIT" }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("basket_report_overview_card"),
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
                Text(
                    text = "KẾT QUẢ HIỆU SUẤT BACKTEST V2.0",
                    color = CryptoGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vốn: $${String.format(Locale.US, "%,.0f", report.initialCapital)} ➔ $${String.format(Locale.US, "%,.0f", report.finalEquity)}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

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

            Spacer(modifier = Modifier.height(10.dp))

            // Phân bổ lý do thoát lệnh (Trailing / Timeout / SL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Trailing TP: $trailingHits", color = CryptoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Timeout 72h: $timeoutHits", color = CryptoGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Cắt lỗ SL: $slHits", color = CryptoRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BasketCycleItemCard(cycle: BasketCycleResult) {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val openDateStr = sdf.format(Date(cycle.openTime))
    val closeDateStr = sdf.format(Date(cycle.closeTime))

    val slotBadge = if (cycle.slotId == BasketSlotId.PRIMARY) "PRIMARY (07h)" else "SECONDARY (19h)"
    val slotColor = if (cycle.slotId == BasketSlotId.PRIMARY) CryptoGold else Color(0xFF64B5F6)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("basket_cycle_item_${cycle.cycleIndex}"),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = slotColor.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, slotColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = slotBadge,
                            color = slotColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Giỏ #${cycle.cycleIndex} • ${cycle.coinsCount} coins",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${if (cycle.realizedProfitUsdt >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", cycle.realizedProfitUsdt)} (${if (cycle.basketRoiPercent >= 0) "+" else ""}${String.format(Locale.US, "%.1f", cycle.basketRoiPercent)}%)",
                    color = if (cycle.realizedProfitUsdt >= 0) CryptoGreen else CryptoRed,
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
                    text = "Lý do: ${cycle.exitReason}",
                    color = when (cycle.exitReason) {
                        "TRAILING_STOP_HIT" -> CryptoGreen
                        "SL_HIT" -> CryptoRed
                        else -> CryptoGold
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (cycle.symbolsList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Coins: ${cycle.symbolsList.joinToString(", ")}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}