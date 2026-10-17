package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.IndicatorData
import com.example.repository.CryptoRepository
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.IndicatorMA20
import com.example.ui.theme.IndicatorSuperTrendDown
import com.example.ui.theme.IndicatorSuperTrendUp
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickChart(
    indicatorData: List<IndicatorData>,
    timeframe: String,
    modifier: Modifier = Modifier
) {
    if (indicatorData.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(DarkSurface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Đang tải dữ liệu biểu đồ nến...",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
        return
    }

    // Display the most recent 35 candles to keep candle bodies readable on mobile
    val displayCount = min(35, indicatorData.size)
    val visibleData = remember(indicatorData, displayCount) {
        indicatorData.takeLast(displayCount)
    }

    var selectedIndex by remember(visibleData) {
        mutableStateOf<Int?>(null)
    }

    val activeItem = selectedIndex?.let { visibleData.getOrNull(it) } ?: visibleData.lastOrNull()

    val textMeasurer = rememberTextMeasurer()
    val dateFormat = remember(timeframe) {
        if (timeframe == "1d") SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        else SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Legend & Selected Candle Info Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // MA20 indicator badge
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(IndicatorMA20, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "MA(20): " + (activeItem?.ma20?.let { CryptoRepository.formatPrice(it) } ?: "--"),
                    color = IndicatorMA20,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.width(12.dp))

                // SuperTrend badge
                val stColor = if (activeItem?.superTrend?.isBullish == true) IndicatorSuperTrendUp else IndicatorSuperTrendDown
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(stColor, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "ST(20,4): " + (activeItem?.superTrend?.value?.let { CryptoRepository.formatPrice(it) } ?: "--"),
                    color = stColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Signal status text
            val isBullish = activeItem?.isMaAboveSuperTrend == true
            Surface(
                color = if (isBullish) CryptoGreen.copy(alpha = 0.15f) else CryptoRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (isBullish) "MA20 > ST (TĂNG)" else "MA20 ≤ ST",
                    color = if (isBullish) CryptoGreen else CryptoRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Price details (O, H, L, C)
        if (activeItem != null) {
            val c = activeItem.candle
            val isUp = c.close >= c.open
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(Date(c.openTime)),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row {
                    Text(
                        text = "O: ${CryptoRepository.formatPrice(c.open)} ",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "H: ${CryptoRepository.formatPrice(c.high)} ",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "L: ${CryptoRepository.formatPrice(c.low)} ",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "C: ${CryptoRepository.formatPrice(c.close)}",
                        color = if (isUp) CryptoGreen else CryptoRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Candlestick Drawing Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(visibleData) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val candleWidth = size.width / visibleData.size
                                val index = (offset.x / candleWidth).toInt().coerceIn(0, visibleData.size - 1)
                                selectedIndex = index
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val candleWidth = size.width / visibleData.size
                                val index = (change.position.x / candleWidth).toInt().coerceIn(0, visibleData.size - 1)
                                selectedIndex = index
                            },
                            onDragEnd = {
                                selectedIndex = null
                            },
                            onDragCancel = {
                                selectedIndex = null
                            }
                        )
                    }
                    .pointerInput(visibleData) {
                        detectTapGestures { offset ->
                            val candleWidth = size.width / visibleData.size
                            val index = (offset.x / candleWidth).toInt().coerceIn(0, visibleData.size - 1)
                            selectedIndex = if (selectedIndex == index) null else index
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                // Calculate min and max price across visible candles and indicators
                var minPrice = Double.MAX_VALUE
                var maxPrice = Double.MIN_VALUE

                visibleData.forEach { item ->
                    minPrice = min(minPrice, item.candle.low)
                    maxPrice = max(maxPrice, item.candle.high)
                    item.ma20?.let {
                        minPrice = min(minPrice, it)
                        maxPrice = max(maxPrice, it)
                    }
                    item.superTrend?.let {
                        minPrice = min(minPrice, it.value)
                        maxPrice = max(maxPrice, it.value)
                    }
                }

                if (minPrice >= maxPrice) {
                    minPrice *= 0.99
                    maxPrice *= 1.01
                } else {
                    // Add padding to top and bottom (5%)
                    val range = maxPrice - minPrice
                    minPrice -= range * 0.05
                    maxPrice += range * 0.05
                }

                val priceRange = maxPrice - minPrice

                fun priceToY(price: Double): Float {
                    return (chartHeight - ((price - minPrice) / priceRange * chartHeight)).toFloat()
                }

                // Draw Horizontal Price Grid Lines
                val gridLines = 4
                for (g in 0..gridLines) {
                    val y = chartHeight * g / gridLines
                    val priceVal = maxPrice - (priceRange * g / gridLines)

                    drawLine(
                        color = DarkBorder,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Draw price label
                    val text = CryptoRepository.formatPrice(priceVal)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = text,
                        topLeft = Offset(chartWidth - 65f, max(0f, y - 18f)),
                        style = TextStyle(
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                val candleCount = visibleData.size
                val stepX = chartWidth / candleCount
                val candleWidth = (stepX * 0.7f).coerceAtLeast(3f)

                // Draw Candles
                for (i in 0 until candleCount) {
                    val item = visibleData[i]
                    val c = item.candle
                    val centerX = i * stepX + stepX / 2f

                    val isBullish = c.close >= c.open
                    val candleColor = if (isBullish) CryptoGreen else CryptoRed

                    val highY = priceToY(c.high)
                    val lowY = priceToY(c.low)
                    val openY = priceToY(c.open)
                    val closeY = priceToY(c.close)

                    // Wick
                    drawLine(
                        color = candleColor,
                        start = Offset(centerX, highY),
                        end = Offset(centerX, lowY),
                        strokeWidth = 1.5f
                    )

                    // Body
                    val bodyTop = min(openY, closeY)
                    val bodyBottom = max(openY, closeY)
                    val bodyHeight = max(2f, bodyBottom - bodyTop)

                    drawRect(
                        color = candleColor,
                        topLeft = Offset(centerX - candleWidth / 2f, bodyTop),
                        size = Size(candleWidth, bodyHeight)
                    )
                }

                // Draw SuperTrend Line (segments with green/red trend)
                for (i in 0 until candleCount - 1) {
                    val st1 = visibleData[i].superTrend ?: continue
                    val st2 = visibleData[i + 1].superTrend ?: continue

                    val x1 = i * stepX + stepX / 2f
                    val y1 = priceToY(st1.value)
                    val x2 = (i + 1) * stepX + stepX / 2f
                    val y2 = priceToY(st2.value)

                    val segmentColor = if (st2.isBullish) IndicatorSuperTrendUp else IndicatorSuperTrendDown

                    drawLine(
                        color = segmentColor,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 3f
                    )
                }

                // Draw MA20 Line (Smooth Golden Yellow line)
                val maPath = Path()
                var hasStartedMa = false

                for (i in 0 until candleCount) {
                    val ma = visibleData[i].ma20 ?: continue
                    val x = i * stepX + stepX / 2f
                    val y = priceToY(ma)

                    if (!hasStartedMa) {
                        maPath.moveTo(x, y)
                        hasStartedMa = true
                    } else {
                        maPath.lineTo(x, y)
                    }
                }

                if (hasStartedMa) {
                    drawPath(
                        path = maPath,
                        color = IndicatorMA20,
                        style = Stroke(width = 2.5f)
                    )
                }

                // Draw Crosshair inspection line if selected
                selectedIndex?.let { idx ->
                    if (idx in 0 until candleCount) {
                        val crossX = idx * stepX + stepX / 2f
                        val selectedCandle = visibleData[idx].candle
                        val crossY = priceToY(selectedCandle.close)

                        // Vertical dashed crosshair
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(crossX, 0f),
                            end = Offset(crossX, chartHeight),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )

                        // Horizontal dashed crosshair
                        drawLine(
                            color = Color.White.copy(alpha = 0.5f),
                            start = Offset(0f, crossY),
                            end = Offset(chartWidth, crossY),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                        )

                        // Glowing circle on candle close
                        drawCircle(
                            color = Color.White,
                            radius = 4f,
                            center = Offset(crossX, crossY)
                        )
                    }
                }
            }
        }
    }
}
