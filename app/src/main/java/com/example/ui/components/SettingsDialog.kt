package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.BinanceAccountVerificationResult
import com.example.model.BinanceApiConfig
import com.example.model.TradeSettings

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
    var volumePercent by remember { mutableStateOf(currentSettings.volumePercentOfBalance.toString()) }
    var fixedVolume by remember { mutableStateOf(currentSettings.fixedVolumeUsdt.toString()) }
    var apiKey by remember { mutableStateOf(apiConfig.apiKey) }
    var apiSecret by remember { mutableStateOf(apiConfig.apiSecret) }
    var accessPin by remember { mutableStateOf(apiConfig.accessPin) }
    var isTestnet by remember { mutableStateOf(apiConfig.isTestnet) }
    var liveTrading by remember { mutableStateOf(apiConfig.liveTradingEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt giao dịch") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = volumePercent,
                    onValueChange = { volumePercent = it },
                    label = { Text("Tỷ lệ vốn %") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fixedVolume,
                    onValueChange = { fixedVolume = it },
                    label = { Text("Volume cố định USDT") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("API Secret") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = accessPin,
                    onValueChange = { accessPin = it },
                    label = { Text("PIN truy cập") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isTestnet, onCheckedChange = { isTestnet = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Demo/Testnet")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = liveTrading, onCheckedChange = { liveTrading = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cho phép live trading")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedSettings = currentSettings.copy(
                        volumePercentOfBalance = volumePercent.toDoubleOrNull() ?: currentSettings.volumePercentOfBalance,
                        fixedVolumeUsdt = fixedVolume.toDoubleOrNull() ?: currentSettings.fixedVolumeUsdt
                    )
                    onSave(updatedSettings)
                    onSaveApiCredentials(apiKey, apiSecret, accessPin, isTestnet, liveTrading) { }
                    onDismiss()
                }
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onOpenApiConfig) { Text("API") }
                TextButton(onClick = onResetAccount) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        }
    )
}
