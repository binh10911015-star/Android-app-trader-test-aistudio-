package com.example.indicators

import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.model.SuperTrendPoint
import kotlin.math.abs
import kotlin.math.max

object TechnicalAnalysis {

    const val DEFAULT_MA_PERIOD = 20
    const val DEFAULT_ST_PERIOD = 20
    const val DEFAULT_ST_MULTIPLIER = 4.0

    /**
     * Calculates MA20, SuperTrend(20, 4.0), and checks the bullish condition:
     * MA20 > SuperTrend(20, 4)
     */
    fun computeIndicators(
        candles: List<Candle>,
        maPeriod: Int = DEFAULT_MA_PERIOD,
        stPeriod: Int = DEFAULT_ST_PERIOD,
        stMultiplier: Double = DEFAULT_ST_MULTIPLIER
    ): List<IndicatorData> {
        if (candles.isEmpty()) return emptyList()

        val n = candles.size
        // 1. Calculate MA 20
        val maValues = DoubleArray(n) { 0.0 }
        val maHasValue = BooleanArray(n) { false }

        var runningSum = 0.0
        for (i in 0 until n) {
            runningSum += candles[i].close
            if (i >= maPeriod) {
                runningSum -= candles[i - maPeriod].close
            }
            if (i >= maPeriod - 1) {
                maValues[i] = runningSum / maPeriod
                maHasValue[i] = true
            }
        }

        // 2. Calculate True Range (TR)
        val tr = DoubleArray(n) { 0.0 }
        for (i in 0 until n) {
            val high = candles[i].high
            val low = candles[i].low
            if (i == 0) {
                tr[i] = high - low
            } else {
                val prevClose = candles[i - 1].close
                val hl = high - low
                val hc = abs(high - prevClose)
                val lc = abs(low - prevClose)
                tr[i] = max(hl, max(hc, lc))
            }
        }

        // 3. Calculate ATR (period 20)
        val atr = DoubleArray(n) { 0.0 }
        var initialTrSum = 0.0
        for (i in 0 until n) {
            if (i < stPeriod) {
                initialTrSum += tr[i]
                atr[i] = initialTrSum / (i + 1)
            } else {
                // Wilder's Smoothing: (prior ATR * (period - 1) + current TR) / period
                atr[i] = (atr[i - 1] * (stPeriod - 1) + tr[i]) / stPeriod
            }
        }

        // 4. Calculate SuperTrend(20, 4)
        val basicUpper = DoubleArray(n)
        val basicLower = DoubleArray(n)
        val finalUpper = DoubleArray(n)
        val finalLower = DoubleArray(n)
        val superTrendValues = DoubleArray(n)
        val isBullishTrend = BooleanArray(n)

        for (i in 0 until n) {
            val hl2 = (candles[i].high + candles[i].low) / 2.0
            basicUpper[i] = hl2 + (stMultiplier * atr[i])
            basicLower[i] = hl2 - (stMultiplier * atr[i])

            if (i == 0) {
                finalUpper[0] = basicUpper[0]
                finalLower[0] = basicLower[0]
                isBullishTrend[0] = candles[0].close >= candles[0].open
                superTrendValues[0] = if (isBullishTrend[0]) finalLower[0] else finalUpper[0]
            } else {
                val prevClose = candles[i - 1].close

                // Final Upper Band
                if (basicUpper[i] < finalUpper[i - 1] || prevClose > finalUpper[i - 1]) {
                    finalUpper[i] = basicUpper[i]
                } else {
                    finalUpper[i] = finalUpper[i - 1]
                }

                // Final Lower Band
                if (basicLower[i] > finalLower[i - 1] || prevClose < finalLower[i - 1]) {
                    finalLower[i] = basicLower[i]
                } else {
                    finalLower[i] = finalLower[i - 1]
                }

                // Determine trend direction
                val prevTrend = isBullishTrend[i - 1]
                if (prevTrend) {
                    if (candles[i].close < finalLower[i - 1]) {
                        isBullishTrend[i] = false
                        superTrendValues[i] = finalUpper[i]
                    } else {
                        isBullishTrend[i] = true
                        superTrendValues[i] = finalLower[i]
                    }
                } else {
                    if (candles[i].close > finalUpper[i - 1]) {
                        isBullishTrend[i] = true
                        superTrendValues[i] = finalLower[i]
                    } else {
                        isBullishTrend[i] = false
                        superTrendValues[i] = finalUpper[i]
                    }
                }
            }
        }

        // Assemble list of IndicatorData
        return candles.indices.map { i ->
            val ma = if (maHasValue[i]) maValues[i] else null
            val st = SuperTrendPoint(
                value = superTrendValues[i],
                isBullish = isBullishTrend[i]
            )
            val isAbove = ma != null && ma > st.value
            IndicatorData(
                candle = candles[i],
                ma20 = ma,
                superTrend = st,
                isMaAboveSuperTrend = isAbove
            )
        }
    }
}
