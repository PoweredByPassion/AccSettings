package app.owlow.accsettings.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AccPrimary,
    onPrimary = AccOnPrimary,
    secondary = AccSecondary,
    onSecondary = AccOnSecondary,
    surface = AccSurface,
    onSurface = Zinc950,
    surfaceVariant = AccSurfaceVariant,
    onSurfaceVariant = Zinc600,
    background = AccBackground,
    onBackground = Zinc950,
    error = AccError,
    outline = AccDivider,
    outlineVariant = AccDivider,
    secondaryContainer = Zinc100,
    tertiary = AccAccent,
    onTertiary = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Zinc50,
    onPrimary = Zinc950,
    secondary = Zinc400,
    onSecondary = Zinc950,
    surface = Zinc900,
    onSurface = Zinc50,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,
    background = Zinc950,
    onBackground = Zinc50,
    error = AccError,
    outline = Zinc700,
    outlineVariant = Zinc700,
    secondaryContainer = Zinc800,
    tertiary = AccAccentDark,
    onTertiary = Zinc950
)

@Composable
fun AccSettingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AccTypography,
        content = content
    )
}
