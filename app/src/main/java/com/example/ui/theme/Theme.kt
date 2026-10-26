package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BiometricColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = NeonCyan,
    secondary = NeonEmerald,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00381C),
    onSecondaryContainer = NeonEmerald,
    tertiary = AlertAmber,
    onTertiary = Color.Black,
    error = DangerCrimson,
    onError = Color.White,
    background = CyberBackground,
    onBackground = TextPrimary,
    surface = CyberSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary
)

@Composable
fun TruthLensTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BiometricColorScheme,
        typography = Typography,
        content = content
    )
}
