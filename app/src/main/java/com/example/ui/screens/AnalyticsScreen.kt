package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PeriodPnL
import com.example.model.TradePosition
import com.example.ui.MainViewModel
import com.example.ui.components.PnLBarChart
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
import java.util.Locale

@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    closedPositions: List<TradePosition>
) {
    var periodMode by remember { mutableStateOf("daily") } // "daily" or "weekly"

    val dailyPnL = remember(closedPositions) { viewModel.computeDailyPnL(closedPositions) }
    val weeklyPnL = remember(closedPositions) { viewModel.computeWeeklyPnL(closedPositions) }

    val activePeriodList = if (periodMode == "daily") dailyPnL else weeklyPnL

    // Performance Calculations
    val totalTrades = closedPositions.size
    val winTrades = closedPositions.count { (it.pnlUsdt ?: 0.0) > 0 }
    val lossTrades = closedPositions.count { (it.pnlUsdt ?: 0.0) < 0 }
    val winRate = if (totalTrades > 0) (winTrades.toDouble() / totalTrades) * 100.0 else 0.0
    val totalPnL = closedPositions.sumOf { it.pnlUsdt ?: 0.0 }
    val isNetProfit = totalPnL >= 0

    val maxWin = closedPositions.maxOfOrNull { it.pnlUsdt ?: 0.0 }?.coerceAtLeast(0.0) ?: 0.0
    val maxLoss = closedPositions.minOfOrNull { it.pnlUsdt ?: 0.0 }?.coerceAtMost(0.0) ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Assessment,
                contentDescription = null,
                tint = CryptoGold,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Thống Kê Lợi Nhuận",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Biểu đồ hiệu quả giao dịch theo ngày & tuần",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Period Toggle: Daily vs Weekly
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chu kỳ thống kê:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = periodMode == "daily",
                        onClick = { periodMode = "daily" },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Theo Ngày", fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("daily_filter_chip")
                    )

                    FilterChip(
                        selected = periodMode == "weekly",
                        onClick = { periodMode = "weekly" },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Theo Tuần", fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("weekly_filter_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive PnL Bar Chart
            PnLBarChart(
                items = activePeriodList,
                title = if (periodMode == "daily") "Lợi Nhuận Theo Từng Ngày (USDT)" else "Lợi Nhuận Theo Từng Tuần (USDT)"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Key Metrics Grid
            Text(
                text = "Tổng Quan Hiệu Suất",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Net Profit
                MetricCard(
                    title = "Tổng Lợi Nhuận",
                    value = "${if (isNetProfit) "+" else ""}$${String.format(Locale.US, "%.2f", totalPnL)}",
                    subtitle = "Vốn 0.5% số dư/lệnh",
                    valueColor = if (isNetProfit) CryptoGreen else CryptoRed,
                    icon = if (isNetProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    modifier = Modifier.weight(1f)
                )

                // Win Rate
                MetricCard(
                    title = "Tỷ Lệ Thắng",
                    value = "${String.format(Locale.US, "%.1f", winRate)}%",
                    subtitle = "$winTrades thắng / $lossTrades thua",
                    valueColor = CryptoGold,
                    icon = Icons.Default.EmojiEvents,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Best Trade
                MetricCard(
                    title = "Lãi Lớn Nhất",
                    value = "+$${String.format(Locale.US, "%.2f", maxWin)}",
                    subtitle = "Chốt lời TP tự động",
                    valueColor = CryptoGreen,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                )

                // Worst Trade / Max Drawdown
                MetricCard(
                    title = "Lỗ Lớn Nhất",
                    value = "$${String.format(Locale.US, "%.2f", maxLoss)}",
                    subtitle = "Cắt lỗ SL tự động",
                    valueColor = CryptoRed,
                    icon = Icons.Default.TrendingDown,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Period Breakdown Table
            if (activePeriodList.isNotEmpty()) {
                Text(
                    text = "Bảng Chi Tiết ${if (periodMode == "daily") "Theo Ngày" else "Theo Tuần"}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Thời Gian", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Số Lệnh", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Thắng/Tổng", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Lợi Nhuận (USDT)", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        activePeriodList.reversed().forEach { period ->
                            val isProf = period.pnlUsdt >= 0
                            val sign = if (isProf) "+" else ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = period.periodLabel,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${period.tradesCount} lệnh",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${period.winCount}/${period.tradesCount}",
                                    color = CryptoGold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "$sign$${String.format(Locale.US, "%.2f", period.pnlUsdt)}",
                                    color = if (isProf) CryptoGreen else CryptoRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    valueColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = TextSecondary, fontSize = 11.sp)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}
