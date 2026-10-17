package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.example.model.BinanceTicker
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import androidx.compose.foundation.BorderStroke
import com.example.model.VolumeType
import com.example.ui.MarketFilter
import com.example.ui.UiState
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBg
import androidx.compose.material3.CardDefaults
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
    openPositions: List<TradePosition>,
    onSelectCoin: (String) -> Unit,
    onOpenApiConfig: () -> Unit = {}
) {
    val apiConfig by viewModel.apiConfig.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    // PIN verification state for entering orders
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingOrderAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinActionTitle by remember { mutableStateOf("vào lệnh") }

    val performWithPinCheck: (String, () -> Unit) -> Unit = { title, action ->
        pinActionTitle = title
        pendingOrderAction = action
        showPinDialog = true
    }

    if (showPinDialog) {
        PinVerificationDialog(
            actionTitle = pinActionTitle,
            onVerify = { pin -> viewModel.authorizePin(pin) },
            onSuccess = {
                pendingOrderAction?.invoke()
                pendingOrderAction = null
            },
            onDismiss = {
                showPinDialog = false
                pendingOrderAction = null
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = uiState.settings,
            apiConfig = apiConfig,
            onSave = { viewModel.updateSettings(it) },
            onResetAccount = { viewModel.resetAccountBalance() },
            onSaveApiCredentials = { apiKey, secret, pin, isTestnet, liveTrading, onResult ->
                viewModel.saveAndVerifyApiCredentials(
                    apiKey = apiKey,
                    apiSecret = secret,
                    accessPin = pin,
                    isTestnet = isTestnet,
                    liveTradingEnabled = liveTrading,
                    onResult = onResult
                )
            },
            onOpenApiConfig = {
                showSettingsDialog = false
                onOpenApiConfig()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    val openSymbols = remember(openPositions) { openPositions.map { it.symbol }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(CryptoGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Binance Futures",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "MA20 > SuperTrend(20,4) Bot",
                        color = CryptoGold,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Prominent API Status / Config Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (apiConfig.isConnected) CryptoGreen.copy(alpha = 0.15f) else CryptoGold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (apiConfig.isConnected) CryptoGreen.copy(alpha = 0.5f) else CryptoGold.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable { onOpenApiConfig() }
                        .testTag("top_api_pill_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = if (apiConfig.isConnected) CryptoGreen else CryptoGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (apiConfig.isConnected)
                                (if (apiConfig.isTestnet) "DEMO API ✓" else "REAL API ✓")
                            else
                                "NHẬP API",
                            color = if (apiConfig.isConnected) CryptoGreen else CryptoGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // API Key Config Button
                IconButton(
                    onClick = onOpenApiConfig,
                    modifier = Modifier.testTag("top_api_config_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (apiConfig.isConnected) {
                                Badge(
                                    containerColor = CryptoGreen,
                                    modifier = Modifier.size(6.dp)
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Cấu hình API Binance",
                            tint = if (apiConfig.isConnected) CryptoGold else TextSecondary
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.refreshTickers() },
                    modifier = Modifier.testTag("refresh_button")
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            color = CryptoGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = TextSecondary)
                    }
                }
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Cấu hình", tint = TextSecondary)
                }
            }
        }

        // Account & Auto-Trade Control Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.clickable { onOpenApiConfig() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (apiConfig.isConnected) "Số Dư Binance Futures" else "Số Dư Mô Phỏng",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (apiConfig.isConnected) CryptoGreen.copy(alpha = 0.18f) else DarkSurfaceHighlight
                            ) {
                                Text(
                                    text = if (apiConfig.isConnected)
                                        (if (apiConfig.isTestnet) "Testnet" else "Live")
                                    else
                                        "Mô Phỏng",
                                    color = if (apiConfig.isConnected) CryptoGreen else TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (apiConfig.isConnected && apiConfig.totalWalletBalance != null)
                                "$${String.format(Locale.US, "%,.2f", apiConfig.totalWalletBalance)} USDT"
                            else
                                "$${String.format(Locale.US, "%,.2f", uiState.settings.simulatedBalanceUsdt)} USDT",
                            color = if (apiConfig.isConnected) CryptoGreen else CryptoGold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Auto-trade toggle switch
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (uiState.settings.autoTradeEnabled) "AUTO-TRADE: BẬT" else "AUTO-TRADE: TẮT",
                                color = if (uiState.settings.autoTradeEnabled) CryptoGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.settings.autoTradeEnabled)
                                    "Tự động mở vị thế (5s/lệnh)"
                                else
                                    "Chỉ quét, không mở vị thế",
                                color = if (uiState.settings.autoTradeEnabled) CryptoGreen.copy(alpha = 0.85f) else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.settings.autoTradeEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    performWithPinCheck("bật Bot tự động vào lệnh") {
                                        viewModel.toggleAutoTrade(true)
                                    }
                                } else {
                                    viewModel.toggleAutoTrade(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBg,
                                checkedTrackColor = CryptoGreen,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceHighlight
                            ),
                            modifier = Modifier.testTag("auto_trade_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compact 4-Hour Scan Schedule (Starting 7:00 UTC+7) & Full Scanner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceHighlight)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quét 4H (7:00 UTC+7):",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = CryptoGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = uiState.countdownText.ifEmpty { "Đang tính..." },
                                    color = CryptoGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (uiState.isScanning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = CryptoGold,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${(uiState.scanPercent * 100).toInt()}%",
                                    color = CryptoGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            Surface(
                                color = CryptoGold,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable { viewModel.scanAllFuturesCoins(isAutoScheduled = false) }
                                    .testTag("scan_all_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = DarkBg,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Quét (${uiState.tickers.size})",
                                        color = DarkBg,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isScanning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.scanProgress,
                                color = CryptoGold,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { uiState.scanPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = CryptoGold,
                            trackColor = DarkSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (uiState.settings.autoTradeEnabled)
                            "• Tự động quét 4H & tự động mở vị thế (Auto-trade: BẬT)"
                        else
                            "• Tự động quét 4H (Chỉ quét tín hiệu, KHÔNG mở vị thế vì Auto-trade: TẮT)",
                        color = if (uiState.settings.autoTradeEnabled) CryptoGreen.copy(alpha = 0.85f) else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Search Bar with clear no-USDT instruction
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Nhập tên coin (ví dụ: BTC, ETH, SOL, DOGE...)", fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = CryptoGold)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            color = CryptoGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa tìm kiếm", tint = TextSecondary)
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CryptoGold,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("search_coin_input")
        )

        // Quick Suggestion Chips for Popular Coins (Tap to search without typing USDT)
        val popularCoins = remember { listOf("BTC", "ETH", "SOL", "BNB", "XRP", "DOGE", "PEPE", "SUI") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            popularCoins.take(6).forEach { coin ->
                val isSelected = uiState.searchQuery.trim().equals(coin, ignoreCase = true)
                Surface(
                    color = if (isSelected) CryptoGold else DarkSurfaceHighlight,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable {
                        if (isSelected) viewModel.setSearchQuery("") else viewModel.setSearchQuery(coin)
                    }
                ) {
                    Text(
                        text = coin,
                        color = if (isSelected) DarkBg else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Dedicated Search Suggestions Dropdown Card when searching coin name
        if (uiState.searchQuery.trim().isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.isSearching) {
                                "Đang tìm kiếm \"${uiState.searchQuery.trim()}\"..."
                            } else {
                                "Gợi ý khớp với \"${uiState.searchQuery.trim()}\" (Bấm để xem nến H4):"
                            },
                            color = CryptoGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Đóng",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { viewModel.setSearchQuery("") }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val searchMatches = uiState.coinStateList.take(5)
                    if (searchMatches.isEmpty() && !uiState.isSearching) {
                        Text(
                            text = "Không tìm thấy coin nào phù hợp với \"${uiState.searchQuery}\"",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        searchMatches.forEach { ticker ->
                            val baseName = ticker.symbol.removeSuffix("USDT")
                            val isBullish = viewModel.repository.getCachedSignal(ticker.symbol) == true

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        onSelectCoin(ticker.symbol)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = baseName,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "/USDT",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                    if (isBullish) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = CryptoGreen.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "MA20 > ST",
                                                color = CryptoGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$${CryptoRepository.formatPrice(ticker.lastPrice)}",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = CryptoGold,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ShowChart,
                                                contentDescription = null,
                                                tint = DarkBg,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Mở nến H4",
                                                color = DarkBg,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val unopenedBullishCount = remember(uiState.scannedBullishTickers, openSymbols) {
            uiState.scannedBullishTickers.count { !openSymbols.contains(it.symbol) }
        }

        // Scanned Signals Section (Displayed right below search bar as requested:
        // "Bên dưới mục tìm kiếm, hiển thị các tín hiệu sau khi được quét")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = CryptoGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Tín Hiệu Đã Quét",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = CryptoGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${uiState.scannedBullishTickers.size}",
                            color = CryptoGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Button to Open All Positions for Scanned Bullish Coins
                    if (uiState.scannedBullishTickers.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                uiState.isOpeningPositions -> CryptoGold.copy(alpha = 0.2f)
                                unopenedBullishCount > 0 -> CryptoGreen
                                else -> DarkSurfaceHighlight
                            },
                            modifier = Modifier
                                .testTag("open_all_scanned_positions_button")
                                .clickable(enabled = !uiState.isOpeningPositions && unopenedBullishCount > 0) {
                                    performWithPinCheck("mở toàn bộ vị thế") {
                                        viewModel.openAllScannedPositions(isAuto = false)
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                if (uiState.isOpeningPositions) {
                                    CircularProgressIndicator(
                                        color = CryptoGold,
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (unopenedBullishCount > 0) Icons.Default.Bolt else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (unopenedBullishCount > 0) DarkBg else CryptoGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        uiState.isOpeningPositions -> "Đang mở (5s/lệnh)..."
                                        unopenedBullishCount > 0 -> "Mở toàn bộ ($unopenedBullishCount)"
                                        else -> "Đã mở tất cả"
                                    },
                                    color = when {
                                        uiState.isOpeningPositions -> CryptoGold
                                        unopenedBullishCount > 0 -> DarkBg
                                        else -> TextSecondary
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL) "Đang lọc" else "Xem tất cả",
                        color = CryptoGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { viewModel.setFilterMode(MarketFilter.BULLISH_SIGNAL) }
                            .padding(4.dp)
                    )
                }
            }

            if (uiState.isOpeningPositions && uiState.openingPositionsProgress.isNotEmpty()) {
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
                        CircularProgressIndicator(
                            color = CryptoGold,
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.openingPositionsProgress,
                            color = CryptoGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (uiState.scannedBullishTickers.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceHighlight
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.isScanning) "Đang quét tín hiệu toàn bộ Binance Futures..." else "Chưa có tín hiệu tăng mới",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        if (!uiState.isScanning) {
                            Text(
                                text = "Quét ngay",
                                color = CryptoGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { viewModel.scanAllFuturesCoins(isAutoScheduled = false) }
                            )
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.scannedBullishTickers, key = { "scanned_${it.symbol}" }) { ticker ->
                        val isOpen = openSymbols.contains(ticker.symbol)
                        ScannedSignalCard(
                            ticker = ticker,
                            isOpen = isOpen,
                            onClick = {
                                onSelectCoin(ticker.symbol)
                            },
                            onOpenPosition = {
                                performWithPinCheck("mở vị thế ${ticker.symbol}") {
                                    viewModel.executeBuy(ticker.symbol, isAuto = false)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.filterMode == MarketFilter.ALL,
                onClick = { viewModel.setFilterMode(MarketFilter.ALL) },
                label = { Text("Tất Cả (${uiState.tickers.size})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CryptoGold,
                    selectedLabelColor = DarkBg,
                    labelColor = TextSecondary
                )
            )
            FilterChip(
                selected = uiState.filterMode == MarketFilter.BULLISH_SIGNAL,
                onClick = { viewModel.setFilterMode(MarketFilter.BULLISH_SIGNAL) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ Tín Hiệu Tăng", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (uiState.bullishCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL) DarkBg else CryptoGreen,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${uiState.bullishCount}",
                                    color = if (uiState.filterMode == MarketFilter.BULLISH_SIGNAL) CryptoGreen else DarkBg,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Text(" (0)", fontSize = 11.sp)
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CryptoGreen,
                    selectedLabelColor = DarkBg,
                    labelColor = if (uiState.bullishCount > 0) CryptoGreen else TextSecondary
                )
            )
            FilterChip(
                selected = uiState.filterMode == MarketFilter.OPEN_POSITIONS,
                onClick = { viewModel.setFilterMode(MarketFilter.OPEN_POSITIONS) },
                label = { Text("Vị Thế Mở (${openPositions.size})", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CryptoGold,
                    selectedLabelColor = DarkBg,
                    labelColor = TextSecondary
                )
            )
        }

        // Search feedback header when query is active
        if (uiState.searchQuery.trim().isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.isSearching) {
                        "Đang tìm kiếm cho: \"${uiState.searchQuery.trim()}\"..."
                    } else {
                        "Kết quả tìm kiếm cho: \"${uiState.searchQuery.trim()}\" (${uiState.coinStateList.size} coin)"
                    },
                    color = CryptoGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Bỏ lọc",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { viewModel.setSearchQuery("") }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Coin List (backed by debounced coinStateList)
        val displayList = if (uiState.searchQuery.trim().isNotEmpty()) {
            uiState.coinStateList
        } else {
            uiState.coinStateList.ifEmpty {
                if (uiState.filterMode == MarketFilter.ALL) uiState.tickers else emptyList()
            }
        }

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (uiState.isRefreshing) "Đang tải danh sách Binance Futures..." else "Không tìm thấy hợp đồng phù hợp",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayList, key = { it.symbol }) { ticker ->
                    CoinCardItem(
                        ticker = ticker,
                        isOpenPosition = openSymbols.contains(ticker.symbol),
                        isBullishSignal = viewModel.repository.getCachedSignal(ticker.symbol),
                        onClick = { onSelectCoin(ticker.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
fun CoinCardItem(
    ticker: BinanceTicker,
    isOpenPosition: Boolean,
    isBullishSignal: Boolean?,
    onClick: () -> Unit
) {
    val isProfit = ticker.priceChangePercent >= 0
    val changeSign = if (isProfit) "+" else ""

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isOpenPosition) CryptoGold else if (isBullishSignal == true) CryptoGreen.copy(alpha = 0.5f) else DarkBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Symbol & Status Badge
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ticker.symbol.removeSuffix("USDT"),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/USDT",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Signal indicator badge
                when {
                    isOpenPosition -> {
                        Surface(
                            color = CryptoGold.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ĐANG MỞ LỆNH",
                                color = CryptoGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    isBullishSignal == true -> {
                        Surface(
                            color = CryptoGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = CryptoGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "MA20 > ST (TĂNG)",
                                    color = CryptoGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    isBullishSignal == false -> {
                        Surface(
                            color = CryptoRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "MA20 ≤ ST",
                                color = CryptoRed.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Khối lượng: $${String.format(Locale.US, "%,.0f", ticker.volume * ticker.lastPrice)}",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Right Column: Price & 24h Change %
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${CryptoRepository.formatPrice(ticker.lastPrice)}",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = if (isProfit) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "$changeSign${String.format(Locale.US, "%.2f", ticker.priceChangePercent)}%",
                        color = if (isProfit) CryptoGreen else CryptoRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "Nến H4",
                        color = CryptoGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ScannedSignalCard(
    ticker: BinanceTicker,
    isOpen: Boolean = false,
    onClick: () -> Unit,
    onOpenPosition: (() -> Unit)? = null
) {
    val baseName = ticker.symbol.removeSuffix("USDT")
    val isPositive = ticker.priceChangePercent >= 0.0

    Surface(
        modifier = Modifier
            .width(175.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("scanned_signal_${ticker.symbol}"),
        color = DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOpen) CryptoGreen else CryptoGreen.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = baseName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/USDT",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Surface(
                    color = (if (isPositive) CryptoGreen else CryptoRed).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.1f", ticker.priceChangePercent)}%",
                        color = if (isPositive) CryptoGreen else CryptoRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$${CryptoRepository.formatPrice(ticker.lastPrice)}",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                if (isOpen) {
                    Surface(
                        color = CryptoGreen.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "✓ Vị thế mở",
                            color = CryptoGreen,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                if (!isOpen && onOpenPosition != null) {
                    Surface(
                        color = CryptoGreen,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .testTag("open_position_${ticker.symbol}")
                            .clickable { onOpenPosition() }
                    ) {
                        Text(
                            text = "+ Mở vị thế",
                            color = DarkBg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        color = CryptoGreen.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "MA20 > ST",
                            color = CryptoGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = CryptoGold,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = DarkBg,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "Nến H4",
                            color = DarkBg,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
