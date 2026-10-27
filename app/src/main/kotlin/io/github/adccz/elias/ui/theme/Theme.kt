package io.github.adccz.elias.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = SlateBlue40,
    secondary = Sage40,
    tertiary = Sand40,
)

private val DarkColors = darkColorScheme(
    primary = SlateBlue80,
    secondary = Sage80,
    tertiary = Sand80,
)

/**
 * Elias theme. It uses its own calm palette instead of wallpaper-based dynamic colors,
 * so the app looks the same on every phone (and in the README screenshots).
 */
@Composable
fun EliasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
