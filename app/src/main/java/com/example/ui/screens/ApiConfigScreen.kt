package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BinanceAccountVerificationResult
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
import java.util.Locale

@Composable
fun ApiConfigScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    val apiConfig by viewModel.apiConfig.collectAsState()
    val isVerifying by viewModel.isVerifyingApi.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    // Form states initialized with current config or default testnet credentials
    val defaultInitKey = if (apiConfig.apiKey.isNotBlank()) apiConfig.apiKey else com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_KEY
    val defaultInitSecret = if (apiConfig.apiSecret.isNotBlank()) apiConfig.apiSecret else com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_SECRET

    var apiKey by remember(apiConfig) { mutableStateOf(defaultInitKey) }
    var apiSecret by remember(apiConfig) { mutableStateOf(defaultInitSecret) }
    var accessPin by remember(apiConfig) { mutableStateOf(apiConfig.accessPin.ifBlank { "1234" }) }
    var isTestnet by remember(apiConfig) { mutableStateOf(apiConfig.isTestnet) }
    var liveTradingEnabled by remember(apiConfig) { mutableStateOf(apiConfig.liveTradingEnabled) }

    var showSecret by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<BinanceAccountVerificationResult?>(null) }
    var showSuccessToast by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = CryptoGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Kết Nối Binance Futures API",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Giao thức HMAC-SHA256 bảo mật trực tiếp",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (apiConfig.isConnected) CryptoGreen.copy(alpha = 0.18f) else DarkSurfaceHighlight
            ) {
                Text(
                    text = if (apiConfig.isConnected) "ĐÃ KẾT NỐI ✓" else "CHƯA KẾT NỐI",
                    color = if (apiConfig.isConnected) CryptoGreen else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Connection Status Banner
        if (apiConfig.isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CryptoGreen.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, CryptoGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(CryptoGreen.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CryptoGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (apiConfig.isTestnet) "Tài Khoản Testnet Demo Binance" else "Tài Khoản Live Mainnet Binance",
                            color = CryptoGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val bal = apiConfig.totalWalletBalance
                        Text(
                            text = if (bal != null) "Số dư ví: $${String.format(Locale.US, "%,.2f", bal)} USDT" else "Đã kết nối thành công",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Environment Selector: Testnet vs Mainnet
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "1. Chọn Môi Trường Binance",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Testnet Option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isTestnet = true }
                            .border(
                                1.dp,
                                if (isTestnet) CryptoGold else DarkBorder,
                                RoundedCornerShape(10.dp)
                            ),
                        color = if (isTestnet) CryptoGold.copy(alpha = 0.12f) else DarkSurface
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (isTestnet) CryptoGold else DarkSurfaceHighlight, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Testnet (Demo)",
                                    color = if (isTestnet) CryptoGold else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "testnet.binancefuture.com\nTiền giả lập, không rủi ro",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // Mainnet Option
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isTestnet = false }
                            .border(
                                1.dp,
                                if (!isTestnet) CryptoGreen else DarkBorder,
                                RoundedCornerShape(10.dp)
                            ),
                        color = if (!isTestnet) CryptoGreen.copy(alpha = 0.12f) else DarkSurface
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (!isTestnet) CryptoGreen else DarkSurfaceHighlight, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live Mainnet",
                                    color = if (!isTestnet) CryptoGreen else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "fapi.binance.com\nTiền thật tài khoản Binance",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // API Credentials Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "2. Thông Tin Khóa API (API Credentials)",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                // API Key Field
                Text(text = "Binance API Key", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    placeholder = { Text("Nhập 64 ký tự API Key...", fontSize = 12.sp) },
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (apiKey.isNotEmpty()) {
                                IconButton(onClick = { apiKey = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextMuted)
                                }
                            }
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { apiKey = it.trim() }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán", tint = CryptoGold)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // API Secret Field
                Text(text = "Binance API Secret", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it.trim() },
                    placeholder = { Text("Nhập 64 ký tự Secret Key...", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecret) "Ẩn Secret" else "Hiện Secret",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_secret_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Access PIN Field (Optional extra security layer)
                Text(text = "Mã PIN Bảo Vệ (4-6 số)", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = accessPin,
                    onValueChange = { if (it.length <= 6 && it.all { char -> char.isDigit() }) accessPin = it },
                    placeholder = { Text("Nhập mã PIN xác nhận vào lệnh (mặc định: 1234)", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(16.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("access_pin_input")
                )

                if (!isTestnet) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceHighlight)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cho phép giao dịch tiền thật (Live Trading)",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lệnh mua/bán sẽ khớp trực tiếp trên Binance",
                                color = CryptoRed.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = liveTradingEnabled,
                            onCheckedChange = { liveTradingEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBg,
                                checkedTrackColor = CryptoGreen,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurface
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Buttons: Direct Test Connection & Save
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Button: Test Direct Binance Connection
            Button(
                onClick = {
                    if (apiKey.isEmpty() || apiSecret.isEmpty()) {
                        verificationResult = BinanceAccountVerificationResult(
                            isSuccess = false,
                            errorMessage = "Vui lòng nhập đầy đủ API Key và API Secret trước khi kiểm tra."
                        )
                    } else {
                        viewModel.saveAndVerifyApiCredentials(
                            apiKey = apiKey,
                            apiSecret = apiSecret,
                            accessPin = accessPin,
                            isTestnet = isTestnet,
                            liveTradingEnabled = liveTradingEnabled
                        ) { result ->
                            verificationResult = result
                        }
                    }
                },
                enabled = !isVerifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_api_connection_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CryptoGold,
                    contentColor = DarkBg
                )
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        color = DarkBg,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đang kết nối máy chủ Binance Futures...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ KIỂM TRA KẾT NỐI TRỰC TIẾP BINANCE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Button: Clear credentials
            if (apiConfig.isConfigured || apiKey.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        apiKey = ""
                        apiSecret = ""
                        accessPin = "1234"
                        verificationResult = null
                        viewModel.clearApiCredentials()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                    border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Xóa thông tin API đã lưu", fontSize = 12.sp)
                }
            }
        }

        // Direct Test Connection Result Card
        verificationResult?.let { result ->
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isSuccess) CryptoGreen.copy(alpha = 0.12f) else CryptoRed.copy(alpha = 0.12f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (result.isSuccess) CryptoGreen.copy(alpha = 0.5f) else CryptoRed.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (result.isSuccess) CryptoGreen else CryptoRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (result.isSuccess) "KẾT NỐI THÀNH CÔNG!" else "LỖI KẾT NỐI BINANCE",
                            color = if (result.isSuccess) CryptoGreen else CryptoRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (result.isSuccess) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "• Tổng số dư ví: $${String.format(Locale.US, "%,.2f", result.totalWalletBalance)} USDT",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Số dư khả dụng: $${String.format(Locale.US, "%,.2f", result.availableBalance)} USDT",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "• Quyền giao dịch Futures: ${if (result.canTrade) "HỢP LỆ ✓" else "Chưa kích hoạt"}",
                                color = if (result.canTrade) CryptoGreen else CryptoGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• Môi trường: ${if (isTestnet) "Binance Futures Testnet (Demo)" else "Binance Futures Live Mainnet"}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Text(
                            text = result.errorMessage.ifEmpty { "Không thể xác thực khóa API với Binance." },
                            color = CryptoRed,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gợi ý: Kiểm tra lại API Key/Secret, chọn đúng môi trường Testnet/Mainnet và đảm bảo API đã tích chọn 'Enable Futures'.",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Guide Card for Binance API Security
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hướng Dẫn Cấu Hình An Toàn",
                        color = CryptoGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Truy cập Binance > Quản lý API (API Management)\n" +
                            "2. Tạo khóa mới (System generated)\n" +
                            "3. CHỈ tích chọn: 'Đọc (Read Only)' và 'Cho phép Hợp đồng tương lai (Enable Futures)'\n" +
                            "4. TUYỆT ĐỐI KHÔNG tích chọn 'Cho phép rút tiền (Enable Withdrawals)'\n" +
                            "5. API Key được lưu trữ mã hóa an toàn trên thiết bị của bạn.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
