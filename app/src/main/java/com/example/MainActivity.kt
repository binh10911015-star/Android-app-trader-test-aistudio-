package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
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
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class NavScreen {
    MARKET,
    DETAIL,
    POSITIONS,
    ANALYTICS,
    API_CONFIG
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    // Số lượng lệnh Paper Trade đang mở
    val paperOpenCount = openPositions.count { !it.isLiveBinanceOrder }

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

    // Handle back button on sub-screens
    BackHandler(enabled = currentScreen != NavScreen.MARKET) {
        currentScreen = previousScreen
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                // 1. Thị Trường
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
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_market")
                )

                // 2. Biểu Đồ (Nến & Chỉ Báo)
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
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_detail")
                )

                // 3. Paper Trade (Trước đây là Vị Thế)
                NavigationBarItem(
                    selected = currentScreen == NavScreen.POSITIONS,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.POSITIONS
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (paperOpenCount > 0) {
                                    Badge(
                                        containerColor = CryptoGold,
                                        contentColor = DarkBg
                                    ) {
                                        Text("$paperOpenCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Paper Trade")
                        }
                    },
                    label = {
                        Text("Paper Trade", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_positions")
                )

                // 4. Thống Kê PnL
                NavigationBarItem(
                    selected = currentScreen == NavScreen.ANALYTICS,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.ANALYTICS
                    },
                    icon = {
                        Icon(Icons.Default.BarChart, contentDescription = "Thống kê")
                    },
                    label = {
                        Text("Thống kê", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_analytics")
                )

                // 5. Kết Nối API Binance
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
                            Icon(Icons.Default.VpnKey, contentDescription = "Khóa API")
                        }
                    },
                    label = {
                        Text("API Key", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBg,
                        selectedTextColor = CryptoGold,
                        indicatorColor = CryptoGold,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("nav_api_config")
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

            // Red warning risk badge when in LIVE MAINNET
            if (isLiveMainnet) {
                LiveMainnetRiskWarningBanner()
            }

            // Live Floating Notification Banner for Auto-Trades / TP / SL
            TradeAlertBanner(
                alert = uiState.activeAlert,
                onDismiss = { viewModel.dismissAlert() }
            )

            // Screen Content Crossfade
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
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Badge màu đỏ cảnh báo rủi ro vốn thật khi ở chế độ LIVE MAINNET
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
                    text = "Mọi lệnh đóng/mở & TP/SL sẽ được gửi thẳng lên sàn Binance Mainnet qua Proxy an toàn.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp
                )
            }
        }
    }
}