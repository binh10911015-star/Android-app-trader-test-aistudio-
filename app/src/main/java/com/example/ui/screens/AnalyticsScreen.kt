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
    var perTradePctText by remember { mutableStateOf((currentConfig.perTradePct * 100).toString()) }
    var maxAllocationPctText by remember { mutableStateOf((currentConfig.maxPortfolioAllocation * 100).toInt().toString()) }

    // 3. Bộ lọc Macro & Chọn Coin
    var minQuoteVolumeText by remember { mutableStateOf(currentConfig.minQuoteVolume24h.toLong().toString()) }
    var minVolRatioText by remember { mutableStateOf(currentConfig.minVolRatio.toString()) }
    var maxD1ChangePctText by remember { mutableStateOf(currentConfig.maxD1ChangePct.toString()) }
    var maxAtrPctText by remember { mutableStateOf((currentConfig.maxAtrPercent * 100).toString()) }
    var btcPanicBreakerText by remember { mutableStateOf(currentConfig.btcPanicCircuitBreaker.toString()) }

    // 4. Thoát lệnh động ATR & Timeout
    var kTpText by remember { mutableStateOf(currentConfig.kTp.toString()) }
    var kTrailText by remember { mutableStateOf(currentConfig.kTrail.toString()) }
    var kSlText by remember { mutableStateOf(currentConfig.kSl.toString()) }
    var timeoutHoursText by remember { mutableStateOf(currentConfig.maxHoldingHours.toString()) }

    // Lắng nghe khi có đề xuất AI 1-chạm được áp dụng
    LaunchedEffect(currentConfig, currentDays) {
        daysText = currentDays.toString()
        slotScanMode = currentConfig.slotScanMode
        initialCapitalText = currentConfig.initialCapital.toInt().toString()
        perTradePctText = (currentConfig.perTradePct * 100).toString()
        maxAllocationPctText = (currentConfig.maxPortfolioAllocation * 100).toInt().toString()
        minQuoteVolumeText = currentConfig.minQuoteVolume24h.toLong().toString()
        minVolRatioText = currentConfig.minVolRatio.toString()
        maxD1ChangePctText = currentConfig.maxD1ChangePct.toString()
        maxAtrPctText = (currentConfig.maxAtrPercent * 100).toString()
        btcPanicBreakerText = currentConfig.btcPanicCircuitBreaker.toString()
        kTpText = currentConfig.kTp.toString()
        kTrailText = currentConfig.kTrail.toString()
        kSlText = currentConfig.kSl.toString()
        timeoutHoursText = currentConfig.maxHoldingHours.toString()
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

                        listOf("60", "90", "180", "360").forEach { d ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { daysText = d },
                                color = if (daysText == d) CryptoGold else DarkSurfaceHighlight,
                                border = BorderStroke(0.5.dp, if (daysText == d) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = "${d}d",
                                    color = if (daysText == d) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
                            Text(text = "Vốn/Lệnh (%) (Tối đa 2%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = perTradePctText,
                                onValueChange = { perTradePctText = it },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "BTC Panic Breaker (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = btcPanicBreakerText,
                                onValueChange = { btcPanicBreakerText = it },
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
        // NHÓM 4: CƠ CHẾ CHỐT LỜI & THOÁT LỆNH ĐỘNG
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
                            text = "4. Cơ Chế Chốt Lời & Thoát Lệnh Động",
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
                            Text(text = "Hệ số kích hoạt TP (K_TP)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = kTpText,
                                onValueChange = { kTpText = it },
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
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Hệ số Stop-Loss (K_SL)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = kSlText,
                                onValueChange = { kSlText = it },
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
                            Text(text = "Timeout giải phóng (Giờ)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = timeoutHoursText,
                                onValueChange = { timeoutHoursText = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                        }
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
                    val perTrade = (perTradePctText.toDoubleOrNull() ?: 1.5) / 100.0
                    val maxAlloc = (maxAllocationPctText.toDoubleOrNull() ?: 60.0) / 100.0
                    val minVol = minQuoteVolumeText.toDoubleOrNull() ?: 20_000_000.0
                    val minRvol = minVolRatioText.toDoubleOrNull() ?: 2.0
                    val maxD1 = maxD1ChangePctText.toDoubleOrNull() ?: 10.0
                    val maxAtr = (maxAtrPctText.toDoubleOrNull() ?: 12.0) / 100.0
                    val btcPanic = btcPanicBreakerText.toDoubleOrNull() ?: -3.5

                    val kTp = kTpText.toDoubleOrNull() ?: 0.50
                    val kTrail = kTrailText.toDoubleOrNull() ?: 0.25
                    val kSl = kSlText.toDoubleOrNull() ?: 0.50
                    val timeoutHours = timeoutHoursText.toIntOrNull() ?: 72

                    val customConfig = BacktestConfig(
                        initialCapital = initCap,
                        perTradePct = perTrade,
                        maxPortfolioAllocation = maxAlloc,
                        minQuoteVolume24h = minVol,
                        minVolRatio = minRvol,
                        maxD1ChangePct = maxD1,
                        maxAtrPercent = maxAtr,
                        btcPanicCircuitBreaker = btcPanic,
                        slotScanMode = slotScanMode,
                        kTp = kTp,
                        kTrail = kTrail,
                        kSl = kSl,
                        maxHoldingHours = timeoutHours
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