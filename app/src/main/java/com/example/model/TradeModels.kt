package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val lowPrice: Double = 0.0
)

data class SuperTrendPoint(
    val value: Double = 0.0,
    val direction: String = "UP"
)

data class IndicatorData(
    val candle: Candle,
    val ma20: Double = 0.0,
    val superTrend: SuperTrendPoint? = null,
    val isMaAboveSuperTrend: Boolean = false,
    val isBullishSignal: Boolean = false
) {
    fun isBullishSignal(useSuperTrendFilter: Boolean = true): Boolean {
        return if (useSuperTrendFilter) isMaAboveSuperTrend else candle.close >= ma20
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
    val isProxyEnabled: Boolean = false
) {
    fun verifyPin(entered: String): Boolean {
        return accessPin.ifBlank { "1234" } == entered.trim()
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
    val status: String = STATUS_OPEN, // "OPEN", "CLOSED"
    val exitPrice: Double? = null,
    val exitTime: Long? = null,
    val pnlUsdt: Double? = null,
    val pnlPercent: Double? = null,
    val closeReason: String? = null, // "TAKE_PROFIT", "STOP_LOSS", "MANUAL"
    val timeframe: String = "4h",
    val binanceOrderId: Long? = null,
    val binanceClientOrderId: String? = null,
    val isLiveBinanceOrder: Boolean = false,
    val binanceStatus: String? = null, // "NEW", "FILLED", "PARTIALLY_FILLED", "CANCELED"
    val binanceUpdateTime: Long? = null
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_CLOSED = "CLOSED"

        const val REASON_TAKE_PROFIT = "TAKE_PROFIT"
        const val REASON_STOP_LOSS = "STOP_LOSS"
        const val REASON_MANUAL = "MANUAL"
    }

    val unrealizedPnlUsdt: Double
        get() = (currentPrice - entryPrice) * amount

    val unrealizedPnlPercent: Double
        get() = if (entryPrice > 0) ((currentPrice - entryPrice) / entryPrice) * 100.0 else 0.0

    val roiFormatted: String
        get() {
            val sign = if (unrealizedPnlPercent >= 0) "+" else ""
            return "$sign${String.format(java.util.Locale.US, "%.2f", unrealizedPnlPercent)}%"
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
    val positionAmt: Double = 0.0, // > 0: LONG, < 0: SHORT
    val liquidationPrice: Double = 0.0,
    val updateTime: Long = 0L,
    val takeProfitPrice: Double? = null,
    val stopLossPrice: Double? = null
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
    val status: String = "NEW", // NEW, PARTIALLY_FILLED
    val timeInForce: String = "GTC",
    val type: String = "LIMIT", // LIMIT, MARKET, STOP
    val side: String = "BUY", // BUY, SELL
    val updateTime: Long = 0L,
    val avgPrice: Double = 0.0
) {
    val isFilled: Boolean get() = status == "FILLED"
    val isPartiallyFilled: Boolean get() = status == "PARTIALLY_FILLED"
}

data class BinanceAssetBalance(
    val asset: String, // USDT, BNB, USDC, etc.
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
    val status: String, // NEW, FILLED, CANCELED, REJECTED
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
    DEFAULT,     // Mới nhất (theo thời gian vào lệnh)
    DESCENDING,  // ROI Giảm dần (Lãi cao nhất -> Lỗ)
    ASCENDING    // ROI Tăng dần (Lỗ nhiều nhất -> Lãi)
}

enum class VolumeType {
    PERCENT_OF_BALANCE, // % số dư (ví dụ: 0.5% số dư mô phỏng / số dư ví)
    FIXED_AMOUNT        // Volume cố định USDT/lệnh (ví dụ: 50 USDT)
}

enum class TradingStrategyType(val displayName: String, val description: String) {
    D1_SMA20_VOLUME(
        "Chiến lược D1 SMA20 Breakout & Bùng Nổ Khối Lượng (Volume >= 2x)",
        "Nến D1, đường SMA20 tính theo Giá Mở Cửa (Open). Mua khi Giá >= SMA20(D1) và Volume D1 >= 2x ngày hôm trước."
    ),
    FOUR_HOUR_SUPERTREND_MA20(
        "Chiến lược 4H SuperTrend và 4H MA20",
        "Nến 4H, mua khi SuperTrend(20,4) đảo chiều tăng và giá nằm trên đường MA20."
    )
}

data class TradeSettings(
    val autoTradeEnabled: Boolean = false,
    val selectedStrategy: TradingStrategyType = TradingStrategyType.D1_SMA20_VOLUME,
    val volumeMultiplierThreshold: Double = 2.0, // Khối lượng nến D1 hiện tại >= 2 lần ngày hôm trước
    val portfolioTakeProfitPercent: Double = 4.0, // Chốt lời trên toàn danh mục (không chốt lời từng lệnh)
    val volumeType: VolumeType = VolumeType.PERCENT_OF_BALANCE,
    val volumePercentOfBalance: Double = 0.5, // 0.5% số dư
    val fixedVolumeUsdt: Double = 50.0,       // Volume cố định USDT/lệnh
    val capitalPerTradeUsdt: Double = 50.0,   // Tính toán thực tế cho lệnh
    val takeProfitPercent: Double? = 4.0,     // Chốt lời TP
    val stopLossPercent: Double? = null,      // Cắt lỗ (mặc định null)
    val useSuperTrendFilter: Boolean = true,  // Bộ lọc SuperTrend
    val timeframe: String = "1d",
    val simulatedBalanceUsdt: Double = 10000.0,
    val leverage: Int = 10
) {
    /**
     * Volume mỗi vị thế = % số dư mô phỏng HOẶC Volume cố định (USDT/lệnh)
     */
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

    /**
     * Cập nhật số dư mô phỏng sau mỗi lần mở hoặc đóng vị thế,
     * tự động tính lại volume cho các lệnh tiếp theo.
     */
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
    val periodLabel: String, // e.g. "14/10", "Tuần 41"
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
    val lastPrice: Double,
    val priceChangePercent: Double,
    val volume: Double,
    val highPrice: Double = lastPrice,
    val lowPrice: Double = lastPrice,
    val sma20OpenD1: Double = lastPrice * 0.99,
    val volumeRatioD1: Double = 1.0
)

data class SuperTrendPoint(
    val value: Double,
    val isBullish: Boolean
)

data class IndicatorData(
    val candle: Candle,
    val ma20: Double? = null,
    val superTrend: SuperTrendPoint? = null,
    val isMaAboveSuperTrend: Boolean = false,
    val sma20OpenD1: Double? = null,
    val volumeRatioD1: Double = 1.0,
    val isD1BreakoutBuy: Boolean = false
)

data class BinanceApiConfig(
    val apiKey: String = "",
    val apiSecret: String = "",
    val isTestnet: Boolean = true,
    val isConfigured: Boolean = false,
    val liveTradingEnabled: Boolean = false,
    val accessPin: String = "1234",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyType: String = "HTTP",
    val isConnected: Boolean = true
)

data class BinanceAccountVerificationResult(
    val isSuccess: Boolean = false,
    val totalWalletBalance: Double = 0.0,
    val availableBalance: Double = 0.0,
    val positionsCount: Int = 0,
    val errorMessage: String? = null
)
