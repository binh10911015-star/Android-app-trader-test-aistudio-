package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PeriodPnL
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

@Composable
fun PnLBarChart(
    items: List<PeriodPnL>,
    title: String,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(DarkSurface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Chưa có dữ liệu giao dịch chốt lời/cắt lỗ cho chu kỳ này",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        return
    }

    var selectedItem by remember(items) {
        mutableStateOf<PeriodPnL?>(items.lastOrNull())
    }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // Chart Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            selectedItem?.let { sel ->
                val isProfit = sel.pnlUsdt >= 0
                val sign = if (isProfit) "+" else ""
                Text(
                    text = "${sel.periodLabel}: $sign$${String.format(Locale.US, "%.2f", sel.pnlUsdt)} (${sel.winCount}/${sel.tradesCount} thắng)",
                    color = if (isProfit) CryptoGreen else CryptoRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bar Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items) {
                        detectTapGestures { offset ->
                            val barWidth = size.width / items.size
                            val index = (offset.x / barWidth).toInt().coerceIn(0, items.size - 1)
                            selectedItem = items[index]
                        }
                    }
            ) {
                val chartWidth = size.width
                val chartHeight = size.height - 24f // leave room for bottom labels

                val maxAbsPnl = items.maxOfOrNull { abs(it.pnlUsdt) }?.coerceAtLeast(10.0) ?: 10.0
                val zeroY = chartHeight / 2f

                // Draw Zero Baseline
                drawLine(
                    color = DarkBorder,
                    start = Offset(0f, zeroY),
                    end = Offset(chartWidth, zeroY),
                    strokeWidth = 1.5f
                )

                val count = items.size
                val stepX = chartWidth / count
                val barWidth = (stepX * 0.55f).coerceIn(8f, 36f)

                items.forEachIndexed { i, item ->
                    val centerX = i * stepX + stepX / 2f
                    val pnl = item.pnlUsdt
                    val isProfit = pnl >= 0
                    val barColor = if (isProfit) CryptoGreen else CryptoRed

                    // Calculate bar height relative to half chart
                    val heightRatio = (abs(pnl) / maxAbsPnl).toFloat().coerceIn(0.04f, 1f)
                    val barHeight = (chartHeight / 2f - 10f) * heightRatio

                    val top = if (isProfit) zeroY - barHeight else zeroY
                    val isSelected = selectedItem == item

                    // Draw Bar
                    drawRoundRect(
                        color = if (isSelected) barColor else barColor.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - barWidth / 2f, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    // Draw selection glow outline
                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(centerX - barWidth / 2f - 1f, top - 1f),
                            size = Size(barWidth + 2f, barHeight + 2f),
                            cornerRadius = CornerRadius(5f, 5f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                        )
                    }

                    // Period Label below
                    val label = item.periodLabel
                    val labelLayout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = if (isSelected) TextPrimary else TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )

                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(centerX - labelLayout.size.width / 2f, chartHeight + 6f)
                    )
                }
            }
        }
    }
}
