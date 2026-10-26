package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.AiAssistantViewModel
import com.example.ai.ScreenContextBuilder
import com.example.ui.MainViewModel
import com.example.ui.components.AiAssistantBottomSheet
import com.example.ui.components.FloatingAiButton
import com.example.ui.components.PinVerificationDialog
import com.example.ui.components.TradeAlertBanner
import com.example.model.BacktestConfig
import com.example.model.TradeAlert
import com.example.model.TradingStrategyType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.screens.ApiConfigScreen
import com.example.ui.screens.BacktestScreen
import com.example.ui.screens.CoinDetailScreen
import com.example.ui.screens.MarketScreen
import com.example.ui.screens.PositionsScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

enum class NavScreen {
    MARKET,
    POSITIONS,
    BACKTEST,
    ANALYTICS,
    API_CONFIG,
    DETAIL
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Xin quyền hiển thị thông báo trên Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(NavScreen.MARKET) }
    var previousScreen by remember { mutableStateOf(NavScreen.MARKET) }

    val uiState by viewModel.uiState.collectAsState()
    val apiConfig by viewModel.apiConfig.collectAsState()
    val openPositions by viewModel.openPositions.collectAsState()
    val closedPositions by viewModel.closedPositions.collectAsState()
    val binancePortfolio by viewModel.binancePortfolio.collectAsState()
    val livePositionsTpSl by viewModel.livePositionsTpSl.collectAsState()

    var showMainnetPinDialog by remember { mutableStateOf(false) }
    var showBotConfirmDialog by remember { mutableStateOf(false) }
    var showQuickAlertsDialog by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    val aiViewModel: AiAssistantViewModel = viewModel()

    // Số lượng lệnh đang mở
    val openCount = if (apiConfig.isConnected) binancePortfolio.positions.size else openPositions.size
    val unreadAlertsCount = uiState.alertHistory.size + (if (uiState.activeAlert != null) 1 else 0)

    if (showQuickAlertsDialog) {
        QuickAlertsDialog(
            alerts = uiState.alertHistory,
            activeAlert = uiState.activeAlert,
            onDismiss = { showQuickAlertsDialog = false },
            onClearAll = {
                viewModel.clearAllAlerts()
                showQuickAlertsDialog = false
            }
        )
    }

    if (showMainnetPinDialog) {
        PinVerificationDialog(
            actionTitle = "Kích hoạt LIVE MAINNET (Tiền thật)",
            onVerify = { pin -> viewModel.authorizePin(pin) },
            onSuccess = {
                viewModel.toggleTradingMode(toLiveMainnet = true)
                showMainnetPinDialog = false
            },
            onDismiss = { showMainnetPinDialog = false }
        )
    }

    if (showBotConfirmDialog) {
        val currentBConfig by viewModel.backtestConfig.collectAsState()
        BotParametersConfirmationDialog(
            initialConfig = currentBConfig,
            initialCapitalPerTrade = uiState.settings.capitalPerTradeUsdt,
            onConfirmAndStart = { modifiedConfig, modifiedCapital ->
                viewModel.saveCustomBotConfigAndStart(modifiedConfig, modifiedCapital)
                showBotConfirmDialog = false
            },
            onDismiss = {
                showBotConfirmDialog = false
            },
            onFetchOptimalAiConfig = {
                viewModel.getOptimalAiConfig()
            }
        )
    }

    // Xử lý nút Back của Android khi đang ở màn hình phụ
    BackHandler(enabled = currentScreen != NavScreen.MARKET) {
        currentScreen = if (currentScreen == NavScreen.DETAIL && previousScreen != NavScreen.DETAIL) previousScreen else NavScreen.MARKET
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CryptoTraderTopBar(
                isBotRunning = uiState.settings.autoTradeEnabled,
                onToggleBot = { shouldTurnOn ->
                    if (shouldTurnOn) {
                        showBotConfirmDialog = true
                    } else {
                        viewModel.toggleAutoTrade(false)
                    }
                },
                onOpenSettings = {
                    previousScreen = currentScreen
                    currentScreen = NavScreen.API_CONFIG
                },
                onOpenAlerts = {
                    showQuickAlertsDialog = true
                },
                alertCount = unreadAlertsCount
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurfaceElevated,
                contentColor = Color.White,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                // Tab 1: Thị Trường
                NavigationBarItem(
                    selected = currentScreen == NavScreen.MARKET,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.MARKET
                    },
                    icon = {
                        Icon(Icons.Default.AutoGraph, contentDescription = "Thị trường")
                    },
                    label = {
                        Text("Thị trường", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_market")
                )

                // Tab 2: Biểu Đồ
                NavigationBarItem(
                    selected = currentScreen == NavScreen.DETAIL,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.DETAIL
                    },
                    icon = {
                        Icon(Icons.Default.ShowChart, contentDescription = "Biểu đồ")
                    },
                    label = {
                        Text("Biểu đồ", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_detail")
                )

                // Tab 3: Vị Thế
                NavigationBarItem(
                    selected = currentScreen == NavScreen.POSITIONS,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.POSITIONS
                        viewModel.refreshBinancePortfolio()
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (openCount > 0) {
                                    Badge(
                                        containerColor = CryptoGold,
                                        contentColor = DarkBg
                                    ) {
                                        Text("$openCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Vị thế")
                        }
                    },
                    label = {
                        Text("Vị thế", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_positions")
                )

                // Tab 4: Backtest
                NavigationBarItem(
                    selected = currentScreen == NavScreen.BACKTEST,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.BACKTEST
                    },
                    icon = {
                        Icon(Icons.Default.Science, contentDescription = "Backtest")
                    },
                    label = {
                        Text("Backtest", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_backtest")
                )

                // Tab 5: Thống kê (Lịch sử & Đề xuất AI)
                NavigationBarItem(
                    selected = currentScreen == NavScreen.ANALYTICS,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.ANALYTICS
                    },
                    icon = {
                        Icon(Icons.Default.Analytics, contentDescription = "Thống kê")
                    },
                    label = {
                        Text("Thống kê", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_analytics")
                )

                // Tab 4: Cài Đặt (API Config)
                NavigationBarItem(
                    selected = currentScreen == NavScreen.API_CONFIG,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.API_CONFIG
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (apiConfig.isConnected) {
                                    Badge(
                                        containerColor = CryptoGreen,
                                        contentColor = DarkBg
                                    ) {
                                        Text("✓", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Cài đặt")
                        }
                    },
                    label = {
                        Text("Cài đặt", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isLiveMainnet = apiConfig.isConnected && apiConfig.liveTradingEnabled

            // Cảnh báo màu đỏ khi giao dịch Live Mainnet
            if (isLiveMainnet) {
                LiveMainnetRiskWarningBanner()
            }

            // Khung nội dung màn hình chính
            Box(modifier = Modifier.weight(1f)) {
                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        NavScreen.MARKET -> {
                            MarketScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                openPositions = openPositions,
                                apiConfig = apiConfig,
                                binancePortfolio = binancePortfolio,
                                onSelectCoin = { symbol ->
                                    viewModel.selectSymbol(symbol, "4h")
                                    previousScreen = NavScreen.MARKET
                                    currentScreen = NavScreen.DETAIL
                                },
                                onPerformWithPinCheck = { action -> action() },
                                onOpenApiConfig = {
                                    previousScreen = NavScreen.MARKET
                                    currentScreen = NavScreen.API_CONFIG
                                }
                            )
                        }

                        NavScreen.POSITIONS -> {
                            PositionsScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                openPositions = openPositions,
                                closedPositions = closedPositions,
                                apiConfig = apiConfig,
                                binancePortfolio = binancePortfolio,
                                livePositionsTpSl = livePositionsTpSl,
                                onSelectCoin = { symbol ->
                                    viewModel.selectSymbol(symbol, "4h")
                                    previousScreen = NavScreen.POSITIONS
                                    currentScreen = NavScreen.DETAIL
                                },
                                onPerformWithPinCheck = { action -> action() },
                                onEditTpSlTarget = { sym, isLong, tp, sl, id ->
                                    viewModel.setPositionTpSl(sym, isLong, tp, sl, id)
                                }
                            )
                        }

                        NavScreen.BACKTEST -> {
                            BacktestScreen(
                                viewModel = viewModel,
                                closedPositions = closedPositions
                            )
                        }

                        NavScreen.ANALYTICS -> {
                            StatisticsScreen(
                                viewModel = viewModel,
                                onNavigateToBacktest = {
                                    previousScreen = currentScreen
                                    currentScreen = NavScreen.BACKTEST
                                }
                            )
                        }

                        NavScreen.API_CONFIG -> {
                            ApiConfigScreen(
                                viewModel = viewModel,
                                onBack = {
                                    currentScreen = previousScreen
                                }
                            )
                        }

                        NavScreen.DETAIL -> {
                            CoinDetailScreen(
                                viewModel = viewModel,
                                uiState = uiState,
                                openPositions = openPositions,
                                onBack = {
                                    currentScreen = previousScreen
                                }
                            )
                        }
                    }
                }

                // NÚT TRÒN NỔI DUY NHẤT NEO GÓC DƯỚI PHẢI
                if (!showAiSheet) {
                    FloatingAiButton(
                        onClick = { showAiSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                    )
                }

                // POPUP MODAL SHEET CHIẾM 80%
                if (showAiSheet) {
                    val currentBConfig by viewModel.backtestConfig.collectAsState()
                    val currentBDays by viewModel.backtestDays.collectAsState()
                    val bHistory by viewModel.backtestHistory.collectAsState()

                    val contextJson = ScreenContextBuilder.buildDualBasketContext(
                        currentScreen = currentScreen.name,
                        selectedSymbol = uiState.selectedSymbol,
                        primaryBasket = viewModel.primaryBasket,
                        secondaryBasket = viewModel.secondaryBasket,
                        backtestReport = uiState.basketReport,
                        currentBacktestConfig = currentBConfig,
                        backtestDays = currentBDays,
                        backtestHistory = bHistory
                    )

                    AiAssistantBottomSheet(
                        viewModel = aiViewModel,
                        screenContextJson = contextJson,
                        currentScreenName = currentScreen.name,
                        onDismiss = { showAiSheet = false },
                        onApplyRecommendedConfig = { recConfig ->
                            viewModel.applyRecommendedConfig(recConfig)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Thanh tiêu đề ứng dụng tích hợp gọn gàng:
 * - Bên trái: Logo + Tiêu đề + Huy hiệu trạng thái
 * - Bên phải: Hàng 1 gồm Icon Chuông Thông Báo (kèm số lượng) & Icon Cài Đặt.
 *            Hàng 2 (nằm ngay dưới nút Cài Đặt) gồm Switch kích hoạt bot compact.
 */
@Composable
fun CryptoTraderTopBar(
    isBotRunning: Boolean,
    onToggleBot: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAlerts: () -> Unit,
    alertCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("crypto_trader_top_bar"),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Nhóm tiêu đề & Trạng thái Bot bên trái
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Logo ₿
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CryptoGold.copy(alpha = 0.15f))
                        .border(1.dp, CryptoGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₿",
                        color = CryptoGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Tiêu đề ứng dụng
                Text(
                    text = "CryptoTrader",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("app_title")
                )

                // Huy hiệu trạng thái Bot ngay cạnh tiêu đề
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isBotRunning) CryptoGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(
                        1.dp,
                        if (isBotRunning) CryptoGreen.copy(alpha = 0.7f) else DarkBorder
                    ),
                    modifier = Modifier.testTag("bot_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isBotRunning) CryptoGreen else CryptoRed)
                                .testTag("bot_status_icon")
                        )
                        Text(
                            text = if (isBotRunning) "Đang chạy" else "Đang dừng",
                            color = if (isBotRunning) CryptoGreen else CryptoRed.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("bot_status_text")
                        )
                    }
                }
            }

            // Bên phải: Cột gồm 2 hàng
            // Hàng 1: Nút Chuông Thông Báo + Nút Cài Đặt
            // Hàng 2: Nút Kích Hoạt Bot (ở ngay bên dưới nút Cài Đặt)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Hàng 1: Icon Chuông Thông Báo + Icon Cài Đặt
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Nút Chuông Thông Báo
                    IconButton(
                        onClick = onOpenAlerts,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("top_bar_alerts_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (alertCount > 0) {
                                    Badge(
                                        containerColor = CryptoRed,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (alertCount > 99) "99+" else "$alertCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Thông báo",
                                tint = if (alertCount > 0) CryptoGold else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Nút Cài Đặt
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("top_bar_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt",
                            tint = CryptoGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Hàng 2: Nút Switch Kích Hoạt Bot (nằm ngay bên dưới nút Cài Đặt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isBotRunning) CryptoGreen else TextMuted)
                    )
                    Text(
                        text = "BOT",
                        color = if (isBotRunning) CryptoGreen else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = isBotRunning,
                        onCheckedChange = onToggleBot,
                        modifier = Modifier
                            .scale(0.65f)
                            .testTag("bot_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CryptoGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }
            }
        }
    }
}

/**
 * Banner cảnh báo rủi ro vốn thật khi đang bật LIVE MAINNET
 */
@Composable
fun LiveMainnetRiskWarningBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .testTag("live_mainnet_risk_warning_banner"),
        color = CryptoRed.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = CryptoRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CẢNH BÁO RỦI RO: ĐANG GIAO DỊCH VỐN THẬT (LIVE MAINNET)",
                    color = CryptoRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Mọi lệnh đóng/mở & TP/SL sẽ được gửi thẳng lên sàn Binance Mainnet.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Hộp thoại xem nhanh thông báo cảnh báo giao dịch (Quick Alerts Dialog)
 * Mở khi người dùng nhấn vào biểu tượng chuông trên thanh tiêu đề.
 */
@Composable
fun QuickAlertsDialog(
    alerts: List<TradeAlert>,
    activeAlert: TradeAlert?,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    val combinedAlerts = remember(alerts, activeAlert) {
        val list = mutableListOf<TradeAlert>()
        if (activeAlert != null) list.add(activeAlert)
        list.addAll(alerts.filter { it.id != activeAlert?.id })
        list
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thông Báo Cảnh Báo",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                if (combinedAlerts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CryptoRed.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${combinedAlerts.size}",
                            color = CryptoRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (combinedAlerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hiện không có thông báo mới",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(combinedAlerts.size) { index ->
                        val alert = combinedAlerts[index]
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurface,
                            border = BorderStroke(0.5.dp, DarkBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = CryptoGold.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = alert.symbol,
                                            color = CryptoGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = timeFormat.format(Date(alert.timestamp)),
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = alert.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = alert.message,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg),
                modifier = Modifier.testTag("btn_close_alerts_dialog")
            ) {
                Text("Đóng", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            if (combinedAlerts.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                    border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("btn_clear_all_alerts")
                ) {
                    Text("Xóa tất cả", fontSize = 12.sp)
                }
            }
        },
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun BotParametersConfirmationDialog(
    initialConfig: BacktestConfig,
    initialCapitalPerTrade: Double,
    onConfirmAndStart: (BacktestConfig, Double) -> Unit,
    onDismiss: () -> Unit,
    onFetchOptimalAiConfig: () -> BacktestConfig
) {
    var capitalText by remember { mutableStateOf(initialCapitalPerTrade.toInt().toString()) }
    var kTrailText by remember { mutableStateOf(initialConfig.kTrail.toString()) }
    var floorLockRoiText by remember { mutableStateOf((initialConfig.floorLockMinRoi * 100.0).toString()) }
    var minVolRatioText by remember { mutableStateOf(initialConfig.minVolRatio.toString()) }

    var enableTrailing by remember { mutableStateOf(initialConfig.enableTrailingStop) }
    var enableFloorLock by remember { mutableStateOf(initialConfig.enableFloorLock) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CryptoGold)
                Text("Xác nhận thông số Bot v2.0", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Kiểm tra và tùy chỉnh thông số trước khi khởi động bot tự động (Dual Basket v2.0 - Pure Trailing Stop & FloorLock, không SL/Timeout/TP cố định):",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }

                // AI Optimal Config Button
                item {
                    Button(
                        onClick = {
                            val optimal = onFetchOptimalAiConfig()
                            kTrailText = optimal.kTrail.toString()
                            floorLockRoiText = (optimal.floorLockMinRoi * 100.0).toString()
                            minVolRatioText = optimal.minVolRatio.toString()
                            enableTrailing = optimal.enableTrailingStop
                            enableFloorLock = optimal.enableFloorLock
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoGold.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, CryptoGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apply_ai_config_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Điền bộ thông số tối ưu từ AI", color = CryptoGold, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Vốn mỗi lệnh USDT
                item {
                    OutlinedTextField(
                        value = capitalText,
                        onValueChange = { capitalText = it },
                        label = { Text("Vốn mỗi lệnh (USDT)") },
                        modifier = Modifier.fillMaxWidth().testTag("bot_capital_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoGold,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // K_Trail & Sàn FloorLock (%)
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = kTrailText,
                            onValueChange = { kTrailText = it },
                            label = { Text("K_Trail (Trượt ATR)") },
                            modifier = Modifier.weight(1f).testTag("bot_ktrail_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = floorLockRoiText,
                            onValueChange = { floorLockRoiText = it },
                            label = { Text("Sàn khóa lãi (%)") },
                            modifier = Modifier.weight(1f).testTag("bot_floorlock_roi_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGold,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                // RVol
                item {
                    OutlinedTextField(
                        value = minVolRatioText,
                        onValueChange = { minVolRatioText = it },
                        label = { Text("Ngưỡng RVol(20) tối thiểu") },
                        modifier = Modifier.fillMaxWidth().testTag("bot_rvol_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoGold,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                // Safety Toggles
                item {
                    Text("Cơ chế thoát lệnh (Pure Trend Following):", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BotToggleRow("Trailing Stop ATR", "Bám theo xu hướng tối đa hóa lợi nhuận", enableTrailing) { enableTrailing = it }
                        BotToggleRow("FloorLock (Khóa lãi >= +0.5%)", "Bảo toàn lãi ròng tối thiểu khi đã vào trailing", enableFloorLock) { enableFloorLock = it }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalConfig = initialConfig.copy(
                        kTrail = kTrailText.toDoubleOrNull() ?: initialConfig.kTrail,
                        floorLockMinRoi = (floorLockRoiText.toDoubleOrNull() ?: 0.5) / 100.0,
                        minVolRatio = minVolRatioText.toDoubleOrNull() ?: initialConfig.minVolRatio,
                        enableTrailingStop = enableTrailing,
                        enableFloorLock = enableFloorLock
                    )
                    val finalCapital = capitalText.toDoubleOrNull() ?: initialCapitalPerTrade
                    onConfirmAndStart(finalConfig, finalCapital)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGreen),
                modifier = Modifier.testTag("confirm_and_start_bot_btn")
            ) {
                Text("Xác nhận & Khởi động", color = DarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.testTag("cancel_start_bot_btn")
            ) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun BotToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = CryptoGold,
                checkedTrackColor = CryptoGold.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurface
            )
        )
    }
}

/**
 * Service chạy ngầm duy trì CPU & mạng liên tục 24/7 để quét nến 7h00 sáng và tính PnL 1s
 */
class TradingForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "trading_bot_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_BOT"
        const val ACTION_STOP = "ACTION_STOP_BOT"

        fun startService(context: Context) {
            val intent = Intent(context, TradingForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TradingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Giữ CPU chạy liên tục khi tắt màn hình
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CryptoTrader::BotWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // Giữ wake lock 24 giờ
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            else -> {
                val notification = buildNotification("Bot đang chạy ngầm • Giám sát PnL 1s & quét 7h00")
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        return START_STICKY // Tự động hồi sinh nếu hệ thống ép tắt do thiếu RAM
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CryptoTrader Bot Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bot Trading Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kênh thông báo duy trì bot chạy liên tục"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}