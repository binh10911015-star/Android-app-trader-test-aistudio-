package com.example.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CryptoGold
import com.example.ui.theme.CryptoRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PinVerificationDialog(
    actionTitle: String = "Xác nhận vào lệnh",
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = CryptoGold,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Binance Access PIN",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Vui lòng nhập mã PIN bảo mật Binance Access PIN để tiếp tục $actionTitle:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorMessage = null
                    },
                    label = { Text("Mã PIN bảo mật") },
                    placeholder = { Text("Nhập mã PIN của bạn...", color = TextSecondary.copy(alpha = 0.5f)) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CryptoGold)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPinVisible) "Ẩn PIN" else "Hiện PIN",
                                tint = TextSecondary
                            )
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
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = CryptoRed, fontSize = 11.sp)
                        } else {
                            Text("Bảo mật tài khoản: Cần nhập đúng mã PIN mới được vào lệnh", color = CryptoGold, fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pin_verification_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onVerify(pinInput)) {
                        onSuccess()
                        onDismiss()
                    } else {
                        errorMessage = "Mã PIN không chính xác! Vui lòng kiểm tra lại."
                    }
                },
                enabled = pinInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CryptoGold,
                    contentColor = DarkBg
                ),
                modifier = Modifier.testTag("confirm_pin_button")
            ) {
                Text("Xác Nhận & Tiếp Tục", fontWeight = FontWeight.Bold)
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
