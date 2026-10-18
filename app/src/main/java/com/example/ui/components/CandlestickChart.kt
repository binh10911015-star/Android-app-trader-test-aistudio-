package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.repository.CryptoRepository
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.IndicatorMA20
import com.example.ui.theme.IndicatorSuperTrendDown
import com.example.ui.theme.IndicatorSuperTrendUp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ChartStrategyMode {
    SMA_SUPERTREND,
    SMA_VOLUME
}

@Composable
fun CandlestickChart(
    indicatorData: List<IndicatorData> = emptyList(),
    timeframe: String = "4h",
    candles: List<Candle> = indicatorData.map { it.candle },
    initialMode: ChartStrategyMode = ChartStrategyMode.SMA_SUPERTREND,
    modifier: Modifier = Modifier
) {
    var strategyMode by remember { mutableStateOf(initialMode) }

    // Take exactly the 30 most recent candles for clear spacing
    val displayIndicators = remember(indicatorData) {
        if (indicatorData.size > 30) indicatorData.takeLast(30) else indicatorData
    }
    val displayCandles = remember(candles, displayIndicators) {
        if (displayIndicators.isNotEmpty()) {
            displayIndicators.map { it.candle }
        } else if (candles.size > 30) {
            candles.takeLast(30)
        } else {
            candles
        }
    }

    if (displayCandles.isEmpty()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp),
            shape = RoundedCornerShape(12.dp),
            color = DarkSurface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Chưa có dữ liệu nến Binance ($timeframe)",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
        return
    }

    val latestCandle = displayCandles.lastOrNull()
    val latestIndicator = displayIndicators.lastOrNull()

    // Calculate price bounds
    var minPrice = Double.MAX_VALUE
    var maxPrice = Double.MIN_VALUE

    displayCandles.forEach { candle ->
        if (candle.low < minPrice) minPrice = candle.low
        if (candle.high > maxPrice) maxPrice = candle.high
    }

    displayIndicators.forEach { ind ->
        ind.ma20?.let {
            if (it < minPrice) minPrice = it
            if (it > maxPrice) maxPrice = it
        }
        ind.superTrend?.let { st ->
            if (st.value > 0.0) {
                if (st.value < minPrice) minPrice = st.value
                if (st.value > maxPrice) maxPrice = st.value
            }
        }
    }

    if (minPrice == Double.MAX_VALUE || maxPrice == Double.MIN_VALUE || minPrice == maxPrice) {
        minPrice = (latestCandle?.close ?: 100.0) * 0.98
        maxPrice = (latestCandle?.close ?: 100.0) * 102
    }

    // Add 4% padding on top and bottom
    val priceRange = maxPrice - minPrice
    val paddedMinPrice = minPrice - priceRange * 0.04
    val paddedMaxPrice = maxPrice + priceRange * 0.04
    val effectivePriceRange = max(1e-8, paddedMaxPrice - paddedMinPrice)

    // Calculate Volume bounds for Volume mode
    var maxVolume = 1.0
    var totalVolume = 0.0
    displayCandles.forEach { candle ->
        if (candle.volume > maxVolume) maxVolume = candle.volume
        totalVolume += candle.volume
    }
    val avgVolume = if (displayCandles.isNotEmpty()) totalVolume / displayCandles.size else 1.0
    val doubleAvgVolume = avgVolume * 2.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        // Strategy Mode Selector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Biểu đồ (30 nến gần nhất)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = DarkSurfaceHighlight
                ) {
                    Text(
                        text = timeframe.uppercase(),
                        color = CryptoGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Toggle Buttons for Strategies
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (strategyMode == ChartStrategyMode.SMA_SUPERTREND) CryptoGold else DarkSurfaceHighlight,
                    modifier = Modifier
                        .clickable { strategyMode = ChartStrategyMode.SMA_SUPERTREND }
                        .testTag("chart_mode_sma_supertrend")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = if (strategyMode == ChartStrategyMode.SMA_SUPERTREND) Color.Black else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SMA + SuperTrend",
                            color = if (strategyMode == ChartStrategyMode.SMA_SUPERTREND) Color.Black else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (strategyMode == ChartStrategyMode.SMA_VOLUME) CryptoGold else DarkSurfaceHighlight,
                    modifier = Modifier
                        .clickable { strategyMode = ChartStrategyMode.SMA_VOLUME }
                        .testTag("chart_mode_sma_volume")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = if (strategyMode == ChartStrategyMode.SMA_VOLUME) Color.Black else TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SMA + Khối lượng",
                            color = if (strategyMode == ChartStrategyMode.SMA_VOLUME) Color.Black else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic Indicators Legend Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceHighlight.copy(alpha = 0.7f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Candle Close
                Text(text = "Giá: ", color = TextSecondary, fontSize = 10.sp)
                Text(
                    text = "$${latestCandle?.let { CryptoRepository.formatPrice(it.close) } ?: "--"}",
                    color = if ((latestCandle?.close ?: 0.0) >= (latestCandle?.open ?: 0.0)) CryptoGreen else CryptoRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(10.dp))

                // MA20
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(IndicatorMA20, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "MA20: ", color = TextSecondary, fontSize = 10.sp)
                Text(
                    text = "$${latestIndicator?.ma20?.let { CryptoRepository.formatPrice(it) } ?: "--"}",
                    color = IndicatorMA20,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (strategyMode == ChartStrategyMode.SMA_SUPERTREND) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isBullish = latestIndicator?.superTrend?.isBullish == true
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isBullish) IndicatorSuperTrendUp else IndicatorSuperTrendDown, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "ST(20,4): ", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "$${latestIndicator?.superTrend?.value?.let { CryptoRepository.formatPrice(it) } ?: "--"}",
                        color = if (isBullish) IndicatorSuperTrendUp else IndicatorSuperTrendDown,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val volRatio = if (avgVolume > 0) (latestCandle?.volume ?: 0.0) / avgVolume else 1.0
                    val isVolBreakout = volRatio >= 2.0
                    Text(text = "Tỷ lệ Vol: ", color = TextSecondary, fontSize = 10.sp)
                    Text(
                        text = "${String.format(Locale.US, "%.1f", volRatio)}x",
                        color = if (isVolBreakout) CryptoGreen else TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (isVolBreakout) {
                        Text(text = " 🔥", fontSize = 9.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // High-Precision Candlestick & Indicator Canvas
        val canvasHeight = if (strategyMode == ChartStrategyMode.SMA_VOLUME) 240.dp else 220.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161A1E))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val totalWidth = size.width
                val totalHeight = size.height

                // Partition height: if Volume mode, top 72% for candles/MA20, bottom 24% for volume bars
                val candleAreaHeight = if (strategyMode == ChartStrategyMode.SMA_VOLUME) totalHeight * 0.72f else totalHeight
                val volumeAreaHeight = if (strategyMode == ChartStrategyMode.SMA_VOLUME) totalHeight * 0.24f else 0f
                val volumeAreaTop = totalHeight - volumeAreaHeight

                // Step width per candle
                val candleCount = displayCandles.size
                val step = totalWidth / candleCount
                val candleBodyWidth = max(4f, step * 0.65f)

                // 1. Draw subtle background horizontal price grid lines (3 levels)
                val gridLevels = 3
                for (i in 1..gridLevels) {
                    val y = candleAreaHeight * (i.toFloat() / (gridLevels + 1))
                    drawLine(
                        color = Color(0xFF2B313A),
                        start = Offset(0f, y),
                        end = Offset(totalWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // If Volume mode: draw separator line and 2x average volume threshold line
                if (strategyMode == ChartStrategyMode.SMA_VOLUME) {
                    drawLine(
                        color = Color(0xFF2B313A),
                        start = Offset(0f, volumeAreaTop),
                        end = Offset(totalWidth, volumeAreaTop),
                        strokeWidth = 1.5f
                    )

                    // 2x Volume threshold dashed line
                    val maxVolWithPadding = max(maxVolume, doubleAvgVolume * 1.15)
                    val y2xVol = volumeAreaTop + volumeAreaHeight * (1f - (doubleAvgVolume / maxVolWithPadding).toFloat())
                    drawLine(
                        color = CryptoGold.copy(alpha = 0.75f),
                        start = Offset(0f, y2xVol),
                        end = Offset(totalWidth, y2xVol),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                    )
                }

                // Helper to map price to Y in candleArea
                fun priceToY(price: Double): Float {
                    val normalized = (paddedMaxPrice - price) / effectivePriceRange
                    return (normalized * candleAreaHeight).toFloat().coerceIn(0f, candleAreaHeight)
                }

                // 2. Draw Candlesticks & Volume Bars
                displayCandles.forEachIndexed { index, candle ->
                    val centerX = (index + 0.5f) * step
                    val isGreen = candle.close >= candle.open
                    val candleColor = if (isGreen) CryptoGreen else CryptoRed

                    val highY = priceToY(candle.high)
                    val lowY = priceToY(candle.low)
                    val openY = priceToY(candle.open)
                    val closeY = priceToY(candle.close)

                    val topBodyY = min(openY, closeY)
                    val bodyHeight = max(2f, kotlin.math.abs(closeY - openY))

                    // Wick line (High to Low)
                    drawLine(
                        color = candleColor,
                        start = Offset(centerX, highY),
                        end = Offset(centerX, lowY),
                        strokeWidth = 1.5f
                    )

                    // Candle Body
                    drawRoundRect(
                        color = candleColor,
                        topLeft = Offset(centerX - candleBodyWidth / 2f, topBodyY),
                        size = Size(candleBodyWidth, bodyHeight),
                        cornerRadius = CornerRadius(1.5f, 1.5f)
                    )

                    // Volume Bar (if volume mode)
                    if (strategyMode == ChartStrategyMode.SMA_VOLUME) {
                        val maxVolWithPadding = max(maxVolume, doubleAvgVolume * 1.15)
                        val volHeight = (candle.volume / maxVolWithPadding).toFloat() * volumeAreaHeight
                        val volTop = totalHeight - volHeight
                        val volColor = if (isGreen) CryptoGreen.copy(alpha = 0.55f) else CryptoRed.copy(alpha = 0.55f)

                        drawRect(
                            color = volColor,
                            topLeft = Offset(centerX - candleBodyWidth / 2f, volTop),
                            size = Size(candleBodyWidth, volHeight)
                        )
                    }
                }

                // 3. Draw MA20 Line (Smooth connected path across available MA20 values)
                val maPath = Path()
                var maStarted = false

                displayIndicators.forEachIndexed { index, ind ->
                    ind.ma20?.let { maVal ->
                        val centerX = (index + 0.5f) * step
                        val y = priceToY(maVal)
                        if (!maStarted) {
                            maPath.moveTo(centerX, y)
                            maStarted = true
                        } else {
                            maPath.lineTo(centerX, y)
                        }
                    }
                }

                if (maStarted) {
                    drawPath(
                        path = maPath,
                        color = IndicatorMA20,
                        style = Stroke(width = 2.5f)
                    )
                }

                // 4. Draw SuperTrend (if in SMA_SUPERTREND mode)
                if (strategyMode == ChartStrategyMode.SMA_SUPERTREND) {
                    var lastStX: Float? = null
                    var lastStY: Float? = null
                    var lastBullish: Boolean? = null

                    displayIndicators.forEachIndexed { index, ind ->
                        ind.superTrend?.let { st ->
                            if (st.value > 0.0) {
                                val centerX = (index + 0.5f) * step
                                val y = priceToY(st.value)
                                val stColor = if (st.isBullish) IndicatorSuperTrendUp else IndicatorSuperTrendDown

                                // Draw dot at each point
                                drawCircle(
                                    color = stColor,
                                    radius = 3f,
                                    center = Offset(centerX, y)
                                )

                                // Connect line if same direction
                                if (lastStX != null && lastStY != null && lastBullish == st.isBullish) {
                                    drawLine(
                                        color = stColor,
                                        start = Offset(lastStX!!, lastStY!!),
                                        end = Offset(centerX, y),
                                        strokeWidth = 2.5f
                                    )
                                }

                                lastStX = centerX
                                lastStY = y
                                lastBullish = st.isBullish
                            }
                        }
                    }
                }
            }

            // Legend labels inside canvas
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Max: $${CryptoRepository.formatPrice(paddedMaxPrice)}",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Min: $${CryptoRepository.formatPrice(paddedMinPrice)}",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                if (strategyMode == ChartStrategyMode.SMA_VOLUME) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        color = CryptoGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "--- Ngưỡng 2x Vol",
                            color = CryptoGold,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
