package crazyboyfeng.accSettings.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccPrimary,
    onPrimary = AccOnPrimary,
    secondary = AccSecondary,
    onSecondary = AccOnSecondary,
    surface = AccSurface,
    surfaceVariant = AccSurfaceVariant,
    background = AccSurface
)

private val DarkColors = darkColorScheme(
    primary = AccPrimary,
    onPrimary = AccOnPrimary,
    secondary = AccSecondary,
    onSecondary = AccOnSecondary
)

@Composable
fun AccSettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AccTypography,
        content = content
    )
}
