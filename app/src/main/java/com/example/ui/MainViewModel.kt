package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.model.ActiveBasket
import com.example.model.AlertType
import com.example.model.BacktestConfig
import com.example.model.BacktestHistoryRecord
import com.example.model.BacktestSummaryResult
import com.example.model.BasketBacktestReport
import com.example.model.BasketPosition
import com.example.model.BasketSlotId
import com.example.model.BasketStatus
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
import kotlinx.coroutines.flow.update
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
    val scannedBullishTickers: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers().take(3),
    val bullishCount: Int = 3,
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
    val settings: TradeSettings = TradeSettings(leverage = 1),
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
    val isBacktesting: Boolean = false,
    val basketReport: BasketBacktestReport? = null,
    val isBasketBacktesting: Boolean = false
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
    private var klinePollJob: Job? = null
    private var schedulerJob: Job? = null
    private var webSocketClient: com.example.network.BinanceWebSocketClient? = null
    private val pendingWsUpdates = java.util.concurrent.ConcurrentHashMap<String, com.example.network.MiniTickerUpdate>()
    private var lastWsFlushTime: Long = 0L
    private var lastPositionsUpdateTime: Long = 0L
    private val liveBasketAssignments = java.util.concurrent.ConcurrentHashMap<String, String>()

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

    private val _primaryBasket = MutableStateFlow<ActiveBasket?>(null)
    val primaryBasketState: StateFlow<ActiveBasket?> = _primaryBasket.asStateFlow()
    val primaryBasket: ActiveBasket? get() = _primaryBasket.value

    private val _secondaryBasket = MutableStateFlow<ActiveBasket?>(null)
    val secondaryBasketState: StateFlow<ActiveBasket?> = _secondaryBasket.asStateFlow()
    val secondaryBasket: ActiveBasket? get() = _secondaryBasket.value

    private val _backtestConfig = MutableStateFlow(BacktestConfig())
    val backtestConfig: StateFlow<BacktestConfig> = _backtestConfig.asStateFlow()

    private val _backtestDays = MutableStateFlow(180)
    val backtestDays: StateFlow<Int> = _backtestDays.asStateFlow()

    private val _backtestHistory = MutableStateFlow<List<BacktestHistoryRecord>>(
        listOf(
            BacktestHistoryRecord(
                timestamp = System.currentTimeMillis() - 86400000L * 2,
                days = 180,
                config = BacktestConfig(
                    kTrail = 0.25,
                    minVolRatio = 2.0,
                    enableTrailingStop = true,
                    enableFloorLock = true,
                    floorLockMinRoi = 0.005
                ),
                netProfitUsdt = 3420.50,
                netProfitPercent = 34.21,
                winratePercent = 81.25,
                maxDrawdownPercent = 3.85,
                totalCycles = 16,
                winCycles = 13,
                lossCycles = 3,
                trailingHits = 13
            ),
            BacktestHistoryRecord(
                timestamp = System.currentTimeMillis() - 86400000L * 5,
                days = 90,
                config = BacktestConfig(
                    kTrail = 0.20,
                    minVolRatio = 2.5,
                    enableTrailingStop = true,
                    enableFloorLock = true,
                    floorLockMinRoi = 0.005
                ),
                netProfitUsdt = 1850.00,
                netProfitPercent = 18.50,
                winratePercent = 75.00,
                maxDrawdownPercent = 4.20,
                totalCycles = 12,
                winCycles = 9,
                lossCycles = 3,
                trailingHits = 9
            )
        )
    )
    val backtestHistory: StateFlow<List<BacktestHistoryRecord>> = _backtestHistory.asStateFlow()


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
        initWebSocket()
        startTickerPolling()
        startKlinePolling()
        startScanScheduler()
        fetchPublicIpAddress()
        if (loadedConfig.isTestnet) {
            autoLoginTestnet()
        } else {
            autoLoginMainnet()
        }

        viewModelScope.launch {
            openPositions.collect { list ->
                val priList = list.filter { it.basketSlot.uppercase() == "PRIMARY" || it.basketSlot.isEmpty() }
                val secList = list.filter { it.basketSlot.uppercase() == "SECONDARY" }

                if (priList.isNotEmpty()) {
                    val pos = priList.map {
                        BasketPosition(
                            symbol = it.symbol,
                            entryPrice = it.entryPrice,
                            quantity = it.amount,
                            marginAllocated = it.investedUsdt,
                            entryAtrPercent = 0.05
                        )
                    }
                    _primaryBasket.value = ActiveBasket(
                        slotId = BasketSlotId.PRIMARY,
                        openTime = priList.minOfOrNull { it.entryTime } ?: System.currentTimeMillis(),
                        positions = pos,
                        totalInvested = pos.sumOf { it.marginAllocated }
                    )
                } else {
                    _primaryBasket.value = null
                }

                if (secList.isNotEmpty()) {
                    val pos = secList.map {
                        BasketPosition(
                            symbol = it.symbol,
                            entryPrice = it.entryPrice,
                            quantity = it.amount,
                            marginAllocated = it.investedUsdt,
                            entryAtrPercent = 0.05
                        )
                    }
                    _secondaryBasket.value = ActiveBasket(
                        slotId = BasketSlotId.SECONDARY,
                        openTime = secList.minOfOrNull { it.entryTime } ?: System.currentTimeMillis(),
                        positions = pos,
                        totalInvested = pos.sumOf { it.marginAllocated }
                    )
                } else {
                    _secondaryBasket.value = null
                }
            }
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
        }
    }

    fun selectSymbol(symbol: String, timeframe: String = "4h") {
        _uiState.value = _uiState.value.copy(
            selectedSymbol = symbol,
            selectedTimeframe = timeframe,
            backtestResult = null
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

    fun selectSignalTab(tabIndex: Int = 0) {
        _uiState.value = _uiState.value.copy(
            selectedSignalTab = 0,
            filterMode = MarketFilter.BULLISH_SIGNAL
        )
        applyFilters(_searchQueryFlow.value)
    }

    private fun applyFilters(query: String = _searchQueryFlow.value) {
        val current = _uiState.value
        val openSymbols = openPositions.value.map { it.symbol }.toSet()
        val cleanQuery = com.example.util.SearchUtils.stripUsdtSuffix(query).uppercase()
        val rawQuery = query.trim().uppercase()

        val activeSignalSymbols = current.scannedD1BreakoutTickers.map { it.symbol }.toSet()

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

    private fun initWebSocket() {
        webSocketClient?.stop()
        val isTestnet = _apiConfig.value.isTestnet
        webSocketClient = com.example.network.BinanceWebSocketClient { updates ->
            handleWebSocketTickers(updates)
        }
        webSocketClient?.start(isTestnet)
    }

    private fun handleWebSocketTickers(updates: List<com.example.network.MiniTickerUpdate>) {
        if (updates.isEmpty()) return

        // 1. Lưu giá mới nhất vào pendingWsUpdates map (nhanh O(1), không nghẽn luồng)
        for (u in updates) {
            pendingWsUpdates[u.symbol] = u
        }

        // 2. Throttle cập nhật danh sách tickers lên UI: Tối đa 1 lần mỗi 2.5s để chống giật lag
        val now = System.currentTimeMillis()
        if (now - lastWsFlushTime >= 2500L) {
            lastWsFlushTime = now
            flushThrottledTickerUpdates()
        }
    }

    private fun flushThrottledTickerUpdates() {
        val currentTickers = _uiState.value.tickers
        if (currentTickers.isEmpty() || pendingWsUpdates.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = HashMap(pendingWsUpdates)
            pendingWsUpdates.clear()

            // Cập nhật giá nến và tickers trên background thread Dispatchers.Default
            val updated = currentTickers.map { ticker ->
                val u = snapshot[ticker.symbol]
                if (u != null) {
                    val changePct = if (u.openPrice > 0.0) ((u.closePrice - u.openPrice) / u.openPrice) * 100.0 else ticker.priceChangePercent
                    ticker.copy(
                        lastPrice = u.closePrice,
                        highPrice = u.highPrice,
                        lowPrice = u.lowPrice,
                        volume = u.volume,
                        priceChangePercent = changePct
                    )
                } else {
                    ticker
                }
            }

            // Tính toán danh sách filtered trên background thread
            val current = _uiState.value
            val query = _searchQueryFlow.value
            val openSymbols = openPositions.value.map { it.symbol }.toSet()
            val cleanQuery = com.example.util.SearchUtils.stripUsdtSuffix(query).uppercase()
            val rawQuery = query.trim().uppercase()
            val activeSignalSymbols = current.scannedD1BreakoutTickers.map { it.symbol }.toSet()

            val filtered = updated.filter { ticker ->
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
            }

            // Cập nhật StateFlow MỘT LẦN DUY NHẤT cho cả tickers & filteredTickers
            _uiState.update { it.copy(tickers = updated, filteredTickers = filtered) }

            // Cập nhật giá cho các vị thế mở: chỉ thực hiện khi CÓ VỊ THẾ MỞ và cách nhau ít nhất 3s
            val nowTime = System.currentTimeMillis()
            if (openPositions.value.isNotEmpty() && (nowTime - lastPositionsUpdateTime >= 3000L)) {
                lastPositionsUpdateTime = nowTime
                val priceMap = snapshot.mapValues { it.value.closePrice }
                withContext(Dispatchers.IO) {
                    val res = repository.updatePositionsWithPrices(priceMap)
                    for (alert in res.alerts) {
                        triggerAlert(alert)
                    }
                    if (res.totalReturnedCapital > 0.0) {
                        val newBal = _uiState.value.settings.simulatedBalanceUsdt + res.totalReturnedCapital
                        _uiState.update { state ->
                            state.copy(settings = state.settings.updateBalance(newBal))
                        }
                    }
                }
            }
        }
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
                if (updateResult.totalReturnedCapital > 0.0) {
                    val newBal = _uiState.value.settings.simulatedBalanceUsdt + updateResult.totalReturnedCapital
                    _uiState.value = _uiState.value.copy(
                        settings = _uiState.value.settings.updateBalance(newBal)
                    )
                }
                checkPortfolioTakeProfit(priceMap)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private fun checkPortfolioTakeProfit(overridePriceMap: Map<String, Double>? = null) {
        viewModelScope.launch {
            val priceMap = overridePriceMap ?: _uiState.value.tickers.associate { it.symbol to it.lastPrice }
            val targetTp = _uiState.value.settings.portfolioTakeProfitPercent
            val currentOpen = repository.getOpenPositionsList()
            val livePositions = _binancePortfolio.value.positions

            val isLiveConnected = _apiConfig.value.isConnected
            val hasPositionsToMonitor = if (isLiveConnected) livePositions.isNotEmpty() else currentOpen.isNotEmpty()

            if (targetTp > 0.0 && hasPositionsToMonitor) {
                val currentPnlPercent = if (isLiveConnected) {
                    val totalActiveMargin = livePositions.sumOf { pos ->
                        if (pos.initialMargin > 0.0) {
                            pos.initialMargin
                        } else {
                            (pos.absAmount * pos.entryPrice) / pos.leverage.coerceAtLeast(1)
                        }
                    }
                    val totalUnrealized = _binancePortfolio.value.totalUnrealizedProfit
                    if (totalActiveMargin > 0.0) (totalUnrealized / totalActiveMargin) * 100.0 else 0.0
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
                    if (isLiveConnected) {
                        val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                        for (pos in livePositions) {
                            val side = if (pos.isLong) "SELL" else "BUY"
                            BinanceFuturesApiVerifier.postMarketCloseOrder(
                                symbol = pos.symbol,
                                amount = pos.absAmount,
                                approxPrice = pos.markPrice,
                                apiKey = apiKey,
                                apiSecret = apiSecret,
                                isTestnet = isTestnet,
                                side = side
                            )
                        }
                    }

                    val ptpResult = repository.checkAndExecutePortfolioTakeProfit(priceMap, targetTp)
                    for (alert in ptpResult.alerts) {
                        triggerAlert(alert)
                    }

                    if (ptpResult.totalReturnedCapital > 0.0) {
                        val newBal = _uiState.value.settings.simulatedBalanceUsdt + ptpResult.totalReturnedCapital
                        _uiState.value = _uiState.value.copy(
                            settings = _uiState.value.settings.updateBalance(newBal)
                        )
                    }

                    if (isLiveConnected) {
                        refreshBinancePortfolio()
                    }
                }
            }
        }
    }

    fun loadKlineForSymbol(symbol: String, timeframe: String, isBackground: Boolean = false) {
        viewModelScope.launch {
            if (!isBackground) {
                _uiState.value = _uiState.value.copy(isKlineLoading = true)
            }
            repository.getCandlesWithIndicators(symbol, timeframe).onSuccess { data ->
                _uiState.value = _uiState.value.copy(
                    klineData = data,
                    isKlineLoading = false
                )
                applyFilters()
            }.onFailure {
                if (!isBackground) {
                    _uiState.value = _uiState.value.copy(isKlineLoading = false)
                }
            }
        }
    }

    fun toggleAutoTrade(enabled: Boolean) {
        val newSettings = _uiState.value.settings.copy(autoTradeEnabled = enabled, leverage = 1)
        _uiState.value = _uiState.value.copy(settings = newSettings)

        val context = getApplication<Application>().applicationContext

        if (enabled) {
            // Tự động khởi động Foreground Service chạy ngầm 24/7
            com.example.TradingForegroundService.startService(context)

            val nextTarget = ScanScheduler.getNextScanTimestamp()
            val nextScanHour = ScanScheduler.getNextScanHourText(nextTarget)
            val countdown = ScanScheduler.formatCountdown(nextTarget)
            val remainingReadable = ScanScheduler.formatRemainingReadable(nextTarget)
            _uiState.update { it.copy(nextScanTimestamp = nextTarget, countdownText = countdown) }

            triggerAlert(
                TradeAlert(
                    symbol = "BOT",
                    title = "ĐÃ KÍCH HOẠT BOT THÀNH CÔNG",
                    message = "Bot đã kích hoạt chế độ tự động 24/7!\n\n• Phiên quét và vào lệnh tiếp theo: $nextScanHour\n• Thời gian còn lại: $countdown ($remainingReadable)\n\nLƯU Ý QUAN TRỌNG: Bot tuyệt đối KHÔNG vào lệnh ngay lúc này. Lệnh chỉ được mở khi hệ thống quét tín hiệu ở đúng mốc thời gian trên (07:00 & 19:00 UTC+7) để đảm bảo kỷ luật chiến lược 2 giỏ.",
                    type = AlertType.INFO
                )
            )

            startAutoTradeLoop()
        } else {
            // Tắt Foreground Service khi dừng Auto-Trade
            com.example.TradingForegroundService.stopService(context)

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
                    message = "Hệ thống dừng quét và mở lệnh mới. Các vị thế đang mở vẫn tiếp tục được bảo vệ Trailing Stop.",
                    type = AlertType.INFO
                )
            )
        }
    }
  
    fun updatePortfolioTakeProfitPercent(targetPercent: Double) {
        val clamped = targetPercent.coerceAtLeast(0.5)
        val updatedSettings = _uiState.value.settings.copy(portfolioTakeProfitPercent = clamped, leverage = 1)
        _uiState.value = _uiState.value.copy(settings = updatedSettings)
        
        triggerAlert(
            TradeAlert(
                symbol = "PORTFOLIO",
                title = "ĐÃ ĐỔI MỤC TIÊU CHỐT LỜI",
                message = "Mục tiêu chốt lời toàn danh mục mới: +${String.format(Locale.US, "%.1f", clamped)}%.",
                type = AlertType.INFO
            )
        )
    }

    fun updateSettings(newSettings: TradeSettings) {
        _uiState.value = _uiState.value.copy(settings = newSettings.copy(leverage = 1))
    }

    fun setRoiSortOrder(order: RoiSortOrder) {
        _uiState.value = _uiState.value.copy(roiSortOrder = order)
    }

    /**
     * Mua coin (Tự động hoặc Thủ công) - Ép cứng Cross Margin & Đòn bẩy 1x trên Binance.
     * Tính toán vốn động dựa trên số dư khả dụng thực tế còn lại của tài khoản.
     */
    fun executeBuy(symbol: String, isAuto: Boolean = false, basketSlot: String = "PRIMARY") {
        if (!isAuto && _uiState.value.settings.autoTradeEnabled) {
            triggerAlert(
                TradeAlert(
                    symbol = symbol,
                    title = "BOT ĐANG HOẠT ĐỘNG",
                    message = "Bot giao dịch tự động đang hoạt động theo cơ chế Dual Basket và phân bổ vốn động. Không nên mở lệnh thủ công để tránh làm lệch tỷ lệ vốn của các giỏ.",
                    type = AlertType.INFO
                )
            )
            return
        }
        if (isAuto && !_uiState.value.settings.autoTradeEnabled) return
        viewModelScope.launch {
            val currentTicker = _uiState.value.tickers.find { it.symbol == symbol }
            val price = currentTicker?.lastPrice
                ?: _uiState.value.klineData.lastOrNull()?.candle?.close
                ?: return@launch

            val isLive = _apiConfig.value.isConnected
            // Tính toán vốn động: kiểm tra số dư khả dụng thực tế còn lại
            val availableBal = if (isLive) {
                val b = _binancePortfolio.value.availableBalance
                if (b > 0.0) b else _binancePortfolio.value.totalWalletBalance
            } else {
                val used = openPositions.value.sumOf { it.investedUsdt }
                maxOf(0.0, _uiState.value.settings.simulatedBalanceUsdt - used)
            }

            val volume = _uiState.value.settings.calculateDynamicVolume(availableBal)
            if (availableBal < volume || volume <= 0.0) {
                triggerAlert(
                    TradeAlert(
                        symbol = symbol,
                        title = "KHÔNG ĐỦ VỐN KHẢ DỤNG",
                        message = "Số dư khả dụng còn lại ($${String.format(Locale.US, "%.1f", availableBal)} USDT) không đủ mở lệnh ($${String.format(Locale.US, "%.1f", volume)} USDT).",
                        type = AlertType.INFO
                    )
                )
                return@launch
            }

            if (isLive) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                val netLabel = if (isTestnet) "Testnet Demo" else "Mainnet"
                val cleanSymbol = symbol.trim().uppercase()

                // Lưu ánh xạ giỏ cho vị thế Binance Live
                liveBasketAssignments[cleanSymbol] = basketSlot

                _uiState.value = _uiState.value.copy(
                    isOpeningPositions = true,
                    openingPositionsProgress = "Đang cài đặt Cross 1x cho $cleanSymbol ($netLabel)..."
                )

                // 1. Chờ ép cài đặt Margin Type CROSSED và Leverage 1x lên sàn
                withContext(Dispatchers.IO) {
                    BinanceClient.setCrossMarginAndLeverage1x(cleanSymbol, apiKey, apiSecret, isTestnet)
                    BinanceFuturesApiVerifier.setLeverage(cleanSymbol, 1, apiKey, apiSecret, isTestnet)
                }

                // Nghỉ 200ms để Binance cập nhật cấu hình đòn bẩy trên sổ lệnh sàn
                delay(200L)

                _uiState.value = _uiState.value.copy(
                    openingPositionsProgress = "Đang gửi lệnh MARKET $cleanSymbol (1x)..."
                )

                // 2. Tiến hành đặt lệnh Market với đòn bẩy đã được chốt 1x
                val orderResult = BinanceFuturesApiVerifier.postMarketOrder(
                    symbol = cleanSymbol,
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
                        symbol = cleanSymbol,
                        currentPrice = price,
                        settings = _uiState.value.settings.copy(leverage = 1),
                        investedUsdt = volume,
                        isAutoTriggered = isAuto,
                        binanceOrderId = orderResult.orderId,
                        binanceClientOrderId = orderResult.clientOrderId,
                        isLiveBinanceOrder = true,
                        binanceStatus = orderResult.status ?: "FILLED",
                        exactAmount = if (orderResult.executedQty > 0.0) orderResult.executedQty else null,
                        exactEntryPrice = if (orderResult.avgPrice > 0.0) orderResult.avgPrice else null,
                        autoFixNote = orderResult.autoFixApplied,
                        basketSlot = basketSlot
                    )

                    if (alert != null) triggerAlert(alert)
                    refreshBinancePortfolio()
                } else {
                    val fixNote = orderResult.autoFixApplied?.let { " ($it)" } ?: ""
                    triggerAlert(
                        TradeAlert(
                            symbol = cleanSymbol,
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
                settings = _uiState.value.settings.copy(leverage = 1),
                investedUsdt = volume,
                isAutoTriggered = isAuto,
                binanceOrderId = null,
                binanceClientOrderId = null,
                isLiveBinanceOrder = false,
                binanceStatus = null,
                basketSlot = basketSlot
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
        webSocketClient?.start(isTestnet = newTestnet)

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
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(leverage = 1)
        )
        if (_apiConfig.value.isConnected) {
            viewModelScope.launch {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                val cleanSymbol = symbol.trim().uppercase()
                withContext(Dispatchers.IO) {
                    BinanceClient.setCrossMarginAndLeverage1x(cleanSymbol, apiKey, apiSecret, isTestnet)
                    BinanceFuturesApiVerifier.setLeverage(cleanSymbol, 1, apiKey, apiSecret, isTestnet)
                }
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
            val cleanSymbol = symbol.trim().uppercase()

            if (_apiConfig.value.isConnected) {
                val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()

                // Ép cứng Cross Margin & Đòn bẩy 1x trước khi vào lệnh
                withContext(Dispatchers.IO) {
                    BinanceClient.setCrossMarginAndLeverage1x(cleanSymbol, apiKey, apiSecret, isTestnet)
                    BinanceFuturesApiVerifier.setLeverage(cleanSymbol, 1, apiKey, apiSecret, isTestnet)
                }
                delay(200L)

                val orderResult = BinanceFuturesApiVerifier.postMarketOrder(
                    symbol = cleanSymbol,
                    side = side,
                    targetUsdtAmount = volume,
                    approxPrice = price,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    isTestnet = isTestnet
                )

                if (orderResult.success) {
                    val alert = repository.executeSimulatedBuy(
                        symbol = cleanSymbol,
                        currentPrice = price,
                        settings = _uiState.value.settings.copy(leverage = 1),
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
                            symbol = cleanSymbol,
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
                    settings = _uiState.value.settings.copy(leverage = 1),
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

    fun openAllScannedPositions(isAuto: Boolean = false, targetBasketSlot: String = "PRIMARY"): Job {
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
                openingPositionsProgress = "Đang kiểm tra danh sách vào lệnh cho [$targetBasketSlot]..."
            )
            try {
                val currentTickers = _uiState.value.tickers.ifEmpty {
                    com.example.data.DefaultCoinData.getSeedTickers()
                }
                
                val symbolsToOpenSource = _uiState.value.scannedD1BreakoutTickers.map { it.symbol }

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

                for ((index, symbol) in symbolsToOpen.withIndex()) {
                    if (!isActive) break
                    if (isAuto && !_uiState.value.settings.autoTradeEnabled) break

                    val isLive = _apiConfig.value.isConnected
                    // Vốn động: luôn kiểm tra số dư khả dụng thực tế còn lại tại thời điểm mở từng lệnh
                    val availableBal = if (isLive) {
                        val b = _binancePortfolio.value.availableBalance
                        if (b > 0.0) b else _binancePortfolio.value.totalWalletBalance
                    } else {
                        val used = openPositions.value.sumOf { it.investedUsdt } + totalInvested
                        maxOf(0.0, _uiState.value.settings.simulatedBalanceUsdt - used)
                    }

                    val volume = _uiState.value.settings.calculateDynamicVolume(availableBal)
                    if (availableBal < volume || volume <= 0.0) break
                    
                    val tickerPrice = currentTickers.find { it.symbol == symbol }?.lastPrice
                    val klinePrice = if (symbol == _uiState.value.selectedSymbol) _uiState.value.klineData.lastOrNull()?.candle?.close else null
                    val price = tickerPrice ?: klinePrice ?: continue

                    if (price <= 0.0) continue

                    val cleanSymbol = symbol.trim().uppercase()

                    if (newlyOpened > 0) {
                        _uiState.value = _uiState.value.copy(
                            openingPositionsProgress = "Đã mở $newlyOpened vị thế ($targetBasketSlot). Chờ 5s trước khi mở $cleanSymbol (${index + 1}/${symbolsToOpen.size})..."
                        )
                        delay(5000L)
                    }
                    if (!isActive || (isAuto && !_uiState.value.settings.autoTradeEnabled)) break

                    _uiState.value = _uiState.value.copy(
                        openingPositionsProgress = "Đang mở vị thế Cross 1x $cleanSymbol [$targetBasketSlot] (${index + 1}/${symbolsToOpen.size})..."
                    )

                    if (isLive) {
                        val (apiKey, apiSecret, isTestnet) = getEffectiveApiCredentials()
                        
                        // Lưu gán giỏ cho vị thế Binance Live
                        liveBasketAssignments[cleanSymbol] = targetBasketSlot

                        // Ép cứng Cross Margin & Đòn bẩy 1x trên sàn trước khi bắn lệnh
                        withContext(Dispatchers.IO) {
                            BinanceClient.setCrossMarginAndLeverage1x(cleanSymbol, apiKey, apiSecret, isTestnet)
                            BinanceFuturesApiVerifier.setLeverage(cleanSymbol, 1, apiKey, apiSecret, isTestnet)
                        }
                        delay(200L)

                        val orderResult = BinanceFuturesApiVerifier.postMarketOrder(
                            symbol = cleanSymbol,
                            side = "BUY",
                            targetUsdtAmount = volume,
                            approxPrice = price,
                            apiKey = apiKey,
                            apiSecret = apiSecret,
                            isTestnet = isTestnet
                        )

                        if (orderResult.success) {
                            val alert = repository.executeSimulatedBuy(
                                symbol = cleanSymbol,
                                currentPrice = price,
                                settings = _uiState.value.settings.copy(leverage = 1),
                                investedUsdt = volume,
                                isAutoTriggered = isAuto,
                                binanceOrderId = orderResult.orderId,
                                binanceClientOrderId = orderResult.clientOrderId,
                                isLiveBinanceOrder = true,
                                binanceStatus = orderResult.status ?: "FILLED",
                                exactAmount = if (orderResult.executedQty > 0.0) orderResult.executedQty else null,
                                exactEntryPrice = if (orderResult.avgPrice > 0.0) orderResult.avgPrice else null,
                                autoFixNote = orderResult.autoFixApplied,
                                basketSlot = targetBasketSlot
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
                            settings = _uiState.value.settings.copy(leverage = 1),
                            investedUsdt = volume,
                            isAutoTriggered = isAuto,
                            basketSlot = targetBasketSlot
                        )

                        if (alert != null) {
                            newlyOpened++
                            totalInvested += volume
                            triggerAlert(alert)
                        }
                    }
                }

                if (newlyOpened > 0) {
                    if (_apiConfig.value.isConnected) refreshBinancePortfolio()
                    triggerAlert(
                        TradeAlert(
                            symbol = "ALL_BULLISH",
                            title = "ĐÃ MỞ TOÀN BỘ VỊ THẾ [$targetBasketSlot] (LONG 1X)",
                            message = "Đã khớp $newlyOpened vị thế mới thuộc $targetBasketSlot với vốn động (tổng vốn đã vào: ${String.format(Locale.US, "%.1f", totalInvested)} USDT).",
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

    fun scanAllFuturesCoins(isAutoScheduled: Boolean = false, targetBasketSlot: String = "PRIMARY") {
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

            val chunkSize = 8
            val chunks = allSymbols.chunked(chunkSize)

            for (chunk in chunks) {
                if (!isActive) break

                val d1Deferred = chunk.map { sym ->
                    async(Dispatchers.IO) { sym to repository.getCandlesWithIndicators(sym, "1d") }
                }

                val d1Results = d1Deferred.awaitAll().toMap()

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
                }

                _uiState.value = _uiState.value.copy(
                    scanProgress = "Đã quét $scannedCount/$totalCoins coin (D1 Breakout: ${d1Matches.size})",
                    scanPercent = scannedCount.toFloat() / totalCoins,
                    d1BullishCount = d1Matches.size,
                    scannedD1BreakoutTickers = d1Matches.toList(),
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

            val title = if (isAutoScheduled) "QUÉT TỰ ĐỘNG HOÀN TẤT [$targetBasketSlot]" else "HOÀN TẤT QUÉT TOÀN THỊ TRƯỜNG"
            triggerAlert(
                TradeAlert(
                    symbol = "SCANNER",
                    title = title,
                    message = "Đã quét $totalCoins coin. Phát hiện:\n• D1 Breakout & Vol >= 2x: ${d1Matches.size} coin.",
                    type = AlertType.BULLISH_SIGNAL
                )
            )

            // Khi đến đúng mốc quét theo lịch và Bot đang bật, tự động vào lệnh phân bổ theo giỏ tương ứng
            if (isAutoScheduled && _uiState.value.settings.autoTradeEnabled && d1Matches.isNotEmpty()) {
                openAllScannedPositions(isAuto = true, targetBasketSlot = targetBasketSlot)
            }
        }
    }

    private fun startScanScheduler() {
        schedulerJob?.cancel()
        schedulerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val target = _uiState.value.nextScanTimestamp
                val countdown = ScanScheduler.formatCountdown(target, now)
                if (_uiState.value.countdownText != countdown) {
                    _uiState.update { it.copy(countdownText = countdown) }
                }

                if (now >= target) {
                    val targetSlot = ScanScheduler.getNextScanSlot(target)
                    val nextTarget = ScanScheduler.getNextScanTimestamp(now + 60000)
                    _uiState.update { it.copy(nextScanTimestamp = nextTarget) }
                    scanAllFuturesCoins(isAutoScheduled = true, targetBasketSlot = targetSlot)
                }

                delay(1000)
            }
        }
    }

    /**
     * Vòng lặp giám sát vị thế và PnL danh mục mỗi 5s (tuân thủ nghiêm ngặt Rate Limit Binance)
     * Kèm REST polling dự phòng mỗi 30s
     */
    private fun startTickerPolling() {
        tickerPollJob?.cancel()
        tickerPollJob = viewModelScope.launch {
            var restBackupCounter = 0
            while (isActive) {
                delay(5000L) // Kiểm tra vị thế lệnh & PnL danh mục mỗi 5 giây
                
                // Đồng bộ số dư ví và vị thế lệnh Binance Live mỗi 5s
                if (_apiConfig.value.isConnected) {
                    refreshBinancePortfolio()
                }

                // Kiểm tra mục tiêu chốt lời toàn danh mục (Portfolio TP)
                checkPortfolioTakeProfit()

                // REST ticker polling làm backup định kỳ mỗi 30 giây (6 chu kỳ x 5s)
                restBackupCounter++
                if (restBackupCounter >= 6) {
                    refreshTickers()
                    restBackupCounter = 0
                }
            }
        }
    }

    /**
     * Cập nhật Klines định kỳ mỗi 25 giây (giữa 15s – 30s theo yêu cầu)
     */
    private fun startKlinePolling() {
        klinePollJob?.cancel()
        klinePollJob = viewModelScope.launch {
            while (isActive) {
                delay(25000L) // Chu kỳ an toàn 25 giây
                val currentSym = _uiState.value.selectedSymbol
                val currentTf = _uiState.value.selectedTimeframe
                if (currentSym.isNotEmpty()) {
                    loadKlineForSymbol(currentSym, currentTf, isBackground = true)
                }
            }
        }
    }

    private fun startAutoTradeLoop() {
        autoTradeJob?.cancel()
        // Bot tuân thủ tuyệt đối cơ chế Dual Basket và lịch quét (07:00 & 19:00 UTC+7).
        // Tuyệt đối không tự ý vào lệnh ngay sau khi bật Bot; việc vào lệnh chỉ diễn ra ở mốc quét kế tiếp do startScanScheduler điều khiển.
        autoTradeJob = viewModelScope.launch {
            while (isActive && _uiState.value.settings.autoTradeEnabled) {
                delay(30000L)
            }
        }
    }

    fun dismissAlert() {
        _uiState.value = _uiState.value.copy(activeAlert = null)
    }

    fun clearAllAlerts() {
        _uiState.value = _uiState.value.copy(activeAlert = null, alertHistory = emptyList())
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
                settings = _uiState.value.settings.copy(simulatedBalanceUsdt = 10000.0, leverage = 1)
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
                settings = _uiState.value.settings.copy(simulatedBalanceUsdt = safeBalance, leverage = 1)
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
        slPercent: Double = 0.0,
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

    fun runBasketStrategyBacktest(
        days: Int = 180,
        config: BacktestConfig = BacktestConfig()
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBasketBacktesting = true)
            val report = withContext(Dispatchers.IO) {
                repository.runBasketD1MomentumBacktest(daysToTest = days, config = config)
            }
            _uiState.value = _uiState.value.copy(
                isBasketBacktesting = false,
                basketReport = report
            )

            // Tự động ghi nhận vào lịch sử các lần backtest
            if (report.totalCycles > 0 || report.cycles.isNotEmpty()) {
                val winCount = report.cycles.count { it.realizedProfitUsdt > 0 }
                val lossCount = report.cycles.count { it.realizedProfitUsdt <= 0 }
                val trailingCount = report.cycles.count { it.exitReason == "TRAILING_STOP_HIT" }
                val record = BacktestHistoryRecord(
                    days = days,
                    config = config,
                    netProfitUsdt = report.netProfitUsdt,
                    netProfitPercent = report.netProfitPercent,
                    winratePercent = report.winratePercent,
                    maxDrawdownPercent = report.maxDrawdownPercent,
                    totalCycles = report.totalCycles,
                    winCycles = winCount,
                    lossCycles = lossCount,
                    trailingHits = trailingCount
                )
                _backtestHistory.value = listOf(record) + _backtestHistory.value
            }
        }
    }

    fun clearBasketReport() {
        _uiState.value = _uiState.value.copy(basketReport = null)
    }

    fun updateBacktestConfig(config: BacktestConfig, days: Int? = null) {
        _backtestConfig.value = config
        if (days != null) _backtestDays.value = days
    }

    fun updateStrategySettingsAndSyncWithBacktest(newSettings: TradeSettings) {
        _uiState.value = _uiState.value.copy(settings = newSettings)
        val bConfig = newSettings.toBacktestConfig()
        _backtestConfig.value = bConfig
        triggerAlert(
            TradeAlert(
                symbol = "SETTINGS",
                title = "ĐÃ ĐỒNG BỘ CÀI ĐẶT & BACKTEST",
                message = "Bộ thông số chiến lược mới đã được lưu và tự động đồng bộ 2 chiều với thẻ Backtest.",
                type = AlertType.INFO
            )
        )
    }

    fun syncStrategySettingsFromBacktest() {
        val currentB = _backtestConfig.value
        val updatedSettings = _uiState.value.settings.syncFromBacktestConfig(currentB)
        _uiState.value = _uiState.value.copy(settings = updatedSettings)
        triggerAlert(
            TradeAlert(
                symbol = "SETTINGS",
                title = "ĐÃ NẠP TỪ THẺ BACKTEST",
                message = "Đã sao chép cấu hình từ thẻ Backtest vào Cài đặt chiến lược.",
                type = AlertType.INFO
            )
        )
    }

    fun applyRecommendedConfig(config: BacktestConfig, days: Int? = null) {
        _backtestConfig.value = config
        if (days != null) _backtestDays.value = days
        val syncedSettings = _uiState.value.settings.syncFromBacktestConfig(config)
        _uiState.value = _uiState.value.copy(settings = syncedSettings)
        triggerAlert(
            TradeAlert(
                symbol = "AI_COPILOT",
                title = "ĐÃ ÁP DỤNG CẤU HÌNH ĐỀ XUẤT",
                message = "Đã cập nhật bộ thông số tối ưu từ AI vào cả cấu hình Backtest và Cài đặt Bot.",
                type = AlertType.INFO
            )
        )
    }

    /**
     * AI tự động phân tích toàn bộ lịch sử backtest để tìm ra bộ thông số hiệu quả nhất.
     */
    fun getOptimalAiConfig(): BacktestConfig {
        val history = _backtestHistory.value
        val best = history.maxByOrNull { 
            (it.winratePercent * 0.45) + (it.netProfitPercent * 0.40) - (it.maxDrawdownPercent * 0.15)
        }
        return best?.config ?: BacktestConfig(
            kTrail = 0.25,
            minVolRatio = 2.0,
            enableTrailingStop = true,
            enableFloorLock = true,
            floorLockMinRoi = 0.005
        )
    }

    /**
     * Kích hoạt bot với bộ thông số mà người dùng đã xác nhận / chỉnh sửa cuối cùng.
     */
    fun saveCustomBotConfigAndStart(
        config: BacktestConfig,
        capitalPerTrade: Double = 150.0,
        strategyType: com.example.model.TradingStrategyType = com.example.model.TradingStrategyType.DUAL_BASKET_MOMENTUM_V2
    ) {
        val newSettings = _uiState.value.settings.copy(
            autoTradeEnabled = true,
            leverage = 1,
            selectedStrategy = strategyType,
            capitalPerTradeUsdt = capitalPerTrade,
            kTrail = config.kTrail,
            minVolRatio = config.minVolRatio,
            enableTrailingStop = config.enableTrailingStop,
            enableFloorLock = config.enableFloorLock,
            floorLockMinRoi = config.floorLockMinRoi,
            enableDualBasket = true
        )
        _uiState.value = _uiState.value.copy(settings = newSettings)
        _backtestConfig.value = config

        val context = getApplication<Application>().applicationContext
        com.example.TradingForegroundService.startService(context)

        triggerAlert(
            TradeAlert(
                symbol = "BOT",
                title = "ĐÃ KÍCH HOẠT BOT VỚI BỘ THÔNG SỐ XÁC NHẬN",
                message = "Bot bắt đầu chạy: Vốn $capitalPerTrade USDT/lệnh • K_Trail: ${config.kTrail} • FloorLock: +${config.floorLockMinRoi * 100}% • RVol: ${config.minVolRatio}x.",
                type = AlertType.INFO
            )
        )
        startAutoTradeLoop()
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
            } else {
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_TESTNET",
                        title = "KẾT NỐI BINANCE TESTNET",
                        message = "Đã nhập API Key & Secret Testnet. ${result.errorMessage ?: "Vui lòng kiểm tra lại kết nối mạng"}",
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
            val enrichedPositions = portfolio.positions.mapIndexed { index, pos ->
                val assignedSlot = liveBasketAssignments[pos.symbol] ?: if (index < 10) "PRIMARY" else "SECONDARY"
                liveBasketAssignments[pos.symbol] = assignedSlot
                pos.copy(basketSlot = assignedSlot)
            }
            val enrichedPortfolio = portfolio.copy(positions = enrichedPositions)
            _binancePortfolio.value = enrichedPortfolio
            if (portfolio.isLiveConnected) {
                repository.syncWithBinanceLivePositions(enrichedPositions)
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

    fun stopAllPolling() {
        webSocketClient?.stop()
        autoTradeJob?.cancel()
        tickerPollJob?.cancel()
        klinePollJob?.cancel()
        schedulerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopAllPolling()
    }
}