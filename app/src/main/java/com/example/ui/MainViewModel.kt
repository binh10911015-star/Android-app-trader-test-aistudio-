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
import com.example.repository.CryptoRepository
import com.example.util.SecureApiKeyStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.example.util.ScanScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

enum class MarketFilter {
    ALL,
    BULLISH_SIGNAL, // MA20 > SuperTrend
    OPEN_POSITIONS
}

data class UiState(
    val tickers: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers(),
    val filteredTickers: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers(),
    val coinStateList: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers(),
    val scannedBullishTickers: List<BinanceTicker> = com.example.data.DefaultCoinData.getSeedTickers().filter {
        it.symbol in listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "DOGEUSDT", "PEPEUSDT", "SUIUSDT", "AVAXUSDT", "NEARUSDT", "WIFUSDT", "AAVEUSDT", "ENAUSDT")
    },
    val searchQuery: String = "",
    val debouncedSearchQuery: String = "",
    val isSearching: Boolean = false,
    val filterMode: MarketFilter = MarketFilter.ALL,
    val selectedSymbol: String = "BTCUSDT",
    val selectedTimeframe: String = "4h", // "4h" or "1d"
    val klineData: List<IndicatorData> = emptyList(),
    val isKlineLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isScanning: Boolean = false,
    val settings: TradeSettings = TradeSettings(),
    val activeAlert: TradeAlert? = null,
    val alertHistory: List<TradeAlert> = emptyList(),
    val scanProgress: String = "",
    val scanPercent: Float = 0f,
    val bullishCount: Int = 12,
    val nextScanTimestamp: Long = ScanScheduler.getNextScanTimestamp(),
    val countdownText: String = "",
    val lastScanTimestamp: Long = 0L,
    val totalScannedCount: Int = 0,
    val roiSortOrder: RoiSortOrder = RoiSortOrder.DEFAULT,
    val isOpeningPositions: Boolean = false,
    val openingPositionsProgress: String = ""
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
    /**
     * Debounced coin state list updated only after 300ms of typing inactivity.
     */
    val coinStateList: StateFlow<List<BinanceTicker>> = _coinStateList.asStateFlow()

    val openPositions: StateFlow<List<TradePosition>> = repository.getOpenPositions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val closedPositions: StateFlow<List<TradePosition>> = repository.getClosedPositions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var autoTradeJob: Job? = null
    private var openPositionsJob: Job? = null
    private var tickerPollJob: Job? = null
    private var schedulerJob: Job? = null

    // Debounced search query flow (triggers list update only after 300ms of typing inactivity)
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

    init {
        val loadedConfig = secureApiStorage.loadConfig()
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
            // Yêu cầu: Chỉ tự động quét, không tự động mở vị thế nếu chưa ấn nút Auto-trade
            if (_uiState.value.settings.autoTradeEnabled) {
                openAllScannedPositions(isAuto = true)
            }
            // Khởi chạy quét toàn bộ Binance Futures (chỉ quét tín hiệu, KHÔNG mở vị thế nếu chưa bật Auto-trade)
            scanAllFuturesCoins(isAutoScheduled = false)
        }
    }

    fun selectSymbol(symbol: String, timeframe: String = "4h") {
        _uiState.value = _uiState.value.copy(
            selectedSymbol = symbol,
            selectedTimeframe = timeframe
        )
        loadKlineForSymbol(symbol, timeframe)
    }

    fun setTimeframe(tf: String) {
        if (tf != _uiState.value.selectedTimeframe) {
            _uiState.value = _uiState.value.copy(selectedTimeframe = tf)
            loadKlineForSymbol(_uiState.value.selectedSymbol, tf)
        }
    }

    /**
     * Updates the search query in the search bar. The text field updates immediately
     * for responsive typing, while the coin state list updates through the debounced flow
     * only after 300ms of inactivity.
     */
    fun setSearchQuery(query: String) {
        val willDebounce = query.isNotBlank() && query != _uiState.value.debouncedSearchQuery
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            isSearching = willDebounce
        )
        // If user clears query completely, reset immediately for instant snappy UX
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

    private fun applyFilters(query: String = _searchQueryFlow.value) {
        val current = _uiState.value
        val openSymbols = openPositions.value.map { it.symbol }.toSet()
        // Strip common separators and trailing 'USDT' suffix from input string
        val cleanQuery = com.example.util.SearchUtils.stripUsdtSuffix(query).uppercase()
        val rawQuery = query.trim().uppercase()

        val listToFilter = current.tickers

        val filtered = listToFilter.filter { ticker ->
            val baseSymbol = ticker.symbol.removeSuffix("USDT")
            val fullSymbol = ticker.symbol

            val matchesQuery = if (cleanQuery.isEmpty()) {
                if (rawQuery.isNotEmpty()) {
                    // e.g. User specifically typed "USDT", show all USDT contracts
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

            // When searching by coin name, prioritize finding the coin across the market
            val matchesFilter = if (cleanQuery.isNotEmpty() || rawQuery.isNotEmpty()) {
                true
            } else {
                when (current.filterMode) {
                    MarketFilter.ALL -> true
                    MarketFilter.BULLISH_SIGNAL -> repository.getCachedSignal(ticker.symbol) == true
                    MarketFilter.OPEN_POSITIONS -> openSymbols.contains(ticker.symbol)
                }
            }
            matchesQuery && matchesFilter
        }.sortedWith { a, b ->
            if (cleanQuery.isNotEmpty()) {
                val baseA = a.symbol.removeSuffix("USDT")
                val baseB = b.symbol.removeSuffix("USDT")

                // 1. Exact match on coin base name (e.g. "BTC" == "BTC")
                val exactA = baseA.equals(cleanQuery, ignoreCase = true)
                val exactB = baseB.equals(cleanQuery, ignoreCase = true)
                if (exactA != exactB) return@sortedWith if (exactA) -1 else 1

                // 2. Starts with query (e.g. "SOL" startsWith "SO")
                val startsA = baseA.startsWith(cleanQuery, ignoreCase = true)
                val startsB = baseB.startsWith(cleanQuery, ignoreCase = true)
                if (startsA != startsB) return@sortedWith if (startsA) -1 else 1

                // 3. Higher volume first
                (b.volume * b.lastPrice).compareTo(a.volume * a.lastPrice)
            } else {
                // Default: higher volume first
                (b.volume * b.lastPrice).compareTo(a.volume * a.lastPrice)
            }
        }

        val scannedBullish = current.tickers.filter { repository.getCachedSignal(it.symbol) == true }

        _coinStateList.value = filtered
        _uiState.value = _uiState.value.copy(
            filteredTickers = filtered,
            coinStateList = filtered,
            scannedBullishTickers = scannedBullish,
            bullishCount = scannedBullish.size,
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

                // Update open positions with new prices and trigger TP/SL
                val priceMap = list.associate { it.symbol to it.lastPrice }
                val updateResult = repository.updatePositionsWithPrices(priceMap)
                for (alert in updateResult.alerts) {
                    triggerAlert(alert)
                }
                // Cập nhật số dư mô phỏng khi lệnh chạm TP hoặc SL
                if (updateResult.closedPositions.isNotEmpty()) {
                    val newBal = _uiState.value.settings.simulatedBalanceUsdt + updateResult.totalReturnedCapital
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
            triggerAlert(
                TradeAlert(
                    symbol = "BOT",
                    title = "ĐÃ BẬT TỰ ĐỘNG GIAO DỊCH (AUTO-TRADE)",
                    message = "Auto-trade đã kích hoạt! Hệ thống bắt đầu tự động mở vị thế cho các coin có tín hiệu tăng (cách nhau 5 giây/lệnh, volume 0.5% số dư).",
                    type = AlertType.INFO
                )
            )
            // Khi người dùng ấn nút Auto-trade, mở vị thế tự động cho các coin đã quét có tín hiệu tăng
            openAllScannedPositions(isAuto = true)
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
                    title = "ĐÃ TẮT TỰ ĐỘNG GIAO DỊCH (AUTO-TRADE)",
                    message = "Hệ thống dừng mở lệnh mới. Chỉ tự động quét tín hiệu, KHÔNG tự động mở vị thế. Các vị thế đang mở vẫn được giám sát TP/SL.",
                    type = AlertType.INFO
                )
            )
        }
    }

    fun updateSettings(newSettings: TradeSettings) {
        _uiState.value = _uiState.value.copy(settings = newSettings)
    }

    fun setRoiSortOrder(order: RoiSortOrder) {
        _uiState.value = _uiState.value.copy(roiSortOrder = order)
    }

    /**
     * User or Bot buys coin.
     * Volume mỗi vị thế = 0,5% số dư mô phỏng.
     * Enforces: "Nếu danh sách vào lệnh đã có thì không tự động mở thêm lệnh mới cho coin đó."
     */
    fun executeBuy(symbol: String, isAuto: Boolean = false) {
        // Yêu cầu: Không tự động mở vị thế nếu chưa ấn nút Auto-trade
        if (isAuto && !_uiState.value.settings.autoTradeEnabled) {
            return
        }
        viewModelScope.launch {
            val currentTicker = _uiState.value.tickers.find { it.symbol == symbol }
            val price = currentTicker?.lastPrice
                ?: _uiState.value.klineData.lastOrNull()?.candle?.close
                ?: return@launch

            val volume = _uiState.value.settings.calculatePositionVolume()
            if (_uiState.value.settings.simulatedBalanceUsdt < volume || volume <= 0.0) {
                if (!isAuto) {
                    triggerAlert(
                        TradeAlert(
                            symbol = symbol,
                            title = "SỐ DƯ KHÔNG ĐỦ",
                            message = "Số dư mô phỏng ($${CryptoRepository.formatPrice(_uiState.value.settings.simulatedBalanceUsdt)} USDT) không đủ để mở vị thế ($${CryptoRepository.formatPrice(volume)} USDT).",
                            type = AlertType.INFO
                        )
                    )
                }
                return@launch
            }

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

                    if (alert != null) {
                        triggerAlert(alert)
                    }
                    refreshBinancePortfolio()
                } else {
                    val fixNote = orderResult.autoFixApplied?.let { " (Hệ thống đã tự động thử: $it)" } ?: ""
                    triggerAlert(
                        TradeAlert(
                            symbol = symbol,
                            title = "LỖI GỬI LỆNH LÊN BINANCE DEMO",
                            message = "${orderResult.errorMessage ?: "Không thể kết nối"}$fixNote. Vui lòng kiểm tra lại quyền API.",
                            type = AlertType.INFO
                        )
                    )
                }
                return@launch
            }

            // Chế độ Paper Trading thuần túy (khi chưa kết nối API)
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
                // Khấu trừ vốn đầu tư (volume 0.5% số dư) vào số dư mô phỏng
                val newBal = _uiState.value.settings.simulatedBalanceUsdt - volume
                _uiState.value = _uiState.value.copy(
                    settings = _uiState.value.settings.updateBalance(newBal)
                )
            } else if (!isAuto) {
                // If manual click but already open
                triggerAlert(
                    TradeAlert(
                        symbol = symbol,
                        title = "VỊ THẾ ĐÃ TỒN TẠI",
                        message = "Coin $symbol đã có trong danh sách vào lệnh. Không thể mở thêm lệnh trùng lặp!",
                        type = AlertType.INFO
                    )
                )
            }
        }
    }

    fun manualClosePosition(positionId: Long) {
        viewModelScope.launch {
            val pos = openPositions.value.find { it.id == positionId } ?: return@launch
            val currentPrice = _uiState.value.tickers.find { it.symbol == pos.symbol }?.lastPrice ?: pos.currentPrice

            // Nếu đây là lệnh Binance thực tế, gửi lệnh MARKET SELL (reduceOnly=true) lên Binance
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
                    // Hoàn lại vốn đầu tư + PnL thực nhận vào số dư mô phỏng
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

    /**
     * Đóng tất cả các vị thế đang mở và hoàn lại toàn bộ vốn + PnL vào số dư mô phỏng
     */
    fun closeAllPositions() {
        viewModelScope.launch {
            val count = openPositions.value.size
            if (count == 0) return@launch
            val priceMap = _uiState.value.tickers.associate { it.symbol to it.lastPrice }

            // Đóng các vị thế Binance trực tiếp nếu có
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

    /**
     * Mở toàn bộ vị thế sau khi quét thấy tín hiệu tăng (MA20 > SuperTrend 4H).
     * Mở vị thế mô phỏng với volume = 0,5% số dư mô phỏng kèm TP/SL.
     * Tự động bỏ qua các coin đã có trong danh sách vào lệnh để tránh lệnh trùng lặp.
     * Yêu cầu: Mỗi vị thế được mở cách nhau 5 giây.
     */
    fun openAllScannedPositions(isAuto: Boolean = false): Job {
        // Yêu cầu: Chỉ tự động quét, không tự động mở vị thế nếu chưa ấn nút Auto-trade
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
                val bullishSymbols = repository.getAllBullishSymbols()
                if (bullishSymbols.isEmpty()) {
                    if (!isAuto) {
                        triggerAlert(
                            TradeAlert(
                                symbol = "SCANNER",
                                title = "CHƯA CÓ TÍN HIỆU TĂNG",
                                message = "Chưa có coin nào đạt tín hiệu tăng MA20 > SuperTrend. Vui lòng bấm Quét để cập nhật tín hiệu mới!",
                                type = AlertType.INFO
                            )
                        )
                    }
                    return@launch
                }

                val openSymbols = repository.getOpenPositionSymbols()
                val symbolsToOpen = bullishSymbols.filter { !openSymbols.contains(it) }

                if (symbolsToOpen.isEmpty()) {
                    if (!isAuto) {
                        triggerAlert(
                            TradeAlert(
                                symbol = "POSITIONS",
                                title = "TẤT CẢ VỊ THẾ ĐÃ ĐƯỢC MỞ",
                                message = "Toàn bộ ${bullishSymbols.size} coin có tín hiệu tăng đã có vị thế trong danh sách vào lệnh!",
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
                    // Yêu cầu: Dừng ngay nếu là tự động mở mà người dùng đã tắt Auto-trade
                    if (isAuto && !_uiState.value.settings.autoTradeEnabled) {
                        break
                    }

                    // Volume mỗi vị thế theo cấu hình: % số dư HOẶC volume cố định USDT/lệnh
                    val volume = _uiState.value.settings.calculatePositionVolume(balance)
                    if (balance < volume || volume <= 0.0) {
                        break // Số dư mô phỏng không đủ để mở thêm lệnh
                    }
                    val tickerPrice = currentTickers.find { it.symbol == symbol }?.lastPrice
                    val klinePrice = if (symbol == _uiState.value.selectedSymbol) _uiState.value.klineData.lastOrNull()?.candle?.close else null
                    val price = tickerPrice ?: klinePrice ?: continue

                    if (price <= 0.0) continue

                    // Yêu cầu: Mỗi vị thế được mở cách nhau 5 giây
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
                        } else {
                            val fixNote = orderResult.autoFixApplied?.let { " (Đã tự động thử sửa: $it)" } ?: ""
                            triggerAlert(
                                TradeAlert(
                                    symbol = symbol,
                                    title = "LỖI LỆNH BINANCE: $symbol",
                                    message = "${orderResult.errorMessage ?: "Không thể kết nối sàn"}$fixNote",
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
                            isAutoTriggered = isAuto,
                            binanceOrderId = null,
                            binanceClientOrderId = null,
                            isLiveBinanceOrder = false,
                            binanceStatus = null
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
                    if (_apiConfig.value.isConnected) {
                        refreshBinancePortfolio()
                    }
                    val title = if (isAuto) "TỰ ĐỘNG MỞ TOÀN BỘ VỊ THẾ TĂNG" else "ĐÃ MỞ TOÀN BỘ VỊ THẾ TĂNG"
                    val modeNote = if (_apiConfig.value.isConnected) " (Đã gửi thẳng lên Binance Demo)" else " (Paper Trading)"
                    triggerAlert(
                        TradeAlert(
                            symbol = "ALL_BULLISH",
                            title = "$title$modeNote",
                            message = "Đã khớp thành công $newlyOpened vị thế mới (mỗi vị thế cách nhau 5 giây, Volume 0.5% số dư mỗi vị thế, tổng vốn: ${String.format(Locale.US, "%.1f", totalInvested)} USDT).",
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

        if (isAuto) {
            openPositionsJob = job
        }
        return job
    }

    /**
     * Scans ALL coins in Binance Futures without limit using concurrent batched coroutines.
     * Can be invoked manually by user or automatically every 4 hours (starting at 7:00 UTC+7).
     * Mở toàn bộ vị thế sau khi quét thấy tín hiệu tăng.
     */
    fun scanAllFuturesCoins(isAutoScheduled: Boolean = false) {
        if (_uiState.value.isScanning) return // Already scanning

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scanProgress = "Đang tải danh sách toàn bộ Binance Futures...",
                scanPercent = 0f
            )

            // Ensure tickers are loaded
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
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanProgress = "",
                    scanPercent = 0f
                )
                return@launch
            }

            val totalCoins = allSymbols.size
            var scannedCount = 0
            var newlyTriggeredOrders = 0

            // Batched concurrent fetching (8 per batch) for fast scan without hitting rate limits
            val chunkSize = 8
            val chunks = allSymbols.chunked(chunkSize)

            for (chunk in chunks) {
                if (!isActive) break

                val deferredList = chunk.map { symbol ->
                    async(Dispatchers.IO) {
                        symbol to repository.getCandlesWithIndicators(symbol, _uiState.value.settings.timeframe)
                    }
                }

                val results = deferredList.awaitAll()

                for ((symbol, result) in results) {
                    scannedCount++
                    result.onSuccess { data ->
                        val latest = data.lastOrNull()
                        val isBullish = latest?.isBullishSignal(_uiState.value.settings.useSuperTrendFilter) == true
                        if (isBullish) {
                            repository.setCachedSignal(symbol, true)
                        }
                    }
                }

                val currentBullish = repository.getBullishSignalsCount()
                _uiState.value = _uiState.value.copy(
                    scanProgress = "Đã quét $scannedCount/$totalCoins coin (Tín hiệu tăng: $currentBullish)",
                    scanPercent = scannedCount.toFloat() / totalCoins,
                    bullishCount = currentBullish,
                    totalScannedCount = scannedCount
                )
                applyFilters()
                delay(60) // Short delay between batches
            }

            val finalBullish = repository.getBullishSignalsCount()
            val now = System.currentTimeMillis()

            // Yêu cầu: Chỉ tự động quét, không tự động mở vị thế nếu chưa ấn nút Auto-trade
            if (_uiState.value.settings.autoTradeEnabled) {
                openAllScannedPositions(isAuto = true)
            }

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                scanProgress = "",
                scanPercent = 1f,
                bullishCount = finalBullish,
                lastScanTimestamp = now
            )
            applyFilters()

            val title = if (isAutoScheduled) "QUÉT TỰ ĐỘNG 4H HOÀN TẤT (UTC+7)" else "HOÀN TẤT QUÉT TOÀN BỘ BINANCE FUTURES"
            val autoTradeNote = if (_uiState.value.settings.autoTradeEnabled) {
                " [Auto-trade BẬT]: Đang tự động mở vị thế cách nhau 5s cho các coin có tín hiệu tăng."
            } else {
                " [Auto-trade TẮT]: Hệ thống chỉ tự động quét tín hiệu, KHÔNG tự động mở vị thế. Bật nút Auto-trade hoặc bấm 'Mở toàn bộ' nếu muốn vào lệnh."
            }

            triggerAlert(
                TradeAlert(
                    symbol = "SCANNER",
                    title = title,
                    message = "Đã quét $totalCoins coin. Phát hiện $finalBullish coin có xu hướng tăng MA20 > SuperTrend(20,4).$autoTradeNote",
                    type = AlertType.BULLISH_SIGNAL
                )
            )
        }
    }

    /**
     * Automatic scheduler checking every second:
     * Triggers full scan at 07:00, 11:00, 15:00, 19:00, 23:00, 03:00 (UTC+7).
     */
    private fun startScanScheduler() {
        schedulerJob?.cancel()
        schedulerJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val target = _uiState.value.nextScanTimestamp
                val countdown = ScanScheduler.formatCountdown(target, now)
                _uiState.value = _uiState.value.copy(countdownText = countdown)

                // Trigger scan when scheduled time is reached
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
                delay(8000)
                refreshTickers()
            }
        }
    }

    private fun startAutoTradeLoop() {
        autoTradeJob?.cancel()
        autoTradeJob = viewModelScope.launch {
            while (isActive && _uiState.value.settings.autoTradeEnabled) {
                val candidateSymbols = _uiState.value.tickers
                    .take(15)
                    .map { it.symbol }
                    .ifEmpty { listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT") }

                for (sym in candidateSymbols) {
                    if (!isActive || !_uiState.value.settings.autoTradeEnabled) break

                    // Rule check first: If already open, skip!
                    if (repository.hasOpenPositionForSymbol(sym)) {
                        continue
                    }

                    // Check technical indicator
                    val res = repository.getCandlesWithIndicators(sym, _uiState.value.settings.timeframe)
                    var tradeExecuted = false
                    res.onSuccess { data ->
                        val latest = data.lastOrNull()
                        if (latest != null && latest.isBullishSignal(_uiState.value.settings.useSuperTrendFilter)) {
                            // Signal condition met (Price > SMA20, with or without SuperTrend)
                            val vol = _uiState.value.settings.calculatePositionVolume()
                            if (_uiState.value.settings.simulatedBalanceUsdt >= vol && vol > 0.0) {
                                executeBuy(sym, isAuto = true)
                                tradeExecuted = true
                            }
                        }
                    }
                    if (tradeExecuted) {
                        // Yêu cầu: Mỗi vị thế được mở cách nhau 5 giây
                        delay(5000L)
                    } else {
                        delay(300L) // gentle delay between symbol kline requests
                    }
                }

                delay(15000) // Wait 15s between scan rounds
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

    /**
     * Compute Daily PnL stats from closed positions
     */
    fun computeDailyPnL(closed: List<TradePosition>): List<PeriodPnL> {
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        // Group by day key
        val map = linkedMapOf<String, MutableList<TradePosition>>()

        // Sort ascending by exitTime
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

    /**
     * Compute Weekly PnL stats from closed positions
     */
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

    /**
     * Checks if PIN verification is required before entering orders.
     * QUY TẮC BẮT BUỘC: Luôn luôn hiển thị và bắt buộc người dùng nhập đúng mã PIN bảo mật
     * trước khi cho phép bấm nút kích hoạt 'Auto Trade' hoặc thực thi bất kỳ thao tác đặt lệnh nào.
     */
    fun isPinRequired(): Boolean {
        return true
    }

    /**
     * Authorizes user session with the entered PIN.
     */
    fun authorizePin(pin: String): Boolean {
        val configured = _apiConfig.value.accessPin.ifBlank { "1234" }
        val ok = pin.trim() == configured
        if (ok) {
            _isPinVerifiedInSession.value = true
        }
        return ok
    }

    /**
     * Resets session PIN authorization.
     */
    fun lockPin() {
        _isPinVerifiedInSession.value = false
    }

    /**
     * Helper cung cấp API Key, Secret Key và chế độ Testnet/Mainnet chính xác
     */
    fun getEffectiveApiCredentials(): Triple<String, String, Boolean> {
        val config = _apiConfig.value
        val isTestnet = config.isTestnet
        val defaultKey = if (isTestnet) SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_KEY else SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY
        val defaultSecret = if (isTestnet) SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_SECRET else SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_SECRET
        val apiKey = config.apiKey.ifBlank { defaultKey }
        val apiSecret = config.apiSecret.ifBlank { defaultSecret }
        return Triple(apiKey, apiSecret, isTestnet)
    }

    /**
     * Tự động đăng nhập vào Binance Futures Mainnet với thông tin API Key và Secret Key do người dùng cung cấp.
     */
    fun autoLoginMainnet(onResult: ((BinanceAccountVerificationResult) -> Unit)? = null) {
        viewModelScope.launch {
            val (apiKey, apiSecret) = secureApiStorage.getMainnetCredentials()
            val isTestnet = false

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

            if (result.isSuccess) {
                refreshBinancePortfolio()
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_MAINNET",
                        title = "ĐÃ KẾT NỐI BINANCE MAINNET THỰC TẾ",
                        message = "Đã kết nối tài khoản Binance Futures Mainnet thực tế! Số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT (Khả dụng: $${String.format(Locale.US, "%,.2f", result.availableBalance)} USDT). Cho phép gửi lệnh kiểm thử thực tế.",
                        type = AlertType.INFO
                    )
                )
            } else {
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_MAINNET",
                        title = "KẾT NỐI BINANCE MAINNET",
                        message = "Đã cấu hình API Key & Secret Binance Mainnet. ${result.errorMessage ?: "Vui lòng kiểm tra lại kết nối mạng hoặc quyền truy cập IP/Futures trên Binance."}",
                        type = AlertType.INFO
                    )
                )
            }
            onResult?.invoke(result)
        }
    }

    /**
     * Tự động đăng nhập vào Binance Futures Testnet với thông tin API Key và Secret Key của người dùng.
     */
    fun autoLoginTestnet(onResult: ((BinanceAccountVerificationResult) -> Unit)? = null) {
        viewModelScope.launch {
            val (apiKey, apiSecret) = secureApiStorage.getTestnetCredentials()
            val isTestnet = true

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

            if (result.isSuccess) {
                refreshBinancePortfolio()
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_TESTNET",
                        title = "ĐÃ ĐĂNG NHẬP BINANCE TESTNET THÀNH CÔNG",
                        message = "Tự động đăng nhập Binance Futures Testnet thành công! Số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT (Khả dụng: $${String.format(Locale.US, "%,.2f", result.availableBalance)} USDT).",
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

    /**
     * Verifies Binance API credentials against Binance Futures server and securely saves if valid.
     */
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
            val result = BinanceFuturesApiVerifier.verifyAccount(apiKey, apiSecret, isTestnet)
            if (result.isSuccess) {
                secureApiStorage.saveCredentials(apiKey, apiSecret, accessPin, isTestnet, liveTradingEnabled)
                secureApiStorage.updateVerificationResult(result)
                val updated = secureApiStorage.loadConfig()
                _apiConfig.value = updated
                refreshBinancePortfolio()
                triggerAlert(
                    TradeAlert(
                        symbol = "BINANCE_API",
                        title = "KẾT NỐI BINANCE THÀNH CÔNG",
                        message = "Đã kết nối tài khoản Binance Futures (${if (isTestnet) "Testnet Demo" else "Mainnet"}). Số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT.",
                        type = AlertType.INFO
                    )
                )
            } else {
                secureApiStorage.updateVerificationResult(result)
                _apiConfig.value = _apiConfig.value.copy(
                    isConnected = false,
                    lastErrorMessage = result.errorMessage
                )
            }
            _isVerifyingApi.value = false
            onResult(result)
        }
    }

    /**
     * Verifies user's Binance Access PIN before executing orders.
     */
    fun verifyAccessPin(enteredPin: String): Boolean {
        return _apiConfig.value.verifyPin(enteredPin)
    }

    /**
     * Re-tests the current saved API configuration.
     */
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
            val result = BinanceFuturesApiVerifier.verifyAccount(
                current.apiKey,
                current.apiSecret,
                current.isTestnet
            )
            secureApiStorage.updateVerificationResult(result)
            _apiConfig.value = secureApiStorage.loadConfig()
            if (result.isSuccess) {
                refreshBinancePortfolio()
            }
            _isVerifyingApi.value = false
            onResult(result)
        }
    }

    /**
     * Tải địa chỉ IP công khai của thiết bị phục vụ cho IP Whitelist trên Binance Mainnet.
     */
    fun fetchPublicIpAddress(forceRefresh: Boolean = false) {
        if (!forceRefresh && _publicIpAddress.value != null) return
        viewModelScope.launch {
            _isFetchingIp.value = true
            val ip = BinanceFuturesApiVerifier.fetchPublicIpAddress()
            _publicIpAddress.value = ip
            _isFetchingIp.value = false
        }
    }

    /**
     * Cập nhật cấu hình Custom Proxy (HTTP/SOCKS5) nhằm vượt hạn chế vùng địa lý khi gọi Binance API.
     */
    fun updateProxyConfig(host: String, port: Int, proxyType: String = "HTTP") {
        secureApiStorage.saveProxyConfig(host, port, proxyType)
        BinanceClient.updateProxy(host, port, proxyType.equals("SOCKS", ignoreCase = true))
        _apiConfig.value = secureApiStorage.loadConfig()
        fetchPublicIpAddress(forceRefresh = true)
        triggerAlert(
            TradeAlert(
                symbol = "PROXY",
                title = if (host.isNotBlank() && port > 0) "ĐÃ LƯU CẤU HÌNH PROXY" else "ĐÃ TẮT PROXY",
                message = if (host.isNotBlank() && port > 0)
                    "Đã lưu và kích hoạt Proxy $proxyType://${host.trim()}:$port. Các yêu cầu Binance Futures sẽ được định tuyến qua Proxy để vượt hạn chế vùng."
                else
                    "Đã tắt Proxy. Kết nối trực tiếp được khôi phục.",
                type = AlertType.INFO
            )
        )
    }

    /**
     * Xóa cấu hình Proxy và khôi phục kết nối trực tiếp bình thường.
     */
    fun clearProxyConfig() {
        secureApiStorage.clearProxyConfig()
        BinanceClient.updateProxy("", 0)
        _apiConfig.value = secureApiStorage.loadConfig()
        fetchPublicIpAddress(forceRefresh = true)
        triggerAlert(
            TradeAlert(
                symbol = "PROXY",
                title = "ĐÃ TẮT PROXY",
                message = "Đã xóa cấu hình Proxy, quay lại kết nối trực tiếp bình thường.",
                type = AlertType.INFO
            )
        )
    }

    /**
     * Tải toàn bộ danh mục từ Binance Futures (số dư các ví, vị thế mở, lệnh chờ).
     */
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
            _isRefreshingPortfolio.value = false
        }
    }

    /**
     * Kiểm tra chính xác lệnh đã đưa lên Binance chưa qua API GET /fapi/v1/order.
     */
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

    /**
     * Toggles live trading vs simulation mode.
     */
    fun setLiveTradingEnabled(enabled: Boolean) {
        secureApiStorage.setLiveTradingEnabled(enabled)
        _apiConfig.value = _apiConfig.value.copy(liveTradingEnabled = enabled)
    }

    /**
     * Removes API credentials securely from device storage.
     */
    fun clearApiCredentials() {
        secureApiStorage.clearCredentials()
        _apiConfig.value = BinanceApiConfig()
        _binancePortfolio.value = BinancePortfolio()
        triggerAlert(
            TradeAlert(
                symbol = "BINANCE_API",
                title = "ĐÃ XÓA KHÓA API",
                message = "Đã xóa toàn bộ khóa API khỏi thiết bị. Ứng dụng đã chuyển về Chế độ Mô phỏng.",
                type = AlertType.INFO
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        autoTradeJob?.cancel()
        tickerPollJob?.cancel()
        schedulerJob?.cancel()
    }
}
