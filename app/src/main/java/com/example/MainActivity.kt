package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.components.TradeAlertBanner
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ApiConfigScreen
import com.example.ui.screens.CoinDetailScreen
import com.example.ui.screens.MarketScreen
import com.example.ui.screens.PositionsScreen
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
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

                // 3. Vị Thế (Positions)
                NavigationBarItem(
                    selected = currentScreen == NavScreen.POSITIONS,
                    onClick = {
                        previousScreen = currentScreen
                        currentScreen = NavScreen.POSITIONS
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (openPositions.isNotEmpty()) {
                                    Badge(
                                        containerColor = CryptoGreen,
                                        contentColor = DarkBg
                                    ) {
                                        Text("${openPositions.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                onSelectCoin = { symbol ->
                                    viewModel.selectSymbol(symbol, "4h")
                                    previousScreen = NavScreen.MARKET
                                    currentScreen = NavScreen.DETAIL
                                },
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
                                openPositions = openPositions,
                                closedPositions = closedPositions,
                                onSelectCoin = { symbol ->
                                    viewModel.selectSymbol(symbol, "4h")
                                    previousScreen = NavScreen.POSITIONS
                                    currentScreen = NavScreen.DETAIL
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
