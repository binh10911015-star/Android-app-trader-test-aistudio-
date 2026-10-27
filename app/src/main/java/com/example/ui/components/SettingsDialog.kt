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
    var fixedVolume by remember { mutableStateOf(if (currentSettings.capitalPerTradeUsdt > 0.0) currentSettings.capitalPerTradeUsdt.toString() else "5.1") }
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
                    text = "Cài Đặt Bot SMA20 D1 (RR 1:3)",
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
                // SECTION 1: THÔNG TIN CHIẾN LƯỢC
                Text(
                    text = "1. CHIẾN LƯỢC HOẠT ĐỘNG (AUTO-TRADE)",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceHighlight,
                    border = BorderStroke(1.dp, CryptoGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = true,
                                    onClick = { },
                                    colors = RadioButtonDefaults.colors(selectedColor = CryptoGold)
                                )
                                Text(
                                    text = "SMA20 D1 (RR 1:3)",
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
                                    text = "ĐANG CHẠY",
                                    color = CryptoGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Quét tự động: 07:00 AM UTC+7 (Đóng nến D1 lúc 00:00 UTC)\n• Điều kiện vào: Nến t-1 tăng (Close > Open), Close > SMA20 và Low <= SMA20\n• Stop Loss: Đáy nến t-1 (Low[t-1])\n• Take Profit: Cố định 3R (Entry + 3R)\n• Danh mục: Mở tất cả tín hiệu, không giới hạn trần danh mục. Loại trừ BTC & Stablecoins.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: QUẢN LÝ VỐN VỊ THẾ
                Text(
                    text = "2. QUẢN LÝ VỐN MỖI LỆNH (LONG CROSS 1X)",
                    color = CryptoGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = fixedVolume,
                    onValueChange = { fixedVolume = it },
                    label = { Text("Vốn danh nghĩa mỗi lệnh (USDT)") },
                    trailingIcon = { Text("USDT", color = CryptoGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp)) },
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5.1 to "5.1$ (Chuẩn)", 10.0 to "10$", 20.0 to "20$", 50.0 to "50$").forEach { (amt, label) ->
                        val isSelected = fixedVolume == amt.toString()
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { fixedVolume = amt.toString() },
                            color = if (isSelected) CryptoGold.copy(alpha = 0.25f) else DarkSurfaceElevated,
                            border = BorderStroke(0.5.dp, if (isSelected) CryptoGold else DarkBorder)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CryptoGold else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 3: PIN TRUY CẬP & MÔI TRƯỜNG BINANCE
                Text(
                    text = "3. BẢO MẬT PIN & MÔI TRƯỜNG BINANCE",
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
                    val fixedVol = fixedVolume.toDoubleOrNull() ?: 5.1
                    val updatedSettings = currentSettings.copy(
                        selectedStrategy = TradingStrategyType.SMA20_D1_RR3,
                        capitalPerTradeUsdt = fixedVol,
                        fixedVolumeUsdt = fixedVol,
                        rrRatio = 3.0,
                        smaPeriod = 20,
                        maxPortfolioAllocation = 1.0,
                        enableTrailingStop = false,
                        enableFloorLock = false,
                        enableBtcMacroFilter = false,
                        enableSmartTimeout = false
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
