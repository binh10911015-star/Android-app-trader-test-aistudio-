package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAssistantViewModel
import com.example.ai.AiUiState
import com.example.ui.theme.*

@Composable
fun FloatingAiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(52.dp)
            .testTag("floating_ai_button")
            .clickable { onClick() },
        shape = CircleShape,
        color = DarkSurfaceElevated,
        border = BorderStroke(1.5.dp, CryptoGold),
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Trợ lý AI Co-Pilot",
                tint = CryptoGold,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    viewModel: AiAssistantViewModel,
    screenContextJson: String,
    currentScreenName: String,
    onDismiss: () -> Unit
) {
    val aiState by viewModel.aiState.collectAsState()
    var inputQuery by remember { mutableStateOf("") }

    val quickChips = when (currentScreenName) {
        "POSITIONS" -> listOf(
            "Giải thích trạng thái 2 giỏ",
            "Rủi ro dính Timeout 72h?",
            "Khoảng cách đến Trailing Stop"
        )
        "ANALYTICS" -> listOf(
            "Đánh giá kết quả chu kỳ vừa test",
            "Gợi ý tối ưu tham số TP/SL",
            "Mức Max Drawdown có an toàn?"
        )
        "CHART", "DETAIL" -> listOf(
            "Đánh giá nến D1 so với EMA20",
            "RVol coin này có nổ 2x không?",
            "ATR% biến động ra sao?"
        )
        else -> listOf(
            "Tóm tắt sức khỏe thị trường",
            "BTC có thỏa mãn Regime không?",
            "Đề xuất phiên quét kế tiếp"
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CryptoGold.copy(alpha = 0.5f)) },
        modifier = Modifier.testTag("ai_assistant_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f) // MỞ RỘNG ĐÚNG 80% CHIỀU CAO MÀN HÌNH
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = CryptoGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cố Vấn AI • Dual Basket v2.0",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_ai_sheet_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary)
                }
            }

            // Quick Question Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickChips) { chipText ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DarkSurfaceHighlight,
                        border = BorderStroke(0.5.dp, DarkBorder),
                        modifier = Modifier
                            .testTag("ai_chip_$chipText")
                            .clickable { viewModel.askAi(chipText, screenContextJson) }
                    ) {
                        Text(
                            text = chipText,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            // Streaming / Response Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (val state = aiState) {
                    is AiUiState.Idle -> {
                        Text(
                            text = "Chào anh Bình! Em là Cố vấn AI Co-Pilot độc quyền cho hệ thống Dual Basket v2.0.\n\nEm đã nắm rõ toàn bộ thuật toán lọc 3 tầng, quy tắc 2 giỏ độc lập lúc 07:00 & 19:00, cơ chế dời Trailing Take-Profit theo Basket ATR% và giới hạn 72h Timeout.\n\nAnh hãy chọn một trong các gợi ý phía trên hoặc gõ câu hỏi bất kỳ ở bên dưới nhé!",
                            color = TextMuted,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    is AiUiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CryptoGold,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Gemini đang đọc dữ liệu giỏ lệnh...",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    is AiUiState.Streaming -> {
                        Text(
                            text = state.partialResponse,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    is AiUiState.Success -> {
                        Text(
                            text = state.fullResponse,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    is AiUiState.Error -> {
                        Text(
                            text = "Lỗi: ${state.message}",
                            color = CryptoRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Input Query Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = {
                        Text(
                            "Hỏi AI về vị thế hoặc chiến lược...",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("ai_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CryptoGold
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputQuery.isNotBlank()) {
                            viewModel.askAi(inputQuery, screenContextJson)
                            inputQuery = ""
                        }
                    },
                    modifier = Modifier
                        .background(CryptoGold, RoundedCornerShape(8.dp))
                        .size(48.dp)
                        .testTag("ai_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Gửi",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
