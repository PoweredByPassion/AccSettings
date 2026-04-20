package app.owlow.accsetting.ui.tools

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.owlow.accsetting.ui.theme.AccSettingTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ToolsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun runtimeLogs_renderInsideToolsScreen() {
        composeRule.setContent {
            AccSettingTheme {
                ToolsScreen(
                    state = ToolsUiState(
                        logsSection = ToolLogSection(
                            title = "Runtime Logs",
                            summary = "summary",
                            content = "log content"
                        )
                    ),
                    onAction = {},
                    onConfirmAction = {},
                    onDismissConfirmation = {}
                )
            }
        }

        composeRule.onNodeWithText("Runtime Logs").assertExists()
    }

    @Test
    @Config(sdk = [33], qualifiers = "zh-rCN")
    fun chineseLocale_usesLocalizedStaticText() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        composeRule.setContent {
            AccSettingTheme {
                ToolsScreen(
                    state = ToolsUiState(),
                    onAction = {},
                    onConfirmAction = {},
                    onDismissConfirmation = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(app.owlow.accsetting.R.string.tools)).assertExists()
        composeRule.onNodeWithText(context.getString(app.owlow.accsetting.R.string.tools_intro)).assertExists()
    }
}
