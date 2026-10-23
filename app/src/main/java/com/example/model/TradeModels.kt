package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long
)

data class BinanceTicker(
    val symbol: String,
    val lastPrice: Double = 0.0,
    val priceChangePercent: Double = 0.0,
    val volume: Double = 0.0,
    val highPrice: Double = 0.0,
    val lowPrice: Double = 0.0,
    val sma20OpenD1: Double = lastPrice * 0.99,
    val volumeRatioD1: Double = 1.0
)

data class SuperTrendPoint(
    val value: Double = 0.0,
    val isBullish: Boolean = true,
    val direction: String = if (isBullish) "UP" else "DOWN"
)

data class IndicatorData(
    val candle: Candle,
    val ma20: Double? = null,
    val superTrend: SuperTrendPoint? = null,
    val isMaAboveSuperTrend: Boolean = false,
    val isBullishSignal: Boolean = false,
    val sma20OpenD1: Double? = null,
    val volumeRatioD1: Double = 1.0,
    val isD1BreakoutBuy: Boolean = false
) {
    val isPriceAboveSma20: Boolean
        get() = (ma20 != null && candle.close >= ma20)

    fun isBullishSignal(useSuperTrend: Boolean = false): Boolean {
        return isPriceAboveSma20
    }
}

data class BinanceApiConfig(
    val apiKey: String = "",
    val apiSecret: String = "",
    val accessPin: String = "",
    val isTestnet: Boolean = true,
    val liveTradingEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyType: String = "HTTP",
    val lastErrorMessage: String = "",
    val totalWalletBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val isConfigured: Boolean = false,
    val isProxyEnabled: Boolean = false,
    val isHedgeMode: Boolean = false
) {
    val hasAccessPin: Boolean
        get() = accessPin.isNotBlank()

    fun verifyPin(entered: String): Boolean {
        if (!hasAccessPin) return true
        return accessPin == entered.trim()
    }

    fun resolvePositionSideParam(side: String): String {
        return if (isHedgeMode) side.uppercase() else "BOTH"
    }
}

data class BinanceAccountVerificationResult(
    val isSuccess: Boolean = false,
    val totalWalletBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val errorMessage: String = "",
    val isConnected: Boolean = isSuccess,
    val message: String = errorMessage,
    val canTrade: Boolean = false,
    val canDeposit: Boolean = false,
    val canWithdraw: Boolean = false,
    val openPositionsCount: Int = 0
)

@Entity(tableName = "trade_positions")
data class TradePosition(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val entryPrice: Double,
    val currentPrice: Double,
    val amount: Double,
    val investedUsdt: Double = 100.0,
    val takeProfitPrice: Double? = null,
    val stopLossPrice: Double? = null,
    val entryTime: Long = System.currentTimeMillis(),
    val status: String = STATUS_OPEN,
    val exitPrice: Double? = null,
    val exitTime: Long? = null,
    val pnlUsdt: Double? = null,
    val pnlPercent: Double? = null,
    val closeReason: String? = null,
    val timeframe: String = "4h",
    val binanceOrderId: Long? = null,
    val binanceClientOrderId: String? = null,
    val isLiveBinanceOrder: Boolean = false,
    val binanceStatus: String? = null,
    val binanceUpdateTime: Long? = null,
    val positionSide: String = "LONG",
    val liveUnrealizedProfit: Double? = null,
    val leverage: Int = 1,
    val markPrice: Double = currentPrice,
    val tpOrderId: Long? = null,
    val slOrderId: Long? = null
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"
        const val REASON_TAKE_PROFIT = "TAKE_PROFIT"
        const val REASON_STOP_LOSS = "STOP_LOSS"
        const val REASON_MANUAL = "MANUAL"
        const val REASON_BINANCE_SYNC = "BINANCE_SYNC"
    }

    private val effectivePrice: Double
        get() = if (markPrice > 0.0) markPrice else currentPrice

    val unrealizedPnlUsdt: Double
        get() = liveUnrealizedProfit ?: if (positionSide.uppercase() == "SHORT") {
            (entryPrice - effectivePrice) * kotlin.math.abs(amount)
        } else {
            (effectivePrice - entryPrice) * kotlin.math.abs(amount)
        }

    val unrealizedPnlPercent: Double
        get() {
            val margin = if (investedUsdt > 0.0) investedUsdt else (entryPrice * kotlin.math.abs(amount) / leverage.coerceAtLeast(1))
            if (margin > 0.0) {
                return (unrealizedPnlUsdt / margin) * 100.0
            }
            if (entryPrice > 0.0) {
                return if (positionSide.uppercase() == "SHORT") {
                    ((entryPrice - effectivePrice) / entryPrice) * 100.0 * leverage
                } else {
                    ((effectivePrice - entryPrice) / entryPrice) * 100.0 * leverage
                }
            }
            return 0.0
        }

    val roiFormatted: String
        get() {
            val sign = if (unrealizedPnlPercent >= 0) "+" else ""
            return "$sign${String.format(Locale.US, "%.2f", unrealizedPnlPercent)}%"
        }

    val isBinanceVerified: Boolean
        get() = isLiveBinanceOrder && binanceOrderId != null && binanceOrderId > 0
}

data class BinanceLivePosition(
    val symbol: String,
    val initialMargin: Double = 0.0,
    val maintMargin: Double = 0.0,
    val unrealizedProfit: Double = 0.0,
    val positionInitialMargin: Double = 0.0,
    val openOrderInitialMargin: Double = 0.0,
    val leverage: Int = 1,
    val isolated: Boolean = false,
    val entryPrice: Double = 0.0,
    val markPrice: Double = 0.0,
    val positionAmt: Double = 0.0,
    val liquidationPrice: Double = 0.0,
    val updateTime: Long = 0L,
    val takeProfitPrice: Double? = null,
    val stopLossPrice: Double? = null,
    val positionSide: String = "BOTH"
) {
    val isLong: Boolean get() = positionAmt > 0
    val isShort: Boolean get() = positionAmt < 0
    val absAmount: Double get() = kotlin.math.abs(positionAmt)
    val notional: Double get() = absAmount * markPrice
    val roiPercent: Double
        get() = if (initialMargin > 0) (unrealizedProfit / initialMargin) * 100.0 else 0.0
}

data class BinanceTpSlResult(
    val tpSuccess: Boolean = false,
    val slSuccess: Boolean = false,
    val tpOrderId: Long? = null,
    val slOrderId: Long? = null,
    val errorMessage: String? = null
)

data class BinanceOpenOrderItem(
    val orderId: Long,
    val symbol: String,
    val clientOrderId: String = "",
    val price: Double = 0.0,
    val origQty: Double = 0.0,
    val executedQty: Double = 0.0,
    val status: String = "NEW",
    val timeInForce: String = "GTC",
    val type: String = "LIMIT",
    val side: String = "BUY",
    val updateTime: Long = 0L,
    val avgPrice: Double = 0.0
)

data class BinanceAssetBalance(
    val asset: String,
    val walletBalance: Double = 0.0,
    val unrealizedProfit: Double = 0.0,
    val marginBalance: Double = 0.0,
    val maintMargin: Double = 0.0,
    val initialMargin: Double = 0.0,
    val availableBalance: Double = 0.0
)

data class BinancePortfolio(
    val totalWalletBalance: Double = 0.0,
    val totalMarginBalance: Double = 0.0,
    val totalUnrealizedProfit: Double = 0.0,
    val availableBalance: Double = 0.0,
    val assets: List<BinanceAssetBalance> = emptyList(),
    val positions: List<BinanceLivePosition> = emptyList(),
    val openOrders: List<BinanceOpenOrderItem> = emptyList(),
    val lastUpdatedTimestamp: Long = 0L,
    val isLiveConnected: Boolean = false,
    val error: String? = null
)

data class BinanceOrderVerificationDetail(
    val orderId: Long,
    val clientOrderId: String,
    val symbol: String,
    val status: String,
    val side: String,
    val type: String,
    val price: Double,
    val origQty: Double,
    val executedQty: Double,
    val avgPrice: Double,
    val time: Long,
    val updateTime: Long,
    val isVerified: Boolean = true,
    val message: String = ""
)

enum class RoiSortOrder {
    DEFAULT,
    DESCENDING,
    ASCENDING
}

enum class VolumeType {
    PERCENT_OF_BALANCE,
    FIXED_AMOUNT
}

enum class TradingStrategyType(val displayName: String, val description: String) {
    D1_SMA20_VOLUME(
        "Chiến lược D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)",
        "Nến D1, đường SMA20 tính theo Giá Mở Cửa (Open). Mua khi Giá >= SMA20(D1) và Volume D1 >= 2x ngày hôm trước."
    ),
    DUAL_BASKET_MOMENTUM_V2(
        "Chiến lược Dual Basket Momentum v2.0 (Khuyến nghị)",
        "Quản lý 2 giỏ lệnh Primary (07:00) & Secondary (19:00 UTC+7). Lọc BTC Regime + RVol >= 2.0x, ATR trailing take-profit, timeout 72h."
    )
}

data class TradeSettings(
    val autoTradeEnabled: Boolean = false,
    val selectedStrategy: TradingStrategyType = TradingStrategyType.DUAL_BASKET_MOMENTUM_V2,
    val volumeMultiplierThreshold: Double = 2.0,
    val portfolioTakeProfitPercent: Double = 4.0,
    val volumeType: VolumeType = VolumeType.PERCENT_OF_BALANCE,
    val volumePercentOfBalance: Double = 0.5,
    val fixedVolumeUsdt: Double = 50.0,
    val capitalPerTradeUsdt: Double = 50.0,
    val takeProfitPercent: Double? = 4.0,
    val stopLossPercent: Double? = null,
    val enableDualBasket: Boolean = true,
    val timeframe: String = "1d",
    val simulatedBalanceUsdt: Double = 10000.0,
    val leverage: Int = 1
) {
    fun calculatePositionVolume(customBalance: Double = simulatedBalanceUsdt): Double {
        return when (volumeType) {
            VolumeType.PERCENT_OF_BALANCE -> {
                val calculated = customBalance * (volumePercentOfBalance / 100.0)
                if (calculated > 0.0) calculated else 50.0
            }
            VolumeType.FIXED_AMOUNT -> {
                if (fixedVolumeUsdt > 0.0) fixedVolumeUsdt else 50.0
            }
        }
    }

    fun updateBalance(newBalance: Double): TradeSettings {
        val safeBal = if (newBalance < 0.0) 0.0 else newBalance
        val updatedVolume = when (volumeType) {
            VolumeType.PERCENT_OF_BALANCE -> safeBal * (volumePercentOfBalance / 100.0)
            VolumeType.FIXED_AMOUNT -> fixedVolumeUsdt
        }
        return copy(
            simulatedBalanceUsdt = safeBal,
            capitalPerTradeUsdt = if (updatedVolume > 0.0) updatedVolume else 50.0
        )
    }
}

data class PositionUpdateResult(
    val alerts: List<TradeAlert> = emptyList(),
    val closedPositions: List<TradePosition> = emptyList(),
    val totalReturnedCapital: Double = 0.0
)

data class TradeAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbol: String,
    val title: String,
    val message: String,
    val type: AlertType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AlertType {
    BULLISH_SIGNAL,
    AUTO_BUY_ORDER,
    TAKE_PROFIT,
    STOP_LOSS,
    MANUAL_CLOSE,
    INFO
}

data class PeriodPnL(
    val periodLabel: String,
    val timestamp: Long,
    val pnlUsdt: Double,
    val tradesCount: Int,
    val winCount: Int
)

data class BinanceOrderPlacementResult(
    val success: Boolean,
    val orderId: Long? = null,
    val clientOrderId: String? = null,
    val status: String? = null,
    val executedQty: Double = 0.0,
    val avgPrice: Double = 0.0,
    val autoFixApplied: String? = null,
    val errorMessage: String? = null,
    val isVerifiedOnExchange: Boolean = false
)

data class SymbolExchangeFilter(
    val symbol: String,
    val stepSize: Double = 0.001,
    val minQty: Double = 0.001,
    val tickSize: Double = 0.01,
    val minNotional: Double = 5.0
)

// ==========================================
// DATA MODELS CHO BACKTEST & BASKET STRATEGY
// ==========================================

data class BacktestConfig(
    val initialCapital: Double = 10_000.0,
    val perTradePct: Double = 0.015,
    val maxPortfolioAllocation: Double = 0.60,
    val targetPortfolioRoi: Double = 0.015,
    val minQuoteVolume24h: Double = 20_000_000.0, // Chuẩn v2.0 >= 20M USDT
    val minVolRatio: Double = 2.0, // Ngưỡng nổ RVol(20) >= 2.0x
    val minD1ChangePct: Double = 2.0,
    val maxD1ChangePct: Double = 10.0, // Chuẩn v2.0 <= 10.0%
    val maxAtrPercent: Double = 0.12, // Trần biến động ATR% D1 <= 12.0%
    val btcPanicCircuitBreaker: Double = -3.5, // BTC Panic Breaker -3.5%
    val slotScanMode: String = "DUAL", // "DUAL", "PRIMARY_ONLY", "SECONDARY_ONLY"
    val kTp: Double = 0.50, // Hệ số kích hoạt TP K_TP = 0.50 (tối thiểu 1.5%)
    val kTrail: Double = 0.25, // Bước đệm Trailing K_Trail = 0.25 * ATR giỏ
    val kSl: Double = 0.75, // Hệ số Stop-Loss K_SL = 0.75 (sàn -3.5%) v2.1
    val maxHoldingHours: Int = 144, // Timeout 144 giờ (6 ngày) giải phóng vốn v2.1
    val enableTrailingStop: Boolean = true,
    val enableFloorLock: Boolean = true,
    val enableStopLoss: Boolean = true,
    val enableTimeout: Boolean = true
) {
    companion object {
        const val BASE_MAINNET_URL = "https://fapi.binance.com"
    }
}

data class BacktestHistoryRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val days: Int = 180,
    val config: BacktestConfig,
    val netProfitUsdt: Double,
    val netProfitPercent: Double,
    val winratePercent: Double,
    val maxDrawdownPercent: Double,
    val totalCycles: Int,
    val winCycles: Int,
    val lossCycles: Int,
    val trailingHits: Int,
    val slHits: Int,
    val timeoutHits: Int
)

data class BacktestTradeItem(
    val entryTime: Long,
    val exitTime: Long,
    val symbol: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlUsdt: Double,
    val roiPercent: Double,
    val isWin: Boolean,
    val exitReason: String
)

data class BacktestSummaryResult(
    val symbol: String,
    val timeframe: String,
    val totalTrades: Int = 0,
    val winTrades: Int = 0,
    val lossTrades: Int = 0,
    val winRatePercent: Double = 0.0,
    val netProfitUsdt: Double = 0.0,
    val totalRoiPercent: Double = 0.0,
    val maxDrawdownPercent: Double = 0.0,
    val profitFactor: Double = 0.0,
    val tradesHistory: List<BacktestTradeItem> = emptyList(),
    val errorMessage: String? = null
)

enum class BasketSlotId {
    PRIMARY,    // Giỏ quét chính lúc 07:00 AM (UTC+7 / 00:00 UTC)
    SECONDARY   // Giỏ quét phụ lúc 19:00 PM (UTC+7 / 12:00 UTC)
}

enum class BasketStatus {
    OPEN,               // Đang chạy, chưa kích hoạt Trailing
    TRAILING_ACTIVE,    // Đã vượt mốc kích hoạt cơ sở, đang bám đỉnh theo ATR
    CLOSING,            // Đang thanh lý lệnh trên sàn (khóa slot, cấm ghi đè)
    CLOSED              // Đã tất toán xong, giải phóng slot cho chu kỳ tiếp theo
}

data class BasketPosition(
    val symbol: String,
    val entryPrice: Double,
    val quantity: Double,
    val marginAllocated: Double,
    val entryAtrPercent: Double = 0.05 // ATR(14) D1 / entryPrice tại thời điểm vào lệnh
)

data class ActiveBasket(
    val slotId: BasketSlotId = BasketSlotId.PRIMARY,
    val openTime: Long = System.currentTimeMillis(),
    val positions: List<BasketPosition> = emptyList(),
    val totalInvested: Double = 0.0,
    var status: BasketStatus = BasketStatus.OPEN,
    val maxHoldingMillis: Long = 144 * 3600 * 1000L, // Timeout 144 giờ (6 ngày) v2.1
    val kTp: Double = 0.50,
    val kTrail: Double = 0.25,
    val kSl: Double = 0.75
) {
    // 1. Biến động trọng số toàn giỏ: Basket ATR%
    val basketAtrPercent: Double by lazy {
        if (totalInvested <= 0.0) 0.0
        else positions.sumOf { it.entryAtrPercent * (it.marginAllocated / totalInvested) }
    }

    // 2. Ngưỡng kích hoạt Trailing cơ sở: K_TP (Tối thiểu +1.5%)
    val activationRoi: Double by lazy {
        maxOf(0.015, kTp * basketAtrPercent)
    }

    // 3. Ngưỡng cắt lỗ danh mục ban đầu: K_SL = 0.75 (Sàn -3.5%) v2.1
    val initialStopLossRoi: Double by lazy {
        -maxOf(0.035, kSl * basketAtrPercent)
    }

    // 4. Bước đệm kéo lùi Trailing: K_Trail * ATR giỏ (Tối thiểu 0.6%)
    val trailStepRoi: Double by lazy {
        maxOf(0.006, kTrail * basketAtrPercent)
    }

    // Sàn bảo vệ lợi nhuận tối thiểu khi đã bật trailing (đủ bù trượt giá & phí giao dịch)
    val floorLockRoi: Double = 0.005 // +0.5% net

    // Biến trạng thái động theo dõi theo thời gian thực
    var peakRoi: Double = 0.0
    var currentTrailingStopRoi: Double = -1.0
}

data class ScoredCoin(
    val symbol: String,
    val score: Double,
    val entryPrice: Double,
    val volRatio: Double,
    val changePct: Double = 0.0,
    val atrPercent: Double = 0.05
)

data class BasketPositionDetail(
    val symbol: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val marginAllocated: Double,
    val pnlUsdt: Double,
    val roiPercent: Double,
    val isWin: Boolean
)

data class BasketCycleResult(
    val cycleIndex: Int,
    val slotId: BasketSlotId = BasketSlotId.PRIMARY,
    val openTime: Long,
    val closeTime: Long,
    val holdingDays: Double,
    val totalInvested: Double,
    val realizedProfitUsdt: Double,
    val basketRoiPercent: Double,
    val equityAfterClose: Double,
    val coinsCount: Int,
    val symbolsList: List<String>,
    val exitReason: String = "TRAILING_STOP_HIT",
    val positionDetails: List<BasketPositionDetail> = emptyList()
)

data class BasketBacktestReport(
    val totalCycles: Int = 0,
    val winratePercent: Double = 0.0,
    val averageHoldingDays: Double = 0.0,
    val maxDrawdownPercent: Double = 0.0,
    val initialCapital: Double = 10_000.0,
    val finalEquity: Double = 10_000.0,
    val netProfitUsdt: Double = 0.0,
    val netProfitPercent: Double = 0.0,
    val cycles: List<BasketCycleResult> = emptyList(),
    val equityCurve: List<Pair<Long, Double>> = emptyList(),
    val errorMessage: String? = null
)