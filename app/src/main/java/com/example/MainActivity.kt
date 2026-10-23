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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ApiConfigScreen
import com.example.ui.screens.CoinDetailScreen
import com.example.ui.screens.MarketScreen
import com.example.ui.screens.PositionsScreen
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
    var showAiSheet by remember { mutableStateOf(false) }
    val aiViewModel: AiAssistantViewModel = viewModel()

    // Số lượng lệnh đang mở
    val openCount = if (apiConfig.isConnected) binancePortfolio.positions.size else openPositions.size

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
                onToggleBot = { viewModel.toggleAutoTrade(it) }
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

                // Tab 3: Thống kê
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
                        Text("Thống kê", fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
            val isLiveMainnet = !apiConfig.isTestnet && apiConfig.liveTradingEnabled

            // Cảnh báo màu đỏ khi giao dịch Live Mainnet
            if (isLiveMainnet) {
                LiveMainnetRiskWarningBanner()
            }

            // Thanh thông báo lệnh nổi (Floating Alert Banner)
            TradeAlertBanner(
                alert = uiState.activeAlert,
                onDismiss = { viewModel.dismissAlert() }
            )

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

                        NavScreen.ANALYTICS -> {
                            AnalyticsScreen(
                                viewModel = viewModel,
                                closedPositions = closedPositions
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

                    val contextJson = ScreenContextBuilder.buildDualBasketContext(
                        currentScreen = currentScreen.name,
                        selectedSymbol = uiState.selectedSymbol,
                        primaryBasket = viewModel.primaryBasket,
                        secondaryBasket = viewModel.secondaryBasket,
                        backtestReport = uiState.basketReport,
                        currentBacktestConfig = currentBConfig,
                        backtestDays = currentBDays
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
 * Thanh tiêu đề ứng dụng hiển thị rõ ràng trạng thái bot (Đang chạy/Đang dừng)
 * và Toggle mới giúp người dùng bật/tắt bot tức thì.
 */
@Composable
fun CryptoTraderTopBar(
    isBotRunning: Boolean,
    onToggleBot: (Boolean) -> Unit,
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Nhóm tiêu đề & Trạng thái Bot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Logo ₿
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CryptoGold.copy(alpha = 0.15f))
                        .border(1.dp, CryptoGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "₿",
                        color = CryptoGold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Tiêu đề ứng dụng
                Text(
                    text = "CryptoTrader",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("app_title")
                )

                // Huy hiệu trạng thái Bot ngay cạnh tiêu đề
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isBotRunning) CryptoGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(
                        1.dp,
                        if (isBotRunning) CryptoGreen.copy(alpha = 0.7f) else DarkBorder
                    ),
                    modifier = Modifier.testTag("bot_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Icon trạng thái nhỏ thay đổi màu sắc tức thì (Xanh khi chạy / Đỏ khi dừng)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isBotRunning) CryptoGreen else CryptoRed)
                                .border(
                                    1.dp,
                                    if (isBotRunning) CryptoGreen.copy(alpha = 0.8f) else CryptoRed.copy(alpha = 0.8f),
                                    CircleShape
                                )
                                .testTag("bot_status_icon")
                        )
                        // Chữ thông báo trạng thái rõ ràng: Đang chạy / Đang dừng
                        Text(
                            text = if (isBotRunning) "Đang chạy" else "Đang dừng",
                            color = if (isBotRunning) CryptoGreen else CryptoRed.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("bot_status_text")
                        )
                    }
                }
            }

            // Toggle mới bật/tắt bot tức thì
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Bot",
                    color = if (isBotRunning) CryptoGreen else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = isBotRunning,
                    onCheckedChange = onToggleBot,
                    modifier = Modifier
                        .scale(0.72f)
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