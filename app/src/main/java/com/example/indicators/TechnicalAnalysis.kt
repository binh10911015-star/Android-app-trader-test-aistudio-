package com.example.indicators

import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.model.SuperTrendPoint
import kotlin.math.abs

object TechnicalAnalysis {
    fun computeIndicators(candles: List<Candle>): List<IndicatorData> {
        if (candles.isEmpty()) return emptyList()

        val results = mutableListOf<IndicatorData>()
        val window = candles.takeLast(20)
        val ma20 = if (window.isNotEmpty()) window.map { it.close }.average() else 0.0

        for (idx in candles.indices) {
            val candle = candles[idx]
            val recent = candles.take(maxOf(0, idx + 1)).takeLast(20)
            val currentMa = if (recent.isNotEmpty()) recent.map { it.close }.average() else candle.close
            val superTrendValue = currentMa * if (candle.close >= currentMa) 1.02 else 0.98
            val isBullish = candle.close >= currentMa && candle.close >= superTrendValue
            results.add(
                IndicatorData(
                    candle = candle,
                    ma20 = currentMa,
                    superTrend = SuperTrendPoint(value = superTrendValue),
                    isMaAboveSuperTrend = isBullish,
                    isBullishSignal = isBullish
                )
            )
        }
        return results
    }

    fun computeMa20(candles: List<Candle>): Double {
        if (candles.isEmpty()) return 0.0
        val recent = candles.takeLast(20)
        return recent.map { it.close }.average()
    }

    fun signalStrength(candle: Candle): Double {
        val recent = candle.close
        val diff = abs(recent - candle.open)
        return if (recent > 0.0) diff / recent else 0.0
    }
}
