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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiAssistantViewModel
import com.example.ai.AiUiState
import com.example.ai.OptimizationGoal
import com.example.ai.ParameterDiff
import com.example.model.BacktestConfig
import com.example.ui.theme.*
import java.util.Locale

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
    onDismiss: () -> Unit,
    onApplyRecommendedConfig: (BacktestConfig) -> Unit = {}
) {
    val aiState by viewModel.aiState.collectAsState()
    var inputQuery by remember { mutableStateOf("") }
    var appliedSuccess by remember { mutableStateOf(false) }
    var selectedGoal by remember { mutableStateOf(OptimizationGoal.BALANCED) }

    val quickChips = when (currentScreenName) {
        "POSITIONS" -> listOf(
            "Giải thích trạng thái 2 giỏ",
            "Rủi ro dính Timeout 144h?",
            "Khoảng cách đến Trailing Stop"
        )
        "ANALYTICS" -> listOf(
            "Tối ưu hóa thông số thích ứng",
            "Đánh giá điểm nghẽn chu kỳ vừa test",
            "So sánh với các lần test trước trong lịch sử"
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
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
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
                        text = "Cố Vấn AI • Dual Basket v2.1",
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

            // HÀNG NÚT CHỌN MỤC TIÊU TỐI ƯU HÓA ĐA CHẾ ĐỘ (MULTI-OBJECTIVE OPTIMIZATION MODES)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OptimizationGoal.values().forEach { goal ->
                    val isSelected = (selectedGoal == goal)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) CryptoGold.copy(alpha = 0.2f) else DarkSurfaceHighlight,
                        border = BorderStroke(1.dp, if (isSelected) CryptoGold else DarkBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("goal_chip_${goal.name}")
                            .clickable {
                                selectedGoal = goal
                                appliedSuccess = false
                                viewModel.askAi(
                                    prompt = "Hãy phân tích lịch sử và tối ưu hóa bộ thông số theo mục tiêu: ${goal.title}",
                                    screenContextJson = screenContextJson,
                                    goal = goal
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = goal.title,
                                color = if (isSelected) CryptoGold else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Quick Question Chips Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(quickChips) { chipText ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurfaceHighlight,
                        border = BorderStroke(0.5.dp, DarkBorder),
                        modifier = Modifier
                            .testTag("ai_chip_$chipText")
                            .clickable {
                                appliedSuccess = false
                                viewModel.askAi(chipText, screenContextJson, selectedGoal)
                            }
                    ) {
                        Text(
                            text = chipText,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (val state = aiState) {
                    is AiUiState.Idle -> {
                        Text(
                            text = "Chào anh Bình! Em là Cố vấn AI Chiến lược Định lượng cho hệ thống Dual Basket v2.1.\n\n" +
                                    "✨ **Điểm mới:**\n" +
                                    "• Phân tích đối chiếu toàn bộ lịch sử các lần backtest trước.\n" +
                                    "• Chẩn đoán điểm nghẽn chính xác (tỷ lệ dính SL, Timeout, Trailing hit).\n" +
                                    "• Tối ưu thích ứng đa mục tiêu: Cân bằng, Tối đa Lợi nhuận, An toàn / Giảm Max DD.\n\n" +
                                    "Anh có thể nhấn trực tiếp vào 1 trong 3 chế độ tối ưu ở trên hoặc chọn câu hỏi nhanh bên dưới để bắt đầu phân tích nhé!",
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
                                text = "Đang phân tích dữ liệu lịch sử & tính toán thông số tối ưu...",
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
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.fullResponse,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )

                            // CARD 1-CHẠM: ÁP DỤNG CẤU HÌNH ĐỀ XUẤT TỪ AI CO-PILOT
                            state.recommendedConfig?.let { recConfig ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_recommended_config_card"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceHighlight),
                                    border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.8f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Tune,
                                                    contentDescription = null,
                                                    tint = CryptoGold,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Bộ Thông Số Khuyến Nghị Thích Ứng",
                                                    color = CryptoGold,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = CryptoGold.copy(alpha = 0.2f),
                                                border = BorderStroke(0.5.dp, CryptoGold)
                                            ) {
                                                Text(
                                                    text = state.goal.title,
                                                    color = CryptoGold,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // BẢNG DIFFS NẾU CÓ THAY ĐỔI RÕ RỆT
                                        if (state.diffs.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Các điều chỉnh thích ứng chính:",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(DarkSurface, RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                state.diffs.forEach { diff ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(diff.name, color = TextMuted, fontSize = 11.sp)
                                                        Row {
                                                            Text(diff.oldValue, color = TextMuted, fontSize = 11.sp)
                                                            Text(" ➔ ", color = CryptoGold, fontSize = 11.sp)
                                                            Text(diff.newValue, color = CryptoGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Bảng tóm tắt toàn bộ cấu hình chuẩn v2.1
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkSurface, RoundedCornerShape(6.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Chế độ quét:", color = TextMuted, fontSize = 11.sp)
                                                Text(recConfig.slotScanMode, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Vốn/Lệnh:", color = TextMuted, fontSize = 11.sp)
                                                Text("${String.format(Locale.US, "%.1f", recConfig.perTradePct * 100)}% (Trần: ${String.format(Locale.US, "%.0f", recConfig.maxPortfolioAllocation * 100)}%)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Lọc Vol 24h:", color = TextMuted, fontSize = 11.sp)
                                                Text("${String.format(Locale.US, "%,.0f", recConfig.minQuoteVolume24h)} USDT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Chặn trên D1 / RVol:", color = TextMuted, fontSize = 11.sp)
                                                Text("${recConfig.maxD1ChangePct}% / ${recConfig.minVolRatio}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Thoát ATR (K_TP / K_Trail):", color = TextMuted, fontSize = 11.sp)
                                                Text("${recConfig.kTp} / ${recConfig.kTrail}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Cắt lỗ K_SL / Timeout:", color = TextMuted, fontSize = 11.sp)
                                                Text("${recConfig.kSl} / ${recConfig.maxHoldingHours} giờ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• Cơ chế v2.1:", color = TextMuted, fontSize = 11.sp)
                                                Text(
                                                    "Trailing: ${if (recConfig.enableTrailingStop) "BẬT" else "TẮT"} | FloorLock: ${if (recConfig.enableFloorLock) "BẬT" else "TẮT"} | SL: ${if (recConfig.enableStopLoss) "BẬT" else "TẮT"}",
                                                    color = CryptoGold,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Button(
                                            onClick = {
                                                onApplyRecommendedConfig(recConfig)
                                                appliedSuccess = true
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .testTag("apply_recommended_config_button"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (appliedSuccess) CryptoGreen else CryptoGold,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (appliedSuccess) Icons.Default.Check else Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (appliedSuccess) "ĐÃ ÁP DỤNG THÀNH CÔNG VÀO BẢNG ĐIỀU KHIỂN!" else "ÁP DỤNG BỘ THÔNG SỐ NÀY",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                            "Hỏi AI về vị thế hoặc tối ưu thông số...",
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
                            appliedSuccess = false
                            viewModel.askAi(inputQuery, screenContextJson, selectedGoal)
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
