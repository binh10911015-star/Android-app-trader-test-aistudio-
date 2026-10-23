package com.example.repository

import com.example.data.TradeDao
import com.example.indicators.TechnicalAnalysis
import com.example.model.ActiveBasket
import com.example.model.AlertType
import com.example.model.BacktestConfig
import com.example.model.BacktestSummaryResult
import com.example.model.BacktestTradeItem
import com.example.model.BasketBacktestReport
import com.example.model.BasketCycleResult
import com.example.model.BasketPosition
import com.example.model.BasketSlotId
import com.example.model.BasketStatus
import com.example.model.BinanceTicker
import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.model.PeriodPnL
import com.example.model.PositionUpdateResult
import com.example.model.ScoredCoin
import com.example.model.TradeAlert
import com.example.model.TradePosition
import com.example.model.TradeSettings
import com.example.network.BinanceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

class CryptoRepository(private val tradeDao: TradeDao) {

    private val signalCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    init {
        listOf(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "DOGEUSDT",
            "PEPEUSDT", "SUIUSDT", "AVAXUSDT", "NEARUSDT", "WIFUSDT", "AAVEUSDT", "ENAUSDT"
        ).forEach {
            signalCache[it] = true
        }
    }

    suspend fun getTickers(): Result<List<BinanceTicker>> = withContext(Dispatchers.IO) {
        try {
            val list = BinanceClient.fetchTickersWithFallback()
            Result.success(list)
        } catch (e: Exception) {
            Result.success(com.example.data.DefaultCoinData.getSeedTickers())
        }
    }

    suspend fun getCandlesWithIndicators(
        symbol: String,
        interval: String = "4h"
    ): Result<List<IndicatorData>> = withContext(Dispatchers.IO) {
        try {
            val candles = BinanceClient.fetchKlinesWithFallback(symbol, interval)
            val indicatorData = TechnicalAnalysis.computeIndicators(candles)
            if (indicatorData.isNotEmpty()) {
                val lastItem = indicatorData.last()
                signalCache[symbol] = lastItem.isPriceAboveSma20
            }
            Result.success(indicatorData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedSignal(symbol: String): Boolean? = signalCache[symbol]

    fun setCachedSignal(symbol: String, isBullish: Boolean) {
        signalCache[symbol] = isBullish
    }

    fun getBullishSignalsCount(): Int = signalCache.values.count { it }

    fun getAllBullishSymbols(): Set<String> = signalCache.filterValues { it }.keys.toSet()

    fun getOpenPositions(): Flow<List<TradePosition>> = tradeDao.getOpenPositions()

    fun getClosedPositions(): Flow<List<TradePosition>> = tradeDao.getClosedPositions()

    fun getAllPositions(): Flow<List<TradePosition>> = tradeDao.getAllPositions()

    suspend fun getOpenPositionsList(): List<TradePosition> = withContext(Dispatchers.IO) {
        tradeDao.getOpenPositionsList()
    }

    fun getOpenPositionsListSync(): List<TradePosition> = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
        tradeDao.getOpenPositionsList()
    }

    suspend fun hasOpenPositionForSymbol(symbol: String): Boolean = withContext(Dispatchers.IO) {
        tradeDao.countOpenForSymbol(symbol) > 0
    }

    suspend fun getOpenPositionSymbols(): Set<String> = withContext(Dispatchers.IO) {
        tradeDao.getOpenPositionsList().map { it.symbol }.toSet()
    }

    suspend fun executeSimulatedBuy(
        symbol: String,
        currentPrice: Double,
        settings: TradeSettings,
        investedUsdt: Double? = null,
        isAutoTriggered: Boolean = false,
        binanceOrderId: Long? = null,
        binanceClientOrderId: String? = null,
        isLiveBinanceOrder: Boolean = false,
        binanceStatus: String? = null,
        exactAmount: Double? = null,
        exactEntryPrice: Double? = null,
        autoFixNote: String? = null
    ): TradeAlert? = withContext(Dispatchers.IO) {
        val effectiveEntryPrice = if (exactEntryPrice != null && exactEntryPrice > 0.0) exactEntryPrice else currentPrice
        if (effectiveEntryPrice <= 0.0) return@withContext null

        if (tradeDao.countOpenForSymbol(symbol) > 0) return@withContext null

        val invested = investedUsdt ?: settings.calculatePositionVolume()
        val amount = exactAmount ?: (invested / effectiveEntryPrice)
        val tpPrice = settings.takeProfitPercent?.let { effectiveEntryPrice * (1.0 + it / 100.0) }

        val position = TradePosition(
            symbol = symbol,
            entryPrice = effectiveEntryPrice,
            currentPrice = currentPrice,
            amount = amount,
            investedUsdt = invested,
            takeProfitPrice = tpPrice,
            stopLossPrice = null,
            entryTime = System.currentTimeMillis(),
            status = TradePosition.STATUS_OPEN,
            timeframe = settings.timeframe,
            binanceOrderId = binanceOrderId,
            binanceClientOrderId = binanceClientOrderId,
            isLiveBinanceOrder = isLiveBinanceOrder,
            binanceStatus = binanceStatus ?: if (isLiveBinanceOrder) "FILLED" else null,
            binanceUpdateTime = if (isLiveBinanceOrder) System.currentTimeMillis() else null
        )

        tradeDao.insert(position)

        val priceStr = formatPrice(effectiveEntryPrice)
        val tpStr = if (tpPrice != null && settings.takeProfitPercent != null) {
            "$$${formatPrice(tpPrice)} (+${settings.takeProfitPercent}%)"
        } else {
            "Không thiết lập"
        }

        val volStr = if (invested % 1.0 == 0.0) "${invested.toInt()} USDT" else "${String.format(Locale.US, "%.1f", invested)} USDT"
        val fixPrefix = if (!autoFixNote.isNullOrBlank()) "[$autoFixNote] " else ""

        val title = if (isLiveBinanceOrder) {
            if (isAutoTriggered) "TỰ ĐỘNG KHỚP LỆNH BINANCE" else "ĐÃ KHỚP LỆNH BINANCE"
        } else {
            if (isAutoTriggered) "TỰ ĐỘNG VÀO LỆNH LONG" else "MỞ LỆNH MUA"
        }

        val message = if (isLiveBinanceOrder) {
            "${fixPrefix}Lệnh #$binanceOrderId đã khớp trực tiếp trên sàn Binance! Giá khớp: $$priceStr, KL: ${String.format(Locale.US, "%.4f", amount)} (Vốn: $volStr). TP: $tpStr"
        } else {
            "Đã mua $symbol tại $$priceStr (Vốn: $volStr). TP: $tpStr"
        }

        TradeAlert(
            symbol = symbol,
            title = title,
            message = message,
            type = if (isAutoTriggered) AlertType.AUTO_BUY_ORDER else AlertType.INFO
        )
    }

    suspend fun updatePositionsWithPrices(tickerMap: Map<String, Double>): PositionUpdateResult = withContext(Dispatchers.IO) {
        val alerts = mutableListOf<TradeAlert>()
        val closedPositions = mutableListOf<TradePosition>()
        var totalReturnedCapital = 0.0
        val openPositions = tradeDao.getOpenPositionsList()

        for (pos in openPositions) {
            val latestPrice = tickerMap[pos.symbol] ?: continue
            val now = System.currentTimeMillis()

            if (pos.takeProfitPrice != null && latestPrice >= pos.takeProfitPrice) {
                val pnlUsdt = (pos.takeProfitPrice - pos.entryPrice) * pos.amount
                val pnlPercent = ((pos.takeProfitPrice - pos.entryPrice) / pos.entryPrice) * 100.0
                val closedPos = pos.copy(
                    currentPrice = pos.takeProfitPrice,
                    status = TradePosition.STATUS_CLOSED,
                    exitPrice = pos.takeProfitPrice,
                    exitTime = now,
                    pnlUsdt = pnlUsdt,
                    pnlPercent = pnlPercent,
                    closeReason = TradePosition.REASON_TAKE_PROFIT
                )
                tradeDao.update(closedPos)
                closedPositions.add(closedPos)
                totalReturnedCapital += (pos.investedUsdt + pnlUsdt)
                alerts.add(
                    TradeAlert(
                        symbol = pos.symbol,
                        title = "CHỐT LỜI THÀNH CÔNG (TP)",
                        message = "Lệnh ${pos.symbol} đã chạm TP tại $${formatPrice(pos.takeProfitPrice)}. Lợi nhuận: +$${String.format(Locale.US, "%.2f", pnlUsdt)} (+${String.format(Locale.US, "%.2f", pnlPercent)}%)",
                        type = AlertType.TAKE_PROFIT
                    )
                )
            } else if (pos.stopLossPrice != null && latestPrice <= pos.stopLossPrice) {
                val pnlUsdt = (pos.stopLossPrice - pos.entryPrice) * pos.amount
                val pnlPercent = ((pos.stopLossPrice - pos.entryPrice) / pos.entryPrice) * 100.0
                val closedPos = pos.copy(
                    currentPrice = pos.stopLossPrice,
                    status = TradePosition.STATUS_CLOSED,
                    exitPrice = pos.stopLossPrice,
                    exitTime = now,
                    pnlUsdt = pnlUsdt,
                    pnlPercent = pnlPercent,
                    closeReason = TradePosition.REASON_STOP_LOSS
                )
                tradeDao.update(closedPos)
                closedPositions.add(closedPos)
                totalReturnedCapital += (pos.investedUsdt + pnlUsdt)
                alerts.add(
                    TradeAlert(
                        symbol = pos.symbol,
                        title = "CẮT LỖ BẢO TOÀN VỐN (SL)",
                        message = "Lệnh ${pos.symbol} đã chạm SL tại $${formatPrice(pos.stopLossPrice)}. Thua lỗ: $${String.format(Locale.US, "%.2f", pnlUsdt)} (${String.format(Locale.US, "%.2f", pnlPercent)}%)",
                        type = AlertType.STOP_LOSS
                    )
                )
            } else if (latestPrice != pos.currentPrice) {
                tradeDao.update(pos.copy(currentPrice = latestPrice))
            }
        }

        PositionUpdateResult(alerts, closedPositions, totalReturnedCapital)
    }

    suspend fun updatePositionTpSl(positionId: Long, tpPrice: Double?, slPrice: Double?) = withContext(Dispatchers.IO) {
        val pos = tradeDao.getPositionById(positionId) ?: return@withContext
        val updated = pos.copy(takeProfitPrice = tpPrice, stopLossPrice = slPrice)
        tradeDao.update(updated)
    }

    suspend fun closePositionManually(positionId: Long, currentMarketPrice: Double): TradeAlert? = withContext(Dispatchers.IO) {
        val pos = tradeDao.getPositionById(positionId) ?: return@withContext null
        if (pos.status != TradePosition.STATUS_OPEN) return@withContext null

        val exitPrice = if (currentMarketPrice > 0.0) currentMarketPrice else pos.currentPrice
        val pnlUsdt = (exitPrice - pos.entryPrice) * pos.amount
        val pnlPercent = ((exitPrice - pos.entryPrice) / pos.entryPrice) * 100.0
        val now = System.currentTimeMillis()

        val closed = pos.copy(
            currentPrice = exitPrice,
            status = TradePosition.STATUS_CLOSED,
            exitPrice = exitPrice,
            exitTime = now,
            pnlUsdt = pnlUsdt,
            pnlPercent = pnlPercent,
            closeReason = TradePosition.REASON_MANUAL
        )
        tradeDao.update(closed)

        val sign = if (pnlUsdt >= 0) "+" else ""
        TradeAlert(
            symbol = pos.symbol,
            title = "ĐÃ ĐÓNG LỆNH THỦ CÔNG",
            message = "Đã đóng ${pos.symbol} tại $${formatPrice(exitPrice)}. PnL: $sign$${String.format(Locale.US, "%.2f", pnlUsdt)} ($sign${String.format(Locale.US, "%.2f", pnlPercent)}%)",
            type = AlertType.MANUAL_CLOSE
        )
    }

    suspend fun closeAllOpenPositions(tickerMap: Map<String, Double>): Pair<List<TradePosition>, Double> = withContext(Dispatchers.IO) {
        val openPositions = tradeDao.getOpenPositionsList()
        val closedList = mutableListOf<TradePosition>()
        var totalReturned = 0.0
        val now = System.currentTimeMillis()

        for (pos in openPositions) {
            val exitPrice = tickerMap[pos.symbol] ?: pos.currentPrice
            val pnlUsdt = (exitPrice - pos.entryPrice) * pos.amount
            val pnlPercent = if (pos.entryPrice > 0) ((exitPrice - pos.entryPrice) / pos.entryPrice) * 100.0 else 0.0
            val closed = pos.copy(
                currentPrice = exitPrice,
                status = TradePosition.STATUS_CLOSED,
                exitPrice = exitPrice,
                exitTime = now,
                pnlUsdt = pnlUsdt,
                pnlPercent = pnlPercent,
                closeReason = TradePosition.REASON_MANUAL
            )
            tradeDao.update(closed)
            closedList.add(closed)
            totalReturned += (pos.investedUsdt + pnlUsdt)
        }
        Pair(closedList, totalReturned)
    }

    suspend fun checkAndExecutePortfolioTakeProfit(
        tickerMap: Map<String, Double>,
        targetTpPercent: Double
    ): PositionUpdateResult = withContext(Dispatchers.IO) {
        val openPositions = tradeDao.getOpenPositionsList()
        if (openPositions.isEmpty() || targetTpPercent <= 0.0) {
            return@withContext PositionUpdateResult(emptyList(), emptyList(), 0.0)
        }

        var totalInvested = 0.0
        var totalUnrealizedPnl = 0.0

        for (pos in openPositions) {
            val latestPrice = tickerMap[pos.symbol] ?: pos.currentPrice
            val diff = (latestPrice - pos.entryPrice) * pos.amount
            totalInvested += pos.investedUsdt
            totalUnrealizedPnl += diff
        }

        val portfolioRoi = if (totalInvested > 0.0) (totalUnrealizedPnl / totalInvested) * 100.0 else 0.0

        if (portfolioRoi >= targetTpPercent) {
            val alerts = mutableListOf<TradeAlert>()
            val closedPositions = mutableListOf<TradePosition>()
            var totalReturnedCapital = 0.0
            val now = System.currentTimeMillis()

            for (pos in openPositions) {
                val latestPrice = tickerMap[pos.symbol] ?: pos.currentPrice
                val pnlUsdt = (latestPrice - pos.entryPrice) * pos.amount
                val pnlPercent = if (pos.entryPrice > 0) ((latestPrice - pos.entryPrice) / pos.entryPrice) * 100.0 else 0.0

                val closedPos = pos.copy(
                    currentPrice = latestPrice,
                    status = TradePosition.STATUS_CLOSED,
                    exitPrice = latestPrice,
                    exitTime = now,
                    pnlUsdt = pnlUsdt,
                    pnlPercent = pnlPercent,
                    closeReason = "PORTFOLIO_TAKE_PROFIT"
                )
                tradeDao.update(closedPos)
                closedPositions.add(closedPos)
                totalReturnedCapital += (pos.investedUsdt + pnlUsdt)
            }

            alerts.add(
                TradeAlert(
                    symbol = "PORTFOLIO",
                    title = "CHỐT LỜI TOÀN DANH MỤC THÀNH CÔNG",
                    message = "🎯 Đạt mục tiêu chốt lời toàn danh mục (+${String.format(Locale.US, "%.2f", portfolioRoi)}% >= +${String.format(Locale.US, "%.1f", targetTpPercent)}%)! Đã tự động đóng đồng loạt toàn bộ ${closedPositions.size} vị thế (Tổng lãi: +$${String.format(Locale.US, "%.2f", totalUnrealizedPnl)} USDT).",
                    type = AlertType.TAKE_PROFIT
                )
            )

            return@withContext PositionUpdateResult(alerts, closedPositions, totalReturnedCapital)
        }

        PositionUpdateResult(emptyList(), emptyList(), 0.0)
    }

    suspend fun syncWithBinanceLivePositions(
        livePositions: List<com.example.model.BinanceLivePosition>
    ) = withContext(Dispatchers.IO) {
        val currentOpen = tradeDao.getOpenPositionsList()
        val liveLocalPositions = currentOpen.filter { it.isLiveBinanceOrder }
        val liveLocalMap = liveLocalPositions.associateBy { it.symbol }
        val liveSymbolsFromBinance = livePositions.map { it.symbol }.toSet()

        for (live in livePositions) {
            val existingLive = liveLocalMap[live.symbol]
            val side = if (live.isLong) "LONG" else "SHORT"
            val invested = if (live.initialMargin > 0) live.initialMargin else (live.absAmount * live.entryPrice / live.leverage.coerceAtLeast(1))

            if (existingLive != null) {
                val updated = existingLive.copy(
                    entryPrice = live.entryPrice,
                    currentPrice = live.markPrice,
                    amount = live.absAmount,
                    investedUsdt = invested,
                    positionSide = side,
                    liveUnrealizedProfit = live.unrealizedProfit,
                    leverage = live.leverage,
                    binanceUpdateTime = System.currentTimeMillis()
                )
                tradeDao.update(updated)
            } else {
                val newPos = TradePosition(
                    symbol = live.symbol,
                    entryPrice = live.entryPrice,
                    currentPrice = live.markPrice,
                    amount = live.absAmount,
                    investedUsdt = invested,
                    entryTime = if (live.updateTime > 0) live.updateTime else System.currentTimeMillis(),
                    status = TradePosition.STATUS_OPEN,
                    isLiveBinanceOrder = true,
                    positionSide = side,
                    liveUnrealizedProfit = live.unrealizedProfit,
                    leverage = live.leverage,
                    binanceUpdateTime = System.currentTimeMillis()
                )
                tradeDao.insert(newPos)
            }
        }

        for (pos in liveLocalPositions) {
            if (pos.symbol !in liveSymbolsFromBinance) {
                val closed = pos.copy(
                    status = TradePosition.STATUS_CLOSED,
                    exitPrice = pos.currentPrice,
                    exitTime = System.currentTimeMillis(),
                    pnlUsdt = pos.unrealizedPnlUsdt,
                    pnlPercent = pos.unrealizedPnlPercent,
                    closeReason = TradePosition.REASON_BINANCE_SYNC
                )
                tradeDao.update(closed)
            }
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        tradeDao.clearAll()
    }

    // ==========================================
    // MODULE BACKTEST 1: ĐƠN LẺ D1 BREAKOUT
    // ==========================================

    fun runD1BreakoutBacktest(
        symbol: String, 
        initialBalance: Double = 1000.0,
        riskPerTradePercent: Double = 0.5,
        takeProfitPercent: Double = 4.0,
        stopLossPercent: Double = 0.0,
        candleLimit: Int = 180,
        volumeThreshold: Double = 2.0
    ): BacktestSummaryResult {
        val candles = BinanceClient.fetchKlinesWithFallback(symbol, interval = "1d", limit = candleLimit)
        if (candles.size < 25) {
            return BacktestSummaryResult(
                symbol = symbol,
                timeframe = "1d",
                errorMessage = "Chưa đủ 25 nến D1 từ Binance để thực hiện Backtest."
            )
        }

        var balance = initialBalance
        var peakBalance = balance
        var maxDrawdown = 0.0
        val trades = mutableListOf<BacktestTradeItem>()

        var totalProfit = 0.0
        var totalLoss = 0.0

        var i = 20
        while (i < candles.size - 1) {
            val currentCandle = candles[i]
            val previous20 = candles.subList(i - 20, i)

            val currentPrice = currentCandle.close
            val currentVol = currentCandle.volume
            val sma20Open = previous20.map { it.open }.average()
            val avgVol20 = previous20.map { it.volume }.average()

            val volRatio = if (avgVol20 > 0.0) currentVol / avgVol20 else 0.0
            val isSignal = currentPrice >= sma20Open && volRatio >= volumeThreshold

            if (isSignal) {
                val entryPrice = currentPrice
                val investedAmount = balance * (riskPerTradePercent / 100.0).coerceAtLeast(0.005)
                val coinQty = investedAmount / entryPrice
                val entryTime = currentCandle.openTime

                var exitPrice = entryPrice
                var exitTime = entryTime
                var exitReason = "HẾT DỮ LIỆU"
                var closed = false

                for (j in (i + 1) until candles.size) {
                    val futureCandle = candles[j]
                    val highRoi = ((futureCandle.high - entryPrice) / entryPrice) * 100.0

                    if (highRoi >= takeProfitPercent) {
                        exitPrice = entryPrice * (1.0 + takeProfitPercent / 100.0)
                        exitTime = futureCandle.openTime
                        exitReason = "TP (+${takeProfitPercent}%)"
                        closed = true
                        i = j
                        break
                    }

                    if (futureCandle.close < sma20Open && j > i + 2) {
                        exitPrice = futureCandle.close
                        exitTime = futureCandle.openTime
                        exitReason = "Gãy SMA20"
                        closed = true
                        i = j
                        break
                    }
                }

                if (!closed) {
                    val lastCandle = candles.last()
                    exitPrice = lastCandle.close
                    exitTime = lastCandle.openTime
                    i = candles.size
                }

                val pnl = (exitPrice - entryPrice) * coinQty
                val roi = ((exitPrice - entryPrice) / entryPrice) * 100.0
                val isWin = pnl > 0

                if (isWin) totalProfit += pnl else totalLoss += Math.abs(pnl)

                balance += pnl
                if (balance > peakBalance) peakBalance = balance
                val dd = if (peakBalance > 0.0) ((peakBalance - balance) / peakBalance) * 100.0 else 0.0
                if (dd > maxDrawdown) maxDrawdown = dd

                trades.add(
                    BacktestTradeItem(
                        entryTime = entryTime,
                        exitTime = exitTime,
                        symbol = symbol,
                        entryPrice = entryPrice,
                        exitPrice = exitPrice,
                        pnlUsdt = pnl,
                        roiPercent = roi,
                        isWin = isWin,
                        exitReason = exitReason
                    )
                )
            }
            i++
        }

        val winCount = trades.count { it.isWin }
        val lossCount = trades.count { !it.isWin }
        val winRate = if (trades.isNotEmpty()) (winCount.toDouble() / trades.size) * 100.0 else 0.0
        val profitFactor = if (totalLoss > 0.0) totalProfit / totalLoss else if (totalProfit > 0.0) 99.9 else 0.0
        val netProfit = balance - initialBalance
        val totalRoi = (netProfit / initialBalance) * 100.0

        return BacktestSummaryResult(
            symbol = symbol,
            timeframe = "1d",
            totalTrades = trades.size,
            winTrades = winCount,
            lossTrades = lossCount,
            winRatePercent = winRate,
            netProfitUsdt = netProfit,
            totalRoiPercent = totalRoi,
            maxDrawdownPercent = maxDrawdown,
            profitFactor = profitFactor,
            tradesHistory = trades.reversed()
        )
    }

    // ==========================================
    // MODULE BACKTEST 2: ENGINE BASKET D1 MOMENTUM (V2.0)
    // ==========================================

    private fun fetchHighLiquiditySymbolsFromMainnet(config: BacktestConfig): List<String> {
        val tickers = BinanceClient.fetchTickersWithFallback()
        val list = tickers.filter {
            val volumeUsdt = it.volume * it.lastPrice
            it.symbol.endsWith("USDT") && !it.symbol.contains("_") && (config.minQuoteVolume24h <= 0.0 || volumeUsdt >= config.minQuoteVolume24h)
        }.map { it.symbol }

        return if (list.isNotEmpty()) list else tickers.map { it.symbol }.filter { it.endsWith("USDT") && !it.contains("_") }
    }

    suspend fun runBasketD1MomentumBacktest(
        daysToTest: Int = 180,
        config: BacktestConfig = BacktestConfig(),
        symbolsPool: List<String> = emptyList()
    ): BasketBacktestReport = withContext(Dispatchers.IO) {
        // 1. Quét toàn bộ thị trường từ Binance Mainnet, không giới hạn take(50)
        val activeSymbols = if (symbolsPool.isNotEmpty()) symbolsPool else fetchHighLiquiditySymbolsFromMainnet(config)
        if (activeSymbols.isEmpty()) {
            return@withContext BasketBacktestReport(errorMessage = "Không tải được danh sách cặp coin từ Binance Mainnet.")
        }

        // 2. Tải Klines D1 từ máy chủ Binance Mainnet
        val klinesMap = mutableMapOf<String, List<Candle>>()
        activeSymbols.forEach { sym ->
            val candles = BinanceClient.fetchKlinesWithFallback(sym, interval = "1d", limit = daysToTest + 30)
            if (candles.size >= 30) klinesMap[sym] = candles
        }

        val btcCandles = klinesMap["BTCUSDT"] ?: return@withContext BasketBacktestReport(errorMessage = "Thiếu dữ liệu nến BTCUSDT để kiểm tra Regime.")

        var currentEquity = config.initialCapital
        var peakEquity = currentEquity
        var maxDrawdown = 0.0

        // Quản lý chính xác 2 Slot giỏ lệnh độc lập
        var primaryBasket: ActiveBasket? = null
        var secondaryBasket: ActiveBasket? = null
        var basketCycleCounter = 0
        val cyclesHistory = mutableListOf<BasketCycleResult>()
        val equityCurve = mutableListOf<Pair<Long, Double>>()

        val startIndex = 25
        equityCurve.add(Pair(btcCandles[startIndex].openTime, currentEquity))

        for (dayIdx in startIndex until btcCandles.size) {
            val currentTime = btcCandles[dayIdx].openTime
            val currentBtc = btcCandles[dayIdx - 1] // Nến D1 hôm qua vừa đóng lúc 07:00 AM

            // BƯỚC 1: ĐỊNH GIÁ & KIỂM TRA THOÁT LỆNH ĐỘC LẬP TỪNG SLOT (TRÁNH LOOK-AHEAD BIAS)
            fun evaluateBasketExit(basket: ActiveBasket?): ActiveBasket? {
                if (basket == null || basket.status == BasketStatus.CLOSED) return null

                var unrealizedPnL = 0.0
                for (pos in basket.positions) {
                    val candleToday = klinesMap[pos.symbol]?.find { it.openTime == currentTime }
                    if (candleToday != null) {
                        unrealizedPnL += (candleToday.close - pos.entryPrice) * pos.quantity
                    }
                }

                val currentRoi = if (basket.totalInvested > 0) unrealizedPnL / basket.totalInvested else 0.0
                val isTimedOut = (currentTime - basket.openTime) >= basket.maxHoldingMillis

                var exitReason: String? = null

                when (basket.status) {
                    BasketStatus.OPEN -> {
                        when {
                            currentRoi >= basket.activationRoi -> {
                                basket.status = BasketStatus.TRAILING_ACTIVE
                                basket.peakRoi = currentRoi
                                basket.currentTrailingStopRoi = maxOf(basket.floorLockRoi, currentRoi - basket.trailStepRoi)
                            }
                            currentRoi <= basket.initialStopLossRoi -> exitReason = "SL_HIT"
                            isTimedOut -> exitReason = "TIMEOUT_72H"
                        }
                    }
                    BasketStatus.TRAILING_ACTIVE -> {
                        if (currentRoi > basket.peakRoi) {
                            basket.peakRoi = currentRoi
                            val newStop = maxOf(basket.floorLockRoi, basket.peakRoi - basket.trailStepRoi)
                            if (newStop > basket.currentTrailingStopRoi) basket.currentTrailingStopRoi = newStop
                        }
                        if (currentRoi <= basket.currentTrailingStopRoi) exitReason = "TRAILING_STOP_HIT"
                        else if (isTimedOut) exitReason = "TIMEOUT_IN_TRAIL"
                    }
                    else -> Unit
                }

                if (exitReason != null) {
                    val grossProfit = basket.totalInvested * currentRoi
                    val takerFee = basket.totalInvested * 0.0008 // Phí giao dịch 2 chiều 0.08%
                    val netProfit = grossProfit - takerFee
                    currentEquity += netProfit
                    peakEquity = maxOf(peakEquity, currentEquity)

                    cyclesHistory.add(
                        BasketCycleResult(
                            cycleIndex = ++basketCycleCounter,
                            slotId = basket.slotId,
                            openTime = basket.openTime,
                            closeTime = currentTime,
                            holdingDays = maxOf(1.0, (currentTime - basket.openTime) / 86400000.0),
                            totalInvested = basket.totalInvested,
                            realizedProfitUsdt = netProfit,
                            basketRoiPercent = currentRoi * 100.0,
                            equityAfterClose = currentEquity,
                            coinsCount = basket.positions.size,
                            symbolsList = basket.positions.map { it.symbol },
                            exitReason = exitReason
                        )
                    )
                    return null // Giải phóng slot
                }

                // Ghi nhận Drawdown danh mục
                val tempEquity = currentEquity + unrealizedPnL
                val dd = if (peakEquity > 0) ((peakEquity - tempEquity) / peakEquity) * 100.0 else 0.0
                if (dd > maxDrawdown) maxDrawdown = dd
                return basket
            }

            primaryBasket = evaluateBasketExit(primaryBasket)
            secondaryBasket = evaluateBasketExit(secondaryBasket)

            // BƯỚC 2: KIỂM TRA MỞ GIỎ MỚI NẾU CÒN SLOT TRỐNG THEO CHẾ ĐỘ QUÉT
            val canOpenPrimary = (config.slotScanMode == "DUAL" || config.slotScanMode == "PRIMARY_ONLY") && primaryBasket == null
            val canOpenSecondary = (config.slotScanMode == "DUAL" || config.slotScanMode == "SECONDARY_ONLY") && secondaryBasket == null
            val hasAvailableSlot = canOpenPrimary || canOpenSecondary

            val runningSymbols = mutableSetOf<String>().apply {
                primaryBasket?.positions?.forEach { add(it.symbol) }
                secondaryBasket?.positions?.forEach { add(it.symbol) }
            }

            // TẦNG 1: BỘ LỌC MACRO BTC REGIME (SMA20 + EMA50 + Panic Circuit Breaker)
            val btcPrev20 = btcCandles.subList(dayIdx - 21, dayIdx - 1).map { it.close }
            val btcSma20 = btcPrev20.average()
            val btcPrev50 = if (dayIdx >= 51) btcCandles.subList(dayIdx - 51, dayIdx - 1).map { it.close } else emptyList()
            val btcEma50 = if (btcPrev50.isNotEmpty()) {
                val k = 2.0 / (50 + 1)
                var ema = btcPrev50.first()
                for (price in btcPrev50) {
                    ema = price * k + ema * (1 - k)
                }
                ema
            } else btcSma20
            val btcD1Change = if (currentBtc.open > 0) ((currentBtc.close - currentBtc.open) / currentBtc.open) * 100.0 else 0.0
            val isBtcBullish = currentBtc.close > btcSma20 && currentBtc.close > btcEma50 && btcD1Change > config.btcPanicCircuitBreaker

            if (hasAvailableSlot && isBtcBullish) {
                val totalRunningInvested = (primaryBasket?.totalInvested ?: 0.0) + (secondaryBasket?.totalInvested ?: 0.0)
                val availableBudget = (currentEquity * config.maxPortfolioAllocation) - totalRunningInvested

                if (availableBudget >= (currentEquity * config.perTradePct)) {
                    val candidates = mutableListOf<ScoredCoin>()

                    for ((sym, candles) in klinesMap) {
                        if (sym in runningSymbols) continue // LỌC TRÙNG TUYỆT ĐỐI
                        val cIdx = candles.indexOfFirst { it.openTime == currentTime }
                        if (cIdx < 25) continue

                        val k1 = candles[cIdx - 1]
                        val k2 = candles[cIdx - 2]
                        val prev20 = candles.subList(cIdx - 21, cIdx - 1)
                        val sma20 = prev20.map { it.close }.average()
                        val rvol = if (k2.volume > 0) k1.volume / k2.volume else 1.0
                        val changePct = if (k1.open > 0) ((k1.close - k1.open) / k1.open) * 100.0 else 0.0

                        // Tính biến động ATR(14)
                        val atr14 = candles.subList(cIdx - 15, cIdx - 1).map { maxOf(it.high - it.low, Math.abs(it.high - it.close)) }.average()
                        val atrPct = if (k1.close > 0) atr14 / k1.close else 0.05

                        // Kiểm tra điều kiện cứng v2.0
                        if (k1.close > sma20 && rvol >= config.minVolRatio && changePct in config.minD1ChangePct..config.maxD1ChangePct && atrPct <= config.maxAtrPercent) {
                            val score = rvol * (1.0 + ((k1.close - k1.open) / maxOf(1e-8, k1.high - k1.low)))
                            candidates.add(ScoredCoin(sym, score, candles[cIdx].open, rvol, changePct, atrPct))
                        }
                    }

                    if (candidates.isNotEmpty()) {
                        val sorted = candidates.sortedByDescending { it.score }
                        val perTradeUsdt = currentEquity * config.perTradePct
                        val maxSlots = (availableBudget / perTradeUsdt).toInt().coerceAtLeast(1)
                        val chosen = sorted.take(maxSlots)

                        val positions = chosen.map {
                            BasketPosition(
                                symbol = it.symbol,
                                entryPrice = it.entryPrice,
                                quantity = perTradeUsdt / it.entryPrice,
                                marginAllocated = perTradeUsdt,
                                entryAtrPercent = it.atrPercent
                            )
                        }

                        val assignedSlot = if (canOpenPrimary) BasketSlotId.PRIMARY else BasketSlotId.SECONDARY
                        val newBasket = ActiveBasket(
                            slotId = assignedSlot,
                            openTime = currentTime,
                            positions = positions,
                            totalInvested = perTradeUsdt * positions.size,
                            maxHoldingMillis = config.maxHoldingHours * 3600 * 1000L,
                            kTp = config.kTp,
                            kTrail = config.kTrail,
                            kSl = config.kSl
                        )

                        if (assignedSlot == BasketSlotId.PRIMARY) primaryBasket = newBasket else secondaryBasket = newBasket
                    }
                }
            }
            equityCurve.add(Pair(currentTime, currentEquity))
        }

        val totalCycles = cyclesHistory.size
        val winCycles = cyclesHistory.count { it.realizedProfitUsdt > 0 }
        val winrate = if (totalCycles > 0) (winCycles.toDouble() / totalCycles) * 100.0 else 0.0
        val netProfit = currentEquity - config.initialCapital

        return@withContext BasketBacktestReport(
            totalCycles = totalCycles,
            winratePercent = winrate,
            averageHoldingDays = if (totalCycles > 0) cyclesHistory.map { it.holdingDays }.average() else 0.0,
            maxDrawdownPercent = maxDrawdown,
            initialCapital = config.initialCapital,
            finalEquity = currentEquity,
            netProfitUsdt = netProfit,
            netProfitPercent = (netProfit / config.initialCapital) * 100.0,
            cycles = cyclesHistory.reversed(),
            equityCurve = equityCurve
        )
    }

    companion object {
        fun formatPrice(price: Double): String {
            return when {
                price >= 1000.0 -> String.format(Locale.US, "%,.2f", price)
                price >= 1.0 -> String.format(Locale.US, "%.3f", price)
                price >= 0.01 -> String.format(Locale.US, "%.4f", price)
                else -> String.format(Locale.US, "%.6f", price)
            }
        }
    }
}