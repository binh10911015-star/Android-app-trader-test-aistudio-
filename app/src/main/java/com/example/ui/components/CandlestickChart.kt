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
import com.example.indicators.TechnicalAnalysis
import com.example.model.Candle
import com.example.model.IndicatorData
import com.example.network.BinanceClient
import com.example.repository.CryptoRepository
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.IndicatorMA20
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ChartStrategyMode {
    SMA_VOLUME
}

@Composable
fun CandlestickChart(
    indicatorData: List<IndicatorData> = emptyList(),
    timeframe: String = "4h",
    candles: List<Candle> = indicatorData.map { it.candle },
    initialMode: ChartStrategyMode = ChartStrategyMode.SMA_VOLUME,
    modifier: Modifier = Modifier
) {
    var strategyMode by remember(initialMode) { mutableStateOf(initialMode) }

    // Đảm bảo luôn có 30 nến kèm chỉ báo MA20 đầy đủ
    val effectiveIndicators = remember(indicatorData, candles, timeframe) {
        if (indicatorData.isNotEmpty()) {
            if (indicatorData.size > 30) indicatorData.takeLast(30) else indicatorData
        } else if (candles.isNotEmpty()) {
            val list = if (candles.size > 30) candles.takeLast(30) else candles
            TechnicalAnalysis.computeIndicators(list)
        } else {
            val fallbackCandles = emptyList<Candle>()
            TechnicalAnalysis.computeIndicators(fallbackCandles)
        }
    }

    val displayCandles = remember(effectiveIndicators) {
        effectiveIndicators.map { it.candle }
    }

    val latestCandle = displayCandles.lastOrNull()
    val latestIndicator = effectiveIndicators.lastOrNull()

    // Tính toán biên độ giá min/max cho 30 nến
    var minPrice = Double.MAX_VALUE
    var maxPrice = Double.MIN_VALUE

    displayCandles.forEach { candle ->
        if (candle.low < minPrice) minPrice = candle.low
        if (candle.high > maxPrice) maxPrice = candle.high
    }

    effectiveIndicators.forEach { ind ->
        ind.ma20?.let {
            if (it < minPrice) minPrice = it
            if (it > maxPrice) maxPrice = it
        }
    }

    if (minPrice == Double.MAX_VALUE || maxPrice == Double.MIN_VALUE || minPrice >= maxPrice) {
        val base = latestCandle?.close ?: 80000.0
        minPrice = base * 0.97
        maxPrice = base * 1.03
    }

    // Đệm 5% ở 2 đỉnh đáy để nến và chỉ báo không bị chạm viền
    val priceRange = maxPrice - minPrice
    val paddedMinPrice = minPrice - priceRange * 0.05
    val paddedMaxPrice = maxPrice + priceRange * 0.05
    val effectivePriceRange = max(1e-8, paddedMaxPrice - paddedMinPrice)

    // Tính toán Volume cho chế độ SMA + Khối lượng
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
        // HÀNG 1: Tiêu đề & Cặp nến & Giá hiện tại
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
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Giá hiện tại
            latestCandle?.let { c ->
                val isUp = c.close >= c.open
                Text(
                    text = "$${CryptoRepository.formatPrice(c.close)}",
                    color = if (isUp) CryptoGreen else CryptoRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // HÀNG 2: Thanh thông tin chỉ báo hoạt động
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp)),
            color = DarkSurfaceHighlight
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = IndicatorMA20,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chỉ báo hoạt động: SMA(20) & Khối lượng",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CryptoGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "ACTIVE",
                        color = CryptoGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // HÀNG 3: Chú giải chỉ báo động (Legend Bar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceHighlight.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // MA20
                Box(
                    modifier = Modifier
                        .size(7.dp)
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
                    Text(text = " 🔥 (Đạt ngưỡng 2x)", color = CryptoGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // HÀNG 4: KHUNG CANVAS VẼ NẾN & CHỈ BÁO RÕ NÉT
        val canvasHeight = if (strategyMode == ChartStrategyMode.SMA_VOLUME) 240.dp else 220.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14171A))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val totalWidth = size.width
                val totalHeight = size.height

                // Nếu là chế độ Volume: chia 72% trên cho nến/MA20, 24% dưới cho cột volume
                val candleAreaHeight = if (strategyMode == ChartStrategyMode.SMA_VOLUME) totalHeight * 0.72f else totalHeight
                val volumeAreaHeight = if (strategyMode == ChartStrategyMode.SMA_VOLUME) totalHeight * 0.24f else 0f
                val volumeAreaTop = totalHeight - volumeAreaHeight

                val candleCount = max(1, displayCandles.size)
                val step = totalWidth / candleCount
                val candleBodyWidth = max(4f, step * 0.65f)

                // 1. Lưới ngang giá ngầm
                val gridLevels = 3
                for (i in 1..gridLevels) {
                    val y = candleAreaHeight * (i.toFloat() / (gridLevels + 1))
                    drawLine(
                        color = Color(0xFF242A32),
                        start = Offset(0f, y),
                        end = Offset(totalWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                // Nếu là chế độ volume: vẽ vạch ngăn cách & đường ngưỡng 2x Vol
                if (strategyMode == ChartStrategyMode.SMA_VOLUME) {
                    drawLine(
                        color = Color(0xFF2E3540),
                        start = Offset(0f, volumeAreaTop),
                        end = Offset(totalWidth, volumeAreaTop),
                        strokeWidth = 1.5f
                    )

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

                // Chuyển đổi giá sang tọa độ Y
                fun priceToY(price: Double): Float {
                    val normalized = (paddedMaxPrice - price) / effectivePriceRange
                    return (normalized * candleAreaHeight).toFloat().coerceIn(0f, candleAreaHeight)
                }

                // 2. Vẽ từng thanh nến (Bóng nến & Thân nến)
                displayCandles.forEachIndexed { index, candle ->
                    val centerX = (index + 0.5f) * step
                    val isGreen = candle.close >= candle.open
                    val candleColor = if (isGreen) CryptoGreen else CryptoRed

                    val highY = priceToY(candle.high)
                    val lowY = priceToY(candle.low)
                    val openY = priceToY(candle.open)
                    val closeY = priceToY(candle.close)

                    val topBodyY = min(openY, closeY)
                    val bodyHeight = max(2.5f, kotlin.math.abs(closeY - openY))

                    // Râu nến
                    drawLine(
                        color = candleColor,
                        start = Offset(centerX, highY),
                        end = Offset(centerX, lowY),
                        strokeWidth = 1.5f
                    )

                    // Thân nến
                    drawRoundRect(
                        color = candleColor,
                        topLeft = Offset(centerX - candleBodyWidth / 2f, topBodyY),
                        size = Size(candleBodyWidth, bodyHeight),
                        cornerRadius = CornerRadius(2f, 2f)
                    )

                    // Cột Volume (nếu ở chế độ SMA_VOLUME)
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

                // 3. Vẽ đường MA20 liền mạch màu vàng
                val maPath = Path()
                var maStarted = false

                effectiveIndicators.forEachIndexed { index, ind ->
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

            }

            // Nhãn giá Max / Min góc trên bên phải
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Cao: $${CryptoRepository.formatPrice(paddedMaxPrice)}",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Thấp: $${CryptoRepository.formatPrice(paddedMinPrice)}",
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