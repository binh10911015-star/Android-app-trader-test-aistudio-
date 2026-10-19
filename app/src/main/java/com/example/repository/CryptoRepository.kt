package com.example.repository

import com.example.data.TradeDao
import com.example.indicators.TechnicalAnalysis
import com.example.model.AlertType
import com.example.model.BinanceTicker
import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.model.PeriodPnL
import com.example.model.PositionUpdateResult
import com.example.model.TradeAlert
import com.example.model.TradePosition
import com.example.model.TradeSettings
import com.example.network.BinanceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CryptoRepository(private val tradeDao: TradeDao) {

    // Thread-safe in-memory cache of scanned signal status for coins: symbol -> isBullish
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
                signalCache[symbol] = lastItem.isMaAboveSuperTrend
            }
            Result.success(indicatorData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedSignal(symbol: String): Boolean? {
        return signalCache[symbol]
    }

    fun setCachedSignal(symbol: String, isBullish: Boolean) {
        signalCache[symbol] = isBullish
    }

    fun getBullishSignalsCount(): Int {
        return signalCache.values.count { it }
    }

    fun getAllBullishSymbols(): Set<String> {
        return signalCache.filterValues { it }.keys.toSet()
    }

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

        if (tradeDao.countOpenForSymbol(symbol) > 0) {
            return@withContext null
        }

        val invested = investedUsdt ?: settings.calculatePositionVolume()
        val amount = exactAmount ?: (invested / effectiveEntryPrice)
        val tpPrice = settings.takeProfitPercent?.let { effectiveEntryPrice * (1.0 + it / 100.0) }
        val slPrice = settings.stopLossPercent?.let { effectiveEntryPrice * (1.0 - it / 100.0) }

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
            binanceUpdateTime = if (isLiveBinanceOrder) System.currentTimeMillis() else null
        )

        tradeDao.insert(position)

        val priceStr = formatPrice(effectiveEntryPrice)
        val tpStr = if (tpPrice != null && settings.takeProfitPercent != null) {
            "$$${formatPrice(tpPrice)} (+${settings.takeProfitPercent}%)"
        } else {
            "Không thiết lập"
        }
        val slStr = if (slPrice != null && settings.stopLossPercent != null) {
            "$$${formatPrice(slPrice)} (-${settings.stopLossPercent}%)"
        } else {
            "Không thiết lập"
        }

        val volStr = if (invested % 1.0 == 0.0) "${invested.toInt()} USDT" else "${String.format(Locale.US, "%.1f", invested)} USDT"
        val fixPrefix = if (!autoFixNote.isNullOrBlank()) "[$autoFixNote] " else ""

        val title = if (isLiveBinanceOrder) {
            if (isAutoTriggered) "TỰ ĐỘNG KHỚP LỆNH BINANCE DEMO" else "ĐÃ KHỚP LỆNH BINANCE DEMO"
        } else {
            if (isAutoTriggered) "TỰ ĐỘNG VÀO LỆNH LONG (MÔ PHỎNG)" else "MỞ LỆNH MUA MÔ PHỎNG"
        }

        val message = if (isLiveBinanceOrder) {
            "${fixPrefix}Lệnh #$binanceOrderId đã khớp trực tiếp trên Binance Demo! Giá khớp: $$priceStr, KL: ${String.format(Locale.US, "%.4f", amount)} (Vốn: $volStr). TP: $tpStr"
        } else {
            "Đã mua $symbol tại $$priceStr (Vốn: $volStr - 0.5% số dư). TP: $tpStr, SL: $slStr"
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
            message = "Đã đóng ${pos.symbol} tại $${formatPrice(exitPrice)}. PnL: $sign$${String.format(Locale.US, "%.2f", pnlUsdt)} ($sign$${String.format(Locale.US, "%.2f", pnlPercent)}%)",
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

    /**
     * Đồng bộ trực tiếp danh sách lệnh đang mở trên Binance Demo với Room database của app,
     * đảm bảo không có bất kỳ sự sai lệch nào.
     */
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