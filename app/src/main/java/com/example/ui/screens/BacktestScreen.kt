package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import com.example.model.BacktestConfig
import com.example.model.BasketCycleResult
import com.example.model.BasketPositionDetail
import com.example.model.BasketSlotId
import com.example.model.TradePosition
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BacktestScreen(
    viewModel: MainViewModel,
    closedPositions: List<TradePosition> = emptyList(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning = uiState.isBasketBacktesting
    val report = uiState.basketReport
    var cycleFilter by remember { mutableStateOf("ALL") }

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
    var minD1ChangePctText by remember { mutableStateOf(currentConfig.minD1ChangePct.toString()) }
    var maxD1ChangePctText by remember { mutableStateOf(currentConfig.maxD1ChangePct.toString()) }
    var maxAtrPctText by remember { mutableStateOf((currentConfig.maxAtrPercent * 100).toString()) }

    // 4. Thoát lệnh động ATR Trailing Stop & FloorLock
    var kTrailText by remember { mutableStateOf(currentConfig.kTrail.toString()) }
    var floorLockMinRoiText by remember { mutableStateOf((currentConfig.floorLockMinRoi * 100).toString()) }

    // 2 TOGGLE BẬT/TẮT CƠ CHẾ THOÁT LỆNH
    var enableTrailingStop by remember { mutableStateOf(currentConfig.enableTrailingStop) }
    var enableFloorLock by remember { mutableStateOf(currentConfig.enableFloorLock) }

    // Quản lý Dialog giải thích thông số
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogContent by remember { mutableStateOf<String?>(null) }

    // Lắng nghe khi có cấu hình được nạp
    LaunchedEffect(currentConfig, currentDays) {
        daysText = currentDays.toString()
        slotScanMode = currentConfig.slotScanMode
        initialCapitalText = currentConfig.initialCapital.toInt().toString()
        perTradePctText = (currentConfig.perTradePct * 100).toString()
        maxAllocationPctText = (currentConfig.maxPortfolioAllocation * 100).toInt().toString()
        minQuoteVolumeText = currentConfig.minQuoteVolume24h.toLong().toString()
        minVolRatioText = currentConfig.minVolRatio.toString()
        minD1ChangePctText = currentConfig.minD1ChangePct.toString()
        maxD1ChangePctText = currentConfig.maxD1ChangePct.toString()
        maxAtrPctText = (currentConfig.maxAtrPercent * 100).toString()
        kTrailText = currentConfig.kTrail.toString()
        floorLockMinRoiText = (currentConfig.floorLockMinRoi * 100).toString()
        enableTrailingStop = currentConfig.enableTrailingStop
        enableFloorLock = currentConfig.enableFloorLock
    }

    if (infoDialogTitle != null && infoDialogContent != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogContent = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = infoDialogTitle ?: "",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Text(
                    text = infoDialogContent ?: "",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        infoDialogTitle = null
                        infoDialogContent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoGold)
                ) {
                    Text("Đã hiểu", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(horizontal = 14.dp)
            .testTag("backtest_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp)
    ) {
        // --- TIÊU ĐỀ MÀN HÌNH (GỌN GÀNG, VỪA VẶN VỚI CHỮ) ---
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backtest_header_card"),
                shape = RoundedCornerShape(10.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Thử Nghiệm & Tối Ưu Backtest",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Dual Basket Momentum v2.0 • Tự do bật tắt cơ chế thoát lệnh",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CryptoGold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Dual Basket v2.0",
                            color = CryptoGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "1. Khung Thời Gian & Slot Quét", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                infoDialogTitle = "Khung Thời Gian & Slot Quét"
                                infoDialogContent = "Số ngày backtest nến lịch sử (mặc định 180 ngày). Chế độ DUAL vận hành 2 slot giỏ độc lập: Primary (07:00 AM) và Secondary (19:00 PM UTC+7)."
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Thông tin", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Số ngày kiểm thử lịch sử (Days)", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = daysText,
                        onValueChange = { daysText = it },
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Chế độ Slot Quét", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DUAL" to "Dual (07h & 19h)", "PRIMARY_ONLY" to "Slot Chính (07h)", "SECONDARY_ONLY" to "Slot Phụ (19h)").forEach { (key, label) ->
                            val isSel = slotScanMode == key
                            Surface(
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { slotScanMode = key },
                                color = if (isSel) CryptoGold.copy(alpha = 0.2f) else DarkSurface,
                                border = BorderStroke(1.dp, if (isSel) CryptoGold else DarkBorder)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) CryptoGold else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
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
                            Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "2. Quản Trị Vốn & Phân Bổ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                infoDialogTitle = "Quản Trị Vốn & Phân Bổ"
                                infoDialogContent = "Vốn ban đầu cho bài test. Vốn/Lệnh (%) quy định tỷ lệ vốn phân bổ cho mỗi coin (chuẩn 1.5%). Trần danh mục (%) là giới hạn tổng ký quỹ mở cùng lúc (tối đa 60%) để giữ 40% tiền mặt chống sụt giảm."
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Thông tin", tint = TextMuted, modifier = Modifier.size(16.dp))
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.FilterAlt, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "3. Bộ Lọc Macro & Chọn Coin", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                infoDialogTitle = "Bộ Lọc Macro & Chọn Coin"
                                infoDialogContent = "• Min Vol 24h: Khối lượng giao dịch tối thiểu để đảm bảo thanh khoản.\n• RVol(20): Tỷ lệ khối lượng nến D1 hôm qua so với trung bình 20 ngày (>= 2.0x là dòng tiền nổ).\n• Chặn trên D1: Không mua đuổi coin đã tăng quá trần.\n• ATR% trần: Loại coin siêu biến động rủi ro cao.\n• BTC Circuit Breaker: Dừng mở giỏ mới nếu BTC giảm sâu hơn mức này."
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Thông tin", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Khối lượng 24h tối thiểu (USDT)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = minQuoteVolumeText,
                                onValueChange = { minQuoteVolumeText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Ngưỡng RVol(20) tối thiểu", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = minVolRatioText,
                                onValueChange = { minVolRatioText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Biên độ D1 tối thiểu (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = minD1ChangePctText,
                                onValueChange = { minD1ChangePctText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Biên độ D1 tối đa (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = maxD1ChangePctText,
                                onValueChange = { maxD1ChangePctText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }

        // =========================================================================
        // NHÓM 4: CƠ CHẾ CHỐT LỜI ĐỘNG (FLOORLOCK & ATR TRAILING STOP)
        // =========================================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("backtest_group_4_card"),
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
                            Icon(imageVector = Icons.Default.TrendingUp, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "4. Cơ Chế Thoát Lệnh (Pure Trailing Stop)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                infoDialogTitle = "Cơ Chế Chốt Lời Động (Pure Trailing Stop)"
                                infoDialogContent = "Chiến lược Trend Following loại bỏ hoàn toàn Stop Loss, Timeout và TP cố định:\n• FloorLock: Sàn khóa lãi net tối thiểu (+0.5%) ngay khi giỏ có lãi dương để bảo vệ khỏi bị trượt âm.\n• Trailing Stop ATR: Bám sát đỉnh lợi nhuận cao nhất và chốt trọn sóng khi giá thoái lui K_Trail * ATR giỏ."
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "Thông tin", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // BẢNG 2 NÚT BẬT/TẮT TỪNG CHỨC NĂNG
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "BẬT / TẮT CƠ CHẾ CHỐT LỜI:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CryptoGold
                            )

                            // Toggle 1: Trailing Stop ATR
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("1. Trailing Stop ATR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enableTrailingStop) CryptoGreen else TextMuted)
                                    Text("Bám đỉnh theo bước đệm ATR giỏ để tối đa hóa lợi nhuận", fontSize = 10.sp, color = TextMuted)
                                }
                                Switch(
                                    checked = enableTrailingStop,
                                    onCheckedChange = { enableTrailingStop = it },
                                    modifier = Modifier.scale(0.72f).testTag("toggle_trailing_stop"),
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CryptoGreen)
                                )
                            }

                            Divider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                            // Toggle 2: Sàn khóa lãi FloorLock
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("2. Sàn khóa lãi FloorLock (+0.5% net)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enableFloorLock) CryptoGreen else TextMuted)
                                    Text("Bảo vệ lãi ròng tối thiểu khi đã kích hoạt trailing", fontSize = 10.sp, color = TextMuted)
                                }
                                Switch(
                                    checked = enableFloorLock,
                                    onCheckedChange = { enableFloorLock = it },
                                    modifier = Modifier.scale(0.72f).testTag("toggle_floor_lock"),
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CryptoGreen)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Các ô nhập hệ số
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Bước đệm Trailing (K_Trail)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = kTrailText,
                                onValueChange = { kTrailText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Sàn khóa lãi tối thiểu (%)", color = TextMuted, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = floorLockMinRoiText,
                                onValueChange = { floorLockMinRoiText = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CryptoGold, unfocusedBorderColor = DarkBorder, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // NÚT CHẠY BACKTEST
        // ==========================================
        item {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull() ?: 180
                    val updatedCfg = BacktestConfig(
                        initialCapital = initialCapitalText.toDoubleOrNull() ?: 10000.0,
                        perTradePct = (perTradePctText.toDoubleOrNull() ?: 1.5) / 100.0,
                        maxPortfolioAllocation = (maxAllocationPctText.toDoubleOrNull() ?: 60.0) / 100.0,
                        minQuoteVolume24h = minQuoteVolumeText.toDoubleOrNull() ?: 20_000_000.0,
                        minVolRatio = minVolRatioText.toDoubleOrNull() ?: 2.0,
                        minD1ChangePct = minD1ChangePctText.toDoubleOrNull() ?: 2.0,
                        maxD1ChangePct = maxD1ChangePctText.toDoubleOrNull() ?: 10.0,
                        maxAtrPercent = (maxAtrPctText.toDoubleOrNull() ?: 12.0) / 100.0,
                        slotScanMode = slotScanMode,
                        kTrail = kTrailText.toDoubleOrNull() ?: 0.25,
                        enableTrailingStop = enableTrailingStop,
                        enableFloorLock = enableFloorLock,
                        floorLockMinRoi = (floorLockMinRoiText.toDoubleOrNull() ?: 0.5) / 100.0
                    )
                    viewModel.updateBacktestConfig(updatedCfg, days)
                    viewModel.runBasketStrategyBacktest(days = days, config = updatedCfg)
                },
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_run_basket_backtest"),
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Đang chạy mô phỏng lịch sử...", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Chạy Thử Nghiệm Backtest ($daysText Ngày)", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // BÁO CÁO KẾT QUẢ BACKTEST
        // ==========================================
        if (report != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("backtest_report_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, if (report.netProfitUsdt >= 0) CryptoGreen.copy(alpha = 0.5f) else CryptoRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "KẾT QUẢ MÔ PHỎNG", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val isWin = report.netProfitUsdt >= 0
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isWin) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isWin) "LỢI NHUẬN DƯƠNG" else "THUA LỖ",
                                    color = if (isWin) CryptoGreen else CryptoRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Lợi nhuận ròng", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = "${if (report.netProfitUsdt >= 0) "+" else ""}${"%.2f".format(report.netProfitUsdt)} USDT",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.netProfitUsdt >= 0) CryptoGreen else CryptoRed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Tỷ suất ROI", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = "${if (report.netProfitPercent >= 0) "+" else ""}${"%.2f".format(report.netProfitPercent)}%",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.netProfitPercent >= 0) CryptoGreen else CryptoRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Tỷ lệ thắng (Winrate)", fontSize = 11.sp, color = TextMuted)
                                Text(text = "${"%.1f".format(report.winratePercent)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CryptoGold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Tổng số chu kỳ giỏ", fontSize = 11.sp, color = TextMuted)
                                Text(text = "${report.totalCycles}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Max Drawdown", fontSize = 11.sp, color = TextMuted)
                                Text(text = "-${"%.2f".format(report.maxDrawdownPercent)}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CryptoRed)
                            }
                        }
                    }
                }
            }

            if (report.cycles.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CHI TIẾT CÁC CHU KỲ (${report.cycles.size})",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Nhấn vào từng chu kỳ để xem chi tiết từng lệnh mua/bán và lãi lỗ của từng coin:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        // Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = cycleFilter == "ALL",
                                onClick = { cycleFilter = "ALL" },
                                label = { Text("Tất cả (${report.cycles.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CryptoGold.copy(alpha = 0.2f),
                                    selectedLabelColor = CryptoGold,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = cycleFilter == "ALL",
                                    borderColor = if (cycleFilter == "ALL") CryptoGold else DarkBorder
                                )
                            )
                            val winCount = report.cycles.count { it.realizedProfitUsdt >= 0 }
                            FilterChip(
                                selected = cycleFilter == "WIN",
                                onClick = { cycleFilter = "WIN" },
                                label = { Text("Thắng ($winCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CryptoGreen.copy(alpha = 0.2f),
                                    selectedLabelColor = CryptoGreen,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = cycleFilter == "WIN",
                                    borderColor = if (cycleFilter == "WIN") CryptoGreen else DarkBorder
                                )
                            )
                            val lossCount = report.cycles.count { it.realizedProfitUsdt < 0 }
                            FilterChip(
                                selected = cycleFilter == "LOSS",
                                onClick = { cycleFilter = "LOSS" },
                                label = { Text("Thua ($lossCount)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CryptoRed.copy(alpha = 0.2f),
                                    selectedLabelColor = CryptoRed,
                                    containerColor = DarkSurfaceElevated,
                                    labelColor = TextMuted
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = cycleFilter == "LOSS",
                                    borderColor = if (cycleFilter == "LOSS") CryptoRed else DarkBorder
                                )
                            )
                        }
                    }
                }

                val filteredCycles = when (cycleFilter) {
                    "WIN" -> report.cycles.filter { it.realizedProfitUsdt >= 0 }
                    "LOSS" -> report.cycles.filter { it.realizedProfitUsdt < 0 }
                    else -> report.cycles
                }

                items(filteredCycles.size) { index ->
                    val cycle = filteredCycles[index]
                    ExpandableBasketCycleCard(cycle = cycle)
                }
            }
        }
    }
}

@Composable
fun ExpandableBasketCycleCard(cycle: BasketCycleResult) {
    var isExpanded by remember { mutableStateOf(false) }
    val isWin = cycle.realizedProfitUsdt >= 0
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("basket_cycle_card_${cycle.cycleIndex}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, if (isWin) CryptoGreen.copy(alpha = 0.35f) else CryptoRed.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (cycle.slotId == BasketSlotId.PRIMARY) CryptoGold.copy(alpha = 0.2f) else Color(0xFF3861FB).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (cycle.slotId == BasketSlotId.PRIMARY) "SLOT 1 • 07:00" else "SLOT 2 • 19:00",
                                color = if (cycle.slotId == BasketSlotId.PRIMARY) CryptoGold else Color(0xFF6B8AFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Exit Reason Badge
                        val reasonColor = when (cycle.exitReason) {
                            "TRAILING_STOP_HIT" -> CryptoGreen
                            "TP_TARGET_HIT" -> Color(0xFF00E5FF)
                            "TIMEOUT_144H", "TIMEOUT_72H", "TIMEOUT_IN_TRAIL" -> Color(0xFFFFB300)
                            "OPEN_AT_END" -> Color(0xFF81D4FA)
                            else -> CryptoRed
                        }
                        val reasonText = when (cycle.exitReason) {
                            "TRAILING_STOP_HIT" -> "TRAILING HIT"
                            "TP_TARGET_HIT" -> "TP TARGET"
                            "TIMEOUT_144H" -> "TIMEOUT 144H"
                            "TIMEOUT_72H" -> "TIMEOUT 72H"
                            "SL_HIT_RELAXED" -> "SL RELAXED"
                            "SL_HIT" -> "STOP LOSS"
                            "OPEN_AT_END" -> "CUỐI KỲ TEST"
                            else -> cycle.exitReason
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = reasonColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = reasonText,
                                color = reasonColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Chu kỳ #${cycle.cycleIndex} • Giữ ${"%.1f".format(cycle.holdingDays)} ngày • ${cycle.coinsCount} coin",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${dateFormat.format(Date(cycle.openTime))} ➔ ${dateFormat.format(Date(cycle.closeTime))}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (isWin) "+" else ""}${"%.2f".format(cycle.realizedProfitUsdt)} USDT",
                            color = if (isWin) CryptoGreen else CryptoRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${if (cycle.basketRoiPercent >= 0) "+" else ""}${"%.2f".format(cycle.basketRoiPercent)}%",
                            color = if (isWin) CryptoGreen else CryptoRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                        tint = CryptoGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Expanded content: Individual coins
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Vốn đầu tư giỏ: ${"%.1f".format(cycle.totalInvested)} USDT",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Equity sau đóng: ${"%.1f".format(cycle.equityAfterClose)} USDT",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "CHI TIẾT TỪNG LỆNH TRONG GIỎ (${cycle.coinsCount} COIN):",
                        color = CryptoGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (cycle.positionDetails.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            cycle.positionDetails.forEach { pos ->
                                CoinPositionItemCard(pos = pos)
                            }
                        }
                    } else {
                        // Fallback nếu chu kỳ chưa có positionDetails chi tiết
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            cycle.symbolsList.forEach { sym ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DarkSurfaceElevated, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = sym, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "Đã khớp trong giỏ", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoinPositionItemCard(pos: BasketPositionDetail) {
    val isWin = pos.pnlUsdt >= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(0.5.dp, if (isWin) CryptoGreen.copy(alpha = 0.25f) else CryptoRed.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CryptoGold.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = pos.symbol, color = CryptoGold, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        text = "Vốn: ${"%.1f".format(pos.marginAllocated)} USDT",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${if (isWin) "+" else ""}${"%.2f".format(pos.pnlUsdt)} USDT",
                        color = if (isWin) CryptoGreen else CryptoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isWin) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${if (pos.roiPercent >= 0) "+" else ""}${"%.2f".format(pos.roiPercent)}%",
                            color = if (isWin) CryptoGreen else CryptoRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vào: ${"%.4f".format(pos.entryPrice)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "➔",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Text(
                    text = "Ra: ${"%.4f".format(pos.exitPrice)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Qty: ${"%.2f".format(pos.quantity)}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
