package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StarlightDarkColorScheme = darkColorScheme(
    primary = StarlightElectricPurple,
    onPrimary = StarlightVoid,
    primaryContainer = StarlightSurfaceElevated,
    onPrimaryContainer = StarlightTextPrimary,
    secondary = StarlightMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A165C),
    onSecondaryContainer = StarlightTextPrimary,
    tertiary = StarlightRose,
    onTertiary = Color.White,
    background = StarlightVoid,
    onBackground = StarlightTextPrimary,
    surface = StarlightObsidian,
    onSurface = StarlightTextPrimary,
    surfaceVariant = StarlightSurface,
    onSurfaceVariant = StarlightTextSecondary,
    outline = StarlightBorder,
    outlineVariant = Color(0xFF281D47),
    error = Color(0xFFFF5C8A),
    onError = Color.White
)

private val StarlightLightColorScheme = lightColorScheme(
    primary = StarlightLightPrimary,
    onPrimary = Color.White,
    primaryContainer = StarlightLightSurfaceVar,
    onPrimaryContainer = StarlightLightTextPrimary,
    secondary = StarlightLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E5F5),
    onSecondaryContainer = StarlightLightTextPrimary,
    tertiary = StarlightRose,
    onTertiary = Color.White,
    background = StarlightLightBg,
    onBackground = StarlightLightTextPrimary,
    surface = StarlightLightSurface,
    onSurface = StarlightLightTextPrimary,
    surfaceVariant = StarlightLightSurfaceVar,
    onSurfaceVariant = StarlightLightTextSecondary,
    outline = StarlightLightBorder,
    outlineVariant = Color(0xFFEBE0FA),
    error = Color(0xFFD32F5E),
    onError = Color.White
)

val StarlightShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object StarlightBrushes {
    val primaryGradient = Brush.linearGradient(
        colors = listOf(StarlightViolet, StarlightMagenta, StarlightRose)
    )
    val cosmicStoryRing = Brush.sweepGradient(
        colors = listOf(StarlightElectricPurple, StarlightMagenta, StarlightRose, StarlightGold, StarlightElectricPurple)
    )
    val darkBackdrop = Brush.verticalGradient(
        colors = listOf(StarlightVoid, StarlightObsidian, Color(0xFF1B0E35))
    )
    val lightBackdrop = Brush.verticalGradient(
        colors = listOf(StarlightLightBg, Color(0xFFF3EBFF), Color(0xFFFFFFFF))
    )
}

@Composable
fun StarlightTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StarlightDarkColorScheme else StarlightLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = StarlightTypography,
        shapes = StarlightShapes,
        content = content
    )
}
