package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig
import com.example.model.TradeSettings
import com.example.model.VolumeType
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
fun SettingsDialog(
    currentSettings: TradeSettings,
    apiConfig: BinanceApiConfig = BinanceApiConfig(),
    onSave: (TradeSettings) -> Unit,
    onResetAccount: () -> Unit,
    onSaveApiCredentials: ((apiKey: String, secret: String, pin: String, isTestnet: Boolean, liveTrading: Boolean, onResult: (BinanceAccountVerificationResult) -> Unit) -> Unit)? = null,
    onSaveProxyConfig: ((host: String, port: Int) -> Unit)? = null,
    onOpenApiConfig: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Volume configuration: 2 choices (% số dư HOẶC volume cố định)
    var selectedVolumeType by remember { mutableStateOf(currentSettings.volumeType) }
    var volumePercentText by remember { mutableStateOf(currentSettings.volumePercentOfBalance.toString()) }
    var fixedVolumeText by remember { mutableStateOf(currentSettings.fixedVolumeUsdt.toString()) }

    // Stop Loss configuration: Yêu cầu không thiết lập cắt lỗ
    var disableStopLoss by remember { mutableStateOf(currentSettings.stopLossPercent == null) }
    var slText by remember { mutableStateOf(currentSettings.stopLossPercent?.toString() ?: "") }

    // Take Profit configuration
    var tpText by remember { mutableStateOf(currentSettings.takeProfitPercent?.toString() ?: "") }

    // Signal Filters: Giá cao hơn SMA20, có hoặc không SuperTrend(20,4)
    var useSuperTrendFilter by remember { mutableStateOf(currentSettings.useSuperTrendFilter) }
    var selectedTimeframe by remember { mutableStateOf(currentSettings.timeframe) }

    // Binance API Config inside Bot Settings
    var showApiSection by remember { mutableStateOf(!apiConfig.isConnected) }
    var apiKeyInput by remember { mutableStateOf(apiConfig.apiKey) }
    var apiSecretInput by remember { mutableStateOf(apiConfig.apiSecret) }
    var accessPinInput by remember { mutableStateOf(apiConfig.accessPin) }
    var isTestnet by remember { mutableStateOf(apiConfig.isTestnet) }
    var proxyHostInput by remember { mutableStateOf(apiConfig.proxyHost) }
    var proxyPortInput by remember { mutableStateOf(if (apiConfig.proxyPort > 0) apiConfig.proxyPort.toString() else "") }
    var proxySavedMessage by remember { mutableStateOf<String?>(null) }
    val composeClipboard = LocalClipboardManager.current
    var isApiKeyVisible by remember { mutableStateOf(true) }
    var isSecretVisible by remember { mutableStateOf(true) }
    var isPinVisible by remember { mutableStateOf(false) }
    var quickPasteTarget by remember { mutableStateOf<String?>(null) }
    var isVerifyingApi by remember { mutableStateOf(false) }
    var apiStatusMessage by remember { mutableStateOf<String?>(null) }
    var isApiSuccess by remember { mutableStateOf(apiConfig.isConnected) }

    var showResetConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    if (quickPasteTarget != null) {
        when (quickPasteTarget) {
            "API_KEY" -> {
                QuickPasteModalDialog(
                    title = "Dán Binance API Key",
                    hintText = "Nhấn giữ hoặc bấm phím Ctrl+V để dán API Key (khoảng 64 ký tự)...",
                    initialValue = apiKeyInput,
                    onConfirm = {
                        apiKeyInput = it
                        isApiKeyVisible = true
                        Toast.makeText(context, "Đã áp dụng API Key thành công!", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { quickPasteTarget = null }
                )
            }
            "SECRET_KEY" -> {
                QuickPasteModalDialog(
                    title = "Dán Binance Secret Key",
                    hintText = "Nhấn giữ hoặc bấm phím Ctrl+V để dán Secret Key bí mật...",
                    initialValue = apiSecretInput,
                    onConfirm = {
                        apiSecretInput = it
                        isSecretVisible = true
                        Toast.makeText(context, "Đã áp dụng Secret Key thành công!", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { quickPasteTarget = null }
                )
            }
            "ACCESS_PIN" -> {
                QuickPasteModalDialog(
                    title = "Dán Binance Access PIN",
                    hintText = "Nhập hoặc dán mã PIN bảo mật vào lệnh...",
                    initialValue = accessPinInput,
                    onConfirm = {
                        accessPinInput = it
                        isPinVisible = true
                        Toast.makeText(context, "Đã áp dụng mã PIN thành công!", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { quickPasteTarget = null }
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Khôi phục tài khoản?", color = TextPrimary) },
            text = {
                Text(
                    "Số dư mô phỏng sẽ được thiết lập lại 10,000 USDT và toàn bộ lịch sử lệnh sẽ được xóa sạch.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirm = false
                        onResetAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed)
                ) {
                    Text("Xác nhận Khôi phục")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = CryptoGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cấu Hình Bot Tự Động",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                // Section 1: API BINANCE CONNECTION FOR DEMO/REAL FUTURES
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, if (apiConfig.isConnected) CryptoGreen.copy(alpha = 0.5f) else CryptoGold.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kết Nối API Binance",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Connection Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (apiConfig.isConnected || isApiSuccess) CryptoGreen.copy(alpha = 0.15f) else DarkSurfaceHighlight,
                                border = BorderStroke(1.dp, if (apiConfig.isConnected || isApiSuccess) CryptoGreen else DarkBorder)
                            ) {
                                Text(
                                    text = if (apiConfig.isConnected || isApiSuccess)
                                        (if (isTestnet) "DEMO TESTNET ✓" else "MAINNET ✓")
                                    else "CHẾ ĐỘ MÔ PHỎNG",
                                    color = if (apiConfig.isConnected || isApiSuccess) CryptoGreen else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kết nối API để bot chạy thử nghiệm Demo trên Binance Futures Testnet hoặc tài khoản thực.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Toggle expand/collapse form
                        OutlinedButton(
                            onClick = { showApiSection = !showApiSection },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (showApiSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showApiSection) "Thu gọn cài đặt API" else "Nhập thông tin API Key & PIN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        AnimatedVisibility(
                            visible = showApiSection,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                // Network Choice
                                Text("Mạng Binance Futures:", color = TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = isTestnet,
                                        onClick = { isTestnet = true },
                                        label = { Text("Testnet (Demo Futures)", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CryptoGold,
                                            selectedLabelColor = DarkBg
                                        )
                                    )
                                    FilterChip(
                                        selected = !isTestnet,
                                        onClick = { isTestnet = false },
                                        label = { Text("Mainnet (Thực)", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CryptoGold,
                                            selectedLabelColor = DarkBg
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Quick action: sample testnet fill
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Khóa API & Mật mã xác thực:", color = TextSecondary, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Toggle Show/Hide All Keys
                                        OutlinedButton(
                                            onClick = {
                                                val both = isApiKeyVisible && isSecretVisible
                                                isApiKeyVisible = !both
                                                isSecretVisible = !both
                                                Toast.makeText(
                                                    context,
                                                    if (!both) "Đã hiển thị toàn bộ API Key và Secret Key!" else "Đã ẩn API Key và Secret Key!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, if (isApiKeyVisible && isSecretVisible) CryptoGreen.copy(alpha = 0.5f) else CryptoGold.copy(alpha = 0.5f)),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isApiKeyVisible && isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = if (isApiKeyVisible && isSecretVisible) CryptoGreen else CryptoGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = if (isApiKeyVisible && isSecretVisible) "Ẩn khóa" else "Hiện khóa",
                                                color = if (isApiKeyVisible && isSecretVisible) CryptoGreen else CryptoGold,
                                                fontSize = 10.sp
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                apiKeyInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY
                                                apiSecretInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_SECRET
                                                accessPinInput = "1234"
                                                isTestnet = false
                                                isApiKeyVisible = true
                                                isSecretVisible = true
                                                isPinVisible = true
                                                Toast.makeText(context, "Đã điền khóa Binance Mainnet thực tế!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Mainnet", color = CryptoGold, fontSize = 10.sp)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                apiKeyInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_KEY
                                                apiSecretInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_SECRET
                                                accessPinInput = "1234"
                                                isTestnet = true
                                                isApiKeyVisible = true
                                                isSecretVisible = true
                                                isPinVisible = true
                                                Toast.makeText(context, "Đã điền khóa Binance Futures Testnet!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, TextMuted),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Testnet", color = TextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // API Key Input
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("Binance API Key", fontSize = 11.sp) },
                                    placeholder = { Text("Dán API Key...", fontSize = 11.sp, color = TextMuted) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                                Icon(
                                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (isApiKeyVisible) "Ẩn Key" else "Hiện Key",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            // Direct paste
                                            IconButton(onClick = {
                                                val clip = readSafeClipboardText(context, composeClipboard)
                                                if (!clip.isNullOrBlank()) {
                                                    apiKeyInput = clip
                                                    isApiKeyVisible = true
                                                    Toast.makeText(context, "Đã dán API Key!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Bộ nhớ tạm trống. Mở hộp thoại dán...", Toast.LENGTH_SHORT).show()
                                                    quickPasteTarget = "API_KEY"
                                                }
                                            }) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán", tint = CryptoGold, modifier = Modifier.size(16.dp))
                                            }
                                            // Quick Paste Dialog button
                                            IconButton(onClick = { quickPasteTarget = "API_KEY" }) {
                                                Icon(Icons.Default.Assignment, contentDescription = "Hộp thoại dán", tint = CryptoGold, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
                                    singleLine = false,
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CryptoGold,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = CryptoGold,
                                        unfocusedLabelColor = TextSecondary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("dialog_api_key_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Secret Key Input
                                OutlinedTextField(
                                    value = apiSecretInput,
                                    onValueChange = { apiSecretInput = it },
                                    label = { Text("Binance Secret Key", fontSize = 11.sp) },
                                    placeholder = { Text("Dán Secret Key...", fontSize = 11.sp, color = TextMuted) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                                Icon(
                                                    imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            // Direct paste
                                            IconButton(onClick = {
                                                val clip = readSafeClipboardText(context, composeClipboard)
                                                if (!clip.isNullOrBlank()) {
                                                    apiSecretInput = clip
                                                    isSecretVisible = true
                                                    Toast.makeText(context, "Đã dán Secret Key!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Bộ nhớ tạm trống. Mở hộp thoại dán...", Toast.LENGTH_SHORT).show()
                                                    quickPasteTarget = "SECRET_KEY"
                                                }
                                            }) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán", tint = CryptoGold, modifier = Modifier.size(16.dp))
                                            }
                                            // Quick Paste Dialog button
                                            IconButton(onClick = { quickPasteTarget = "SECRET_KEY" }) {
                                                Icon(Icons.Default.Assignment, contentDescription = "Hộp thoại dán", tint = CryptoGold, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    visualTransformation = if (isSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false),
                                    singleLine = false,
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CryptoGold,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = CryptoGold,
                                        unfocusedLabelColor = TextSecondary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("dialog_api_secret_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Binance Access PIN Input (cần nhập mới được vào lệnh)
                                OutlinedTextField(
                                    value = accessPinInput,
                                    onValueChange = { accessPinInput = it },
                                    label = { Text("Binance Access PIN (Mã bảo mật)", fontSize = 11.sp) },
                                    placeholder = { Text("Nhập PIN (VD: 1234)...", fontSize = 11.sp, color = TextMuted) },
                                    supportingText = {
                                        Text(
                                            text = "Bắt buộc nhập mã PIN này mới được phép vào lệnh bot/thủ công.",
                                            color = CryptoGold,
                                            fontSize = 10.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                                Icon(
                                                    imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(onClick = {
                                                val clip = readSafeClipboardText(context, composeClipboard)
                                                if (!clip.isNullOrBlank()) {
                                                    accessPinInput = clip
                                                    isPinVisible = true
                                                    Toast.makeText(context, "Đã dán PIN!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    quickPasteTarget = "ACCESS_PIN"
                                                }
                                            }) {
                                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán PIN", tint = CryptoGold, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CryptoGold,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedLabelColor = CryptoGold,
                                        unfocusedLabelColor = TextSecondary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("dialog_access_pin_input")
                                )

                                // Hiển thị chi tiết API key và secret key
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Chi Tiết Khóa Đang Nhập",
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isApiKeyVisible && isSecretVisible) "Đang Hiển Thị" else "Đang Ẩn",
                                                color = if (isApiKeyVisible && isSecretVisible) CryptoGreen else TextMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // API Key Text
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("API Key:", color = CryptoGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                if (apiKeyInput.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("(${apiKeyInput.length} ký tự)", color = TextMuted, fontSize = 9.sp)
                                                }
                                            }
                                            if (apiKeyInput.isNotBlank()) {
                                                TextButton(
                                                    onClick = { copyToClipboard(context, apiKeyInput, "API Key") },
                                                    modifier = Modifier.height(20.dp)
                                                ) {
                                                    Text("Sao chép", color = CryptoGold, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = DarkBg,
                                            border = BorderStroke(1.dp, DarkBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (apiKeyInput.isBlank()) "(Chưa nhập API Key)" else if (isApiKeyVisible) apiKeyInput else "••••••••••••••••••••••••••••••••••••••••",
                                                color = if (apiKeyInput.isBlank()) TextMuted else TextPrimary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Secret Key Text
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Secret Key:", color = CryptoGold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                if (apiSecretInput.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("(${apiSecretInput.length} ký tự)", color = TextMuted, fontSize = 9.sp)
                                                }
                                            }
                                            if (apiSecretInput.isNotBlank()) {
                                                TextButton(
                                                    onClick = { copyToClipboard(context, apiSecretInput, "Secret Key") },
                                                    modifier = Modifier.height(20.dp)
                                                ) {
                                                    Text("Sao chép", color = CryptoGold, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = DarkBg,
                                            border = BorderStroke(1.dp, DarkBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (apiSecretInput.isBlank()) "(Chưa nhập Secret Key)" else if (isSecretVisible) apiSecretInput else "••••••••••••••••••••••••••••••••••••••••",
                                                color = if (apiSecretInput.isBlank()) TextMuted else TextPrimary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }

                                if (apiStatusMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = apiStatusMessage ?: "",
                                        color = if (isApiSuccess) CryptoGreen else CryptoRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Cấu hình Proxy trong Dialog
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface,
                                    border = BorderStroke(1.dp, if (apiConfig.isProxyEnabled) CryptoGreen.copy(alpha = 0.4f) else DarkBorder),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Custom Proxy (Socks5/HTTP)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (apiConfig.isProxyEnabled) {
                                                Text("Đang bật", color = CryptoGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Cấu hình Proxy trung gian giúp định tuyến API Mainnet qua VPS sạch IP khi gặp lỗi restricted location",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Proxy Host
                                        OutlinedTextField(
                                            value = proxyHostInput,
                                            onValueChange = { proxyHostInput = it; proxySavedMessage = null },
                                            label = { Text("Proxy Host", fontSize = 11.sp) },
                                            placeholder = { Text("123.45.67.89 hoặc vps.domain.com", fontSize = 10.sp, color = TextMuted) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("dialog_proxy_host_input")
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Proxy Port
                                        OutlinedTextField(
                                            value = proxyPortInput,
                                            onValueChange = { proxyPortInput = it.filter { c -> c.isDigit() }; proxySavedMessage = null },
                                            label = { Text("Proxy Port", fontSize = 11.sp) },
                                            placeholder = { Text("1080 hoặc 8080", fontSize = 10.sp, color = TextMuted) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("dialog_proxy_port_input")
                                        )

                                        if (proxySavedMessage != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(proxySavedMessage!!, color = CryptoGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = {
                                                val port = proxyPortInput.trim().toIntOrNull() ?: 0
                                                val host = proxyHostInput.trim()
                                                onSaveProxyConfig?.invoke(host, port)
                                                proxySavedMessage = if (host.isNotEmpty() && port > 0) "Đã lưu Proxy $host:$port!" else "Đã tắt Proxy, dùng kết nối trực tiếp"
                                                Toast.makeText(context, proxySavedMessage, Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth().height(34.dp).testTag("dialog_save_proxy_button"),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg)
                                        ) {
                                            Text("Lưu Cấu Hình Proxy", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Button to verify and save API
                                if (onSaveApiCredentials != null) {
                                    Button(
                                        onClick = {
                                            isVerifyingApi = true
                                            apiStatusMessage = "Đang kiểm tra kết nối tài khoản..."
                                            onSaveApiCredentials(
                                                apiKeyInput.trim(),
                                                apiSecretInput.trim(),
                                                accessPinInput.trim(),
                                                isTestnet,
                                                false // default paper/demo verification
                                            ) { result ->
                                                isVerifyingApi = false
                                                if (result.isSuccess) {
                                                    isApiSuccess = true
                                                    apiStatusMessage = "Kết nối thành công! Số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT"
                                                } else {
                                                    isApiSuccess = false
                                                    apiStatusMessage = "Lỗi kết nối: ${result.errorMessage ?: "Không thể kết nối sàn Binance"}"
                                                }
                                            }
                                        },
                                        enabled = apiKeyInput.isNotBlank() && apiSecretInput.isNotBlank() && !isVerifyingApi,
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CryptoGold,
                                            contentColor = DarkBg
                                        )
                                    ) {
                                        if (isVerifyingApi) {
                                            CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Đang kiểm tra kết nối...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Kiểm Tra & Lưu API Binance", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = onOpenApiConfig,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mở toàn màn hình Quản lý API Key", color = CryptoGold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: VOLUME CONFIGURATION (2 CHOICES: % SỐ DƯ HOẶC VOLUME CỐ ĐỊNH)
                Text(
                    text = "Tỷ Lệ Volume Mỗi Lệnh:",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Chọn phương thức phân bổ volume cho từng vị thế được mở:",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: % Số Dư
                    FilterChip(
                        selected = selectedVolumeType == VolumeType.PERCENT_OF_BALANCE,
                        onClick = { selectedVolumeType = VolumeType.PERCENT_OF_BALANCE },
                        label = { Text("% Số Dư", fontSize = 11.sp) },
                        leadingIcon = {
                            if (selectedVolumeType == VolumeType.PERCENT_OF_BALANCE) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.weight(1f).testTag("chip_volume_percent")
                    )

                    // Option 2: Volume Cố Định (USDT)
                    FilterChip(
                        selected = selectedVolumeType == VolumeType.FIXED_AMOUNT,
                        onClick = { selectedVolumeType = VolumeType.FIXED_AMOUNT },
                        label = { Text("Volume Cố Định", fontSize = 11.sp) },
                        leadingIcon = {
                            if (selectedVolumeType == VolumeType.FIXED_AMOUNT) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.weight(1f).testTag("chip_volume_fixed")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedVolumeType == VolumeType.PERCENT_OF_BALANCE) {
                    val pct = volumePercentText.toDoubleOrNull() ?: 0.5
                    val currentVol = currentSettings.simulatedBalanceUsdt * (pct / 100.0)
                    OutlinedTextField(
                        value = volumePercentText,
                        onValueChange = { volumePercentText = it },
                        label = { Text("Tỷ lệ % số dư mỗi lệnh") },
                        placeholder = { Text("0.5") },
                        supportingText = {
                            Text(
                                text = "Ước tính: ~$${String.format(Locale.US, "%.1f", currentVol)} USDT (với số dư $${String.format(Locale.US, "%,.2f", currentSettings.simulatedBalanceUsdt)} USDT)",
                                color = CryptoGold,
                                fontSize = 11.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoGold,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CryptoGold,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("volume_percent_input")
                    )
                } else {
                    val fixedVal = fixedVolumeText.toDoubleOrNull() ?: 50.0
                    OutlinedTextField(
                        value = fixedVolumeText,
                        onValueChange = { fixedVolumeText = it },
                        label = { Text("Volume cố định mỗi lệnh (USDT)") },
                        placeholder = { Text("50.0") },
                        supportingText = {
                            Text(
                                text = "Mỗi vị thế được mở chính xác $${String.format(Locale.US, "%.1f", fixedVal)} USDT không phụ thuộc số dư ví.",
                                color = CryptoGold,
                                fontSize = 11.sp
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoGold,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CryptoGold,
                            unfocusedLabelColor = TextSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("fixed_volume_input")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 3: BỘ LỌC ĐIỀU KIỆN TÍN HIỆU
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bộ Lọc Điều Kiện Tín Hiệu",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Điều kiện 1: Giá cao hơn SMA20
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "1. Giá cao hơn SMA20 (Bắt buộc)",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Nến đóng cửa phải nằm trên đường trung bình động SMA 20",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CryptoGreen.copy(alpha = 0.15f)
                            ) {
                                Text("BẬT", color = CryptoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Điều kiện 2: Có hoặc không SuperTrend(20,4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. SuperTrend (20, 4)",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (useSuperTrendFilter)
                                        "Có dùng: Yêu cầu cả MA20 > SuperTrend(20,4)"
                                    else
                                        "Không dùng: Chỉ cần Giá > SMA20 là kích hoạt tín hiệu",
                                    color = if (useSuperTrendFilter) CryptoGold else TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = useSuperTrendFilter,
                                onCheckedChange = { useSuperTrendFilter = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBg,
                                    checkedTrackColor = CryptoGold,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurfaceHighlight
                                ),
                                modifier = Modifier.testTag("supertrend_filter_switch")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 4: QUẢN LÝ LỆNH: CẮT LỖ (KHÔNG THIẾT LẬP CẮT LỖ) VÀ CHỐT LỜI TP
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Không thiết lập cắt lỗ switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Không thiết lập cắt lỗ",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (disableStopLoss)
                                        "Đang bật: Bot không đặt lệnh Stop Loss, để vị thế chạy theo xu hướng hoặc đóng thủ công / chốt lời TP"
                                    else
                                        "Đang tắt: Sẽ kích hoạt cắt lỗ theo tỷ lệ % bên dưới",
                                    color = if (disableStopLoss) CryptoGreen else CryptoRed,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = disableStopLoss,
                                onCheckedChange = { disableStopLoss = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = DarkBg,
                                    checkedTrackColor = CryptoGreen,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = DarkSurfaceHighlight
                                ),
                                modifier = Modifier.testTag("disable_sl_switch")
                            )
                        }

                        if (!disableStopLoss) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = slText,
                                onValueChange = { slText = it },
                                label = { Text("Cắt Lỗ SL (%)") },
                                placeholder = { Text("Ví dụ: 3.0", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoRed,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedLabelColor = CryptoRed,
                                    unfocusedLabelColor = TextSecondary
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("sl_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Take Profit Input
                        OutlinedTextField(
                            value = tpText,
                            onValueChange = { tpText = it },
                            label = { Text("Chốt Lời TP (%)") },
                            placeholder = { Text("Để trống = Không đặt TP", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp) },
                            supportingText = {
                                Text(
                                    if (tpText.trim().isEmpty()) "Để trống: Không thiết lập TP" else "Tự động chốt lời khi đạt +${tpText.trim()}%",
                                    color = if (tpText.trim().isEmpty()) CryptoGold else CryptoGreen,
                                    fontSize = 10.sp
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoGreen,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = CryptoGreen,
                                unfocusedLabelColor = TextSecondary
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("tp_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Khung giờ áp dụng
                Text("Khung giờ nến phân tích:", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTimeframe == "4h",
                        onClick = { selectedTimeframe = "4h" },
                        label = { Text("H4 (4 Giờ)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg
                        )
                    )
                    FilterChip(
                        selected = selectedTimeframe == "1d",
                        onClick = { selectedTimeframe = "1d" },
                        label = { Text("D1 (1 Ngày)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                    border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Khôi phục tài khoản 10,000 USDT", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val volPct = volumePercentText.trim().toDoubleOrNull() ?: 0.5
                    val fixedVol = fixedVolumeText.trim().toDoubleOrNull() ?: 50.0
                    val tp = if (tpText.trim().isEmpty()) null else tpText.trim().toDoubleOrNull()
                    val sl = if (disableStopLoss || slText.trim().isEmpty()) null else slText.trim().toDoubleOrNull()

                    val updated = currentSettings.copy(
                        volumeType = selectedVolumeType,
                        volumePercentOfBalance = if (volPct > 0.0) volPct else 0.5,
                        fixedVolumeUsdt = if (fixedVol > 0.0) fixedVol else 50.0,
                        capitalPerTradeUsdt = when (selectedVolumeType) {
                            VolumeType.PERCENT_OF_BALANCE -> currentSettings.simulatedBalanceUsdt * (volPct / 100.0)
                            VolumeType.FIXED_AMOUNT -> fixedVol
                        },
                        takeProfitPercent = tp,
                        stopLossPercent = sl,
                        useSuperTrendFilter = useSuperTrendFilter,
                        timeframe = selectedTimeframe
                    )
                    onSave(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoGold, contentColor = DarkBg)
            ) {
                Text("Lưu Cấu Hình Bot", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
