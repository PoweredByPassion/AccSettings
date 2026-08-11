package app.owlow.accsettings.ui.tools

import app.owlow.accsettings.MainDispatcherRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
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
@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun resetBatteryStats_showsSuccessMessageOnBatterySection() = runTest {
        val viewModel = ToolsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            toolsRepository = FakeToolsRepository(actionMessage = "Battery statistics reset")
        )

        viewModel.requestAction(ToolAction.RESET_BATTERY_STATS)
        assertTrue(viewModel.uiState.value.pendingConfirmation == ToolAction.RESET_BATTERY_STATS)

        viewModel.confirmPendingAction()
        runCurrent()

        assertEquals(
            ToolStatusMessage("Battery statistics reset", isError = false),
            viewModel.uiState.value.batterySection.statusMessage
        )
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun exportLogs_showsExportLocationOnBatterySection() = runTest {
        val viewModel = ToolsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            toolsRepository = FakeToolsRepository(actionMessage = "/sdcard/Download/acc-logs-device.tgz")
        )

        viewModel.requestAction(ToolAction.EXPORT_LOGS)
        runCurrent()

        assertEquals(
            ToolStatusMessage("/sdcard/Download/acc-logs-device.tgz", isError = false),
            viewModel.uiState.value.batterySection.statusMessage
        )
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun estimateHealth_requiresInput_thenShowsResult() = runTest {
        val viewModel = ToolsViewModel(
            context = ApplicationProvider.getApplicationContext(),
            toolsRepository = FakeToolsRepository(actionMessage = "Estimated health: 87.3%")
        )

        // Opening the dialog does not perform the action.
        viewModel.showHealthDialog()
        assertTrue(viewModel.uiState.value.showHealthDialog)

        viewModel.updateHealthInput("4000")
        viewModel.estimateHealth().join()

        assertFalse(viewModel.uiState.value.showHealthDialog)
        assertEquals("Estimated health: 87.3%", viewModel.uiState.value.healthResult)
        assertFalse(viewModel.uiState.value.isBusy)
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

        override suspend fun resetBatteryStats(): String = actionResult()

        override suspend fun exportLogs(): String = actionResult()

        override suspend fun estimateHealth(designCapacityMah: Int): String = actionResult()

        private fun actionResult(): String {
            actionError?.let { throw it }
            return actionMessage
        }
    }
}
