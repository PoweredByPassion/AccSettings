package app.owlow.accsetting.ui.tools

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
            ToolsScreen(
                state = ToolsUiState(
                    logsSection = ToolLogSection(
                        title = "Runtime Logs",
                        summary = "Recent ACC log output for troubleshooting.",
                        content = List(100) { "log line $it" }.joinToString("\n")
                    )
                ),
                onAction = {},
                onConfirmAction = {},
                onDismissConfirmation = {}
            )
        }

        composeRule.onNodeWithText("Runtime Logs").assertExists()
    }
}
