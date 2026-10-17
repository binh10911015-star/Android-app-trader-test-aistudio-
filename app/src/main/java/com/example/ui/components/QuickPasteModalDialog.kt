package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoGreen
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Utility function to reliably read text from both Jetpack Compose and Android System Clipboard
 */
fun readSafeClipboardText(context: Context, composeClipboard: androidx.compose.ui.platform.ClipboardManager): String? {
    // 1. Try Compose Clipboard first
    try {
        val composeText = composeClipboard.getText()?.text
        if (!composeText.isNullOrBlank()) {
            return composeText.trim()
        }
    } catch (_: Exception) {}

    // 2. Try Android System ClipboardManager with coerceToText
    try {
        val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = sysClipboard?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val item = clip.getItemAt(0)
            val text = item?.coerceToText(context)?.toString()
            if (!text.isNullOrBlank()) {
                return text.trim()
            }
        }
    } catch (_: Exception) {}

    return null
}

/**
 * Utility function to reliably copy text to Android System Clipboard with Toast feedback.
 */
fun copyToClipboard(context: Context, text: String, label: String = "API") {
    if (text.isBlank()) {
        Toast.makeText(context, "Nội dung trống, không thể sao chép!", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val sysClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        sysClipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép $label vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Lỗi sao chép: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Modal dialog specifically designed to solve copy-paste challenges in web streaming emulators
 * and mobile devices where password masking or sandbox clipboard restrictions prevent direct pasting.
 */
@Composable
fun QuickPasteModalDialog(
    title: String,
    hintText: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val composeClipboard = LocalClipboardManager.current
    var inputText by remember { mutableStateOf(initialValue) }

    // Auto-attempt to paste from clipboard if current input is empty
    LaunchedEffect(Unit) {
        if (inputText.isBlank()) {
            val clip = readSafeClipboardText(context, composeClipboard)
            if (!clip.isNullOrBlank()) {
                inputText = clip
                Toast.makeText(context, "Đã tự động lấy nội dung từ bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    tint = CryptoGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurface,
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CryptoGold,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mẹo: Bạn có thể nhấn giữ vào ô bên dưới rồi chọn 'Dán' (Paste), hoặc dùng phím tắt Ctrl+V (hoặc Cmd+V) trên bàn phím máy tính.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = hintText,
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("quick_paste_input_area"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false
                    ),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoGold,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CryptoGold
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (inputText.isNotBlank()) "Đã nhập: ${inputText.trim().length} ký tự" else "Chưa có nội dung",
                        color = if (inputText.isNotBlank()) CryptoGreen else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row {
                        if (inputText.isNotBlank()) {
                            TextButton(
                                onClick = { inputText = "" },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa", color = TextSecondary, fontSize = 11.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val clip = readSafeClipboardText(context, composeClipboard)
                                if (!clip.isNullOrBlank()) {
                                    inputText = clip
                                    Toast.makeText(context, "Đã dán từ clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Bộ nhớ tạm trên máy ảo đang trống. Hãy nhấn trực tiếp vào khung và bấm Ctrl+V.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CryptoGold.copy(alpha = 0.5f)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp), tint = CryptoGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dán từ bộ nhớ", color = CryptoGold, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(inputText.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CryptoGold,
                    contentColor = DarkBg
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = inputText.isNotBlank(),
                modifier = Modifier.testTag("quick_paste_confirm_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Áp Dụng Khóa", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary, fontSize = 12.sp)
            }
        },
        containerColor = DarkSurfaceElevated
    )
}
