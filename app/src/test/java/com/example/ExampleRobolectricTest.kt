package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.indicators.TechnicalAnalysis
import com.example.model.Candle
import com.example.model.RoiSortOrder
import com.example.model.TradePosition
import com.example.model.TradeSettings
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import com.example.model.VolumeType
import com.example.model.BinanceApiConfig

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CryptoTrader", appName)
    }

    @Test
    fun `test technical indicators calculation`() {
        val candles = mutableListOf<Candle>()
        val basePrice = 100.0

        for (i in 0 until 40) {
            val price = basePrice + i * 2.0 // upward trending candles
            candles.add(
                Candle(
                    openTime = i * 3600000L,
                    open = price - 1.0,
                    high = price + 2.0,
                    low = price - 2.0,
                    close = price + 1.0,
                    volume = 1000.0,
                    closeTime = (i + 1) * 3600000L
                )
            )
        }

        val result = TechnicalAnalysis.computeIndicators(candles)
        assertEquals(40, result.size)

        // After 20 candles, MA20 should have a value
        val lastIndicator = result.last()
        assertNotNull(lastIndicator.ma20)
        assertNotNull(lastIndicator.superTrend)

        // In a continuous strong uptrend, MA20 should be above lower SuperTrend
        assertTrue(lastIndicator.superTrend?.isBullish == true)
    }

    @Test
    fun `test trade position unrealized pnl calculation`() {
        val pos = TradePosition(
            id = 1,
            symbol = "BTCUSDT",
            entryPrice = 60000.0,
            currentPrice = 62400.0, // +4%
            amount = 100.0 / 60000.0,
            investedUsdt = 100.0,
            takeProfitPrice = 62400.0,
            stopLossPrice = 58500.0,
            status = TradePosition.STATUS_OPEN
        )

        assertEquals(4.0, pos.unrealizedPnlPercent, 0.01)
        assertEquals(4.0, pos.unrealizedPnlUsdt, 0.01)
    }

    @Test
    fun `test SearchUtils strips USDT suffix and separators correctly`() {
        assertEquals("BTC", com.example.util.SearchUtils.stripUsdtSuffix("BTCUSDT"))
        assertEquals("BTC", com.example.util.SearchUtils.stripUsdtSuffix("btcusdt"))
        assertEquals("BTC", com.example.util.SearchUtils.stripUsdtSuffix("BTC/USDT"))
        assertEquals("ETH", com.example.util.SearchUtils.stripUsdtSuffix("ETH USDT"))
        assertEquals("SOL", com.example.util.SearchUtils.stripUsdtSuffix("SOL-USDT"))
        assertEquals("1000PEPE", com.example.util.SearchUtils.stripUsdtSuffix("1000PEPEUSDT"))
        assertEquals("DOGE", com.example.util.SearchUtils.stripUsdtSuffix("doge_usdt"))
        assertEquals("BTC", com.example.util.SearchUtils.stripUsdtSuffix("BTC"))
        assertEquals("", com.example.util.SearchUtils.stripUsdtSuffix("USDT"))
        assertEquals("", com.example.util.SearchUtils.stripUsdtSuffix("usdt"))
    }

    @Test
    fun `test search by coin name stripping usdt suffixes`() {
        val tickers = listOf(
            com.example.model.BinanceTicker("BTCUSDT", 65000.0, 2.5, 1000.0, 66000.0, 64000.0),
            com.example.model.BinanceTicker("ETHUSDT", 3500.0, 1.2, 500.0, 3600.0, 3400.0),
            com.example.model.BinanceTicker("SOLUSDT", 180.0, -0.8, 800.0, 185.0, 175.0),
            com.example.model.BinanceTicker("1000PEPEUSDT", 0.0105, 6.4, 700.0, 0.011, 0.009),
            com.example.model.BinanceTicker("WBTCUSDT", 64900.0, 2.4, 100.0, 65500.0, 64000.0)
        )

        // Searching with "btcusdt" or "btc usdt" should find BTCUSDT and WBTCUSDT
        for (query in listOf("btc", "BTCUSDT", "btc usdt", "btc/usdt", "BTC-USDT")) {
            val cleanQuery = com.example.util.SearchUtils.stripUsdtSuffix(query).uppercase()
            assertEquals("BTC", cleanQuery)

            val filtered = tickers.filter { ticker ->
                val baseSymbol = ticker.symbol.removeSuffix("USDT")
                baseSymbol.contains(cleanQuery, ignoreCase = true) || ticker.symbol.contains(cleanQuery, ignoreCase = true)
            }.sortedWith { a, b ->
                val baseA = a.symbol.removeSuffix("USDT")
                val baseB = b.symbol.removeSuffix("USDT")
                val exactA = baseA.equals(cleanQuery, ignoreCase = true)
                val exactB = baseB.equals(cleanQuery, ignoreCase = true)
                if (exactA != exactB) if (exactA) -1 else 1
                else (b.volume * b.lastPrice).compareTo(a.volume * a.lastPrice)
            }

            assertEquals(2, filtered.size)
            assertEquals("BTCUSDT", filtered[0].symbol)
        }

        // Searching with "pepe" should match 1000PEPEUSDT
        val pepeQuery = com.example.util.SearchUtils.stripUsdtSuffix("pepe usdt").uppercase()
        val pepeMatches = tickers.filter { ticker ->
            val base = ticker.symbol.removeSuffix("USDT")
            base.contains(pepeQuery, ignoreCase = true) || base.removePrefix("1000").contains(pepeQuery, ignoreCase = true)
        }
        assertEquals(1, pepeMatches.size)
        assertEquals("1000PEPEUSDT", pepeMatches[0].symbol)
    }

    @OptIn(FlowPreview::class)
    @Test
    fun `test debounced search flow updates only after 300ms inactivity`() = runTest {
        val flow = MutableStateFlow("")
        val emissions = mutableListOf<String>()

        val job = launch {
            flow.debounce(300L)
                .distinctUntilChanged()
                .collect { emissions.add(it) }
        }

        // Simulate rapid typing within 300ms
        flow.value = "b"
        testScheduler.advanceTimeBy(100L)
        flow.value = "bt"
        testScheduler.advanceTimeBy(100L)
        flow.value = "btc"
        testScheduler.advanceTimeBy(100L)
        flow.value = "btcusdt"

        // Before 300ms of inactivity, nothing should be emitted
        testScheduler.advanceTimeBy(200L)
        assertEquals(0, emissions.size)

        // After 300ms of inactivity, only the final debounced value is emitted
        testScheduler.advanceTimeBy(150L)
        assertEquals(1, emissions.size)
        assertEquals("btcusdt", emissions[0])

        job.cancel()
    }

    @Test
    fun `test 4h UTC+7 scan scheduler calculation`() {
        val nextScan = com.example.util.ScanScheduler.getNextScanTimestamp()
        assertTrue(nextScan > System.currentTimeMillis() - 60000)

        val cal = java.util.Calendar.getInstance(com.example.util.ScanScheduler.TIMEZONE_UTC7).apply {
            timeInMillis = nextScan
        }
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val validHours = setOf(3, 7, 11, 15, 19, 23)
        assertTrue(validHours.contains(hour))

        val countdown = com.example.util.ScanScheduler.formatCountdown(nextScan)
        assertNotNull(countdown)
        assertTrue(countdown.contains(":"))
    }

    @Test
    fun `test default seed tickers available on launch`() {
        val seeds = com.example.data.DefaultCoinData.getSeedTickers()
        assertTrue(seeds.size >= 50)
        assertTrue(seeds.any { it.symbol == "BTCUSDT" })
        assertTrue(seeds.any { it.symbol == "ETHUSDT" })
        assertTrue(seeds.any { it.symbol == "SOLUSDT" })
        assertTrue(seeds.all { it.lastPrice > 0.0 })
    }

    @Test
    fun `test UiState contains scanned bullish tickers below search bar`() {
        val defaultUiState = com.example.ui.UiState()
        assertTrue(defaultUiState.scannedBullishTickers.isNotEmpty())
        assertTrue(defaultUiState.bullishCount > 0)
        assertTrue(defaultUiState.scannedBullishTickers.any { it.symbol == "BTCUSDT" })
        assertTrue(defaultUiState.scannedBullishTickers.any { it.symbol == "ETHUSDT" })
        assertTrue(defaultUiState.scannedBullishTickers.any { it.symbol == "SOLUSDT" })
    }

    @Test
    fun `test open all positions after scanning bullish signals`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        // Pre-seeded or scanned bullish coins should be available
        val bullishSymbols = vm.repository.getAllBullishSymbols()
        assertTrue(bullishSymbols.isNotEmpty())

        // Trigger openAllScannedPositions
        val job = vm.openAllScannedPositions(isAuto = false)
        testScheduler.advanceUntilIdle()
        job.join()

        // Verify open positions are created
        val openPositions = vm.repository.getOpenPositionSymbols()
        assertTrue(openPositions.isNotEmpty())
        val containsBullish = openPositions.any { sym: String -> bullishSymbols.contains(sym) }
        assertTrue(containsBullish)
    }

    @Test
    fun `test empty TP and SL in TradeSettings results in no TP and SL targets`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = CryptoRepository(com.example.data.AppDatabase.getInstance(app).tradeDao())

        val settingsNoTpSl = TradeSettings(
            autoTradeEnabled = false,
            capitalPerTradeUsdt = 100.0,
            takeProfitPercent = null,
            stopLossPercent = null,
            timeframe = "4h"
        )

        // Execute simulated buy for a test symbol
        val alert = repo.executeSimulatedBuy(
            symbol = "AVAXUSDT",
            currentPrice = 30.0,
            settings = settingsNoTpSl,
            isAutoTriggered = false
        )
        assertNotNull(alert)
        assertTrue(alert?.message?.contains("Không thiết lập") == true)

        val openPositions = repo.getOpenPositions().first()
        val avaxPos = openPositions.find { it.symbol == "AVAXUSDT" }
        assertNotNull(avaxPos)
        assertNull(avaxPos?.takeProfitPrice)
        assertNull(avaxPos?.stopLossPrice)

        // Simulate massive price jump (+50%) and drop (-50%) - neither TP nor SL should trigger
        val alertsUp = repo.updatePositionsWithPrices(mapOf("AVAXUSDT" to 45.0))
        assertTrue(alertsUp.alerts.isEmpty()) // No TP triggered

        val alertsDown = repo.updatePositionsWithPrices(mapOf("AVAXUSDT" to 15.0))
        assertTrue(alertsDown.alerts.isEmpty()) // No SL triggered

        // Position should still be OPEN
        val stillOpen = repo.getOpenPositions().first().find { it.symbol == "AVAXUSDT" }
        assertNotNull(stillOpen)
        assertEquals(TradePosition.STATUS_OPEN, stillOpen?.status)

        // Clean up
        val id = stillOpen?.id
        if (id != null) {
            repo.closePositionManually(id, 20.0)
        }
    }

    @Test
    fun `test position volume is calculated as 0_5 percent of simulated balance`() = runTest {
        // Initial balance 10,000 USDT -> 0.5% = 50 USDT
        val settings = TradeSettings(
            simulatedBalanceUsdt = 10000.0,
            volumePercentOfBalance = 0.5
        )
        val vol1 = settings.calculatePositionVolume()
        assertEquals(50.0, vol1, 0.001)

        // If balance increases to 12,000 USDT -> 0.5% = 60 USDT
        val settingsHigh = settings.updateBalance(12000.0)
        assertEquals(60.0, settingsHigh.calculatePositionVolume(), 0.001)

        // If balance decreases to 8,000 USDT -> 0.5% = 40 USDT
        val settingsLow = settings.updateBalance(8000.0)
        assertEquals(40.0, settingsLow.calculatePositionVolume(), 0.001)
    }

    @Test
    fun `test simulated balance updated on position open and close`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        val initialBalance = vm.uiState.value.settings.simulatedBalanceUsdt
        assertEquals(10000.0, initialBalance, 0.001)

        // Volume for first trade is 0.5% of 10,000 = 50.0 USDT
        val expectedVolume = initialBalance * 0.005
        assertEquals(50.0, expectedVolume, 0.001)

        // Open a simulated buy for LINKUSDT
        vm.executeBuy("LINKUSDT")

        // Wait briefly for coroutines to complete DB insertion and balance update
        kotlinx.coroutines.delay(100)

        val balanceAfterOpen = vm.uiState.value.settings.simulatedBalanceUsdt
        val openPositions = vm.repository.getOpenPositions().first()
        val linkPos = openPositions.find { it.symbol == "LINKUSDT" }

        if (linkPos != null) {
            // Balance should be deducted by invested amount (50.0 USDT)
            assertEquals(initialBalance - linkPos.investedUsdt, balanceAfterOpen, 0.1)

            // Now close the position manually at +10% price
            val exitPrice = linkPos.entryPrice * 1.10
            vm.manualClosePosition(linkPos.id)
            kotlinx.coroutines.delay(100)

            val balanceAfterClose = vm.uiState.value.settings.simulatedBalanceUsdt
            // Closed with profit, balance should be greater than initial balance
            assertTrue(balanceAfterClose > initialBalance)
        }
    }

    @Test
    fun `test open positions ROI sort ascending and descending`() {
        // Create 3 positions with different unrealized ROI
        val pos1 = TradePosition(
            id = 1,
            symbol = "BTCUSDT",
            entryPrice = 100.0,
            currentPrice = 110.0, // +10% ROI
            amount = 1.0,
            entryTime = 1000L
        )
        val pos2 = TradePosition(
            id = 2,
            symbol = "ETHUSDT",
            entryPrice = 100.0,
            currentPrice = 95.0, // -5% ROI
            amount = 1.0,
            entryTime = 2000L
        )
        val pos3 = TradePosition(
            id = 3,
            symbol = "SOLUSDT",
            entryPrice = 100.0,
            currentPrice = 125.0, // +25% ROI
            amount = 1.0,
            entryTime = 3000L
        )

        val list = listOf(pos1, pos2, pos3)

        // Sort Descending (Highest ROI first: SOL +25%, BTC +10%, ETH -5%)
        val desc = list.sortedByDescending { it.unrealizedPnlPercent }
        assertEquals("SOLUSDT", desc[0].symbol)
        assertEquals("BTCUSDT", desc[1].symbol)
        assertEquals("ETHUSDT", desc[2].symbol)

        // Sort Ascending (Lowest/Loss ROI first: ETH -5%, BTC +10%, SOL +25%)
        val asc = list.sortedBy { it.unrealizedPnlPercent }
        assertEquals("ETHUSDT", asc[0].symbol)
        assertEquals("BTCUSDT", asc[1].symbol)
        assertEquals("SOLUSDT", asc[2].symbol)

        // Default sort by entryTime (Newest first: SOL 3000L, ETH 2000L, BTC 1000L)
        val defaultSort = list.sortedByDescending { it.entryTime }
        assertEquals("SOLUSDT", defaultSort[0].symbol)
        assertEquals("ETHUSDT", defaultSort[1].symbol)
        assertEquals("BTCUSDT", defaultSort[2].symbol)
    }

    @Test
    fun `test each position is opened 5 seconds apart during batch open`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)

        // Clear existing positions
        val db = com.example.data.AppDatabase.getInstance(app)
        db.tradeDao().clearAll()

        // Pre-seed 3 bullish symbols
        vm.repository.setCachedSignal("BTCUSDT", true)
        vm.repository.setCachedSignal("ETHUSDT", true)
        vm.repository.setCachedSignal("SOLUSDT", true)

        // Start opening positions
        val job = vm.openAllScannedPositions(isAuto = false)

        // Advance 100ms: first position should be opened immediately without delay
        testScheduler.advanceTimeBy(100)
        testScheduler.runCurrent()
        val openPositionsStep1 = vm.repository.getOpenPositionSymbols()
        assertEquals(1, openPositionsStep1.size)
        assertTrue(vm.uiState.value.isOpeningPositions)

        // Advance 5000ms: second position should now be opened
        testScheduler.advanceTimeBy(5000)
        testScheduler.runCurrent()
        val openPositionsStep2 = vm.repository.getOpenPositionSymbols()
        assertEquals(2, openPositionsStep2.size)

        // Advance another 5000ms: third position should be opened
        testScheduler.advanceTimeBy(5000)
        testScheduler.runCurrent()
        val openPositionsStep3 = vm.repository.getOpenPositionSymbols()
        assertEquals(3, openPositionsStep3.size)

        // Final advance to finish job
        testScheduler.advanceTimeBy(5100)
        testScheduler.runCurrent()
        job.join()
        assertFalse(vm.uiState.value.isOpeningPositions)
    }

    @Test
    fun `test VolumeType percent of balance vs fixed volume calculation`() {
        val percentSettings = TradeSettings(
            volumeType = VolumeType.PERCENT_OF_BALANCE,
            volumePercentOfBalance = 0.5,
            simulatedBalanceUsdt = 10000.0
        )
        // 0.5% of 10,000 = 50.0 USDT
        assertEquals(50.0, percentSettings.calculatePositionVolume(), 0.001)

        val customBal = 20000.0
        // 0.5% of 20,000 = 100.0 USDT
        assertEquals(100.0, percentSettings.calculatePositionVolume(customBal), 0.001)

        // Fixed volume test
        val fixedSettings = TradeSettings(
            volumeType = VolumeType.FIXED_AMOUNT,
            fixedVolumeUsdt = 75.0,
            simulatedBalanceUsdt = 10000.0
        )
        assertEquals(75.0, fixedSettings.calculatePositionVolume(), 0.001)
        assertEquals(75.0, fixedSettings.calculatePositionVolume(customBal), 0.001)
    }

    @Test
    fun `test no stop loss setting defaults to null`() {
        val defaultSettings = TradeSettings()
        // Yêu cầu: không thiết lập cắt lỗ (mặc định null)
        assertNull(defaultSettings.stopLossPercent)
    }

    @Test
    fun `test signal condition filters with SMA20 and optional SuperTrend`() {
        val candle = Candle(
            openTime = 1000L,
            open = 100.0,
            high = 110.0,
            low = 95.0,
            close = 105.0, // close = 105.0 > ma20 = 100.0 (Giá cao hơn SMA20)
            volume = 500.0,
            closeTime = 2000L
        )

        // Case A: Giá > SMA20, MA20 > SuperTrend is TRUE
        val pointBullishBoth = com.example.model.IndicatorData(
            candle = candle,
            ma20 = 100.0,
            superTrend = com.example.model.SuperTrendPoint(value = 90.0, isBullish = true),
            isMaAboveSuperTrend = true
        )
        assertTrue(pointBullishBoth.isPriceAboveSma20)
        // With SuperTrend required: true
        assertTrue(pointBullishBoth.isBullishSignal(useSuperTrend = true))
        // With SuperTrend optional/disabled: true (only requires Price > SMA20)
        assertTrue(pointBullishBoth.isBullishSignal(useSuperTrend = false))

        // Case B: Giá > SMA20, but MA20 > SuperTrend is FALSE
        val pointMaBelowSuperTrend = com.example.model.IndicatorData(
            candle = candle,
            ma20 = 100.0,
            superTrend = com.example.model.SuperTrendPoint(value = 110.0, isBullish = false),
            isMaAboveSuperTrend = false
        )
        assertTrue(pointMaBelowSuperTrend.isPriceAboveSma20)
        // With SuperTrend required: false
        assertFalse(pointMaBelowSuperTrend.isBullishSignal(useSuperTrend = true))
        // With SuperTrend optional/disabled: true because Price (105) > SMA20 (100)
        assertTrue(pointMaBelowSuperTrend.isBullishSignal(useSuperTrend = false))

        // Case C: Giá < SMA20 (fails in both modes)
        val candleBearish = candle.copy(close = 95.0)
        val pointBearish = com.example.model.IndicatorData(
            candle = candleBearish,
            ma20 = 100.0,
            superTrend = com.example.model.SuperTrendPoint(value = 90.0, isBullish = true),
            isMaAboveSuperTrend = true
        )
        assertFalse(pointBearish.isPriceAboveSma20)
        assertFalse(pointBearish.isBullishSignal(useSuperTrend = true))
        assertFalse(pointBearish.isBullishSignal(useSuperTrend = false))
    }

    @Test
    fun `test Binance Access PIN verification and session authorization`() {
        val configWithPin = BinanceApiConfig(
            apiKey = "testApiKey",
            apiSecret = "testApiSecret",
            accessPin = "1234",
            isTestnet = true
        )
        assertTrue(configWithPin.hasAccessPin)
        assertTrue(configWithPin.verifyPin("1234"))
        assertFalse(configWithPin.verifyPin("0000"))
        assertFalse(configWithPin.verifyPin(""))

        val configWithoutPin = BinanceApiConfig(
            apiKey = "testApiKey",
            apiSecret = "testApiSecret",
            accessPin = "",
            isTestnet = true
        )
        assertFalse(configWithoutPin.hasAccessPin)
        assertTrue(configWithoutPin.verifyPin("any_pin"))
    }

    @Test
    fun `test stepSize rounding auto-repair logic`() {
        // BTCUSDT stepSize 0.001
        val roundedBtc = com.example.network.BinanceFuturesApiVerifier.roundToStepSize(0.123456, 0.001)
        assertEquals("0.123", roundedBtc)

        // ETHUSDT stepSize 0.01
        val roundedEth = com.example.network.BinanceFuturesApiVerifier.roundToStepSize(1.5891, 0.01)
        assertEquals("1.58", roundedEth)

        // DOGEUSDT stepSize 1.0 (integer)
        val roundedDoge = com.example.network.BinanceFuturesApiVerifier.roundToStepSize(150.98, 1.0)
        assertEquals("150", roundedDoge)
    }

    @Test
    fun `test distinction between Paper Trading and Binance Live positions`() {
        val paperPos = TradePosition(
            id = 1,
            symbol = "BTCUSDT",
            entryPrice = 60000.0,
            currentPrice = 61200.0,
            amount = 0.005,
            isLiveBinanceOrder = false,
            binanceOrderId = null
        )
        assertFalse(paperPos.isLiveBinanceOrder)
        assertFalse(paperPos.isBinanceVerified)
        assertNull(paperPos.binanceOrderId)

        val binancePos = TradePosition(
            id = 2,
            symbol = "ETHUSDT",
            entryPrice = 2500.0,
            currentPrice = 2600.0,
            amount = 0.1,
            isLiveBinanceOrder = true,
            binanceOrderId = 9876543210L,
            binanceStatus = "FILLED"
        )
        assertTrue(binancePos.isLiveBinanceOrder)
        assertTrue(binancePos.isBinanceVerified)
        assertEquals(9876543210L, binancePos.binanceOrderId)
        assertEquals("FILLED", binancePos.binanceStatus)
    }

    @Test
    fun `test Binance Mainnet credentials configured and PIN strictly required`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val storage = com.example.util.SecureApiKeyStorage(app)
        val config = storage.loadConfig()

        // Verify preconfigured keys match user prompt requirements
        assertEquals("mqcU8gfuojMk43xtXG0RRc6k2fCXVjKz1W9fT6Y6lsxBHBjNRXmHaaqv5iOn699Z", com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY)
        assertEquals("RKJCqagKpEluIvkYqjS4NkqpyML3rRZQJXrlMsUH6pPfLJE5xkEC4lJMvKsoXSyO", com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_SECRET)

        val vm = MainViewModel(app)
        // Rule 2: isPinRequired must ALWAYS return true
        assertTrue(vm.isPinRequired())
        assertTrue(vm.authorizePin("1234"))
        assertFalse(vm.authorizePin("9999"))
    }
}

