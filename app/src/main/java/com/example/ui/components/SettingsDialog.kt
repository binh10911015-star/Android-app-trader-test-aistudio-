package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig
import com.example.model.TradeSettings
import com.example.model.TradingStrategyType
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    currentSettings: TradeSettings,
    apiConfig: BinanceApiConfig,
    onSave: (TradeSettings) -> Unit,
    onResetAccount: () -> Unit,
    onSaveApiCredentials: (
        String,
        String,
        String,
        Boolean,
        Boolean,
        (BinanceAccountVerificationResult) -> Unit
    ) -> Unit,
    onOpenApiConfig: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(currentSettings.selectedStrategy) }
    var volumeMultiplier by remember { mutableStateOf(currentSettings.volumeMultiplierThreshold) }
    var portfolioTpText by remember { mutableStateOf(currentSettings.portfolioTakeProfitPercent.toString()) }
    var volumePercent by remember { mutableStateOf(currentSettings.volumePercentOfBalance.toString()) }
    var fixedVolume by remember { mutableStateOf(currentSettings.fixedVolumeUsdt.toString()) }
    var apiKey by remember { mutableStateOf(apiConfig.apiKey) }
    var apiSecret by remember { mutableStateOf(apiConfig.apiSecret) }
    var accessPin by remember { mutableStateOf(apiConfig.accessPin) }
    var isTestnet by remember { mutableStateOf(apiConfig.isTestnet) }
    var liveTrading by remember { mutableStateOf(apiConfig.liveTradingEnabled) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = CryptoGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cài Đặt Chiến Lược Giao Dịch",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // SECTION 1: LỰA CHỌN CHIẾN LƯỢC
                Text(
                    text = "1. CHIẾN LƯỢC HOẠT ĐỘNG (AUTO-TRADE)",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Strategy 1: D1 SMA20 Breakout & Volume 2x
                val isD1Selected = selectedStrategy == TradingStrategyType.D1_SMA20_VOLUME
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isD1Selected) DarkSurfaceHighlight else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (isD1Selected) CryptoGold else DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStrategy = TradingStrategyType.D1_SMA20_VOLUME }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isD1Selected,
                                    onClick = { selectedStrategy = TradingStrategyType.D1_SMA20_VOLUME },
                                    colors = RadioButtonDefaults.colors(selectedColor = CryptoGold)
                                )
                                Text(
                                    text = "D1 SMA20 Breakout & Vol >= 2x",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CryptoGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "KHUYÊN DÙNG",
                                    color = CryptoGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Khung thời gian D1 (1 Ngày). Đường SMA20 tính căn cứ theo Giá Mở Cửa (Open). Mua khi Giá >= SMA20(D1) và Khối lượng nến D1 >= 2x hôm trước.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Strategy 2: 4H SuperTrend and 4H MA20
                val is4hSelected = selectedStrategy == TradingStrategyType.FOUR_HOUR_SUPERTREND_MA20
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (is4hSelected) DarkSurfaceHighlight else DarkSurfaceElevated,
                    border = BorderStroke(1.dp, if (is4hSelected) CryptoGold else DarkBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedStrategy = TradingStrategyType.FOUR_HOUR_SUPERTREND_MA20 }
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = is4hSelected,
                                onClick = { selectedStrategy = TradingStrategyType.FOUR_HOUR_SUPERTREND_MA20 },
                                colors = RadioButtonDefaults.colors(selectedColor = CryptoGold)
                            )
                            Text(
                                text = "4H SuperTrend và 4H MA20",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Khung thời gian 4H. Sử dụng chỉ báo SuperTrend(20,4) kết hợp MA20. Mua khi SuperTrend đảo chiều tăng và giá nằm trên MA20.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: HỆ SỐ BÙNG NỔ KHỐI LƯỢNG (D1)
                Text(
                    text = "2. HỆ SỐ BÙNG NỔ KHỐI LƯỢNG TỐI THIỂU",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1.5, 2.0, 2.5, 3.0).forEach { mult ->
                        val isMultSelected = volumeMultiplier == mult
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMultSelected) CryptoGold else DarkSurfaceElevated,
                            border = BorderStroke(1.dp, if (isMultSelected) CryptoGold else DarkBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { volumeMultiplier = mult }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mult}x",
                                    color = if (isMultSelected) DarkBg else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "Khối lượng nến D1 hiện tại phải đạt tối thiểu ${volumeMultiplier}x so với nến D1 hôm qua.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 3: CHỐT LỜI TOÀN DANH MỤC
                Text(
                    text = "3. MỤC TIÊU CHỐT LỜI TOÀN DANH MỤC (%)",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = portfolioTpText,
                    onValueChange = { portfolioTpText = it },
                    label = { Text("Mục tiêu ROI danh mục (%)") },
                    trailingIcon = { Text("%", color = CryptoGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Warning / Guidance Note Box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CryptoGold.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lưu ý: Chốt lời trên toàn danh mục (không chốt lời theo từng vị thế đơn lẻ). Bot tự động đóng đồng loạt toàn bộ vị thế khi tổng ROI đạt ngưỡng.",
                            color = CryptoGold,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 4: QUẢN LÝ VỐN VỊ THẾ
                Text(
                    text = "4. QUẢN LÝ VỐN & VOLUME MỖI LỆNH",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = volumePercent,
                        onValueChange = { volumePercent = it },
                        label = { Text("Tỷ lệ % số dư") },
                        trailingIcon = { Text("%", color = TextSecondary, modifier = Modifier.padding(end = 8.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoGold,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fixedVolume,
                        onValueChange = { fixedVolume = it },
                        label = { Text("Hoặc USDT cố định") },
                        trailingIcon = { Text("$", color = TextSecondary, modifier = Modifier.padding(end = 8.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoGold,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 5: PIN TRUY CẬP & MÔ PHỎNG
                Text(
                    text = "5. BẢO MẬT PIN & CHẾ ĐỘ GIAO DỊCH",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = accessPin,
                    onValueChange = { accessPin = it },
                    label = { Text("Mã PIN bảo mật xác nhận lệnh (4 số)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isTestnet,
                        onCheckedChange = { isTestnet = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sử dụng Binance Testnet Demo (Khuyên dùng)",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portTp = portfolioTpText.toDoubleOrNull() ?: currentSettings.portfolioTakeProfitPercent
                    val volPct = volumePercent.toDoubleOrNull() ?: currentSettings.volumePercentOfBalance
                    val fixedVol = fixedVolume.toDoubleOrNull() ?: currentSettings.fixedVolumeUsdt

                    val updatedSettings = currentSettings.copy(
                        selectedStrategy = selectedStrategy,
                        volumeMultiplierThreshold = volumeMultiplier,
                        portfolioTakeProfitPercent = portTp,
                        volumePercentOfBalance = volPct,
                        fixedVolumeUsdt = fixedVol
                    )
                    onSave(updatedSettings)
                    onSaveApiCredentials(apiKey, apiSecret, accessPin, isTestnet, liveTrading) { }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg)
            ) {
                Text("Lưu Cài Đặt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenApiConfig) {
                    Text("Cấu hình API", color = CryptoGold)
                }
                TextButton(onClick = onDismiss) {
                    Text("Đóng", color = TextSecondary)
                }
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
