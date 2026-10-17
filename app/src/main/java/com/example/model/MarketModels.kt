package com.example.model

data class BinanceTicker(
    val symbol: String,
    val lastPrice: Double,
    val priceChangePercent: Double,
    val volume: Double,
    val highPrice: Double,
    val lowPrice: Double
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

data class SuperTrendPoint(
    val value: Double,
    val isBullish: Boolean // true if uptrend (green), false if downtrend (red)
)

data class IndicatorData(
    val candle: Candle,
    val ma20: Double?,
    val superTrend: SuperTrendPoint?,
    val isMaAboveSuperTrend: Boolean // MA20 > SuperTrend(20,4)
) {
    // Điều kiện: Giá cao hơn SMA20
    val isPriceAboveSma20: Boolean
        get() = ma20 != null && candle.close > ma20

    /**
     * Bộ lọc điều kiện tín hiệu:
     * - Giá cao hơn SMA20
     * - Có hoặc không SuperTrend(20,4) (useSuperTrend == true: kết hợp cả 2; false: chỉ cần Giá > SMA20)
     */
    fun isBullishSignal(useSuperTrend: Boolean): Boolean {
        return if (useSuperTrend) {
            isPriceAboveSma20 && isMaAboveSuperTrend
        } else {
            isPriceAboveSma20
        }
    }
}
