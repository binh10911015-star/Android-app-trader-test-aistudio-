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

    fun isExcludedPerpetualSymbol(symbol: String): Boolean {
        val upper = symbol.uppercase().trim()
        if (!upper.endsWith("USDT")) return true
        val base = upper.removeSuffix("USDT")
        if (base == "BTC" || base.startsWith("BTC")) return true
        val stablecoins = setOf(
            "USDC", "FDUSD", "TUSD", "USDP", "DAI", "USDE",
            "USD1", "BUSD", "RLUSD", "PYUSD", "XUSD", "U"
        )
        if (base in stablecoins) return true
        return false
    }

    suspend fun closeAllOpenPositionsForMigration(reason: String = "STRATEGY_MIGRATION"): List<TradePosition> = withContext(Dispatchers.IO) {
        val openPositions = tradeDao.getOpenPositionsList()
        val now = System.currentTimeMillis()
        val closedList = mutableListOf<TradePosition>()
        for (pos in openPositions) {
            val exitP = if (pos.currentPrice > 0.0) pos.currentPrice else pos.entryPrice
            val pnl = (exitP - pos.entryPrice) * pos.amount
            val roi = if (pos.entryPrice > 0.0) ((exitP - pos.entryPrice) / pos.entryPrice) * 100.0 else 0.0
            val closed = pos.copy(
                status = TradePosition.STATUS_CLOSED,
                exitPrice = exitP,
                exitTime = now,
                pnlUsdt = pnl,
                pnlPercent = roi,
                closeReason = reason
            )
            tradeDao.update(closed)
            closedList.add(closed)
        }
        closedList
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
        basketSlot: String = "PRIMARY",
        atr14D1: Double = 0.0,
        signalLowD1: Double = 0.0,
        customStopLoss: Double? = null,
        customTakeProfit: Double? = null
    ): TradeAlert? = withContext(Dispatchers.IO) {
        val effectiveEntryPrice = if (exactEntryPrice != null && exactEntryPrice > 0.0) exactEntryPrice else currentPrice
        if (effectiveEntryPrice <= 0.0) return@withContext null

        if (tradeDao.countOpenForSymbol(symbol) > 0) return@withContext null

        val invested = investedUsdt ?: settings.calculatePositionVolume()
        val amount = exactAmount ?: (invested / effectiveEntryPrice)

        // Tính SL cố định = Low(t-1), 1R = entry - SL, TP = entry + 3R (RR 1:3)
        val slPrice = customStopLoss ?: if (signalLowD1 > 0.0) signalLowD1 else null
        val rVal = if (slPrice != null && effectiveEntryPrice > slPrice) effectiveEntryPrice - slPrice else 0.0
        val tpPrice = customTakeProfit ?: if (rVal > 0.0) effectiveEntryPrice + (3.0 * rVal) else null

        val position = TradePosition(
            symbol = symbol,
            entryPrice = effectiveEntryPrice,
            currentPrice = currentPrice,
            amount = amount,
            investedUsdt = invested,
            takeProfitPrice = tpPrice,
            stopLossPrice = slPrice,
            entryTime = System.currentTimeMillis(),
            status = TradePosition.STATUS_OPEN,
            timeframe = settings.timeframe,
            binanceOrderId = binanceOrderId,
            binanceClientOrderId = binanceClientOrderId,
            isLiveBinanceOrder = isLiveBinanceOrder,
            binanceStatus = binanceStatus ?: if (isLiveBinanceOrder) "FILLED" else null,
            binanceUpdateTime = if (isLiveBinanceOrder) System.currentTimeMillis() else null,
            basketSlot = basketSlot,
            atr14D1 = atr14D1,
            highestHighPrice = effectiveEntryPrice,
            trailingStopPrice = 0.0,
            isTrailingActive = false,
            debounceCounter = 0,
            signalLowD1 = signalLowD1,
            riskR = rVal
        )

        tradeDao.insert(position)

        val priceStr = formatPrice(effectiveEntryPrice)
        val tpStr = if (tpPrice != null) "$${formatPrice(tpPrice)} (RR 1:3)" else "Chưa đặt"
        val slStr = if (slPrice != null) "$${formatPrice(slPrice)}" else "Chưa đặt"

        val volStr = if (invested % 1.0 == 0.0) "${invested.toInt()} USDT" else "${String.format(Locale.US, "%.1f", invested)} USDT"
        val fixPrefix = if (!autoFixNote.isNullOrBlank()) "[$autoFixNote] " else ""

        val title = if (isLiveBinanceOrder) {
            if (isAutoTriggered) "TỰ ĐỘNG KHỚP LỆNH BINANCE" else "ĐÃ KHỚP LỆNH BINANCE"
        } else {
            if (isAutoTriggered) "TỰ ĐỘNG VÀO LỆNH LONG" else "MỞ LỆNH MUA"
        }

        val message = if (isLiveBinanceOrder) {
            "${fixPrefix}Lệnh #$binanceOrderId đã khớp trực tiếp trên Binance! Giá: $$priceStr | SL: $slStr | TP: $tpStr | Vốn: $volStr"
        } else {
            "Đã mở Long $symbol tại $$priceStr | SL: $slStr | TP: $tpStr (Vốn: $volStr)"
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

    suspend fun updatePosition(position: TradePosition) = withContext(Dispatchers.IO) {
        tradeDao.update(position)
    }

    suspend fun recoverOpenPositionsChandelierState(): List<TradePosition> = withContext(Dispatchers.IO) {
        val openPositions = tradeDao.getOpenPositionsList()
        if (openPositions.isEmpty()) return@withContext emptyList()

        val recoveredList = mutableListOf<TradePosition>()

        for (pos in openPositions) {
            try {
                // 1. Fallback nếu atr14D1 chưa được lưu (vị thế cũ): tính từ nến D1 lịch sử
                var atrVal = pos.atr14D1
                if (atrVal <= 0.0) {
                    val d1Candles = BinanceClient.fetchKlinesWithFallback(pos.symbol, "1d", 25)
                    if (d1Candles.size >= 14) {
                        val trList = d1Candles.takeLast(14).map { 
                            maxOf(it.high - it.low, Math.abs(it.high - it.close), Math.abs(it.low - it.close)) 
                        }
                        atrVal = trList.average()
                    }
                    if (atrVal <= 0.0) {
                        atrVal = pos.entryPrice * 0.05 // 5% fallback nếu không tải được nến D1
                    }
                }

                // 2. Gọi API Klines nến 1H từ mốc entryTime
                val klines1h = BinanceClient.fetchKlinesWithFallback(
                    symbol = pos.symbol,
                    interval = "1h",
                    limit = 500,
                    startTime = pos.entryTime
                )

                // 3. Dựng lại đỉnh cao nhất từ lịch sử
                val maxHistoricalHigh = klines1h.maxOfOrNull { it.high } ?: pos.entryPrice
                val newHighest = maxOf(pos.highestHighPrice, maxHistoricalHigh, pos.entryPrice)

                // 4. Cập nhật lại trạng thái Trailing & Mức chặn lãi (Chandelier Stop)
                val activationPrice = pos.entryPrice + (1.5 * atrVal)
                val isTrailing = pos.isTrailingActive || (newHighest >= activationPrice)

                val finalTrailingStop = if (isTrailing) {
                    val floorLockPrice = pos.entryPrice * 1.005
                    val chandelierStop = newHighest - (2.0 * atrVal)
                    val calculatedStop = maxOf(floorLockPrice, chandelierStop)
                    maxOf(pos.trailingStopPrice, calculatedStop) // Ratchet: chỉ tiến lên theo đỉnh
                } else {
                    pos.trailingStopPrice
                }

                val recoveredPos = pos.copy(
                    atr14D1 = atrVal,
                    highestHighPrice = newHighest,
                    isTrailingActive = isTrailing,
                    trailingStopPrice = finalTrailingStop,
                    debounceCounter = 0
                )

                tradeDao.update(recoveredPos)
                recoveredList.add(recoveredPos)
            } catch (_: Exception) {
                // Giữ nguyên đỉnh cao nhất hiện có và tiếp tục chạy
                recoveredList.add(pos)
            }
        }
        recoveredList
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
        volumeThreshold: Double = 1.0
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

        var i = 21
        while (i < candles.size) {
            val k1 = candles[i - 1] // nến t-1
            val k0 = candles[i]     // nến t (entry)
            val prev20 = candles.subList(i - 21, i - 1)
            val sma20 = prev20.map { it.close }.average()

            // 1. Nến t-1 là nến tăng: close[t-1] > open[t-1]
            // 2. Giá đóng cửa trên SMA20: close[t-1] > SMA20[t-1]
            // 3. SMA20 nằm trong range nến t-1: low[t-1] <= SMA20[t-1]
            val cond1 = k1.close > k1.open
            val cond2 = k1.close > sma20
            val cond3 = k1.low <= sma20
            val entryPrice = k0.open
            val sl = k1.low
            val riskR = entryPrice - sl

            if (cond1 && cond2 && cond3 && riskR > 0.0) {
                val tp = entryPrice + 3.0 * riskR
                val investedAmount = 5.1 // 5.1 USD danh nghĩa mỗi lệnh
                val coinQty = investedAmount / entryPrice
                val entryTime = k0.openTime

                var exitPrice = entryPrice
                var exitTime = entryTime
                var exitReason = "HẾT DỮ LIỆU"
                var closed = false

                for (j in i until candles.size) {
                    val futureCandle = candles[j]
                    // Giả định bất lợi: Chạm cả SL và TP trong cùng ngày -> tính là SL
                    if (futureCandle.low <= sl && futureCandle.high >= tp) {
                        exitPrice = sl
                        exitTime = futureCandle.openTime
                        exitReason = "SL (Bất lợi cùng ngày)"
                        closed = true
                        i = j
                        break
                    }
                    if (futureCandle.low <= sl) {
                        exitPrice = minOf(futureCandle.open, sl)
                        exitTime = futureCandle.openTime
                        exitReason = "SL (-1R)"
                        closed = true
                        i = j
                        break
                    }
                    if (futureCandle.high >= tp) {
                        exitPrice = maxOf(futureCandle.open, tp)
                        exitTime = futureCandle.openTime
                        exitReason = "TP (+3R)"
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

                val grossPnl = (exitPrice - entryPrice) * coinQty
                val fee = investedAmount * 0.0010 // 0.05% mỗi chiều = 0.10% tổng phí taker
                val pnl = grossPnl - fee
                val roi = (pnl / investedAmount) * 100.0
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
        val validPerps = BinanceClient.refreshValidPerpetualSymbols()
        val tickers = BinanceClient.fetchTickersWithFallback()
        val symbolsSet = mutableSetOf<String>()
        symbolsSet.addAll(validPerps)
        symbolsSet.addAll(tickers.map { it.symbol })

        val allPerpSymbols = symbolsSet.filter { sym ->
            sym.endsWith("USDT") && !sym.contains("_") && !isExcludedPerpetualSymbol(sym)
        }.sorted()

        return if (allPerpSymbols.isNotEmpty()) allPerpSymbols else tickers.map { it.symbol }.filter { it.endsWith("USDT") && !it.contains("_") && !isExcludedPerpetualSymbol(it) }
    }

    data class CandidateEvaluation(
        val isQualified: Boolean,
        val score: Double,
        val volumeRatio: Double,
        val deltaPrice: Double,
        val atrPercent: Double,
        val clv: Double,
        val sign: Double
    )

    fun evaluateD1MomentumCandidate(
        candles: List<Candle>,
        quoteVolume24h: Double = 0.0
    ): CandidateEvaluation {
        if (candles.size < 22) {
            return CandidateEvaluation(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }
        if (quoteVolume24h > 0 && quoteVolume24h < 5_000_000.0) {
            return CandidateEvaluation(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val k1 = candles[candles.size - 1] // t-1 (nến D1 hôm qua)
        val k2 = candles[candles.size - 2] // t-2
        val prev20 = candles.takeLast(20) // 20 nến D1 từ (t-20) đến (t-1)
        val sma20Volume = prev20.map { it.volume }.average()
        val sma20Close = prev20.map { it.close }.average()

        val deltaPrice = if (k1.open > 0) ((k1.close - k1.open) / k1.open) * 100.0 else 0.0

        val trList = candles.takeLast(14).map { maxOf(it.high - it.low, Math.abs(it.high - it.close), Math.abs(it.low - it.close)) }
        val atr14 = trList.average()
        val atrPercent = if (k1.close > 0) atr14 / k1.close else 0.05
        val atrPercent100 = atrPercent * 100.0

        val epsilon = 1e-6
        val volumeRatio = k1.volume / (sma20Volume + epsilon)
        val clv = ((k1.close - k1.low) / (k1.high - k1.low + epsilon)).coerceIn(0.0, 1.0)
        val sign = if (k1.close > k1.open && k1.close > k2.close) 1.0 else 0.0
        val score = volumeRatio * clv * sign

        val isTrendUp = k1.close > sma20Close
        val isVolSpike = sma20Volume > 0 && k1.volume >= 2.0 * sma20Volume
        val isDeltaOk = deltaPrice in 2.0..17.5
        val isAtrOk = atrPercent100 <= 12.0

        val isQualified = isTrendUp && isVolSpike && isDeltaOk && isAtrOk && score > 0.0
        return CandidateEvaluation(isQualified, score, volumeRatio, deltaPrice, atrPercent, clv, sign)
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
        val stopLoss: Double,
        val takeProfit: Double,
        val openDayIdx: Int,
        val slotId: BasketSlotId = BasketSlotId.PRIMARY,
        val atrPercent: Double = 0.05
    )

    suspend fun runBasketD1MomentumBacktest(
        daysToTest: Int = 180,
        config: BacktestConfig = BacktestConfig(),
        symbolsPool: List<String> = emptyList()
    ): BasketBacktestReport = withContext(Dispatchers.IO) {
        // 1. Lấy danh sách altcoin USDT-M từ Binance Mainnet
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

        val btcCandles = klinesMap["BTCUSDT"] ?: BinanceClient.fetchKlinesWithFallback("BTCUSDT", interval = "1d", limit = daysToTest + 30)
        if (btcCandles.size < 25) {
            return@withContext BasketBacktestReport(errorMessage = "Thiếu dữ liệu nến D1 để thực hiện mô phỏng.")
        }

        var currentEquity = config.initialCapital
        var peakEquity = currentEquity
        var maxDrawdown = 0.0

        val openPositions = mutableListOf<SinglePositionSimulation>()
        val singleTradesHistory = mutableListOf<SingleTradeRecord>()
        val cyclesHistory = mutableListOf<BasketCycleResult>()
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        var tradeCounter = 0

        val startIndex = 25
        equityCurve.add(Pair(btcCandles[startIndex].openTime, currentEquity))

        for (dayIdx in startIndex until btcCandles.size) {
            val currentTime = btcCandles[dayIdx].openTime

            // BƯỚC 1: KIỂM TRA THOÁT LỆNH CỐ ĐỊNH SL (low t-1) HOẶC TP (3R)
            val openPositionsCopy = openPositions.toList()
            for (pos in openPositionsCopy) {
                val candleToday = klinesMap[pos.symbol]?.find { it.openTime == currentTime } ?: continue
                val holdingDays = maxOf(1, ((currentTime - pos.entryTime) / 86400000.0).toInt())
                var exitReason: String? = null
                var exitPrice = candleToday.close

                // Quy tắc thận trọng: Nếu trong ngày cùng chạm cả TP và SL -> bắt buộc ghi nhận lệnh đó là SL!
                if (candleToday.low <= pos.stopLoss && candleToday.high >= pos.takeProfit) {
                    exitReason = "SL (Cùng chạm TP & SL)"
                    exitPrice = pos.stopLoss
                } else if (candleToday.low <= pos.stopLoss) {
                    exitReason = "SL (-1R)"
                    exitPrice = minOf(candleToday.open, pos.stopLoss)
                } else if (candleToday.high >= pos.takeProfit) {
                    exitReason = "TP (+3R)"
                    exitPrice = maxOf(candleToday.open, pos.takeProfit)
                }

                if (exitReason != null) {
                    val gross = (exitPrice - pos.entryPrice) * pos.quantity
                    val takerFee = pos.margin * 0.0010 // Phí taker 0.05% mỗi chiều = 0.10% tổng
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

            // BƯỚC 2: QUÉT TÍN HIỆU VÀO LỆNH MỚI CHO TỪNG COIN THỎA ĐIỀU KIỆN SMA20 D1 (KHÔNG LỌC BTC)
            val tradeCapital = if (config.useFixedCapitalPerTrade && config.capitalPerTradeUsdt > 0.0) {
                config.capitalPerTradeUsdt
            } else {
                5.1
            }

            val runningSymbols = openPositions.map { it.symbol }.toSet()
            val candidatesToday = mutableListOf<SinglePositionSimulation>()
            val candidatesMeta = mutableMapOf<String, Double>()

            for ((sym, candles) in klinesMap) {
                if (sym in runningSymbols) continue // Không mở trùng coin đang có vị thế
                if (isExcludedPerpetualSymbol(sym)) continue // Loại trừ BTC và stablecoins

                val cIdx = candles.indexOfFirst { it.openTime == currentTime }
                if (cIdx < 21) continue

                val k1 = candles[cIdx - 1] // nến D1 t-1 vừa đóng lúc 00:00 UTC
                val k0 = candles[cIdx]     // nến D1 t hiện tại
                val prev20 = candles.subList(cIdx - 21, cIdx - 1)
                val sma20 = prev20.map { it.close }.average()

                // Quy tắc chiến lược SMA20 D1 (RR 1:3):
                // 1. Nến t-1 là nến tăng: close[t-1] > open[t-1]
                // 2. Giá đóng cửa trên SMA20: close[t-1] > SMA20[t-1]
                // 3. SMA20 nằm trong range nến t-1: low[t-1] <= SMA20[t-1]
                val cond1 = k1.close > k1.open
                val cond2 = k1.close > sma20
                val cond3 = k1.low <= sma20
                val entryPrice = maxOf(1e-8, k0.open)
                val sl = k1.low
                val riskR = entryPrice - sl

                if (cond1 && cond2 && cond3 && riskR > 0.0) {
                    val tp = entryPrice + (3.0 * riskR)
                    // % thay đổi nến t-1 từ Open đến Close
                    val changePctT1 = ((k1.close - k1.open) / k1.open) * 100.0
                    candidatesMeta[sym] = changePctT1
                    candidatesToday.add(
                        SinglePositionSimulation(
                            symbol = sym,
                            entryTime = currentTime,
                            entryPrice = entryPrice,
                            margin = tradeCapital,
                            quantity = tradeCapital / entryPrice,
                            stopLoss = sl,
                            takeProfit = tp,
                            openDayIdx = dayIdx,
                            slotId = BasketSlotId.PRIMARY
                        )
                    )
                }
            }

            // Quy tắc ưu tiên: coin nào có % thay đổi nến t-1 nhỏ nhất vào trước
            candidatesToday.sortBy { candidatesMeta[it.symbol] ?: Double.MAX_VALUE }
            openPositions.addAll(candidatesToday)

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