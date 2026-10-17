package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CryptoGold,
    onPrimary = DarkBg,
    primaryContainer = CryptoGoldLight,
    onPrimaryContainer = DarkBg,
    secondary = CryptoGreen,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = CryptoGreen,
    tertiary = CryptoRed,
    onTertiary = TextPrimary,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceHighlight,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Crypto trading terminal is best in dark mode
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
