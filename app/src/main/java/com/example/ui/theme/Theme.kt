package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Minimal color definitions (placeholders). Replace with your real design tokens.
val CryptoGold = Color(0xFFFFC857)
val CryptoGreen = Color(0xFF42C88A)
val CryptoRed = Color(0xFFEA5C5A)
val DarkBg = Color(0xFF0B0D0F)
val DarkSurface = Color(0xFF0F1113)
val DarkSurfaceElevated = Color(0xFF151617)
val DarkSurfaceHighlight = Color(0xFF1A1C1E)
val DarkBorder = Color(0xFF2A2C2E)
val TextMuted = Color(0xFF8A8D90)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFBFC3C6)
val IndicatorMA20 = Color(0xFF7CD6FF)
val IndicatorEMA50 = Color(0xFFFFB74D)


@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = CryptoGold,
            secondary = CryptoGreen,
            background = DarkBg,
            surface = DarkSurface,
            onPrimary = TextPrimary,
            onBackground = TextPrimary
        ),
        typography = Typography(),
        content = content
    )
}
