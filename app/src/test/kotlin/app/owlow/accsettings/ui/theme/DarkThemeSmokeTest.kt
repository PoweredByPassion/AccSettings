package app.owlow.accsettings.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DarkThemeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightTheme_usesLightColors() {
        var bg: androidx.compose.ui.graphics.Color? = null
        var tertiary: androidx.compose.ui.graphics.Color? = null
        var outlineVariant: androidx.compose.ui.graphics.Color? = null

        composeRule.setContent {
            AccSettingTheme(darkTheme = false) {
                val scheme = MaterialTheme.colorScheme
                bg = scheme.background
                tertiary = scheme.tertiary
                outlineVariant = scheme.outlineVariant
            }
        }

        assertEquals(AccBackground, bg)
        assertEquals(AccAccent, tertiary)
        assertEquals(AccDivider, outlineVariant)
    }

    @Test
    fun darkTheme_usesDarkColors() {
        var bg: androidx.compose.ui.graphics.Color? = null
        var onSurface: androidx.compose.ui.graphics.Color? = null
        var tertiary: androidx.compose.ui.graphics.Color? = null
        var outlineVariant: androidx.compose.ui.graphics.Color? = null

        composeRule.setContent {
            AccSettingTheme(darkTheme = true) {
                val scheme = MaterialTheme.colorScheme
                bg = scheme.background
                onSurface = scheme.onSurface
                tertiary = scheme.tertiary
                outlineVariant = scheme.outlineVariant
            }
        }

        assertEquals(Zinc950, bg)
        assertEquals(Zinc50, onSurface)
        assertEquals(AccAccentDark, tertiary)
        assertEquals(Zinc700, outlineVariant)
    }
}
