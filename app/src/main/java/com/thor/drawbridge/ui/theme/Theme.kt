package com.thor.drawbridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = TextSecondary,
    tertiary = EmeraldAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.Black,
    onSecondary = TextPrimary,
    onTertiary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = BorderDark
)

@Composable
fun ThorDrawBridgeTheme(
    darkTheme: Boolean = true, // Force dark theme for Sophisticated Dark
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
