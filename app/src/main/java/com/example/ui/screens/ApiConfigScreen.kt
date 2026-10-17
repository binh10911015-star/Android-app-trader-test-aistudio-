package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.example.ui.MainViewModel
import com.example.ui.components.QuickPasteModalDialog
import com.example.ui.components.copyToClipboard
import com.example.ui.components.readSafeClipboardText
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiConfig by viewModel.apiConfig.collectAsState()
    val isVerifying by viewModel.isVerifyingApi.collectAsState()
    val publicIp by viewModel.publicIpAddress.collectAsState()
    val isFetchingIp by viewModel.isFetchingIp.collectAsState()

    var apiKeyInput by remember(apiConfig.apiKey) { mutableStateOf(apiConfig.apiKey) }
    var apiSecretInput by remember(apiConfig.apiSecret) { mutableStateOf(apiConfig.apiSecret) }
    var accessPinInput by remember(apiConfig.accessPin) { mutableStateOf(apiConfig.accessPin) }
    var isTestnet by remember(apiConfig.isTestnet) { mutableStateOf(apiConfig.isTestnet) }
    var liveTrading by remember(apiConfig.liveTradingEnabled) { mutableStateOf(apiConfig.liveTradingEnabled) }
    var proxyHostInput by remember(apiConfig.proxyHost) { mutableStateOf(apiConfig.proxyHost) }
    var proxyPortInput by remember(apiConfig.proxyPort) { mutableStateOf(if (apiConfig.proxyPort > 0) apiConfig.proxyPort.toString() else "") }
    var proxyTypeInput by remember(apiConfig.proxyType) { mutableStateOf(apiConfig.proxyType) }

    val composeClipboard = LocalClipboardManager.current
    var isApiKeyVisible by remember { mutableStateOf(true) }
    var isSecretVisible by remember { mutableStateOf(true) }
    var isPinVisible by remember { mutableStateOf(false) }
    var quickPasteTarget by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var testResultBanner by remember { mutableStateOf<BinanceAccountVerificationResult?>(null) }

    val scrollState = rememberScrollState()

    // Confirmation dialog for clearing API keys
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = CryptoRed) },
            title = {
                Text("Xóa Khóa API Binance?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Toàn bộ khóa API Key và Secret Key sẽ được xóa vĩnh viễn khỏi thiết bị này. Ứng dụng sẽ trở lại Chế độ Mô phỏng an toàn.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearApiCredentials()
                        apiKeyInput = ""
                        apiSecretInput = ""
                        accessPinInput = ""
                        testResultBanner = null
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoRed)
                ) {
                    Text("Xóa Ngay", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top Security Header Banner
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CryptoGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Bảo mật",
                            tint = CryptoGold,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kết Nối Binance Futures API",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mã hóa AES-128 lưu trữ trực tiếp trên thiết bị",
                            color = CryptoGold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CryptoGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "QUY TẮC VẬN HÀNH BẮT BUỘC",
                                color = CryptoGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Cho phép gửi lệnh Mainnet: Hệ thống được quyền gọi API /fapi/v1/order để vào lệnh và kiểm tra cơ chế khớp lệnh trên Binance Mainnet nhằm mục đích kiểm thử.\n2. Bảo mật mã PIN: Luôn luôn hiển thị và bắt buộc người dùng nhập đúng mã PIN bảo mật trước khi kích hoạt 'Auto Trade' hoặc đặt lệnh.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Connection Status & Live Balance Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trạng Thái Kết Nối",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            apiConfig.isConnected -> CryptoGreen.copy(alpha = 0.18f)
                            apiConfig.lastErrorMessage != null -> CryptoRed.copy(alpha = 0.18f)
                            else -> DarkSurfaceHighlight
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                apiConfig.isConnected -> CryptoGreen
                                apiConfig.lastErrorMessage != null -> CryptoRed
                                else -> DarkBorder
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            apiConfig.isConnected -> CryptoGreen
                                            apiConfig.lastErrorMessage != null -> CryptoRed
                                            else -> TextMuted
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    apiConfig.isConnected -> "ĐÃ KẾT NỐI"
                                    apiConfig.lastErrorMessage != null -> "LỖI KẾT NỐI"
                                    else -> "CHẾ ĐỘ MÔ PHỎNG"
                                },
                                color = when {
                                    apiConfig.isConnected -> CryptoGreen
                                    apiConfig.lastErrorMessage != null -> CryptoRed
                                    else -> TextSecondary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (apiConfig.isConnected && apiConfig.totalWalletBalance != null) {
                    // Live Futures Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Số Dư Ví Futures (USDT)", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", apiConfig.totalWalletBalance)}",
                                color = CryptoGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Ký Quỹ Khả Dụng", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", apiConfig.availableBalance ?: 0.0)}",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CryptoGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Futures: Cho phép", color = TextSecondary, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rút tiền: Tắt (An toàn)", color = CryptoGold, fontSize = 11.sp)
                        }
                        Text(
                            text = if (apiConfig.isTestnet) "Mạng: Testnet" else "Mạng: Mainnet",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text(
                        text = "Bạn chưa kết nối tài khoản Binance Futures. Ứng dụng hiện đang sử dụng số dư mô phỏng để tính toán và kiểm tra chỉ báo.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                // Error message if any
                if (apiConfig.lastErrorMessage != null && !apiConfig.isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CryptoRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = CryptoRed, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = apiConfig.lastErrorMessage ?: "",
                                color = CryptoRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Verification in progress
                if (isVerifying) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = CryptoGold,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang kết nối và kiểm tra tài khoản Binance...", color = CryptoGold, fontSize = 12.sp)
                    }
                }

                if (apiConfig.isConfigured && !isVerifying) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.testApiConnection { res ->
                                testResultBanner = res
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("retest_connection_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoGold),
                        border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kiểm Tra Kết Nối Lại", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card Hiển Thị Địa Chỉ IP Công Khai Cho Whitelist Trên Binance Mainnet
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ip_whitelist_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CryptoGold.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Địa chỉ IP",
                                tint = CryptoGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Địa Chỉ IP Cho Whitelist",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "IP Egress công khai của thiết bị",
                                color = CryptoGold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.fetchPublicIpAddress(forceRefresh = true) },
                        enabled = !isFetchingIp
                    ) {
                        if (isFetchingIp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = CryptoGold,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Làm mới IP",
                                tint = CryptoGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Khung hiển thị IP & Nút Sao Chép
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PUBLIC IP ADDRESS",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isFetchingIp && publicIp == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = CryptoGold,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Đang kiểm tra IP...", color = TextSecondary, fontSize = 13.sp)
                                }
                            } else {
                                Text(
                                    text = publicIp ?: "Chưa xác định (Bấm làm mới)",
                                    color = if (publicIp != null) CryptoGreen else CryptoRed,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (!publicIp.isNullOrBlank()) {
                                    copyToClipboard(context, publicIp!!, "địa chỉ IP")
                                } else {
                                    viewModel.fetchPublicIpAddress(forceRefresh = true)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CryptoGold,
                                contentColor = DarkBg
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("copy_ip_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sao Chép IP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hộp giải thích cấu hình trên Binance Mainnet
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceHighlight.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Khi tạo API Key trên Binance Mainnet: chọn mục 'Restrict access to trusted IPs only' và dán địa chỉ IP trên vào. Điều này bảo vệ tài khoản của bạn an toàn 100%, chỉ cho phép giao dịch từ thiết bị này.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Card Cấu Hình Custom Proxy (Socks5/HTTP Proxy) Vượt Hạn Chế Vùng Địa Lý
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("proxy_config_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = BorderStroke(1.dp, if (apiConfig.isProxyEnabled) CryptoGreen.copy(alpha = 0.5f) else DarkBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = if (apiConfig.isProxyEnabled) CryptoGreen else CryptoGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Cấu Hình Custom Proxy",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (apiConfig.isProxyEnabled)
                                    "Đang bật: ${apiConfig.proxyType}://${apiConfig.proxyHost}:${apiConfig.proxyPort}"
                                else
                                    "Đang tắt (Kết nối trực tiếp)",
                                color = if (apiConfig.isProxyEnabled) CryptoGreen else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (apiConfig.isProxyEnabled) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CryptoGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CryptoGreen)
                        ) {
                            Text(
                                text = "PROXY BẬT",
                                color = CryptoGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hướng dẫn nhỏ theo đúng yêu cầu brief
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cấu hình Proxy trung gian giúp định tuyến API Mainnet qua VPS sạch IP khi gặp lỗi restricted location",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Loại Proxy: HTTP vs SOCKS5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = proxyTypeInput == "HTTP",
                        onClick = { proxyTypeInput = "HTTP" },
                        label = { Text("HTTP Proxy") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = proxyTypeInput == "SOCKS",
                        onClick = { proxyTypeInput = "SOCKS" },
                        label = { Text("SOCKS5 Proxy") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ô 1: Proxy Host
                OutlinedTextField(
                    value = proxyHostInput,
                    onValueChange = { proxyHostInput = it },
                    label = { Text("Proxy Host") },
                    placeholder = { Text("123.45.67.89 hoặc vps.domain.com", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = CryptoGold)
                    },
                    trailingIcon = {
                        if (proxyHostInput.isNotEmpty()) {
                            IconButton(onClick = { proxyHostInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = CryptoGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_host_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Ô 2: Proxy Port
                OutlinedTextField(
                    value = proxyPortInput,
                    onValueChange = { proxyPortInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Proxy Port") },
                    placeholder = { Text("1080 hoặc 8080", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, tint = CryptoGold)
                    },
                    trailingIcon = {
                        if (proxyPortInput.isNotEmpty()) {
                            IconButton(onClick = { proxyPortInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = CryptoGold,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_port_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Nút Lưu Cấu Hình Proxy & Tắt Proxy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val port = proxyPortInput.trim().toIntOrNull() ?: 0
                            val host = proxyHostInput.trim()
                            if (host.isNotEmpty() && port <= 0) {
                                Toast.makeText(context, "Vui lòng nhập Proxy Port hợp lệ (ví dụ: 1080 hoặc 8080)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.updateProxyConfig(host, port, proxyTypeInput)
                            Toast.makeText(
                                context,
                                if (host.isNotEmpty() && port > 0) "Đã lưu cấu hình Proxy thành công!" else "Đã tắt Proxy, dùng kết nối trực tiếp",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CryptoGold,
                            contentColor = DarkBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_proxy_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lưu Cấu Hình Proxy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (apiConfig.isProxyEnabled || proxyHostInput.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                proxyHostInput = ""
                                proxyPortInput = ""
                                viewModel.clearProxyConfig()
                                Toast.makeText(context, "Đã tắt Proxy, dùng kết nối trực tiếp", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                            border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tắt", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Môi Trường Giao Dịch",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isTestnet,
                        onClick = {
                            isTestnet = false
                            if (apiKeyInput.isBlank() || apiKeyInput == com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_KEY) {
                                apiKeyInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY
                                apiSecretInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_SECRET
                            }
                        },
                        label = { Text("Mainnet (Tài khoản thực)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("chip_mainnet")
                    )

                    FilterChip(
                        selected = isTestnet,
                        onClick = {
                            isTestnet = true
                            if (apiKeyInput.isBlank() || apiKeyInput == com.example.util.SecureApiKeyStorage.PRECONFIGURED_MAINNET_API_KEY) {
                                apiKeyInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_KEY
                                apiSecretInput = com.example.util.SecureApiKeyStorage.PRECONFIGURED_TESTNET_API_SECRET
                            }
                        },
                        label = { Text("Testnet (Thử nghiệm)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CryptoGold,
                            selectedLabelColor = DarkBg,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("chip_testnet")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isTestnet)
                        "Endpoint: https://testnet.binancefuture.com (Tài khoản demo thử nghiệm an toàn)"
                    else
                        "Endpoint: https://fapi.binance.com (Tài khoản Binance Futures Mainnet - Hỗ trợ gửi lệnh /fapi/v1/order để kiểm thử)",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // API Key & Secret Input Form
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nhập Khóa API Binance",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Action Bar for Easy Input & Key Visibility Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Khóa API & Mật mã xác thực",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Toggle Show/Hide All Keys
                        OutlinedButton(
                            onClick = {
                                val bothVisible = isApiKeyVisible && isSecretVisible
                                isApiKeyVisible = !bothVisible
                                isSecretVisible = !bothVisible
                                Toast.makeText(
                                    context,
                                    if (!bothVisible) "Đã hiển thị toàn bộ API Key và Secret Key!" else "Đã ẩn API Key và Secret Key!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isApiKeyVisible && isSecretVisible) CryptoGreen.copy(alpha = 0.5f) else CryptoGold.copy(alpha = 0.5f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = if (isApiKeyVisible && isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (isApiKeyVisible && isSecretVisible) CryptoGreen else CryptoGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isApiKeyVisible && isSecretVisible) "Ẩn" else "Hiện",
                                color = if (isApiKeyVisible && isSecretVisible) CryptoGreen else CryptoGold,
                                fontSize = 11.sp
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
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Mainnet", color = CryptoGold, fontSize = 11.sp)
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
                                Toast.makeText(context, "Đã điền khóa Binance Testnet Demo!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TextMuted),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Testnet", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // API Key Input
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Binance API Key") },
                    placeholder = { Text("Dán hoặc nhập API Key...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = CryptoGold)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "Ẩn Key" else "Hiện Key",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (apiKeyInput.isNotEmpty()) {
                                IconButton(onClick = { apiKeyInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            // Direct Paste from Clipboard
                            IconButton(onClick = {
                                val clip = readSafeClipboardText(context, composeClipboard)
                                if (!clip.isNullOrBlank()) {
                                    apiKeyInput = clip
                                    isApiKeyVisible = true
                                    Toast.makeText(context, "Đã dán API Key từ clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bộ nhớ tạm trống. Mở hộp thoại dán...", Toast.LENGTH_SHORT).show()
                                    quickPasteTarget = "API_KEY"
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán từ clipboard", tint = CryptoGold, modifier = Modifier.size(18.dp))
                            }
                            // Quick Paste Dialog button
                            IconButton(onClick = { quickPasteTarget = "API_KEY" }) {
                                Icon(Icons.Default.Assignment, contentDescription = "Hộp thoại dán", tint = CryptoGold, modifier = Modifier.size(18.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // API Secret Input
                OutlinedTextField(
                    value = apiSecretInput,
                    onValueChange = { apiSecretInput = it },
                    label = { Text("Binance Secret Key") },
                    placeholder = { Text("Dán hoặc nhập Secret Key...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CryptoGold)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSecretVisible = !isSecretVisible }) {
                                Icon(
                                    imageVector = if (isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isSecretVisible) "Ẩn Secret" else "Hiện Secret",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (apiSecretInput.isNotEmpty()) {
                                IconButton(onClick = { apiSecretInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            // Direct Paste from Clipboard
                            IconButton(onClick = {
                                val clip = readSafeClipboardText(context, composeClipboard)
                                if (!clip.isNullOrBlank()) {
                                    apiSecretInput = clip
                                    isSecretVisible = true
                                    Toast.makeText(context, "Đã dán Secret Key từ clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bộ nhớ tạm trống. Mở hộp thoại dán...", Toast.LENGTH_SHORT).show()
                                    quickPasteTarget = "SECRET_KEY"
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán từ clipboard", tint = CryptoGold, modifier = Modifier.size(18.dp))
                            }
                            // Quick Paste Dialog button
                            IconButton(onClick = { quickPasteTarget = "SECRET_KEY" }) {
                                Icon(Icons.Default.Assignment, contentDescription = "Hộp thoại dán", tint = CryptoGold, modifier = Modifier.size(18.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_secret_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Binance Access PIN Input (cần nhập mới được vào lệnh)
                OutlinedTextField(
                    value = accessPinInput,
                    onValueChange = { accessPinInput = it },
                    label = { Text("Binance Access PIN (Mã PIN bảo mật)") },
                    placeholder = { Text("Nhập mã PIN bảo mật (VD: 1234)...", color = TextMuted, fontSize = 12.sp) },
                    supportingText = {
                        Text(
                            text = "Bắt buộc nhập mã PIN này mới được thực hiện vào lệnh trên Binance Futures để đảm bảo an toàn tuyệt đối.",
                            color = CryptoGold,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, tint = CryptoGold)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                Icon(
                                    imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPinVisible) "Ẩn PIN" else "Hiện PIN",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = {
                                val clip = readSafeClipboardText(context, composeClipboard)
                                if (!clip.isNullOrBlank()) {
                                    accessPinInput = clip
                                    isPinVisible = true
                                    Toast.makeText(context, "Đã dán mã PIN!", Toast.LENGTH_SHORT).show()
                                } else {
                                    quickPasteTarget = "ACCESS_PIN"
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Dán PIN", tint = CryptoGold, modifier = Modifier.size(18.dp))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_pin_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // BẢNG HIỂN THỊ CHI TIẾT API KEY VÀ SECRET KEY (Theo yêu cầu: Hiển thị API key và secret key)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_keys_display_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Chi Tiết Khóa Đang Nhập / Đã Lưu",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Quick toggle button for this display card
                            TextButton(
                                onClick = {
                                    val bothVisible = isApiKeyVisible && isSecretVisible
                                    isApiKeyVisible = !bothVisible
                                    isSecretVisible = !bothVisible
                                },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isApiKeyVisible && isSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = CryptoGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isApiKeyVisible && isSecretVisible) "Ẩn Khóa" else "Hiện Khóa",
                                    color = CryptoGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 1. API Key Display
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Binance API Key",
                                        color = CryptoGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (apiKeyInput.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${apiKeyInput.length} ký tự)",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (apiKeyInput.isNotBlank()) {
                                    TextButton(
                                        onClick = { copyToClipboard(context, apiKeyInput, "API Key") },
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sao chép", color = CryptoGold, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkBg,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (apiKeyInput.isBlank()) {
                                        "(Chưa có API Key - dán hoặc nhập ở trên)"
                                    } else if (isApiKeyVisible) {
                                        apiKeyInput
                                    } else {
                                        "••••••••••••••••••••••••••••••••••••••••"
                                    },
                                    color = if (apiKeyInput.isBlank()) TextMuted else TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Secret Key Display
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Binance Secret Key",
                                        color = CryptoGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (apiSecretInput.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${apiSecretInput.length} ký tự)",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                if (apiSecretInput.isNotBlank()) {
                                    TextButton(
                                        onClick = { copyToClipboard(context, apiSecretInput, "Secret Key") },
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sao chép", color = CryptoGold, fontSize = 10.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkBg,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (apiSecretInput.isBlank()) {
                                        "(Chưa có Secret Key - dán hoặc nhập ở trên)"
                                    } else if (isSecretVisible) {
                                        apiSecretInput
                                    } else {
                                        "••••••••••••••••••••••••••••••••••••••••"
                                    },
                                    color = if (apiSecretInput.isBlank()) TextMuted else TextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Trading Switch
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Giao Dịch Futures Thực Tế",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (liveTrading)
                                    "Bật: Bot sẽ đặt lệnh thực trên tài khoản Binance của bạn"
                                else
                                    "Tắt: Bot chỉ chạy mô phỏng an toàn (Paper Trading)",
                                color = if (liveTrading) CryptoGreen else TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = liveTrading,
                            onCheckedChange = {
                                liveTrading = it
                                if (apiConfig.isConfigured) {
                                    viewModel.setLiveTradingEnabled(it)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBg,
                                checkedTrackColor = CryptoGreen,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurfaceHighlight
                            ),
                            modifier = Modifier.testTag("live_trading_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save & Test Connection Button
                Button(
                    onClick = {
                        viewModel.saveAndVerifyApiCredentials(
                            apiKey = apiKeyInput,
                            apiSecret = apiSecretInput,
                            accessPin = accessPinInput,
                            isTestnet = isTestnet,
                            liveTradingEnabled = liveTrading
                        ) { result ->
                            testResultBanner = result
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() && apiSecretInput.isNotBlank() && !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_and_test_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CryptoGold,
                        contentColor = DarkBg,
                        disabledContainerColor = DarkSurfaceHighlight,
                        disabledContentColor = TextMuted
                    )
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            color = DarkBg,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang Xác Thực Tài Khoản...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kiểm Tra Kết Nối & Lưu Khóa API", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Delete Credentials Button (if configured)
                if (apiConfig.isConfigured) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("clear_keys_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CryptoRed),
                        border = BorderStroke(1.dp, CryptoRed.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xóa Khóa API Khỏi Thiết Bị", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Safety Warnings Card (Highlighted in red/amber)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CryptoRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quy Tắc An Toàn Tuyệt Đối",
                        color = CryptoRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• TUYỆT ĐỐI KHÔNG BẬT \"Enable Withdrawals\" (Rút tiền): Ứng dụng này chỉ cần quyền 'Enable Reading' và 'Enable Futures', KHÔNG BAO GIỜ yêu cầu quyền rút tiền.",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• IP Whitelist (Khuyến nghị): Cài đặt giới hạn địa chỉ IP trên Binance để ngăn chặn truy cập trái phép từ bên ngoài.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Không chia sẻ Secret Key: Secret Key chỉ hiển thị một lần duy nhất lúc tạo trên Binance. Ứng dụng mã hóa khóa này trực tiếp trong bộ nhớ máy của bạn.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Step-by-Step Instructions Accordion
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = DarkSurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showGuide = !showGuide },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = CryptoGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hướng Dẫn Tạo API Key Trên Binance",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                AnimatedVisibility(
                    visible = showGuide,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Bước 1: Mở ứng dụng Binance hoặc trang web binance.com, vào mục Avatar -> Quản lý API (API Management).",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bước 2: Nhấn nút \"Tạo API\" (Create API) -> Chọn \"Hệ thống tạo (System generated)\" và đặt tên cho API (ví dụ: FuturesBot).",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bước 3: Nhấn \"Chỉnh sửa quyền\" (Edit restrictions) và tích chọn:\n   ✓ Cho phép đọc (Enable Reading)\n   ✓ Cho phép giao dịch Futures (Enable Futures)\n   ❌ TUYỆT ĐỐI BỎ CHỌN: Cho phép rút tiền (Enable Withdrawals)",
                            color = CryptoGold,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Bước 4: Sao chép mã API Key và Secret Key, dán vào 2 ô nhập liệu ở trên rồi bấm \"Kiểm Tra Kết Nối & Lưu Khóa API\".",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
