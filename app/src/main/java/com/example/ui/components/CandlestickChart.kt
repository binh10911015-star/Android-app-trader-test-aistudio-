package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.Candle
import com.example.model.IndicatorData

@Composable
fun CandlestickChart(
    indicatorData: List<IndicatorData> = emptyList(),
    timeframe: String = "4h",
    candles: List<Candle> = indicatorData.map { it.candle },
    modifier: Modifier = Modifier
) {
    val chartCandles = candles.ifEmpty { indicatorData.map { it.candle } }
    if (chartCandles.isEmpty()) return
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        val width = size.width
        val height = size.height
        val step = if (chartCandles.size > 1) width / chartCandles.size else width
        chartCandles.forEachIndexed { index, candle ->
            val x = index * step + step / 2
            val bodyTop = if (candle.close >= candle.open) height * 0.35f else height * 0.65f
            val bodyBottom = if (candle.close >= candle.open) height * 0.65f else height * 0.35f
            drawLine(
                color = Color(0xFF7CD6FF),
                start = Offset(x, height * 0.2f),
                end = Offset(x, height * 0.8f),
                strokeWidth = 1f
            )
            drawRect(
                color = if (candle.close >= candle.open) Color(0xFF42C88A) else Color(0xFFEA5C5A),
                topLeft = Offset(x - 4f, bodyTop),
                size = Size(8f, bodyBottom - bodyTop)
            )
        }
    }
}
