package com.weatherfocus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.weatherfocus.app.data.model.ThemeMode

// Neutral graphite/charcoal palette - replaces the old bright "sky blue" as the app's main colour so
// it sits quietly in dark mode instead of standing out. Warm accents (sun/rain) stay colourful for contrast.
val GraphiteMid = Color(0xFF4A4E57)
val GraphiteDark = Color(0xFF2A2D33)
val GraphiteDeep = Color(0xFF1A1C20)
val SunYellow = Color(0xFFF2C94C)
val SunAmber = Color(0xFFF7A440)
val RainTeal = Color(0xFF56CCF2)

private val LightColors = lightColorScheme(
    primary = GraphiteMid,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCDEE3),
    onPrimaryContainer = Color(0xFF212328),
    secondary = SunAmber,
    onSecondary = Color(0xFF3A2400),
    secondaryContainer = Color(0xFFFFE3B0),
    onSecondaryContainer = Color(0xFF4A2E00),
    tertiary = RainTeal,
    background = Color(0xFFF2F2F3),
    onBackground = Color(0xFF1C1D20),
    surface = Color.White,
    onSurface = Color(0xFF1C1D20),
    surfaceVariant = Color(0xFFE6E7E9),
    onSurfaceVariant = Color(0xFF48494E),
    outline = Color(0xFFBEC0C5)
)

private val DarkColors = darkColorScheme(
    primary = GraphiteDark,
    onPrimary = Color(0xFFEDEDEF),
    primaryContainer = Color(0xFF35383F),
    onPrimaryContainer = Color(0xFFE4E4E6),
    secondary = SunYellow,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF5A4200),
    onSecondaryContainer = Color(0xFFFFE9B8),
    tertiary = RainTeal,
    background = Color(0xFF101113),
    onBackground = Color(0xFFEAEAEC),
    surface = Color(0xFF191B1E),
    onSurface = Color(0xFFEAEAEC),
    surfaceVariant = Color(0xFF232529),
    onSurfaceVariant = Color(0xFFC6C6C9),
    outline = Color(0xFF4A4C51)
)

@Composable
fun WeatherOnlyTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
