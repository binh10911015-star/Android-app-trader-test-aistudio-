package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Original Binance Futures Dark Palette (Deep Charcoal #0B0E14 & Binance Gold #F0B90B)
val CryptoGold = Color(0xFFF0B90B) // Authentic Binance Gold
val CryptoGreen = Color(0xFF0ECB81) // Binance Futures green for profit / buy
val CryptoRed = Color(0xFFF6465D) // Binance Futures red for stop loss / sell
val DarkBg = Color(0xFF0B0E14) // Deep charcoal dark background
val DarkSurface = Color(0xFF161B22) // Surface container
val DarkSurfaceElevated = Color(0xFF1E2430) // Elevated card container
val DarkSurfaceHighlight = Color(0xFF262D3D) // Input / chip highlight container
val DarkBorder = Color(0xFF2E384D) // Subtle dark border
val TextMuted = Color(0xFF64748B) // Slate muted label
val TextPrimary = Color(0xFFF1F5F9) // High-contrast crisp light text
val TextSecondary = Color(0xFF94A3B8) // Secondary readable text
val IndicatorMA20 = Color(0xFFF0B90B) // Gold for MA20
val IndicatorEMA50 = Color(0xFF38BDF8) // Sky blue for EMA50

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = CryptoGold,
            secondary = CryptoGreen,
            background = DarkBg,
            surface = DarkSurfaceElevated,
            surfaceVariant = DarkSurface,
            outline = DarkBorder,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        typography = Typography(),
        content = content
    )
}
