package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RadarCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E59),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = RadarCoral,
    onSecondary = Color(0xFF3B0900),
    secondaryContainer = Color(0xFF631902),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = RadarEmerald,
    onTertiary = Color(0xFF003919),
    background = RadarNavyDark,
    onBackground = TextPrimaryDark,
    surface = RadarSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = RadarCardDark,
    onSurfaceVariant = TextSecondaryDark,
    error = Color(0xFFFF5252),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = RadarPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F4FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = RadarSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF3B0900),
    tertiary = Color(0xFF008744),
    onTertiary = Color.White,
    background = RadarBgLight,
    onBackground = TextPrimaryLight,
    surface = RadarSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = RadarCardLight,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
