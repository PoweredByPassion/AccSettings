package app.owlow.accsettings.ui.overview

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.owlow.accsettings.ui.theme.AccSettingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverviewScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingPrimaryActions_reportsTheirOwnIds() {
        val tappedActions = mutableListOf<String>()

        composeRule.setContent {
            AccSettingTheme {
                OverviewScreen(
                    uiState = OverviewUiState(
                        isLoading = false,
                        statusHeadline = "ACC is running",
                        primaryActions = listOf(
                            OverviewAction("refresh", "Refresh state"),
                            OverviewAction("configuration", "Open configuration")
                        )
                    ),
                    onAction = { tappedActions += it }
                )
            }
        }

        composeRule.onNodeWithText("Refresh state").performClick()
        composeRule.onNodeWithText("Open configuration").performClick()

        assertEquals(listOf("refresh", "configuration"), tappedActions)
    }

    @Test
    @Config(sdk = [34], qualifiers = "zh-rCN")
    fun chineseLocale_usesLocalizedOverviewTitle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        composeRule.setContent {
            AccSettingTheme {
                OverviewScreen(
                    uiState = OverviewUiState(
                        isLoading = false,
                        statusHeadline = "status"
                    ),
                    onAction = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(app.owlow.accsettings.R.string.overview)).assertExists()
    }
}
