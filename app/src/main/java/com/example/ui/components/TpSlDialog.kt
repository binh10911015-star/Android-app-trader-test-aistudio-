package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.CryptoRepository
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
fun TpSlDialog(
    symbol: String,
    isLong: Boolean,
    entryPrice: Double,
    markPrice: Double,
    leverage: Int = 1,
    initialTpPrice: Double? = null,
    initialSlPrice: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (tpPrice: Double?, slPrice: Double?) -> Unit
) {
    var tpInput by remember {
        mutableStateOf(if (initialTpPrice != null && initialTpPrice > 0.0) CryptoRepository.formatPrice(initialTpPrice) else "")
    }
    var slInput by remember {
        mutableStateOf(if (initialSlPrice != null && initialSlPrice > 0.0) CryptoRepository.formatPrice(initialSlPrice) else "")
    }

    val effectiveLeverage = leverage.coerceAtLeast(1)

    // Helper calculate price from target ROI %
    fun calcTpFromRoi(targetRoiPercent: Double) {
        val priceDiff = entryPrice * (targetRoiPercent / 100.0) / effectiveLeverage
        val targetPrice = if (isLong) entryPrice + priceDiff else entryPrice - priceDiff
        if (targetPrice > 0.0) {
            tpInput = CryptoRepository.formatPrice(targetPrice)
        }
    }

    fun calcSlFromRoi(riskRoiPercent: Double) {
        val priceDiff = entryPrice * (riskRoiPercent / 100.0) / effectiveLeverage
        val targetPrice = if (isLong) entryPrice - priceDiff else entryPrice + priceDiff
        if (targetPrice > 0.0) {
            slInput = CryptoRepository.formatPrice(targetPrice)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        title = {
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
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cài Đặt TP / SL",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Info Header Card
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (isLong) CryptoGreen.copy(alpha = 0.2f) else CryptoRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isLong) "LONG ${effectiveLeverage}x" else "SHORT ${effectiveLeverage}x",
                                        color = if (isLong) CryptoGreen else CryptoRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = symbol,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Giá vào (Entry):", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "$${CryptoRepository.formatPrice(entryPrice)}",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Giá hiện tại (Mark):", color = TextSecondary, fontSize = 10.sp)
                                Text(
                                    text = "$${CryptoRepository.formatPrice(markPrice)}",
                                    color = CryptoGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: TAKE PROFIT (CHỐT LỜI)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Take Profit (Chốt Lời)",
                        color = CryptoGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = tpInput,
                    onValueChange = { tpInput = it },
                    label = { Text("Giá TP (USDT)", fontSize = 11.sp) },
                    placeholder = { Text(if (isLong) "Cao hơn $entryPrice" else "Thấp hơn $entryPrice", fontSize = 10.sp, color = TextMuted) },
                    trailingIcon = {
                        if (tpInput.isNotEmpty()) {
                            IconButton(onClick = { tpInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGreen,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tp_price_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick ROI presets for TP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(10.0, 20.0, 50.0, 100.0).forEach { roi ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurfaceHighlight,
                            border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { calcTpFromRoi(roi) }
                        ) {
                            Text(
                                text = "+${roi.toInt()}%",
                                color = CryptoGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: STOP LOSS (CẮT LỖ)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = CryptoRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Stop Loss (Cắt Lỗ)",
                        color = CryptoRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = slInput,
                    onValueChange = { slInput = it },
                    label = { Text("Giá SL (USDT)", fontSize = 11.sp) },
                    placeholder = { Text(if (isLong) "Thấp hơn $entryPrice" else "Cao hơn $entryPrice", fontSize = 10.sp, color = TextMuted) },
                    trailingIcon = {
                        if (slInput.isNotEmpty()) {
                            IconButton(onClick = { slInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoRed,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sl_price_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick ROI presets for SL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(5.0, 10.0, 15.0, 25.0).forEach { roi ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurfaceHighlight,
                            border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { calcSlFromRoi(roi) }
                        ) {
                            Text(
                                text = "-${roi.toInt()}%",
                                color = CryptoRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Lệnh điều kiện sẽ được gửi lên Binance Futures với reduceOnly=true và closePosition=true để tự động đóng toàn bộ vị thế khi chạm mốc giá kích hoạt.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedTp = tpInput.trim().replace(",", ".").toDoubleOrNull()
                    val parsedSl = slInput.trim().replace(",", ".").toDoubleOrNull()
                    onConfirm(parsedTp, parsedSl)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_tpsl_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Xác Nhận Đặt TP / SL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary, fontSize = 12.sp)
            }
        }
    )
}
