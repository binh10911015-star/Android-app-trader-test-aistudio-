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
import com.example.model.BasketPositionDetail
import com.example.model.BasketSlotId
import com.example.model.BasketStatus
import com.example.model.BinanceTicker
import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.model.PeriodPnL
import com.example.model.PositionUpdateResult
import com.example.model.ScoredCoin
import com.example.model.SingleTradeRecord
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
        autoFixNote: String? = null,
        basketSlot: String = "PRIMARY"
    ): TradeAlert? = withContext(Dispatchers.IO) {
        val effectiveEntryPrice = if (exactEntryPrice != null && exactEntryPrice > 0.0) exactEntryPrice else currentPrice
        if (effectiveEntryPrice <= 0.0) return@withContext null

        if (tradeDao.countOpenForSymbol(symbol) > 0) return@withContext null

        val invested = investedUsdt ?: settings.calculatePositionVolume()
        val amount = exactAmount ?: (invested / effectiveEntryPrice)
        val tpPrice: Double? = null

        val position = TradePosition(
            symbol = symbol,
            entryPrice = effectiveEntryPrice,
            currentPrice = currentPrice,
            amount = amount,
            investedUsdt = invested,
            takeProfitPrice = null,
            stopLossPrice = null,
            entryTime = System.currentTimeMillis(),
            status = TradePosition.STATUS_OPEN,
            timeframe = settings.timeframe,
            binanceOrderId = binanceOrderId,
            binanceClientOrderId = binanceClientOrderId,
            isLiveBinanceOrder = isLiveBinanceOrder,
            binanceStatus = binanceStatus ?: if (isLiveBinanceOrder) "FILLED" else null,
            binanceUpdateTime = if (isLiveBinanceOrder) System.currentTimeMillis() else null,
            basketSlot = basketSlot
        )

        tradeDao.insert(position)

        val priceStr = formatPrice(effectiveEntryPrice)
        val tpStr = "Theo Trailing Stop ATR & FloorLock"

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

    /**
     * Helper tính Exponential Moving Average (EMA) theo chuẩn v2.2
     */
    fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()
        val k = 2.0 / (period + 1)
        val emaList = mutableListOf<Double>()
        var ema = prices.take(period).average()
        emaList.add(ema)

        for (i in period until prices.size) {
            ema = (prices[i] * k) + (ema * (1 - k))
            emaList.add(ema)
        }
        return emaList
    }

    private data class SinglePositionSimulation(
        val symbol: String,
        val entryTime: Long,
        val entryPrice: Double,
        val margin: Double,
        val quantity: Double,
        val atrPercent: Double,
        val openDayIdx: Int,
        val slotId: BasketSlotId = BasketSlotId.PRIMARY,
        var isTrailingActive: Boolean = false,
        var peakRoi: Double = 0.0,
        var currentStopRoi: Double = -1.0
    ) {
        val activationRoi: Double = maxOf(0.015, 0.50 * atrPercent)
        val trailStepRoi: Double = maxOf(0.008, 0.25 * atrPercent)
        val floorLockRoi: Double = 0.005 // +0.5% net
    }

    suspend fun runBasketD1MomentumBacktest(
        daysToTest: Int = 180,
        config: BacktestConfig = BacktestConfig(),
        symbolsPool: List<String> = emptyList()
    ): BasketBacktestReport = withContext(Dispatchers.IO) {
        // 1. Quét toàn bộ danh sách coin thanh khoản cao từ Binance Mainnet
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

        // Single-Position ATR Engine: Quản lý từng coin riêng lẻ
        val openPositions = mutableListOf<SinglePositionSimulation>()
        val singleTradesHistory = mutableListOf<SingleTradeRecord>()
        val cyclesHistory = mutableListOf<BasketCycleResult>()
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        var tradeCounter = 0

        val startIndex = 25
        equityCurve.add(Pair(btcCandles[startIndex].openTime, currentEquity))

        for (dayIdx in startIndex until btcCandles.size) {
            val currentTime = btcCandles[dayIdx].openTime
            val currentBtc = btcCandles[dayIdx - 1] // Nến D1 hôm qua vừa đóng lúc 07:00 AM

            // BƯỚC 1: ĐỊNH GIÁ & KIỂM TRA THOÁT LỆNH ĐỘC LẬP TỪNG COIN (SINGLE-POSITION ATR ENGINE)
            val openPositionsCopy = openPositions.toList()
            for (pos in openPositionsCopy) {
                val candleToday = klinesMap[pos.symbol]?.find { it.openTime == currentTime } ?: continue
                val holdingDays = maxOf(1, ((currentTime - pos.entryTime) / 86400000.0).toInt())
                var exitReason: String? = null
                var exitPrice = candleToday.close

                // 1. Kiểm tra Smart Timeout 7 ngày (168 giờ) -> Đóng lệnh giải phóng vốn ngay lập tức
                if (config.enableSmartTimeout && holdingDays >= config.timeoutDays) {
                    exitReason = "TIMEOUT_7D"
                    exitPrice = candleToday.close
                } else if (config.enableTrailingStop) {
                    // 2. Kiểm tra ATR Trailing Stop độc lập cho từng vị thế
                    val highRoi = if (pos.entryPrice > 0.0) (candleToday.high - pos.entryPrice) / pos.entryPrice else 0.0
                    val lowRoi = if (pos.entryPrice > 0.0) (candleToday.low - pos.entryPrice) / pos.entryPrice else 0.0

                    if (!pos.isTrailingActive) {
                        // Kích hoạt khi chạm Activation ROI
                        if (highRoi >= pos.activationRoi) {
                            pos.isTrailingActive = true
                            pos.peakRoi = highRoi
                            val floor = if (config.enableFloorLock) pos.floorLockRoi else -0.99
                            pos.currentStopRoi = maxOf(floor, pos.peakRoi - pos.trailStepRoi)

                            // Kiểm tra trong nến có chạm stop không
                            if (lowRoi <= pos.currentStopRoi) {
                                exitReason = "TRAILING_STOP_TP"
                                exitPrice = maxOf(1e-8, pos.entryPrice * (1.0 + pos.currentStopRoi))
                            }
                        }
                    } else {
                        // Tịnh tiến trailing stop khi bám đỉnh cao mới
                        if (highRoi > pos.peakRoi) {
                            pos.peakRoi = highRoi
                            val floor = if (config.enableFloorLock) pos.floorLockRoi else -0.99
                            val newStop = maxOf(floor, pos.peakRoi - pos.trailStepRoi)
                            if (newStop > pos.currentStopRoi) {
                                pos.currentStopRoi = newStop
                            }
                        }

                        // Thoát lệnh khi giá rơi chạm mức trailing stop
                        if (lowRoi <= pos.currentStopRoi) {
                            exitReason = "TRAILING_STOP_TP"
                            exitPrice = maxOf(1e-8, pos.entryPrice * (1.0 + pos.currentStopRoi))
                        }
                    }
                }

                if (exitReason != null) {
                    val gross = (exitPrice - pos.entryPrice) * pos.quantity
                    val takerFee = pos.margin * 0.0008 // Phí giao dịch 2 chiều 0.08%
                    val net = gross - takerFee
                    val roi = if (pos.margin > 0.0) (net / pos.margin) * 100.0 else 0.0

                    currentEquity += net
                    peakEquity = maxOf(peakEquity, currentEquity)

                    val record = SingleTradeRecord(
                        symbol = pos.symbol,
                        entryPrice = pos.entryPrice,
                        exitPrice = exitPrice,
                        margin = pos.margin,
                        grossPnl = gross,
                        fee = takerFee,
                        netPnl = net,
                        roiPercent = roi,
                        openDayIdx = pos.openDayIdx,
                        closeDayIdx = dayIdx,
                        holdingDays = holdingDays,
                        exitReason = exitReason
                    )
                    singleTradesHistory.add(record)

                    cyclesHistory.add(
                        BasketCycleResult(
                            cycleIndex = ++tradeCounter,
                            slotId = pos.slotId,
                            openTime = pos.entryTime,
                            closeTime = currentTime,
                            holdingDays = holdingDays.toDouble(),
                            totalInvested = pos.margin,
                            realizedProfitUsdt = net,
                            basketRoiPercent = roi,
                            equityAfterClose = currentEquity,
                            coinsCount = 1,
                            symbolsList = listOf(pos.symbol),
                            exitReason = exitReason,
                            positionDetails = listOf(
                                BasketPositionDetail(
                                    symbol = pos.symbol,
                                    entryPrice = pos.entryPrice,
                                    exitPrice = exitPrice,
                                    quantity = pos.quantity,
                                    marginAllocated = pos.margin,
                                    pnlUsdt = net,
                                    roiPercent = roi,
                                    isWin = net > 0
                                )
                            )
                        )
                    )
                    openPositions.remove(pos)
                }
            }

            // Ghi nhận Drawdown danh mục
            var unrealizedPnL = 0.0
            for (pos in openPositions) {
                val candleToday = klinesMap[pos.symbol]?.find { it.openTime == currentTime }
                if (candleToday != null) {
                    unrealizedPnL += (candleToday.close - pos.entryPrice) * pos.quantity
                }
            }
            val tempEquity = currentEquity + unrealizedPnL
            val dd = if (peakEquity > 0) ((peakEquity - tempEquity) / peakEquity) * 100.0 else 0.0
            if (dd > maxDrawdown) maxDrawdown = dd

            // BƯỚC 2: BỘ LỌC MACRO BTC REGIME V2.2 (EMA20/50 + Slope + Circuit Breaker)
            var isBtcSafeToTrade = true

            if (config.enableBtcMacroFilter) {
                val btcCloses = btcCandles.subList(0, dayIdx).map { it.close }
                val emaFastList = calculateEMA(btcCloses, config.btcEmaFastPeriod)
                val emaSlowList = calculateEMA(btcCloses, config.btcEmaSlowPeriod)

                if (emaFastList.size >= 2 && emaSlowList.isNotEmpty()) {
                    val currentBtcClose = btcCloses.last()
                    val currentEmaFast = emaFastList.last()
                    val prevEmaFast = emaFastList[emaFastList.size - 2]
                    val currentEmaSlow = emaSlowList.last()

                    val isAboveEmas = currentBtcClose > currentEmaFast && currentBtcClose > currentEmaSlow
                    val isEmaSlopePositive = (currentEmaFast - prevEmaFast) >= 0.0

                    isBtcSafeToTrade = isAboveEmas && isEmaSlopePositive
                } else {
                    isBtcSafeToTrade = false
                }
            }

            if (config.enableBtcCircuitBreaker && isBtcSafeToTrade) {
                val btcChangePct = if (currentBtc.open > 0) ((currentBtc.close - currentBtc.open) / currentBtc.open) * 100.0 else 0.0
                if (btcChangePct <= config.btcCircuitBreakerPct) {
                    isBtcSafeToTrade = false
                }
            }

            // BƯỚC 3: MỞ THÊM VỊ THẾ TỪ HẠN MỨC TRỐNG (XOAY VÒNG VỐN CUỐN CHIẾU, TRẦN 60%)
            val currentAllocatedMargin = openPositions.sumOf { it.margin }
            val maxExposure = currentEquity * config.maxPortfolioAllocation // 60% Equity
            val remainingBudget = maxExposure - currentAllocatedMargin

            val tradeCapital = if (config.useFixedCapitalPerTrade && config.capitalPerTradeUsdt > 0.0) {
                config.capitalPerTradeUsdt
            } else {
                currentEquity * config.perTradePct
            }

            if (remainingBudget >= tradeCapital && isBtcSafeToTrade) {
                val candidates = mutableListOf<ScoredCoin>()
                val runningSymbols = openPositions.map { it.symbol }.toSet()

                for ((sym, candles) in klinesMap) {
                    if (sym in runningSymbols) continue // LỌC TRÙNG VỊ THẾ ĐANG MỞ
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

                    // Bộ lọc cứng: QuoteVol >= 5M, Close > SMA20, Vol >= 2.0x, Change in [2%, 17.5%], ATR <= 12%
                    if (k1.close > sma20 &&
                        rvol >= config.minVolRatio &&
                        changePct in config.minD1ChangePct..config.maxD1ChangePct &&
                        atrPct <= config.maxAtrPercent) {
                        val score = rvol * (1.0 + ((k1.close - k1.open) / maxOf(1e-8, k1.high - k1.low)))
                        candidates.add(ScoredCoin(sym, score, candles[cIdx].open, rvol, changePct, atrPct))
                    }
                }

                if (candidates.isNotEmpty()) {
                    val sorted = candidates.sortedByDescending { it.score }
                    val slotsAvailable = (remainingBudget / tradeCapital).toInt().coerceAtLeast(1)
                    val chosen = sorted.take(slotsAvailable)

                    for (coin in chosen) {
                        val entryPrice = maxOf(1e-8, coin.entryPrice)
                        openPositions.add(
                            SinglePositionSimulation(
                                symbol = coin.symbol,
                                entryTime = currentTime,
                                entryPrice = entryPrice,
                                margin = tradeCapital,
                                quantity = tradeCapital / entryPrice,
                                atrPercent = coin.atrPercent,
                                openDayIdx = dayIdx,
                                slotId = BasketSlotId.PRIMARY
                            )
                        )
                    }
                }
            }
            equityCurve.add(Pair(currentTime, currentEquity))
        }

        // Tất toán các vị thế còn đang mở tại nến cuối cùng (Mark-to-Market)
        val finalTime = btcCandles.last().openTime
        for (pos in openPositions.toList()) {
            val candle = klinesMap[pos.symbol]?.find { it.openTime == finalTime }
            val exitPrice = candle?.close ?: pos.entryPrice
            val gross = (exitPrice - pos.entryPrice) * pos.quantity
            val fee = pos.margin * 0.0008
            val net = gross - fee
            val roi = if (pos.margin > 0.0) (net / pos.margin) * 100.0 else 0.0
            val holdingDays = maxOf(1, ((finalTime - pos.entryTime) / 86400000.0).toInt())

            currentEquity += net
            peakEquity = maxOf(peakEquity, currentEquity)

            val record = SingleTradeRecord(
                symbol = pos.symbol,
                entryPrice = pos.entryPrice,
                exitPrice = exitPrice,
                margin = pos.margin,
                grossPnl = gross,
                fee = fee,
                netPnl = net,
                roiPercent = roi,
                openDayIdx = pos.openDayIdx,
                closeDayIdx = btcCandles.size - 1,
                holdingDays = holdingDays,
                exitReason = "OPEN_AT_END"
            )
            singleTradesHistory.add(record)

            cyclesHistory.add(
                BasketCycleResult(
                    cycleIndex = ++tradeCounter,
                    slotId = pos.slotId,
                    openTime = pos.entryTime,
                    closeTime = finalTime,
                    holdingDays = holdingDays.toDouble(),
                    totalInvested = pos.margin,
                    realizedProfitUsdt = net,
                    basketRoiPercent = roi,
                    equityAfterClose = currentEquity,
                    coinsCount = 1,
                    symbolsList = listOf(pos.symbol),
                    exitReason = "OPEN_AT_END",
                    positionDetails = listOf(
                        BasketPositionDetail(
                            symbol = pos.symbol,
                            entryPrice = pos.entryPrice,
                            exitPrice = exitPrice,
                            quantity = pos.quantity,
                            marginAllocated = pos.margin,
                            pnlUsdt = net,
                            roiPercent = roi,
                            isWin = net > 0
                        )
                    )
                )
            )
        }
        openPositions.clear()

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
            equityCurve = equityCurve,
            singleTrades = singleTradesHistory.reversed()
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