package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.model.AlertType
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig
import com.example.model.BinanceAssetBalance
import com.example.model.BinanceLivePosition
import com.example.model.BinanceOpenOrderItem
import com.example.model.BinanceOrderVerificationDetail
import com.example.model.BinancePortfolio
import com.example.model.BinanceTicker
import com.example.model.IndicatorData
import com.example.model.PeriodPnL
import com.example.model.RoiSortOrder
import com.example.model.TradeAlert
import com.example.model.TradePosition
import com.example.model.TradeSettings
import com.example.network.BinanceClient
import com.example.network.BinanceFuturesApiVerifier
import com.example.repository.BacktestSummaryResult
import com.example.repository.CryptoRepository
import com.example.util.ScanScheduler
import com.example.util.SecureApiKeyStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MarketFilter {
    ALL,
    BULLISH_SIGNAL,
    OPEN_POSITIONS
}

data class UiState(
    val tickers: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers(),
    val filteredTickers: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers(),
    val coinStateList: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers(),
    val scannedD1BreakoutTickers: List<BinanceTicker> = emptyList(),
    val scannedSuperTrendTickers: List<BinanceTicker> = emptyList(),
    val selectedSignalTab: Int = 0,
    val searchQuery: String = "",
    val debouncedSearchQuery: String = "",
    val isSearching: Boolean = false,
    val filterMode: MarketFilter = MarketFilter.ALL,
    val selectedSymbol: String = "BTCUSDT",
    val selectedTimeframe: String = "4h",
    val klineData: List<IndicatorData> = emptyList(),
    val isKlineLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isScanning: Boolean = false,
    val settings: TradeSettings = TradeSettings(),
    val activeAlert: TradeAlert? = null,
    val alertHistory: List<TradeAlert> = emptyList(),
    val scanProgress: String = "",
    val scanPercent: Float = 0f,
    val d1BullishCount: Int = 0,
    val superTrendBullishCount: Int = 0,
    val nextScanTimestamp: Long = ScanScheduler.getNextScanTimestamp(),
    val countdownText: String = "",
    val lastScanTimestamp: Long = 0L,
    val totalScannedCount: Int = 0,
    val roiSortOrder: RoiSortOrder = RoiSortOrder.DEFAULT,
    val isOpeningPositions: Boolean = false,
    val openingPositionsProgress: String = "",
    val backtestResult: BacktestSummaryResult? = null,
    val isBacktesting: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val repository = CryptoRepository(db.tradeDao())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val secureApiStorage = SecureApiKeyStorage(application)
    private val _apiConfig = MutableStateFlow(secureApiStorage.loadConfig())
    val apiConfig: StateFlow<BinanceApiConfig> = _apiConfig.asStateFlow()

    private val _isVerifyingApi = MutableStateFlow(false)
    val isVerifyingApi: StateFlow<Boolean> = _isVerifyingApi.asStateFlow()

    private val _coinStateList = MutableStateFlow<List<BinanceTicker>>(com.example.data.DefaultCoinData.getSeedTickers())
    val coinStateList: StateFlow<List<BinanceTicker>> = _coinStateList.asStateFlow()

    val openPositions: StateFlow<List<TradePosition>> = repository.getOpenPositions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val closedPositions: StateFlow<List<TradePosition>> = repository.getClosedPositions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var autoTradeJob: Job? = null
    private var openPositionsJob: Job? = null
    private var tickerPollJob: Job? = null
    private var schedulerJob: Job? = null

    private val _searchQueryFlow = MutableStateFlow("")

    private val _binancePortfolio = MutableStateFlow(BinancePortfolio())
    val binancePortfolio: StateFlow<BinancePortfolio> = _binancePortfolio.asStateFlow()

    private val _isRefreshingPortfolio = MutableStateFlow(false)
    val isRefreshingPortfolio: StateFlow<Boolean> = _isRefreshingPortfolio.asStateFlow()

    private val _publicIpAddress = MutableStateFlow<String?>(null)
    val publicIpAddress: StateFlow<String?> = _publicIpAddress.asStateFlow()

    private val _isFetchingIp = MutableStateFlow(false)
    val isFetchingIp: StateFlow<Boolean> = _isFetchingIp.asStateFlow()

    private val _orderVerificationDetail = MutableStateFlow<BinanceOrderVerificationDetail?>(null)
    val orderVerificationDetail: StateFlow<BinanceOrderVerificationDetail?> = _orderVerificationDetail.asStateFlow()

    private val _isVerifyingOrder = MutableStateFlow(false)
    val isVerifyingOrder: StateFlow<Boolean> = _isVerifyingOrder.asStateFlow()

    private val _livePositionsTpSl = MutableStateFlow<Map<String, Pair<Double?, Double?>>>(emptyMap())
    val livePositionsTpSl: StateFlow<Map<String, Pair<Double?, Double?>>> = _livePositionsTpSl.asStateFlow()

    init {
        val loadedConfig = secureApiStorage.loadConfig()
        BinanceClient.isTestnet = loadedConfig.isTestnet 

        if (loadedConfig.isProxyEnabled) {
            BinanceClient.updateProxy(
                loadedConfig.proxyHost,
                loadedConfig.proxyPort,
                loadedConfig.proxyType.equals("SOCKS", ignoreCase = true)
            )
        }
        setupDebouncedSearch()
        loadInitialData()
        startTickerPolling()
        startScanScheduler()
        fetchPublicIpAddress()
        if (loadedConfig.isTestnet) {
            autoLoginTestnet()
        } else {
            autoLoginMainnet()
        }
    }

    @OptIn(FlowPreview::class)
    private fun setupDebouncedSearch() {
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .collect { debouncedQuery ->
                    applyFilters(debouncedQuery)
                }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            refreshTickers()
            loadKlineForSymbol(_uiState.value.selectedSymbol, _uiState.value.selectedTimeframe)
            scanAllFuturesCoins(isAutoScheduled = false)
        }
    }

    fun selectSymbol(symbol: String, timeframe: String = "4h") {
        _uiState.value = _uiState.value.copy(
            selectedSymbol = symbol,
            selectedTimeframe = timeframe,
            backtestResult = null // xóa kết quả cũ của coin trước
        )
        loadKlineForSymbol(symbol, timeframe)
    }

    fun setTimeframe(tf: String) {
        if (tf != _uiState.value.selectedTimeframe) {
            _uiState.value = _uiState.value.copy(selectedTimeframe = tf)
            loadKlineForSymbol(_uiState.value.selectedSymbol, tf)
        }
    }

    fun setSearchQuery(query: String) {
        val willDebounce = query.isNotBlank() && query != _uiState.value.debouncedSearchQuery
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearching = willDebounce
        )
        if (query.isEmpty()) {
            _searchQueryFlow.value = ""
            applyFilters("")
        } else {
            _searchQueryFlow.value = query
        }
    }

    fun setFilterMode(mode: MarketFilter) {
        _uiState.value = _uiState.value.copy(filterMode = mode)
        applyFilters(_searchQueryFlow.value)
    }

    fun selectSignalTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(
            selectedSignalTab = tabIndex,
            filterMode = MarketFilter.BULLISH_SIGNAL
        )
        applyFilters(_searchQueryFlow.value)
    }

    private fun applyFilters(query: String = _searchQueryFlow.value) {
        val current = _uiState.value
        val openSymbols = openPositions.value.map { it.symbol }.toSet()
        val cleanQuery = com.example.util.SearchUtils.stripUsdtSuffix(query).uppercase()
        val rawQuery = query.trim().uppercase()

        val activeSignalSymbols = if (current.selectedSignalTab == 0) {
            current.scannedD1BreakoutTickers.map { it.symbol }.toSet()
        } else {
            current.scannedSuperTrendTickers.map { it.symbol }.toSet()
        }

        val listToFilter = current.tickers

        val filtered = listToFilter.filter { ticker ->
            val baseSymbol = ticker.symbol.removeSuffix("USDT")
            val fullSymbol = ticker.symbol

            val matchesQuery = if (cleanQuery.isEmpty()) {
                if (rawQuery.isNotEmpty()) {
                    fullSymbol.endsWith("USDT", ignoreCase = true)
                } else {
                    true
                }
            } else {
                baseSymbol.contains(cleanQuery, ignoreCase = true) ||
                fullSymbol.contains(cleanQuery, ignoreCase = true) ||
                baseSymbol.removePrefix("1000").contains(cleanQuery, ignoreCase = true) ||
                (cleanQuery.startsWith("1000") && baseSymbol.contains(cleanQuery.removePrefix("1000"), ignoreCase = true))
            }

            val matchesFilter = if (cleanQuery.isNotEmpty() || rawQuery.isNotEmpty()) {
                true
            } else {
                when (current.filterMode) {
                    MarketFilter.ALL -> true
                    MarketFilter.BULLISH_SIGNAL -> activeSignalSymbols.contains(ticker.symbol)
                    MarketFilter.OPEN_POSITIONS -> openSymbols.contains(ticker.symbol)
                }
            }
            matchesQuery && matchesFilter
        }.sortedWith { a, b ->
            if (cleanQuery.isNotEmpty()) {
                val baseA = a.symbol.removeSuffix("USDT")
                val baseB = b.symbol.removeSuffix("USDT")

                val exactA = baseA.equals(cleanQuery, ignoreCase = true)
                val exactB = baseB.equals(cleanQuery, ignoreCase = true)
                if (exactA != exactB) return@sortedWith if (exactA) -1 else 1

                val startsA = baseA.startsWith(cleanQuery, ignoreCase = true)
                val startsB = baseB.startsWith(cleanQuery, ignoreCase = true)
                if (startsA != startsB) return@sortedWith if (startsA) -1 else 1

                (b.volume * b.lastPrice).compareTo(a.volume * a.lastPrice)
            } else {
                (b.volume * b.lastPrice).compareTo(a.volume * a.lastPrice)
            }
        }

        _coinStateList.value = filtered
        _uiState.value = _uiState.value.copy(
            filteredTickers = filtered,
            coinStateList = filtered,
            debouncedSearchQuery = query,
            isSearching = false
        )
    }

    fun refreshTickers() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.getTickers().onSuccess { list ->
                _uiState.value = _uiState.value.copy(tickers = list, isRefreshing = false)
                applyFilters()

                val priceMap = list.associate { it.symbol to it.lastPrice }
                val updateResult = repository.updatePositionsWithPrices(priceMap)
                for (alert in updateResult.alerts) {
                    triggerAlert(alert)
                }
                var totalReturned = updateResult.totalReturnedCapital

                val targetTp = _uiState.value.settings.portfolioTakeProfitPercent
                val currentOpen = repository.getOpenPositionsList()

                if (targetTp > 0.0 && currentOpen.isNotEmpty()) {
                    val totalWallet = _binancePortfolio.value.totalWalletBalance
                    val totalUnrealized = _binancePortfolio.value.totalUnrealizedProfit

                    val currentPnlPercent = if (_apiConfig.value.isConnected && totalWallet > 0.0) {
                        (totalUnrealized / totalWallet) * 100.0
                    } else {
                        var totalInvested = 0.0
                        var totalCurrentValue = 0.0
                        for (pos in currentOpen) {
                            val currentPrice = priceMap[pos.symbol] ?: pos.currentPrice
                            val pnl = (currentPrice - pos.entryPrice) * pos.amount
                            totalInvested += pos.investedUsdt
                            totalCurrentValue += (pos.investedUsdt + pnl)
                        }
                        if (totalInvested > 0.0) ((totalCurrentValue - totalInvested) / totalInvested) * 100.0 else 0.0
                    }

                    if (currentPnlPercent >= targetTp) {
                        if (_apiConfig.value.isConnected) {
                            val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                            for (pos in currentOpen.filter { it.isLiveBinanceOrder }) {
                                val price = priceMap[pos.symbol] ?: pos.currentPrice
                                BinanceFuturesApiVerifier.postMarketCloseOrder(
                                    symbol = pos.symbol,
                                    amount = pos.amount,
                                    approxPrice = price,
                                    apiKey = apiKey,
                                    apiSecret = apiSecret,
                                    isTestnet = isTestnet
                                )
                            }
                        }
                        
                        val ptpResult = repository.checkAndExecutePortfolioTakeProfit(priceMap, targetTp)
                        for (alert in ptpResult.alerts) {
                            triggerAlert(alert)
                        }
                        totalReturned += ptpResult.totalReturnedCapital
                        
                        if (_apiConfig.value.isConnected) {
                            refreshBinancePortfolio()
                        }
                    }
                }

                if (totalReturned > 0.0) {
                    val newBal = _uiState.value.settings.simulatedBalanceUsdt + totalReturned
                    _uiState.value = _uiState.value.copy(
                        settings = _uiState.value.settings.updateBalance(newBal)
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun loadKlineForSymbol(symbol: String, timeframe: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isKlineLoading = true)
            repository.getCandlesWithIndicators(symbol, timeframe).onSuccess { data ->
                _uiState.value = _uiState.value.copy(
                    klineData = data,
                    isKlineLoading = false
                )
                applyFilters()
            }.onFailure {
                _uiState.value = _uiState.value.copy(isKlineLoading = false)
            }
        }
    }

    fun toggleAutoTrade(enabled: Boolean) {
        val newSettings = _uiState.value.settings.copy(autoTradeEnabled = enabled)
        _uiState.value = _uiState.value.copy(settings = newSettings)

        if (enabled) {
            val isD1 = _uiState.value.settings.selectedStrategy == com.example.model.TradingStrategyType.D1_SMA20_VOLUME
            val strategyTitle = if (isD1) "D1 SMA20 Breakout & Bùng Nổ Khối Lượng" else "4H SuperTrend & MA20"

            triggerAlert(
                TradeAlert(
                    symbol = "BOT",
                    title = "ĐÃ BẬT AUTO-TRADE",
                    message = "Hệ thống bắt đầu quét và giao dịch tự động theo chiến lược: $strategyTitle.",
                    type = AlertType.INFO
                )
            )

            startAutoTradeLoop()
        } else {
            autoTradeJob?.cancel()
            autoTradeJob = null
            openPositionsJob?.cancel()
            openPositionsJob = null
            _uiState.value = _uiState.value.copy(
                isOpeningPositions = false,
                openingPositionsProgress = ""
            )
            triggerAlert(
                TradeAlert(
                    symbol = "BOT",
                    title = "ĐÃ TẮT AUTO-TRADE",
                    message = "Hệ thống dừng mở lệnh mới. Các vị thế đang mở vẫn được giám sát TP/SL.",
                    type = AlertType.INFO
                )
            )
        }
    }
  
    fun updatePortfolioTakeProfitPercent(targetPercent: Double) {
        val clamped = targetPercent.coerceAtLeast(0.5)
        val updatedSettings = _uiState.value.settings.copy(portfolioTakeProfitPercent = clamped)
        _uiState.value = _uiState.value.copy(settings = updatedSettings)
        
        triggerAlert(
            TradeAlert(
                symbol = "PORTFOLIO",
                title = "ĐÃ ĐỔI MỤC TIÊU CHỐT LỜI",
                message = "Mục tiêu chốt lời toàn danh mục mới: +${String.format(Locale.US, "%.1f", clamped)}%. Hệ thống sẽ tự động đóng toàn bộ khi đạt mốc này.",
                type = AlertType.INFO
            )
        )
    }

    fun updateSettings(newSettings: TradeSettings) {
        _uiState.value = _uiState.value.copy(settings = newSettings)
    }

    fun setRoiSortOrder(order: RoiSortOrder) {
        _uiState.value = _uiState.value.copy(roiSortOrder = order)
    }

    fun executeBuy(symbol: String, isAuto: Boolean = false) {
        if (isAuto && !_uiState.value.settings.autoTradeEnabled) return
        viewModelScope.launch {
            val currentTicker = _uiState.value.tickers.find { it.symbol == symbol }
            val price = currentTicker?.lastPrice
                ?: _uiState.value.klineData.lastOrNull()?.candle?.close
                ?: return@launch

            val volume = _uiState.value.settings.calculatePositionVolume()
            if (_uiState.value.settings.simulatedBalanceUsdt < volume || volume <= 0.0) return@launch

            val isLive = _apiConfig.value.isConnected

            if (isLive) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                val netLabel = if (isTestnet) "Testnet Demo" else "Mainnet"

                _uiState.value = _uiState.value.copy(
                    isOpeningPositions = true,
                    openingPositionsProgress = "Đang gửi lệnh MARKET $symbol lên Binance ($netLabel)..."
                )

                val orderResult = BinanceFuturesApiVerifier.postMarketOrder(
                    symbol = symbol,
                    side = "BUY",
                    targetUsdtAmount = volume,
                    approxPrice = price,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    isTestnet = isTestnet
                )

                _uiState.value = _uiState.value.copy(
                    isOpeningPositions = false,
                    openingPositionsProgress = ""
                )

                if (orderResult.success) {
                    val alert = repository.executeSimulatedBuy(
                        symbol = symbol,
                        currentPrice = price,
                        settings = _uiState.value.settings,
                        investedUsdt = volume,
                        isAutoTriggered = isAuto,
                        binanceOrderId = orderResult.orderId,
                        binanceClientOrderId = orderResult.clientOrderId,
                        isLiveBinanceOrder = true,
                        binanceStatus = orderResult.status ?: "FILLED",
                        exactAmount = if (orderResult.executedQty > 0.0) orderResult.executedQty else null,
                        exactEntryPrice = if (orderResult.avgPrice > 0.0) orderResult.avgPrice else null,
                        autoFixNote = orderResult.autoFixApplied
                    )

                    if (alert != null) triggerAlert(alert)
                    refreshBinancePortfolio()
                } else {
                    val fixNote = orderResult.autoFixApplied?.let { " ($it)" } ?: ""
                    triggerAlert(
                        TradeAlert(
                            symbol = symbol,
                            title = "LỖI GỬI LỆNH LÊN BINANCE",
                            message = "${orderResult.errorMessage ?: "Không thể kết nối"}$fixNote",
                            type = AlertType.INFO
                        )
                    )
                }
                return@launch
            }

            val alert = repository.executeSimulatedBuy(
                symbol = symbol,
                currentPrice = price,
                settings = _uiState.value.settings,
                investedUsdt = volume,
                isAutoTriggered = isAuto,
                binanceOrderId = null,
                binanceClientOrderId = null,
                isLiveBinanceOrder = false,
                binanceStatus = null
            )

            if (alert != null) {
                triggerAlert(alert)
                val newBal = _uiState.value.settings.simulatedBalanceUsdt - volume
                _uiState.value = _uiState.value.copy(
                    settings = _uiState.value.settings.updateBalance(newBal)
                )
            }
        }
    }

    fun manualClosePosition(positionId: Long) {
        viewModelScope.launch {
            val pos = openPositions.value.find { it.id == positionId } ?: return@launch
            val currentPrice = _uiState.value.tickers.find { it.symbol == pos.symbol }?.lastPrice ?: pos.currentPrice

            if (pos.isLiveBinanceOrder && _apiConfig.value.isConnected) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                BinanceFuturesApiVerifier.postMarketCloseOrder(
                    symbol = pos.symbol,
                    amount = pos.amount,
                    approxPrice = currentPrice,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    isTestnet = isTestnet
                )
                refreshBinancePortfolio()
            }

            val alert = repository.closePositionManually(positionId, currentPrice)
            if (alert != null) {
                triggerAlert(alert)
                if (!pos.isLiveBinanceOrder) {
                    val pnl = (currentPrice - pos.entryPrice) * pos.amount
                    val returnedCapital = pos.investedUsdt + pnl
                    val newBal = _uiState.value.settings.simulatedBalanceUsdt + returnedCapital
                    _uiState.value = _uiState.value.copy(
                        settings = _uiState.value.settings.updateBalance(newBal)
                    )
                }
            }
        }
    }

    fun closeAllPositions() {
        viewModelScope.launch {
            val count = openPositions.value.size
            if (count == 0) return@launch
            val priceMap = _uiState.value.tickers.associate { it.symbol to it.lastPrice }

            if (_apiConfig.value.isConnected) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                for (pos in openPositions.value.filter { it.isLiveBinanceOrder }) {
                    val p = priceMap[pos.symbol] ?: pos.currentPrice
                    BinanceFuturesApiVerifier.postMarketCloseOrder(
                        symbol = pos.symbol,
                        amount = pos.amount,
                        approxPrice = p,
                        apiKey = apiKey,
                        apiSecret = apiSecret,
                        isTestnet = isTestnet
                    )
                }
                refreshBinancePortfolio()
            }

            val (closedList, totalReturned) = repository.closeAllOpenPositions(priceMap)
            if (closedList.isNotEmpty()) {
                val newBal = _uiState.value.settings.simulatedBalanceUsdt + totalReturned
                _uiState.value = _uiState.value.copy(
                    settings = _uiState.value.settings.updateBalance(newBal)
                )
                triggerAlert(
                    TradeAlert(
                        symbol = "ALL_POSITIONS",
                        title = "ĐÃ ĐÓNG TOÀN BỘ VỊ THẾ",
                        message = "Đã tất toán toàn bộ $count vị thế. Hoàn lại ${String.format(Locale.US, "%.2f", totalReturned)} USDT vào số dư.",
                        type = AlertType.MANUAL_CLOSE
                    )
                )
            }
        }
    }

    fun toggleTradingMode(toLiveMainnet: Boolean) {
        val current = _apiConfig.value
        val newTestnet = !toLiveMainnet
        val newLiveTrading = toLiveMainnet

        BinanceClient.isTestnet = !toLiveMainnet

        val (apiKey, apiSecret) = if (toLiveMainnet) {
            secureApiStorage.getMainnetCredentials()
        } else {
            secureApiStorage.getTestnetCredentials()
        }

        secureApiStorage.saveCredentials(
            apiKey = apiKey,
            apiSecret = apiSecret,
            accessPin = current.accessPin.ifBlank { "1234" },
            isTestnet = newTestnet,
            liveTradingEnabled = newLiveTrading
        )
        _apiConfig.value = secureApiStorage.loadConfig()

        if (toLiveMainnet) {
            autoLoginMainnet()
            triggerAlert(
                TradeAlert(
                    symbol = "MAINNET",
                    title = "⚠️ ĐÃ BẬT CHẾ ĐỘ LIVE MAINNET",
                    message = "CẢNH BÁO: Đang ở chế độ LIVE MAINNET! Mọi thao tác đặt lệnh sẽ sử dụng tiền thật trên Binance Futures.",
                    type = AlertType.INFO
                )
            )
        } else {
            autoLoginTestnet()
            triggerAlert(
                TradeAlert(
                    symbol = "SIMULATION",
                    title = "🛡️ ĐÃ CHUYỂN VỀ BINANCE DEMO",
                    message = "Đã chuyển sang môi trường Binance Testnet Demo an toàn.",
                    type = AlertType.INFO
                )
            )
        }
    }

    fun setPositionTpSl(
        symbol: String,
        isLong: Boolean,
        tpPrice: Double?,
        slPrice: Double?,
        positionId: Long? = null,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            var msg: String
            var success = true

            if (positionId != null) {
                repository.updatePositionTpSl(positionId, tpPrice, slPrice)
            }

            val currentMap = _livePositionsTpSl.value.toMutableMap()
            currentMap[symbol] = Pair(tpPrice, slPrice)
            _livePositionsTpSl.value = currentMap

            if (_apiConfig.value.isConnected) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                val res = BinanceFuturesApiVerifier.setTakeProfitAndStopLoss(
                    symbol = symbol,
                    isLong = isLong,
                    takeProfitPrice = tpPrice,
                    stopLossPrice = slPrice,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    isTestnet = isTestnet
                )
                success = res.tpSuccess || res.slSuccess || res.errorMessage == null
                msg = if (success) {
                    val tpText = tpPrice?.let { "TP: $it" } ?: ""
                    val slText = slPrice?.let { "SL: $it" } ?: ""
                    "Đã đặt lệnh điều kiện thành công: $tpText $slText"
                } else {
                    res.errorMessage ?: "Không thể gửi lệnh điều kiện lên sàn"
                }
            } else {
                msg = "Đã lưu cài đặt TP: $tpPrice | SL: $slPrice cho vị thế mô phỏng."
            }

            triggerAlert(
                TradeAlert(
                    symbol = symbol,
                    title = if (success) "ĐÃ CÀI ĐẶT TP / SL" else "LỖI ĐẶT TP / SL",
                    message = msg,
                    type = if (success) AlertType.TAKE_PROFIT else AlertType.STOP_LOSS
                )
            )
            onResult?.invoke(success, msg)
        }
    }

    fun closeLiveBinancePosition(position: BinanceLivePosition) {
        viewModelScope.launch {
            if (!_apiConfig.value.isConnected) return@launch
            val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
            val side = if (position.isLong) "SELL" else "BUY"

            val orderResult = BinanceFuturesApiVerifier.postMarketCloseOrder(
                symbol = position.symbol,
                amount = position.absAmount,
                approxPrice = position.markPrice,
                apiKey = apiKey,
                apiSecret = apiSecret,
                isTestnet = isTestnet,
                side = side
            )

            if (orderResult.success) {
                triggerAlert(
                    TradeAlert(
                        symbol = position.symbol,
                        title = "ĐÃ ĐÓNG VỊ THẾ BINANCE LIVE",
                        message = "Đã gửi lệnh $side ${String.format(Locale.US, "%.4f", position.absAmount)} ${position.symbol} thành công trên sàn Binance!",
                        type = AlertType.INFO
                    )
                )
                refreshBinancePortfolio()
            } else {
                triggerAlert(
                    TradeAlert(
                        symbol = position.symbol,
                        title = "LỖI ĐÓNG VỊ THẾ BINANCE",
                        message = orderResult.errorMessage ?: "Không thể đóng vị thế",
                        type = AlertType.STOP_LOSS
                    )
                )
            }
        }
    }

    fun closeAllLiveBinancePositions() {
        viewModelScope.launch {
            val livePositions = binancePortfolio.value.positions
            if (livePositions.isEmpty() || !_apiConfig.value.isConnected) return@launch
            val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()

            var closedCount = 0
            for (pos in livePositions) {
                val side = if (pos.isLong) "SELL" else "BUY"
                val res = BinanceFuturesApiVerifier.postMarketCloseOrder(
                    symbol = pos.symbol,
                    amount = pos.absAmount,
                    approxPrice = pos.markPrice,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    isTestnet = isTestnet,
                    side = side
                )
                if (res.success) closedCount++
            }

            refreshBinancePortfolio()
            triggerAlert(
                TradeAlert(
                    symbol = "CLOSE_ALL",
                    title = "ĐÃ ĐÓNG TOÀN BỘ VỊ THẾ LIVE",
                    message = "Đã gửi lệnh đóng $closedCount/${livePositions.size} vị thế trên sàn Binance.",
                    type = AlertType.INFO
                )
            )
        }
    }

    fun updateLeverage(symbol: String, leverage: Int) {
        val clamped = leverage.coerceIn(1, 50)
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(leverage = clamped)
        )
        if (_apiConfig.value.isConnected) {
            viewModelScope.launch {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                BinanceFuturesApiVerifier.setLeverage(symbol, clamped, apiKey, apiSecret, isTestnet)
            }
        }
    }

    fun executeOrder(symbol: String, isLong: Boolean, customAmountUsdt: Double? = null) {
        viewModelScope.launch {
            val price = _uiState.value.tickers.find { it.symbol == symbol }?.lastPrice ?: 0.0
            if (price <= 0.0) return@launch

            val volume = customAmountUsdt ?: _uiState.value.settings.calculatePositionVolume()
            val side = if (isLong) "BUY" else "SELL"
            val sideLabel = if (isLong) "LONG" else "SHORT"

            if (_apiConfig.value.isConnected) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                val netLabel = if (isTestnet) "Testnet Demo" else "Mainnet"

                BinanceFuturesApiVerifier.setLeverage(symbol, _uiState.value.settings.leverage, apiKey, apiSecret, isTestnet)

                val orderResult = BinanceFuturesApiVerifier.postMarketOrder(
                    symbol = symbol,
                    side = side,
                    targetUsdtAmount = volume,
                    approxPrice = price,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    isTestnet = isTestnet
                )

                if (orderResult.success) {
                    val alert = repository.executeSimulatedBuy(
                        symbol = symbol,
                        currentPrice = price,
                        settings = _uiState.value.settings,
                        investedUsdt = volume,
                        isAutoTriggered = false,
                        binanceOrderId = orderResult.orderId,
                        binanceClientOrderId = orderResult.clientOrderId,
                        isLiveBinanceOrder = true,
                        binanceStatus = orderResult.status ?: "FILLED",
                        exactAmount = if (orderResult.executedQty > 0.0) orderResult.executedQty else null,
                        exactEntryPrice = if (orderResult.avgPrice > 0.0) orderResult.avgPrice else null,
                        autoFixNote = orderResult.autoFixApplied
                    )
                    if (alert != null) triggerAlert(alert)
                    refreshBinancePortfolio()
                } else {
                    triggerAlert(
                        TradeAlert(
                            symbol = symbol,
                            title = "LỖI VÀO LỆNH $sideLabel",
                            message = "${orderResult.errorMessage ?: "Không thể kết nối"}. Vui lòng kiểm tra lại quyền API.",
                            type = AlertType.INFO
                        )
                    )
                }
            } else {
                val alert = repository.executeSimulatedBuy(
                    symbol = symbol,
                    currentPrice = price,
                    settings = _uiState.value.settings,
                    investedUsdt = volume,
                    isAutoTriggered = false
                )
                if (alert != null) {
                    triggerAlert(alert)
                    val newBal = _uiState.value.settings.simulatedBalanceUsdt - volume
                    _uiState.value = _uiState.value.copy(
                        settings = _uiState.value.settings.updateBalance(newBal)
                    )
                }
            }
        }
    }

    fun openAllScannedPositions(isAuto: Boolean = false): Job {
        if (isAuto && !_uiState.value.settings.autoTradeEnabled) {
            val completed = Job()
            completed.complete()
            return completed
        }

        if (_uiState.value.isOpeningPositions) {
            val completed = Job()
            completed.complete()
            return completed
        }

        val job = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isOpeningPositions = true,
                openingPositionsProgress = "Đang kiểm tra danh sách vào lệnh..."
            )
            try {
                val currentTickers = _uiState.value.tickers.ifEmpty {
                    com.example.data.DefaultCoinData.getSeedTickers()
                }
                
                val symbolsToOpenSource = if (_uiState.value.selectedSignalTab == 0) {
                    _uiState.value.scannedD1BreakoutTickers.map { it.symbol }
                } else {
                    _uiState.value.scannedSuperTrendTickers.map { it.symbol }
                }

                if (symbolsToOpenSource.isEmpty()) {
                    if (!isAuto) {
                        triggerAlert(
                            TradeAlert(
                                symbol = "SCANNER",
                                title = "CHƯA CÓ TÍN HIỆU",
                                message = "Chưa có coin nào đạt tín hiệu của chiến lược đang chọn. Vui lòng bấm Quét để cập nhật!",
                                type = AlertType.INFO
                            )
                        )
                    }
                    return@launch
                }

                val openSymbols = repository.getOpenPositionSymbols()
                val symbolsToOpen = symbolsToOpenSource.filter { !openSymbols.contains(it) }

                if (symbolsToOpen.isEmpty()) {
                    if (!isAuto) {
                        triggerAlert(
                            TradeAlert(
                                symbol = "POSITIONS",
                                title = "TẤT CẢ VỊ THẾ ĐÃ ĐƯỢC MỞ",
                                message = "Toàn bộ coin đạt tín hiệu đã có vị thế trong danh sách vào lệnh!",
                                type = AlertType.INFO
                            )
                        )
                    }
                    return@launch
                }

                var newlyOpened = 0
                var totalInvested = 0.0
                var balance = _uiState.value.settings.simulatedBalanceUsdt

                for ((index, symbol) in symbolsToOpen.withIndex()) {
                    if (!isActive) break
                    if (isAuto && !_uiState.value.settings.autoTradeEnabled) break

                    val volume = _uiState.value.settings.calculatePositionVolume(balance)
                    if (balance < volume || volume <= 0.0) break
                    
                    val tickerPrice = currentTickers.find { it.symbol == symbol }?.lastPrice
                    val klinePrice = if (symbol == _uiState.value.selectedSymbol) _uiState.value.klineData.lastOrNull()?.candle?.close else null
                    val price = tickerPrice ?: klinePrice ?: continue

                    if (price <= 0.0) continue

                    if (newlyOpened > 0) {
                        _uiState.value = _uiState.value.copy(
                            openingPositionsProgress = "Đã mở $newlyOpened vị thế. Chờ 5s trước khi mở $symbol (${index + 1}/${symbolsToOpen.size})..."
                        )
                        delay(5000L)
                    }
                    if (!isActive || (isAuto && !_uiState.value.settings.autoTradeEnabled)) break

                    _uiState.value = _uiState.value.copy(
                        openingPositionsProgress = "Đang mở vị thế $symbol (${index + 1}/${symbolsToOpen.size})..."
                    )

                    val isLive = _apiConfig.value.isConnected
                    if (isLive) {
                        val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                        BinanceFuturesApiVerifier.setLeverage(symbol, _uiState.value.settings.leverage, apiKey, apiSecret, isTestnet)

                        val orderResult = BinanceFuturesApiVerifier.postMarketOrder(
                            symbol = symbol,
                            side = "BUY",
                            targetUsdtAmount = volume,
                            approxPrice = price,
                            apiKey = apiKey,
                            apiSecret = apiSecret,
                            isTestnet = isTestnet
                        )

                        if (orderResult.success) {
                            val alert = repository.executeSimulatedBuy(
                                symbol = symbol,
                                currentPrice = price,
                                settings = _uiState.value.settings,
                                investedUsdt = volume,
                                isAutoTriggered = isAuto,
                                binanceOrderId = orderResult.orderId,
                                binanceClientOrderId = orderResult.clientOrderId,
                                isLiveBinanceOrder = true,
                                binanceStatus = orderResult.status ?: "FILLED",
                                exactAmount = if (orderResult.executedQty > 0.0) orderResult.executedQty else null,
                                exactEntryPrice = if (orderResult.avgPrice > 0.0) orderResult.avgPrice else null,
                                autoFixNote = orderResult.autoFixApplied
                            )
                            if (alert != null) {
                                newlyOpened++
                                totalInvested += volume
                                triggerAlert(alert)
                            }
                        }
                    } else {
                        val alert = repository.executeSimulatedBuy(
                            symbol = symbol,
                            currentPrice = price,
                            settings = _uiState.value.settings,
                            investedUsdt = volume,
                            isAutoTriggered = isAuto
                        )

                        if (alert != null) {
                            newlyOpened++
                            totalInvested += volume
                            balance -= volume
                            _uiState.value = _uiState.value.copy(
                                settings = _uiState.value.settings.updateBalance(balance)
                            )
                            triggerAlert(alert)
                        }
                    }
                }

                if (newlyOpened > 0) {
                    if (_apiConfig.value.isConnected) refreshBinancePortfolio()
                    triggerAlert(
                        TradeAlert(
                            symbol = "ALL_BULLISH",
                            title = "ĐÃ MỞ TOÀN BỘ VỊ THẾ",
                            message = "Đã khớp $newlyOpened vị thế mới (mỗi vị thế cách nhau 5 giây, tổng vốn: ${String.format(Locale.US, "%.1f", totalInvested)} USDT).",
                            type = AlertType.AUTO_BUY_ORDER
                        )
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    isOpeningPositions = false,
                    openingPositionsProgress = ""
                )
            }
        }

        if (isAuto) openPositionsJob = job
        return job
    }

    fun scanAllFuturesCoins(isAutoScheduled: Boolean = false) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scanProgress = "Đang quét danh sách USDT-M Perpetual từ Binance...",
                scanPercent = 0f
            )

            BinanceClient.refreshValidPerpetualSymbols()

            var currentTickers = _uiState.value.tickers
            if (currentTickers.isEmpty()) {
                val tickersResult = repository.getTickers()
                currentTickers = tickersResult.getOrDefault(emptyList())
                if (currentTickers.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(tickers = currentTickers)
                }
            }

            val allSymbols = currentTickers.map { it.symbol }
            if (allSymbols.isEmpty()) {
                _uiState.value = _uiState.value.copy(isScanning = false, scanProgress = "", scanPercent = 0f)
                return@launch
            }

            val totalCoins = allSymbols.size
            var scannedCount = 0

            val d1Matches = mutableListOf<BinanceTicker>()
            val superTrendMatches = mutableListOf<BinanceTicker>()

            val chunkSize = 8
            val chunks = allSymbols.chunked(chunkSize)

            for (chunk in chunks) {
                if (!isActive) break

                val d1Deferred = chunk.map { sym ->
                    async(Dispatchers.IO) { sym to repository.getCandlesWithIndicators(sym, "1d") }
                }
                val h4Deferred = chunk.map { sym ->
                    async(Dispatchers.IO) { sym to repository.getCandlesWithIndicators(sym, "4h") }
                }

                val d1Results = d1Deferred.awaitAll().toMap()
                val h4Results = h4Deferred.awaitAll().toMap()

                for (sym in chunk) {
                    scannedCount++
                    val ticker = currentTickers.find { it.symbol == sym }

                    val d1Res = d1Results[sym]
                    if (d1Res != null && d1Res.isSuccess) {
                        val candles = d1Res.getOrNull() ?: emptyList()
                        if (candles.size >= 21) {
                            val currentCandle = candles.last()
                            val previous20 = candles.dropLast(1).takeLast(20)

                            val currentPrice = currentCandle.candle.close
                            val currentVolD1 = currentCandle.candle.volume
                            val sma20OpenD1 = previous20.map { it.candle.open }.average()
                            val avgVol20D1 = previous20.map { it.candle.volume }.average()

                            val threshold = _uiState.value.settings.volumeMultiplierThreshold
                            val volRatio = if (avgVol20D1 > 0.0) currentVolD1 / avgVol20D1 else 0.0

                            if (currentPrice >= sma20OpenD1 && volRatio >= threshold && ticker != null) {
                                d1Matches.add(ticker)
                            }
                        }
                    }

                    val h4Res = h4Results[sym]
                    if (h4Res != null && h4Res.isSuccess) {
                        val latest4h = h4Res.getOrNull()?.lastOrNull()
                        if (latest4h?.isBullishSignal(_uiState.value.settings.useSuperTrendFilter) == true && ticker != null) {
                            superTrendMatches.add(ticker)
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    scanProgress = "Đã quét $scannedCount/$totalCoins coin (D1 Vol 2x: ${d1Matches.size} | 4H SuperTrend: ${superTrendMatches.size})",
                    scanPercent = scannedCount.toFloat() / totalCoins,
                    d1BullishCount = d1Matches.size,
                    superTrendBullishCount = superTrendMatches.size,
                    scannedD1BreakoutTickers = d1Matches.toList(),
                    scannedSuperTrendTickers = superTrendMatches.toList(),
                    totalScannedCount = scannedCount
                )
                applyFilters()
                delay(60)
            }

            val now = System.currentTimeMillis()

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scanProgress = "",
                scanPercent = 1f,
                lastScanTimestamp = now
            )
            applyFilters()

            val title = if (isAutoScheduled) "QUÉT TỰ ĐỘNG HOÀN TẤT (UTC+7)" else "HOÀN TẤT QUÉT TOÀN THỊ TRƯỜNG"
            triggerAlert(
                TradeAlert(
                    symbol = "SCANNER",
                    title = title,
                    message = "Đã quét $totalCoins coin. Phát hiện:\n• D1 Breakout & Vol >= 2x: ${d1Matches.size} coin\n• 4H SuperTrend: ${superTrendMatches.size} coin.",
                    type = AlertType.BULLISH_SIGNAL
                )
            )
        }
    }

    private fun startScanScheduler() {
        schedulerJob?.cancel()
        schedulerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val target = _uiState.value.nextScanTimestamp
                val countdown = ScanScheduler.formatCountdown(target, now)
                _uiState.value = _uiState.value.copy(countdownText = countdown)

                if (now >= target) {
                    val nextTarget = ScanScheduler.getNextScanTimestamp(now + 60000)
                    _uiState.value = _uiState.value.copy(nextScanTimestamp = nextTarget)
                    scanAllFuturesCoins(isAutoScheduled = true)
                }

                delay(1000)
            }
        }
    }

    private fun startTickerPolling() {
        tickerPollJob?.cancel()
        tickerPollJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                refreshTickers()
                if (_apiConfig.value.isConnected) {
                    refreshBinancePortfolio()
                }
            }
        }
    }

    private fun startAutoTradeLoop() {
        autoTradeJob?.cancel()
        autoTradeJob = viewModelScope.launch {
            while (isActive && _uiState.value.settings.autoTradeEnabled) {
                val candidateSymbols = _uiState.value.tickers
                    .map { it.symbol }
                    .filter { it.endsWith("USDT") }

                val currentStrategy = _uiState.value.settings.selectedStrategy

                for (sym in candidateSymbols) {
                    if (!isActive || !_uiState.value.settings.autoTradeEnabled) break

                    if (repository.hasOpenPositionForSymbol(sym)) continue

                    var tradeExecuted = false

                    if (currentStrategy == com.example.model.TradingStrategyType.D1_SMA20_VOLUME) {
                        val klinesRes = repository.getCandlesWithIndicators(sym, "1d")
                        klinesRes.onSuccess { data ->
                            if (data.size >= 21) {
                                val currentCandle = data.last()
                                val previous20 = data.dropLast(1).takeLast(20)

                                val currentPrice = currentCandle.candle.close
                                val currentVolumeD1 = currentCandle.candle.volume

                                val sma20OpenD1 = previous20.map { it.candle.open }.average()
                                val avgVolume20D1 = previous20.map { it.candle.volume }.average()

                                val threshold = _uiState.value.settings.volumeMultiplierThreshold
                                val volRatio = if (avgVolume20D1 > 0.0) currentVolumeD1 / avgVolume20D1 else 0.0

                                val isBreakout = currentPrice >= sma20OpenD1 && volRatio >= threshold

                                if (isBreakout && currentPrice > 0.0) {
                                    val vol = _uiState.value.settings.calculatePositionVolume()
                                    if (_uiState.value.settings.simulatedBalanceUsdt >= vol && vol > 0.0) {
                                        executeBuy(sym, isAuto = true)
                                        tradeExecuted = true
                                    }
                                }
                            }
                        }
                    } else {
                        val res = repository.getCandlesWithIndicators(sym, "4h")
                        res.onSuccess { data ->
                            val latest = data.lastOrNull()
                            if (latest != null && latest.isBullishSignal(_uiState.value.settings.useSuperTrendFilter)) {
                                val vol = _uiState.value.settings.calculatePositionVolume()
                                if (_uiState.value.settings.simulatedBalanceUsdt >= vol && vol > 0.0) {
                                    executeBuy(sym, isAuto = true)
                                    tradeExecuted = true
                                }
                            }
                        }
                    }

                    if (tradeExecuted) {
                        delay(5000L)
                    } else {
                        delay(150L)
                    }
                }

                delay(15000L)
            }
        }
    }

    fun dismissAlert() {
        _uiState.value = _uiState.value.copy(activeAlert = null)
    }

    private fun triggerAlert(alert: TradeAlert) {
        val history = listOf(alert) + _uiState.value.alertHistory.take(29)
        _uiState.value = _uiState.value.copy(
            activeAlert = alert,
            alertHistory = history
        )
    }

    fun resetAccountBalance() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(simulatedBalanceUsdt = 10000.0)
            )
            triggerAlert(
                TradeAlert(
                    symbol = "HỆ THỐNG",
                    title = "ĐÃ ĐẶT LẠI TÀI KHOẢN",
                    message = "Số dư mô phỏng đã được khôi phục về 10,000 USDT và xóa toàn bộ lịch sử.",
                    type = AlertType.INFO
                )
            )
        }
    }

    fun updateSimulatedBalance(customBalance: Double) {
        val safeBalance = customBalance.coerceAtLeast(10.0)
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.value = _uiState.value.copy(
                settings = _uiState.value.settings.copy(simulatedBalanceUsdt = safeBalance)
            )
            triggerAlert(
                TradeAlert(
                    symbol = "VÍ ẢO",
                    title = "ĐÃ ĐỔI SỐ VỐN MÔ PHỎNG",
                    message = "Số dư ví ảo mới: $${String.format(Locale.US, "%,.2f", safeBalance)} USDT. Bắt đầu phiên test mới.",
                    type = AlertType.INFO
                )
            )
        }
    }

    fun runBacktestForSelectedCoin(
        candleLimit: Int = 180,
        tpPercent: Double = 4.0,
        slPercent: Double = 3.0,
        volThreshold: Double = 2.0
    ) {
        val sym = _uiState.value.selectedSymbol
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBacktesting = true)
            val result = withContext(Dispatchers.IO) {
                repository.runD1BreakoutBacktest(
                    symbol = sym,
                    initialBalance = _uiState.value.settings.simulatedBalanceUsdt,
                    riskPerTradePercent = 0.5,
                    takeProfitPercent = tpPercent,
                    stopLossPercent = slPercent,
                    candleLimit = candleLimit,
                    volumeThreshold = volThreshold
                )
            }
            _uiState.value = _uiState.value.copy(
                isBacktesting = false,
                backtestResult = result
            )
        }
    }

    fun clearBacktestResult() {
        _uiState.value = _uiState.value.copy(backtestResult = null)
    }

    fun computeDailyPnL(closed: List<TradePosition>): List<PeriodPnL> {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val map = linkedMapOf<String, MutableList<TradePosition>>()

        val sorted = closed.filter { it.exitTime != null }.sortedBy { it.exitTime }
        sorted.forEach { pos ->
            val dateKey = dateFormat.format(Date(pos.exitTime!!))
            map.getOrPut(dateKey) { mutableListOf() }.add(pos)
        }

        return map.map { (key, trades) ->
            val totalPnl = trades.sumOf { it.pnlUsdt ?: 0.0 }
            val winCount = trades.count { (it.pnlUsdt ?: 0.0) > 0 }
            PeriodPnL(
                periodLabel = key,
                timestamp = trades.firstOrNull()?.exitTime ?: 0L,
                pnlUsdt = totalPnl,
                tradesCount = trades.size,
                winCount = winCount
            )
        }
    }

    fun computeWeeklyPnL(closed: List<TradePosition>): List<PeriodPnL> {
        val calendar = Calendar.getInstance()
        val map = linkedMapOf<String, MutableList<TradePosition>>()

        val sorted = closed.filter { it.exitTime != null }.sortedBy { it.exitTime }
        sorted.forEach { pos ->
            calendar.timeInMillis = pos.exitTime!!
            val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
            val weekKey = "T$weekOfYear"
            map.getOrPut(weekKey) { mutableListOf() }.add(pos)
        }

        return map.map { (key, trades) ->
            val totalPnl = trades.sumOf { it.pnlUsdt ?: 0.0 }
            val winCount = trades.count { (it.pnlUsdt ?: 0.0) > 0 }
            PeriodPnL(
                periodLabel = key,
                timestamp = trades.firstOrNull()?.exitTime ?: 0L,
                pnlUsdt = totalPnl,
                tradesCount = trades.size,
                winCount = winCount
            )
        }
    }

    private val _isPinVerifiedInSession = MutableStateFlow(false)
    val isPinVerifiedInSession: StateFlow<Boolean> = _isPinVerifiedInSession.asStateFlow()

    fun isPinRequired(): Boolean = true

    fun authorizePin(pin: String): Boolean {
        val configured = _apiConfig.value.accessPin.ifBlank { "1234" }
        val ok = pin.trim() == configured
        if (ok) _isPinVerifiedInSession.value = true
        return ok
    }

    fun lockPin() {
        _isPinVerifiedInSession.value = false
    }

    fun getEffectiveApiCredentials(): Triple<String, String, Boolean> {
        val config = _apiConfig.value
        val isTestnet = config.isTestnet
        val defaultKey = if (isTestnet) SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_KEY else SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY
        val defaultSecret = if (isTestnet) SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_SECRET else SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_SECRET
        val apiKey = config.apiKey.ifBlank { defaultKey }
        val apiSecret = config.apiSecret.ifBlank { defaultSecret }
        return Triple(apiKey, apiSecret, isTestnet)
    }

    fun autoLoginMainnet(onResult: ((BinanceAccountVerificationResult) -> Unit)? = null) {
        viewModelScope.launch {
            val (apiKey, apiSecret) = secureApiStorage.getMainnetCredentials()
            val isTestnet = false
            BinanceClient.isTestnet = false

            _isVerifyingApi.value = true
            val result = BinanceFuturesApiVerifier.verifyAccount(apiKey, apiSecret, isTestnet)

            secureApiStorage.saveCredentials(
                apiKey = apiKey,
                apiSecret = apiSecret,
                accessPin = _apiConfig.value.accessPin.ifBlank { "1234" },
                isTestnet = isTestnet,
                liveTradingEnabled = true
            )
            secureApiStorage.updateVerificationResult(result)
            val updated = secureApiStorage.loadConfig()
            _apiConfig.value = updated
            _isVerifyingApi.value = false

            refreshTickers()

            if (result.isSuccess) {
                refreshBinancePortfolio()
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_MAINNET",
                        title = "ĐÃ KẾT NỐI BINANCE MAINNET THỰC TẾ",
                        message = "Đã kết nối tài khoản Binance Futures Mainnet thực tế! Số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT.",
                        type = AlertType.INFO
                    )
                )
            }
            onResult?.invoke(result)
        }
    }

    fun autoLoginTestnet(onResult: ((BinanceAccountVerificationResult) -> Unit)? = null) {
        viewModelScope.launch {
            val (apiKey, apiSecret) = secureApiStorage.getTestnetCredentials()
            val isTestnet = true
            BinanceClient.isTestnet = true

            _isVerifyingApi.value = true
            val result = BinanceFuturesApiVerifier.verifyAccount(apiKey, apiSecret, isTestnet)

            secureApiStorage.saveCredentials(
                apiKey = apiKey,
                apiSecret = apiSecret,
                accessPin = _apiConfig.value.accessPin.ifBlank { "1234" },
                isTestnet = isTestnet,
                liveTradingEnabled = true
            )
            secureApiStorage.updateVerificationResult(result)
            val updated = secureApiStorage.loadConfig()
            _apiConfig.value = updated
            _isVerifyingApi.value = false

            refreshTickers()

            if (result.isSuccess) {
                refreshBinancePortfolio()
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_TESTNET",
                        title = "ĐÃ ĐĂNG NHẬP BINANCE TESTNET THÀNH CÔNG",
                        message = "Tự động đăng nhập Binance Futures Testnet thành công! Số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT.",
                        type = AlertType.INFO
                    )
                )
            }
            onResult?.invoke(result)
        }
    }

    fun saveAndVerifyApiCredentials(
        apiKey: String,
        apiSecret: String,
        accessPin: String = "",
        isTestnet: Boolean = true,
        liveTradingEnabled: Boolean = false,
        onResult: (BinanceAccountVerificationResult) -> Unit
    ) {
        viewModelScope.launch {
            _isVerifyingApi.value = true
            BinanceClient.isTestnet = isTestnet
            val result = BinanceFuturesApiVerifier.verifyAccount(apiKey, apiSecret, isTestnet)
            if (result.isSuccess) {
                secureApiStorage.saveCredentials(apiKey, apiSecret, accessPin, isTestnet, liveTradingEnabled)
                secureApiStorage.updateVerificationResult(result)
                val updated = secureApiStorage.loadConfig()
                _apiConfig.value = updated
                refreshBinancePortfolio()
                refreshTickers()
            } else {
                secureApiStorage.updateVerificationResult(result)
                _apiConfig.value = _apiConfig.value.copy(
                    isConnected = false,
                    lastErrorMessage = result.errorMessage
                )
                refreshTickers()
            }
            _isVerifyingApi.value = false
            onResult(result)
        }
    }

    fun verifyAccessPin(enteredPin: String): Boolean = _apiConfig.value.verifyPin(enteredPin)

    fun testApiConnection(onResult: (BinanceAccountVerificationResult) -> Unit) {
        val current = _apiConfig.value
        if (!current.isConfigured) {
            onResult(
                BinanceAccountVerificationResult(
                    isSuccess = false,
                    errorMessage = "Chưa có thông tin API Key và Secret để kiểm tra."
                )
            )
            return
        }
        viewModelScope.launch {
            _isVerifyingApi.value = true
            val result = BinanceFuturesApiVerifier.verifyAccount(current.apiKey, current.apiSecret, current.isTestnet)
            secureApiStorage.updateVerificationResult(result)
            _apiConfig.value = secureApiStorage.loadConfig()
            if (result.isSuccess) refreshBinancePortfolio()
            _isVerifyingApi.value = false
            onResult(result)
        }
    }

    fun fetchPublicIpAddress(forceRefresh: Boolean = false) {
        if (!forceRefresh && _publicIpAddress.value != null) return
        viewModelScope.launch {
            _isFetchingIp.value = true
            val ip = BinanceFuturesApiVerifier.fetchPublicIpAddress()
            _publicIpAddress.value = ip
            _isFetchingIp.value = false
        }
    }

    fun updateProxyConfig(host: String, port: Int, proxyType: String = "HTTP") {
        secureApiStorage.saveProxyConfig(host, port, proxyType)
        BinanceClient.updateProxy(host, port, proxyType.equals("SOCKS", ignoreCase = true))
        _apiConfig.value = secureApiStorage.loadConfig()
        fetchPublicIpAddress(forceRefresh = true)
    }

    fun clearProxyConfig() {
        secureApiStorage.clearProxyConfig()
        BinanceClient.updateProxy("", 0)
        _apiConfig.value = secureApiStorage.loadConfig()
        fetchPublicIpAddress(forceRefresh = true)
    }

    fun refreshBinancePortfolio() {
        val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
        if (apiKey.isBlank() || apiSecret.isBlank()) return

        viewModelScope.launch {
            _isRefreshingPortfolio.value = true
            val portfolio = BinanceFuturesApiVerifier.fetchFullPortfolio(
                apiKey = apiKey,
                apiSecret = apiSecret,
                isTestnet = isTestnet
            )
            _binancePortfolio.value = portfolio
            if (portfolio.isLiveConnected) {
                repository.syncWithBinanceLivePositions(portfolio.positions)
                _apiConfig.value = _apiConfig.value.copy(
                    isConnected = true,
                    totalWalletBalance = portfolio.totalWalletBalance,
                    availableBalance = portfolio.availableBalance
                )
            }
            _isRefreshingPortfolio.value = false
        }
    }

    fun verifyOrderOnBinance(symbol: String, orderId: Long) {
        val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
        viewModelScope.launch {
            _isVerifyingOrder.value = true
            val detail = BinanceFuturesApiVerifier.checkOrderStatus(
                symbol = symbol,
                orderId = orderId,
                apiKey = apiKey,
                apiSecret = apiSecret,
                isTestnet = isTestnet
            )
            _orderVerificationDetail.value = detail
            _isVerifyingOrder.value = false
        }
    }

    fun dismissOrderVerificationDetail() {
        _orderVerificationDetail.value = null
    }

    fun setLiveTradingEnabled(enabled: Boolean) {
        secureApiStorage.setLiveTradingEnabled(enabled)
        _apiConfig.value = _apiConfig.value.copy(liveTradingEnabled = enabled)
    }

    fun clearApiCredentials() {
        secureApiStorage.clearCredentials()
        _apiConfig.value = BinanceApiConfig()
        _binancePortfolio.value = BinancePortfolio()
    }

    override fun onCleared() {
        super.onCleared()
        autoTradeJob?.cancel()
        tickerPollJob?.cancel()
        schedulerJob?.cancel()
    }
}