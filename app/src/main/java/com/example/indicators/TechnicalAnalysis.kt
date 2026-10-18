package com.example.indicators

import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.model.SuperTrendPoint
import kotlin.math.abs
import kotlin.math.max

object TechnicalAnalysis {

    /**
     * Tính toán SMA20 căn cứ theo GIÁ MỞ CỬA (Open Price) của 20 nến D1 gần nhất.
     */
    fun computeSma20Open(candles: List<Candle>): Double? {
        if (candles.size < 20) return null
        val last20 = candles.takeLast(20)
        return last20.map { it.open }.average()
    }

    /**
     * Kiểm tra tức thì điều kiện mua chiến lược D1 SMA20 Breakout & Bùng Nổ Khối Lượng 2x:
     * 1. Giá hiện tại >= SMA20 tính theo giá Mở Cửa (Open) của khung D1
     * 2. Khối lượng nến D1 hiện tại >= multiplier * Khối lượng nến D1 ngày hôm trước (mặc định >= 2x)
     */
    fun evaluateD1BreakoutSignal(
        currentPrice: Double,
        sma20OpenD1: Double,
        todayVolume: Double,
        yesterdayVolume: Double,
        multiplier: Double = 2.0
    ): Boolean {
        val isPriceAboveSma20 = currentPrice >= sma20OpenD1
        val isVolumeExplosion = yesterdayVolume > 0 && (todayVolume >= multiplier * yesterdayVolume)
        return isPriceAboveSma20 && isVolumeExplosion
    }

    /**
     * Tính toán chỉ báo kỹ thuật MA20 và SuperTrend(20, 4) cho chuỗi nến
     */
    fun computeIndicators(
        candles: List<Candle>,
        maPeriod: Int = 20,
        superTrendPeriod: Int = 20,
        superTrendMultiplier: Double = 4.0
    ): List<IndicatorData> {
        if (candles.isEmpty()) return emptyList()

        val results = mutableListOf<IndicatorData>()
        val atrList = computeATR(candles, superTrendPeriod)

        var prevUpperBand = 0.0
        var prevLowerBand = 0.0
        var prevSuperTrend = 0.0
        var isBullish = true

        for (i in candles.indices) {
            val candle = candles[i]

            // 1. MA20 (theo giá đóng cửa cho chiến lược 4H)
            val ma20 = if (i >= maPeriod - 1) {
                var sum = 0.0
                for (j in i - maPeriod + 1..i) {
                    sum += candles[j].close
                }
                sum / maPeriod
            } else null

            // 2. SMA20 theo giá mở cửa (khung D1)
            val sma20Open = if (i >= 19) {
                var sumOpen = 0.0
                for (j in i - 19..i) {
                    sumOpen += candles[j].open
                }
                sumOpen / 20.0
            } else null

            // 3. SuperTrend
            val atr = atrList.getOrNull(i) ?: 0.0
            val hl2 = (candle.high + candle.low) / 2.0
            val basicUpperBand = hl2 + (superTrendMultiplier * atr)
            val basicLowerBand = hl2 - (superTrendMultiplier * atr)

            var finalUpperBand = basicUpperBand
            var finalLowerBand = basicLowerBand

            if (i > 0) {
                val prevClose = candles[i - 1].close
                if (basicUpperBand < prevUpperBand || prevClose > prevUpperBand) {
                    finalUpperBand = basicUpperBand
                } else {
                    finalUpperBand = prevUpperBand
                }

                if (basicLowerBand > prevLowerBand || prevClose < prevLowerBand) {
                    finalLowerBand = basicLowerBand
                } else {
                    finalLowerBand = prevLowerBand
                }
            }

            var currentSuperTrend: Double
            if (i == 0) {
                currentSuperTrend = finalUpperBand
                isBullish = false
            } else {
                val prevClose = candles[i - 1].close
                if (prevSuperTrend == prevUpperBand) {
                    if (candle.close > finalUpperBand) {
                        currentSuperTrend = finalLowerBand
                        isBullish = true
                    } else {
                        currentSuperTrend = finalUpperBand
                        isBullish = false
                    }
                } else {
                    if (candle.close < finalLowerBand) {
                        currentSuperTrend = finalUpperBand
                        isBullish = false
                    } else {
                        currentSuperTrend = finalLowerBand
                        isBullish = true
                    }
                }
            }

            prevUpperBand = finalUpperBand
            prevLowerBand = finalLowerBand
            prevSuperTrend = currentSuperTrend

            val superTrendPoint = SuperTrendPoint(
                value = currentSuperTrend,
                isBullish = isBullish
            )

            val isMaAbove = if (ma20 != null) {
                ma20 >= currentSuperTrend && isBullish
            } else false

            // Tỷ lệ khối lượng nến hiện tại so với nến liền kề trước
            val volRatio = if (i > 0 && candles[i - 1].volume > 0) {
                candle.volume / candles[i - 1].volume
            } else 1.0

            val isD1Breakout = if (sma20Open != null) {
                candle.close >= sma20Open && volRatio >= 2.0
            } else false

            results.add(
                IndicatorData(
                    candle = candle,
                    ma20 = ma20,
                    superTrend = superTrendPoint,
                    isMaAboveSuperTrend = isMaAbove,
                    sma20OpenD1 = sma20Open,
                    volumeRatioD1 = volRatio,
                    isD1BreakoutBuy = isD1Breakout
                )
            )
        }

        return results
    }

    private fun computeATR(candles: List<Candle>, period: Int): List<Double> {
        val trList = mutableListOf<Double>()
        for (i in candles.indices) {
            val c = candles[i]
            if (i == 0) {
                trList.add(c.high - c.low)
            } else {
                val prevC = candles[i - 1]
                val tr = max(
                    c.high - c.low,
                    max(abs(c.high - prevC.close), abs(c.low - prevC.close))
                )
                trList.add(tr)
            }
        }

        val atrList = mutableListOf<Double>()
        var sum = 0.0
        for (i in trList.indices) {
            if (i < period) {
                sum += trList[i]
                atrList.add(sum / (i + 1))
            } else {
                val prevAtr = atrList.last()
                val currentAtr = (prevAtr * (period - 1) + trList[i]) / period
                atrList.add(currentAtr)
            }
        }
        return atrList
    }
}
