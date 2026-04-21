package app.owlow.accsettings.ui.tools

import app.owlow.accsettings.MainDispatcherRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repairAction_exposesProgress_thenSuccessMessage() = runTest {
        val viewModel = ToolsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            toolsRepository = FakeToolsRepository(
                actionMessage = "ACC repaired successfully"
            )
        )

        viewModel.repair().join()

        assertEquals(
            ToolStatusMessage("ACC repaired successfully", isError = false),
            viewModel.uiState.value.installSection.statusMessage
        )
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun repairFailure_exposesErrorMessageOnInstallSection() = runTest {
        val viewModel = ToolsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            toolsRepository = FakeToolsRepository(
                actionError = IllegalStateException("repair failed")
            )
        )

        viewModel.repair().join()

        assertEquals(
            ToolStatusMessage("repair failed", isError = true),
            viewModel.uiState.value.installSection.statusMessage
        )
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun refresh_exposesDetailedLogs() = runTest {
        val viewModel = ToolsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            toolsRepository = FakeToolsRepository(
                actionMessage = "ok",
                logContent = "daemon started\ncharging active"
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.logsSection.content.contains("charging active"))
    }

    private class FakeToolsRepository(
        private val actionMessage: String = "ok",
        private val actionError: Throwable? = null,
        private val logContent: String = "No logs"
    ) : ToolsRepository {
        override suspend fun loadSnapshot(): ToolsSnapshot = ToolsSnapshot(
            status = null,
            capability = null,
            appVersion = "1.0.0",
            bundledAccVersion = "2026.4.17",
            runtimeLog = logContent,
            packageName = "app.owlow.accsettings"
        )

        override suspend fun installOrUpdate(): String = actionResult()

        override suspend fun repair(): String = actionResult()

        override suspend fun restartService(): String = actionResult()

        override suspend fun forceRedetect(): String = actionResult()

        private fun actionResult(): String {
            actionError?.let { throw it }
            return actionMessage
        }
    }
}
