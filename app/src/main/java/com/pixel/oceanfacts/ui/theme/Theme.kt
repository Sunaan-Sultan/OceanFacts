package com.pixel.oceanfacts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ocean Facts is dark, always.
 *
 * There is no light scheme and no dynamic colour on purpose: the app is a window onto the sea,
 * and the sea is not recoloured to match a wallpaper. Almost every surface styles itself from
 * `ui/Surfaces.kt` and `ui/ScreenStyle.kt`; this exists so Material components that the app does
 * use — the dialogs, the switch, the progress indicator — land somewhere sensible.
 */
private val OceanColors = darkColorScheme(
    primary = Color(0xFF3FE0D8),
    onPrimary = Color(0xFF00201E),
    secondary = Color(0xFF6FD0E8),
    onSecondary = Color(0xFF00202B),
    tertiary = Color(0xFFFF9E5C),
    onTertiary = Color(0xFF3A1600),
    background = Color(0xFF03121C),
    onBackground = Color(0xFFE6F1F6),
    surface = Color(0xFF07161F),
    onSurface = Color(0xFFE6F1F6),
    surfaceVariant = Color(0xFF0E2430),
    onSurfaceVariant = Color(0xFF8A9BA6),
    error = Color(0xFFFF6B5A),
)

private val OceanTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
)

@Composable
fun OceanFactsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OceanColors,
        typography = OceanTypography,
        content = content,
    )
}
