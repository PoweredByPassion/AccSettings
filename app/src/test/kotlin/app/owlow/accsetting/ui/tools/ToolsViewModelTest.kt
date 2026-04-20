package app.owlow.accsetting.ui.tools

import app.owlow.accsetting.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repairAction_exposesProgress_thenSuccessMessage() = runTest {
        val viewModel = ToolsViewModel(
            toolsRepository = FakeToolsRepository(
                actionMessage = "ACC repaired successfully"
            )
        )

        viewModel.repair().join()

        assertEquals("ACC repaired successfully", viewModel.uiState.value.lastMessage)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun refresh_exposesDetailedLogs() = runTest {
        val viewModel = ToolsViewModel(
            toolsRepository = FakeToolsRepository(
                actionMessage = "ok",
                logContent = "daemon started\ncharging active"
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.logsSection.content.contains("charging active"))
    }

    private class FakeToolsRepository(
        private val actionMessage: String,
        private val logContent: String = "No logs"
    ) : ToolsRepository {
        override suspend fun loadSnapshot(): ToolsSnapshot = ToolsSnapshot(
            status = null,
            capability = null,
            appVersion = "1.0.0",
            bundledAccVersion = "2026.4.17",
            runtimeLog = logContent,
            packageName = "app.owlow.accsetting"
        )

        override suspend fun installOrUpdate(): String = actionMessage

        override suspend fun repair(): String = actionMessage

        override suspend fun restartService(): String = actionMessage

        override suspend fun forceRedetect(): String = actionMessage
    }
}
