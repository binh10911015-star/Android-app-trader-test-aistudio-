package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TradePosition
import com.example.repository.CryptoRepository
import com.example.ui.MainViewModel
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    closedPositions: List<TradePosition>
) {
    var historyFilter by remember { mutableStateOf("ALL") } // "ALL", "WIN", "LOSS", "TP", "SL"

    // Metrics calculations
    val totalTrades = closedPositions.size
    val winningTrades = closedPositions.count { (it.pnlUsdt ?: 0.0) > 0 }
    val losingTrades = closedPositions.count { (it.pnlUsdt ?: 0.0) < 0 }
    val breakEvenTrades = closedPositions.count { (it.pnlUsdt ?: 0.0) == 0.0 }

    val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0
    val totalNetPnl = closedPositions.sumOf { it.pnlUsdt ?: 0.0 }
    val totalGrossProfit = closedPositions.filter { (it.pnlUsdt ?: 0.0) > 0 }.sumOf { it.pnlUsdt ?: 0.0 }
    val totalGrossLoss = closedPositions.filter { (it.pnlUsdt ?: 0.0) < 0 }.sumOf { abs(it.pnlUsdt ?: 0.0) }
    val profitFactor = if (totalGrossLoss > 0) totalGrossProfit / totalGrossLoss else if (totalGrossProfit > 0) 99.9 else 0.0

    val maxWin = closedPositions.mapNotNull { it.pnlUsdt }.filter { it > 0 }.maxOrNull() ?: 0.0
    val maxLoss = closedPositions.mapNotNull { it.pnlUsdt }.filter { it < 0 }.minOrNull() ?: 0.0

    // Filtered trades list
    val filteredPositions = remember(closedPositions, historyFilter) {
        when (historyFilter) {
            "WIN" -> closedPositions.filter { (it.pnlUsdt ?: 0.0) > 0 }
            "LOSS" -> closedPositions.filter { (it.pnlUsdt ?: 0.0) < 0 }
            "TP" -> closedPositions.filter { it.closeReason == TradePosition.REASON_TAKE_PROFIT || it.closeReason == "PORTFOLIO_TP" }
            "SL" -> closedPositions.filter { it.closeReason == TradePosition.REASON_STOP_LOSS }
            else -> closedPositions
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Header Bar
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thống Kê Lợi Nhuận & Hiệu Quả",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceHighlight
                ) {
                    Text(
                        text = "$totalTrades lệnh đã đóng",
                        color = CryptoGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 2. Summary Metric Cards (Total Net PnL & Win Rate)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Tổng Net PnL
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, if (totalNetPnl >= 0) CryptoGreen.copy(alpha = 0.4f) else CryptoRed.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Tổng PnL Ròng", color = TextSecondary, fontSize = 11.sp)
                            Icon(
                                imageVector = if (totalNetPnl >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = if (totalNetPnl >= 0) CryptoGreen else CryptoRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val sign = if (totalNetPnl >= 0) "+" else ""
                        Text(
                            text = "$sign$${String.format(Locale.US, "%,.2f", totalNetPnl)}",
                            color = if (totalNetPnl >= 0) CryptoGreen else CryptoRed,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (totalNetPnl >= 0) "Lãi ròng tích lũy" else "Lỗ ròng tích lũy",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Card 2: Tỷ Lệ Thắng (Win Rate)
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Tỷ Lệ Thắng", color = TextSecondary, fontSize = 11.sp)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (winRate >= 50) CryptoGreen.copy(alpha = 0.18f) else CryptoRed.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "${winningTrades}W / ${losingTrades}L",
                                    color = if (winRate >= 50) CryptoGreen else CryptoRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${String.format(Locale.US, "%.1f", winRate)}%",
                            color = if (winRate >= 50) CryptoGreen else CryptoGold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (winRate / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (winRate >= 50) CryptoGreen else CryptoGold,
                            trackColor = DarkSurfaceHighlight
                        )
                    }
                }
            }
        }

        // 3. Secondary Detailed Stats Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(text = "Profit Factor", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = String.format(Locale.US, "%.2f", profitFactor),
                            color = if (profitFactor >= 1.5) CryptoGreen else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Lệnh Thắng Lớn Nhất", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "+$${String.format(Locale.US, "%.2f", maxWin)}",
                            color = CryptoGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Lệnh Thua Lớn Nhất", color = TextSecondary, fontSize = 10.sp)
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", maxLoss)}",
                            color = if (maxLoss < 0) CryptoRed else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 4. Cumulative Profit Curve Chart (Biểu đồ tăng trưởng lợi nhuận)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = CryptoGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Biểu Đồ Lợi Nhuận Tích Lũy (PnL Curve)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "${closedPositions.size} mốc giao dịch",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Canvas Chart Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161A1E))
                    ) {
                        if (closedPositions.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.ShowChart,
                                        contentDescription = null,
                                        tint = TextMuted.copy(alpha = 0.5f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Chưa có lệnh đóng để vẽ biểu đồ",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Biểu đồ đường cong lợi nhuận sẽ tự động vẽ khi có lệnh đóng",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            // Compute cumulative equity curve points
                            val pnlPoints = remember(closedPositions) {
                                var runningTotal = 0.0
                                val points = mutableListOf(0.0) // Start at zero
                                closedPositions.sortedBy { it.exitTime ?: it.entryTime }.forEach { pos ->
                                    runningTotal += (pos.pnlUsdt ?: 0.0)
                                    points.add(runningTotal)
                                }
                                points
                            }

                            Canvas(modifier = Modifier.matchParentSize()) {
                                val chartWidth = size.width
                                val chartHeight = size.height

                                var minPnl = pnlPoints.minOrNull() ?: 0.0
                                var maxPnl = pnlPoints.maxOrNull() ?: 0.0

                                if (minPnl > 0.0) minPnl = 0.0
                                if (maxPnl < 0.0) maxPnl = 0.0
                                if (minPnl == maxPnl) {
                                    minPnl = -10.0
                                    maxPnl = 10.0
                                }

                                val pnlRange = max(1e-4, maxPnl - minPnl)

                                fun pnlToY(value: Double): Float {
                                    val norm = (maxPnl - value) / pnlRange
                                    return (norm * (chartHeight * 0.8f) + chartHeight * 0.1f).toFloat()
                                }

                                // Draw Zero Baseline
                                val zeroY = pnlToY(0.0)
                                drawLine(
                                    color = Color(0xFF333B44),
                                    start = Offset(0f, zeroY),
                                    end = Offset(chartWidth, zeroY),
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                                )

                                // Build Curve Path
                                val stepX = chartWidth / max(1, pnlPoints.size - 1)
                                val linePath = Path()
                                val fillPath = Path()

                                fillPath.moveTo(0f, chartHeight)

                                pnlPoints.forEachIndexed { i, pnl ->
                                    val x = i * stepX
                                    val y = pnlToY(pnl)

                                    if (i == 0) {
                                        linePath.moveTo(x, y)
                                        fillPath.lineTo(x, y)
                                    } else {
                                        linePath.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }

                                    // Draw circle marker at trade points
                                    drawCircle(
                                        color = if (pnl >= 0) CryptoGreen else CryptoRed,
                                        radius = 3.5f,
                                        center = Offset(x, y)
                                    )
                                }

                                fillPath.lineTo(chartWidth, chartHeight)
                                fillPath.close()

                                // Gradient Fill under curve
                                val isProfitable = (pnlPoints.lastOrNull() ?: 0.0) >= 0.0
                                val gradientColor = if (isProfitable) CryptoGreen else CryptoRed
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(gradientColor.copy(alpha = 0.25f), Color.Transparent),
                                        startY = 0f,
                                        endY = chartHeight
                                    )
                                )

                                // Draw Main Line
                                drawPath(
                                    path = linePath,
                                    color = gradientColor,
                                    style = Stroke(width = 2.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Order History Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lịch Sử Lệnh (${filteredPositions.size})",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = historyFilter == "ALL",
                        onClick = { historyFilter = "ALL" },
                        label = { Text("Tất cả", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = historyFilter == "WIN",
                        onClick = { historyFilter = "WIN" },
                        label = { Text("Thắng ($winningTrades)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGreen,
                            selectedLabelColor = DarkBg,
                            labelColor = CryptoGreen
                        )
                    )
                    FilterChip(
                        selected = historyFilter == "LOSS",
                        onClick = { historyFilter = "LOSS" },
                        label = { Text("Thua ($losingTrades)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoRed,
                            selectedLabelColor = Color.White,
                            labelColor = CryptoRed
                        )
                    )
                }
            }
        }

        // 6. Closed Positions List
        if (filteredPositions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (closedPositions.isEmpty())
                                "Chưa có lệnh nào được chốt lời hoặc cắt lỗ."
                            else
                                "Không tìm thấy lệnh phù hợp với bộ lọc.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(filteredPositions, key = { "closed_${it.id}_${it.symbol}" }) { position ->
                ClosedPositionItemCard(position = position)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ClosedPositionItemCard(position: TradePosition) {
    val pnl = position.pnlUsdt ?: 0.0
    val isWin = pnl >= 0.0
    val pnlSign = if (isWin) "+" else ""

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }
    val timeText = remember(position.exitTime, position.entryTime) {
        val t = position.exitTime ?: position.entryTime
        dateFormat.format(Date(t))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("closed_trade_${position.symbol}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = BorderStroke(1.dp, if (isWin) CryptoGreen.copy(alpha = 0.35f) else CryptoRed.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Symbol, Reason Badge & PnL
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.symbol.removeSuffix("USDT"),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "/USDT", color = TextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(8.dp))

                    // Reason Badge
                    val (badgeText, badgeColor) = when (position.closeReason) {
                        TradePosition.REASON_TAKE_PROFIT -> "CHỐT LỜI (TP)" to CryptoGreen
                        "PORTFOLIO_TP" -> "TP TOÀN DANH MỤC" to CryptoGreen
                        TradePosition.REASON_STOP_LOSS -> "CẮT LỖ (SL)" to CryptoRed
                        TradePosition.REASON_MANUAL -> "ĐÓNG THỦ CÔNG" to CryptoGold
                        else -> "ĐÃ ĐÓNG" to TextSecondary
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // PnL in USDT
                Text(
                    text = "$pnlSign$${String.format(Locale.US, "%.2f", pnl)} USDT",
                    color = if (isWin) CryptoGreen else CryptoRed,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details Row: Entry Price -> Exit Price, ROI %, Time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceHighlight)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Giá vào -> Ra:", color = TextSecondary, fontSize = 9.5.sp)
                    Text(
                        text = "$${CryptoRepository.formatPrice(position.entryPrice)} -> $${CryptoRepository.formatPrice(position.exitPrice ?: position.currentPrice)}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Tỷ lệ ROI:", color = TextSecondary, fontSize = 9.5.sp)
                    val pnlPercent = position.pnlPercent ?: 0.0
                    Text(
                        text = "$pnlSign${String.format(Locale.US, "%.2f", pnlPercent)}%",
                        color = if (isWin) CryptoGreen else CryptoRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "Thời gian:", color = TextSecondary, fontSize = 9.5.sp)
                    Text(
                        text = timeText,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
