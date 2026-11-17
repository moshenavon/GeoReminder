package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyanDark,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E59),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = AccentCoralDark,
    onSecondary = Color(0xFF3B0900),
    secondaryContainer = Color(0xFF631902),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = AccentEmeraldDark,
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
    onSurfaceVariant = TextSecondaryLight,
    // Material3 container roles default to a purple tint; match them to the blue palette.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F9FD),
    surfaceContainer = Color(0xFFEAF2FA),
    surfaceContainerHigh = Color(0xFFE2ECF6),
    surfaceContainerHighest = Color(0xFFDAE6F2),
    outline = Color(0xFF6F8399),
    outlineVariant = Color(0xFFC3D3E3)
)

/**
 * Accent / decoration colours that are not part of the Material colour scheme and that
 * must differ between dark and light mode (neon-on-navy vs. deep-on-white).
 */
@Immutable
class RadarAccents(
    val isDark: Boolean,
    val cyan: Color,
    val emerald: Color,
    val coral: Color,
    val amber: Color,
    /** Text/icon colour on top of a [cyan] filled surface (e.g. the FAB). */
    val onCyan: Color,
    /** Text/icon colour on top of an [emerald] filled surface. */
    val onEmerald: Color,
    /** Gradient laid over the hero background image. */
    val heroScrim: List<Color>,
    val heroTitle: Color,
    val heroSubtitle: Color,
    val heroMeta: Color,
    /** Fill of the radar disc. */
    val radarDisc: Color
)

private val DarkAccents = RadarAccents(
    isDark = true,
    cyan = AccentCyanDark,
    emerald = AccentEmeraldDark,
    coral = AccentCoralDark,
    amber = AccentAmberDark,
    onCyan = Color(0xFF00242A),
    onEmerald = Color(0xFF002912),
    heroScrim = listOf(Color(0xCC07111E), Color(0xE60B192C), Color(0xF80E1E32)),
    heroTitle = Color.White,
    heroSubtitle = Color(0xFFB8E2F2),
    heroMeta = Color(0xFFBFD4E8),
    radarDisc = Color(0xFF071324)
)

private val LightAccents = RadarAccents(
    isDark = false,
    cyan = AccentCyanLight,
    emerald = AccentEmeraldLight,
    coral = AccentCoralLight,
    amber = AccentAmberLight,
    onCyan = Color.White,
    onEmerald = Color.White,
    heroScrim = listOf(Color(0xE6F2F7FC), Color(0xF0EAF3FB), Color(0xFAE4EFFA)),
    heroTitle = TextPrimaryLight,
    heroSubtitle = Color(0xFF35506B),
    heroMeta = TextSecondaryLight,
    radarDisc = Color(0xFFEAF3FB)
)

val LocalRadarAccents = staticCompositionLocalOf { DarkAccents }

// Accent colours resolve against the current theme, so existing call sites
// (e.g. `tint = RadarCyan`) automatically get the right colour in light and dark mode.
val RadarCyan: Color
    @Composable @ReadOnlyComposable get() = LocalRadarAccents.current.cyan
val RadarEmerald: Color
    @Composable @ReadOnlyComposable get() = LocalRadarAccents.current.emerald
val RadarCoral: Color
    @Composable @ReadOnlyComposable get() = LocalRadarAccents.current.coral
val RadarAmber: Color
    @Composable @ReadOnlyComposable get() = LocalRadarAccents.current.amber

/** Follows the phone's day / night setting unless [darkTheme] is given explicitly. */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val accents = if (darkTheme) DarkAccents else LightAccents

    CompositionLocalProvider(LocalRadarAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
