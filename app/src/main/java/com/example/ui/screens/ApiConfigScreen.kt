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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceAccountVerificationResult
import com.example.model.TradeSettings
import com.example.model.TradingStrategyType
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
import java.util.Locale

@Composable
fun ApiConfigScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val apiConfig by viewModel.apiConfig.collectAsState()
    val isVerifying by viewModel.isVerifyingApi.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .testTag("api_config_screen")
    ) {
        // TOP HEADER
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurfaceElevated,
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_back_settings")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column {
                        Text(
                            text = "Cài Đặt & Cấu Hình Hệ Thống",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedTab == 0) "Thông số chiến lược (Đồng bộ Backtest)" else "Kết nối tài khoản sàn Binance",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (apiConfig.isConnected) CryptoGreen.copy(alpha = 0.18f) else DarkSurfaceHighlight
                ) {
                    Text(
                        text = if (apiConfig.isConnected) "API SẴN SÀNG ✓" else "CHƯA KẾT NỐI",
                        color = if (apiConfig.isConnected) CryptoGreen else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // TAB ROW SELECTOR
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = CryptoGold,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CryptoGold,
                    height = 2.5.dp
                )
            },
            divider = { HorizontalDivider(color = DarkBorder, thickness = 1.dp) }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("tab_strategy_settings"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (selectedTab == 0) CryptoGold else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cài Đặt Chiến Lược",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) CryptoGold else TextMuted
                        )
                    }
                }
            )

            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("tab_binance_api"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = if (selectedTab == 1) CryptoGold else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Kết Nối API Binance",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) CryptoGold else TextMuted
                        )
                    }
                }
            )
        }

        // TAB CONTENT
        if (selectedTab == 0) {
            StrategySettingsContent(viewModel = viewModel, currentSettings = uiState.settings)
        } else {
            BinanceApiSettingsContent(viewModel = viewModel, apiConfig = apiConfig, isVerifying = isVerifying)
        }
    }
}

/**
 * Tab Cài Đặt Chiến Lược Giao Dịch
 * Tab Cài Đặt Chiến Lược Giao Dịch
 * Tham số chuẩn của Chiến Lược SMA20 D1 (RR 1:3), đồng bộ tự động 2 chiều với Backtest.
 */
@Composable
private fun StrategySettingsContent(
    viewModel: MainViewModel,
    currentSettings: TradeSettings
) {
    // Form state variables initialized with current settings
    var simulatedBalanceText by remember(currentSettings.simulatedBalanceUsdt) {
        mutableStateOf(currentSettings.simulatedBalanceUsdt.toInt().toString())
    }
    var capitalPerTradeUsdtText by remember(currentSettings.capitalPerTradeUsdt) {
        mutableStateOf(if (currentSettings.capitalPerTradeUsdt > 0.0) currentSettings.capitalPerTradeUsdt.toString() else "5.1")
    }
    var smaPeriodText by remember(currentSettings.smaPeriod) {
        mutableStateOf(currentSettings.smaPeriod.toString())
    }
    var rrRatioText by remember(currentSettings.rrRatio) {
        mutableStateOf(currentSettings.rrRatio.toString())
    }

    var saveSuccessMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Banner thông báo đồng bộ 2 chiều
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = CryptoGold.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = CryptoGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Đồng Bộ Tự Động Với Backtest",
                        color = CryptoGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Mọi thay đổi lưu tại đây tự động cập nhật sang Thẻ Backtest. Chiến lược: SMA20 D1 (RR 1:3), SL Đáy t-1, TP 3R.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // ==========================================
        // NHÓM 1: THÔNG TIN CHIẾN LƯỢC SMA20 D1
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth().testTag("strategy_group_1_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "1. Chiến Lược SMA20 D1 (RR 1:3)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CryptoGold.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "NGUYÊN TẮC HOẠT ĐỘNG",
                                color = CryptoGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Quét nến D1: 07:00 AM UTC+7 (00:00 UTC) hàng ngày\n• Điều kiện vào lệnh: Nến t-1 tăng (Close > Open), Close > SMA20 và Low <= SMA20\n• Stop Loss (SL): Đáy nến tín hiệu t-1 (Low[t-1])\n• Take Profit (TP): Cố định 3R (Entry + 3R)\n• Danh mục: Mở tất cả coin đạt chuẩn, không giới hạn trần. Bỏ bộ lọc BTC.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ==========================================
        // NHÓM 2: QUẢN TRỊ VỐN & THAM SỐ
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth().testTag("strategy_group_2_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccountBalanceWallet, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "2. Quản Trị Vốn & Tham Số (Long Cross 1x)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "Tổng vốn quản lý / Giả lập (USDT)", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = simulatedBalanceText,
                    onValueChange = { simulatedBalanceText = it },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Chu kỳ SMA (Nến D1)", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = smaPeriodText,
                            onValueChange = { smaPeriodText = it },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
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
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Tỷ lệ Chốt Lời RR", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = rrRatioText,
                            onValueChange = { rrRatioText = it },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Cắt lỗ Stop Loss (SL)", color = TextMuted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                                Text("Đáy nến t-1", color = CryptoRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Thông báo thành công
        saveSuccessMsg?.let { msg ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = CryptoGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = msg, color = CryptoGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // ACTION BUTTONS (LƯU, ĐỒNG BỘ, RESET)
        // ==========================================
        Button(
            onClick = {
                val parsedBalance = simulatedBalanceText.toDoubleOrNull() ?: currentSettings.simulatedBalanceUsdt
                val parsedCapitalPerTrade = capitalPerTradeUsdtText.toDoubleOrNull() ?: 5.1
                val parsedSmaPeriod = smaPeriodText.toIntOrNull() ?: 20
                val parsedRrRatio = rrRatioText.toDoubleOrNull() ?: 3.0

                val newSettings = currentSettings.copy(
                    selectedStrategy = TradingStrategyType.SMA20_D1_RR3,
                    simulatedBalanceUsdt = parsedBalance,
                    capitalPerTradeUsdt = parsedCapitalPerTrade,
                    fixedVolumeUsdt = parsedCapitalPerTrade,
                    volumeType = com.example.model.VolumeType.FIXED_AMOUNT,
                    perTradePct = if (parsedBalance > 0) (parsedCapitalPerTrade / parsedBalance) else 0.0051,
                    maxPortfolioAllocation = 1.0,
                    smaPeriod = parsedSmaPeriod,
                    rrRatio = parsedRrRatio,
                    enableTrailingStop = false,
                    enableFloorLock = false,
                    enableBtcMacroFilter = false,
                    enableBtcCircuitBreaker = false,
                    enableSmartTimeout = false
                )

                viewModel.updateStrategySettingsAndSyncWithBacktest(newSettings)
                saveSuccessMsg = "✓ Đã lưu cài đặt chiến lược SMA20 D1 & tự động đồng bộ sang thẻ Backtest!"
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("btn_save_and_sync_strategy"),
            colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "LƯU & ĐỒNG BỘ VỚI BACKTEST", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.syncStrategySettingsFromBacktest()
                    saveSuccessMsg = "✓ Đã nạp và cập nhật toàn bộ cấu hình từ thẻ Backtest!"
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("btn_copy_from_backtest"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Lấy từ Backtest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    val defaultSettings = TradeSettings()
                    viewModel.updateStrategySettingsAndSyncWithBacktest(defaultSettings)
                    saveSuccessMsg = "✓ Đã khôi phục bộ thông số SMA20 D1 mặc định!"
                },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("btn_reset_strategy_defaults"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                border = BorderStroke(1.dp, DarkBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Khôi phục chuẩn", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tab Kết Nối API Binance (Testnet & Mainnet)
 */
@Composable
private fun BinanceApiSettingsContent(
    viewModel: MainViewModel,
    apiConfig: com.example.model.BinanceApiConfig,
    isVerifying: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val publicIpAddress by viewModel.publicIpAddress.collectAsState()
    val isFetchingIp by viewModel.isFetchingIp.collectAsState()
    var isIpCopied by remember { mutableStateOf(false) }

    val defaultInitKey = if (apiConfig.apiKey.isNotBlank()) apiConfig.apiKey else com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY
    val defaultInitSecret = if (apiConfig.apiSecret.isNotBlank()) apiConfig.apiSecret else com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_SECRET

    var apiKey by remember(apiConfig) { mutableStateOf(defaultInitKey) }
    var apiSecret by remember(apiConfig) { mutableStateOf(defaultInitSecret) }
    var accessPin by remember(apiConfig) { mutableStateOf(apiConfig.accessPin.ifBlank { "1234" }) }
    val isTestnet = false
    var liveTradingEnabled by remember(apiConfig) { mutableStateOf(true) }

    var showSecret by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<BinanceAccountVerificationResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Connection Status Banner
        if (apiConfig.isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CryptoGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CryptoGreen.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CryptoGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tài Khoản Live Mainnet Binance (fapi.binance.com)",
                            color = CryptoGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val bal = apiConfig.totalWalletBalance
                        Text(
                            text = if (bal != null) "Số dư ví: $${String.format(Locale.US, "%,.2f", bal)} USDT" else "Đã kết nối thành công",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 1. THẺ ĐỊA CHỈ IP WHITELIST BINANCE
        Card(
            modifier = Modifier.fillMaxWidth().testTag("card_ip_whitelist"),
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
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1. Địa Chỉ IP Của App (Để Whitelist Binance)",
                            color = CryptoGold,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = {
                            isIpCopied = false
                            viewModel.fetchPublicIpAddress(forceRefresh = true)
                        },
                        modifier = Modifier.size(28.dp).testTag("btn_refresh_ip")
                    ) {
                        if (isFetchingIp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = CryptoGold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Làm mới IP",
                                tint = CryptoGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Outbound Public IP:",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = publicIpAddress ?: if (isFetchingIp) "Đang truy vấn IP..." else "Chưa lấy được IP (Bấm làm mới)",
                                color = if (publicIpAddress != null) CryptoGreen else CryptoGold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                publicIpAddress?.let { ip ->
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(ip))
                                    isIpCopied = true
                                }
                            },
                            enabled = publicIpAddress != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIpCopied) CryptoGreen else CryptoGold,
                                contentColor = DarkBg
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("btn_copy_ip")
                        ) {
                            Icon(
                                imageVector = if (isIpCopied) Icons.Default.Check else Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isIpCopied) "ĐÃ SAO CHÉP" else "SAO CHÉP IP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CryptoGold.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, CryptoGold.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(14.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hướng dẫn Whitelist: Vào Binance > API Management > Chỉnh sửa API Key > Chọn 'Restrict access to trusted IPs only' và dán IP này vào. Điều này giúp API không bị lỗi từ chối IP và bảo vệ an toàn số dư tài khoản.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            lineHeight = 13.5.sp
                        )
                    }
                }
            }
        }

        // API Credentials Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "2. Khóa API (HMAC-SHA256)",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // API Key Field
                Text(text = "Binance API Key", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    placeholder = { Text("Nhập API Key...", fontSize = 11.sp) },
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (apiKey.isNotEmpty()) {
                                IconButton(onClick = { apiKey = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { apiKey = it.trim() }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán", tint = CryptoGold, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // API Secret Field
                Text(text = "Binance API Secret", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it.trim() },
                    placeholder = { Text("Nhập Secret Key...", fontSize = 11.sp) },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecret) "Ẩn Secret" else "Hiện Secret",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("api_secret_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Access PIN Field
                Text(text = "Mã PIN Bảo Vệ (4-6 số)", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = accessPin,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) accessPin = it },
                    placeholder = { Text("Mã PIN (mặc định: 1234)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(14.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("access_pin_input")
                )

                if (!isTestnet) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceHighlight)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cho phép giao dịch tiền thật (Live Trading)",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lệnh khớp trực tiếp trên sàn Binance Mainnet",
                                color = CryptoRed.copy(alpha = 0.9f),
                                fontSize = 9.5.sp
                            )
                        }
                        Switch(
                            checked = liveTradingEnabled,
                            onCheckedChange = { liveTradingEnabled = it },
                            modifier = Modifier.scale(0.75f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBg,
                                checkedTrackColor = CryptoGreen,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurface
                            )
                        )
                    }
                }
            }
        }

        // Test Connection & Clear buttons
        Button(
            onClick = {
                if (apiKey.isEmpty() || apiSecret.isEmpty()) {
                    verificationResult = BinanceAccountVerificationResult(
                        isSuccess = false,
                        errorMessage = "Vui lòng nhập đầy đủ API Key và API Secret trước khi kiểm tra."
                    )
                } else {
                    viewModel.saveAndVerifyApiCredentials(
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        accessPin = accessPin,
                        isTestnet = isTestnet,
                        liveTradingEnabled = liveTradingEnabled
                    ) { result ->
                        verificationResult = result
                    }
                }
            },
            enabled = !isVerifying,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("test_api_connection_button"),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg)
        ) {
            if (isVerifying) {
                CircularProgressIndicator(color = DarkBg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Đang kết nối Binance Futures...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "KIỂM TRA KẾT NỐI BINANCE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (apiConfig.isConfigured || apiKey.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    apiKey = ""
                    apiSecret = ""
                    accessPin = "1234"
                    verificationResult = null
                    viewModel.clearApiCredentials()
                },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Xóa thông tin API đã lưu", fontSize = 11.sp)
            }
        }

        // Verification Result Card
        verificationResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isSuccess) CryptoGreen.copy(alpha = 0.12f) else CryptoRed.copy(alpha = 0.12f)
                ),
                border = BorderStroke(1.dp, if (result.isSuccess) CryptoGreen.copy(alpha = 0.5f) else CryptoRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.isSuccess) CryptoGreen else CryptoRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (result.isSuccess) "KẾT NỐI THÀNH CÔNG!" else "LỖI KẾT NỐI",
                            color = if (result.isSuccess) CryptoGreen else CryptoRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (result.isSuccess) {
                        Text(
                            text = "• Tổng số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT\n" +
                                    "• Số dư khả dụng: $${String.format(Locale.US, "%,.2f", result.availableBalance)} USDT\n" +
                                    "• Quyền Futures: ${if (result.canTrade) "HỢP LỆ ✓" else "Chưa kích hoạt"}",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    } else {
                        Text(
                            text = result.errorMessage.ifEmpty { "Không thể xác thực khóa API với Binance." },
                            color = CryptoRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Security Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Hướng Dẫn Bảo Mật Binance API", color = CryptoGold, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. Truy cập Binance > Quản lý API > Tạo khóa mới\n" +
                            "2. CHỈ cấp quyền: Đọc (Read) & Hợp đồng tương lai (Enable Futures)\n" +
                            "3. TUYỆT ĐỐI KHÔNG cấp quyền Rút tiền (Enable Withdrawals)\n" +
                            "4. Khóa API lưu trữ an toàn bằng EncryptedSharedPreferences.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
