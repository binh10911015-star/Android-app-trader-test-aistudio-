package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.TradePosition
import com.example.ui.MainViewModel

@Composable
fun AnalyticsScreen(viewModel: MainViewModel, closedPositions: List<TradePosition>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Thống kê PnL")
        Text(text = "Số vị thế đóng: ${closedPositions.size}")
    }
}
