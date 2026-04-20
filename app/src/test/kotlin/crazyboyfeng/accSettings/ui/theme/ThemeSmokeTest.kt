package crazyboyfeng.accSettings.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemeSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appTheme_exposesMaterialColorScheme() {
        var colorScheme: ColorScheme? = null

        composeRule.setContent {
            AccSettingsTheme {
                colorScheme = MaterialTheme.colorScheme
            }
        }

        assertNotNull(colorScheme)
    }
}
