package crazyboyfeng.accSettings.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccStateManagerTest {
    @Test
    fun refresh_now_consumes_bridge_results() = runBlocking {
        AccStateManager.resetForTesting(
            bridgeFactory = {
                testBridge(
                    version = 202501010,
                    versionName = "2025.1",
                    daemonRunning = { true },
                    bundledVersionCode = 202505180
                )
            }
        )

        AccStateManager.refreshNow()

        assertEquals(
            AccStatus(
                installState = AccInstallState.UPDATE_AVAILABLE,
                installedVersionName = "2025.1",
                daemonRunning = true,
                canManageDaemon = true,
                showInstallAction = true,
                showUninstallAction = true
            ),
            AccStateManager.getCurrentStatus()
        )
    }

    @Test
    fun settings_state_is_normalized_from_status() {
        val state = AccStateManager.toSettingsState(
            AccStatus(
                installState = AccInstallState.UP_TO_DATE,
                installedVersionName = "2025.5.18-dev",
                daemonRunning = true,
                canManageDaemon = true,
                showInstallAction = false,
                showUninstallAction = true
            )
        )

        assertEquals(AccSettingsSummary.UP_TO_DATE, state.summary)
        assertTrue(state.daemonEnabled)
        assertTrue(state.configEnabled)
        assertTrue(state.shouldServe)
        assertFalse(state.shouldScheduleFollowUpRefresh)
    }

    @Test
    fun daemon_toggle_uses_bridge_instead_of_direct_command_checks() = runBlocking {
        val calls = mutableListOf<Boolean>()
        AccStateManager.resetForTesting(
            bridgeFactory = {
                testBridge(
                    version = 202505180,
                    versionName = "2025.5.18-dev",
                    daemonRunning = { calls.lastOrNull() ?: false },
                    bundledVersionCode = 202505180,
                    onSetDaemonRunning = { enabled ->
                        calls += enabled
                        DaemonActionResult(success = true, daemonRunning = enabled)
                    }
                )
            }
        )

        AccStateManager.setDaemonRunning(true)

        assertEquals(listOf(true), calls)
        assertTrue(AccStateManager.isDaemonRunning())
    }

    private fun testBridge(
        version: Int = 0,
        versionName: String? = null,
        daemonRunning: suspend () -> Boolean = { false },
        bundledVersionCode: Int = 0,
        onSetDaemonRunning: suspend (Boolean) -> DaemonActionResult = {
            DaemonActionResult(success = true, daemonRunning = it)
        }
    ): AccBridge = AccBridge(
        capabilityProbe = { unsupportedCapability() },
        versionReader = { version to versionName },
        daemonReader = daemonRunning,
        currentConfigReader = { java.util.Properties() },
        defaultConfigReader = { java.util.Properties() },
        daemonToggleAction = { enabled -> onSetDaemonRunning(enabled).success },
        bundledVersionCodeProvider = { bundledVersionCode }
    )

    private fun unsupportedCapability(): AccCapability = AccCapability.from(
        AccProbeFacts(
            hasRoot = true,
            availableEntrypoints = emptyList(),
            selectedEntrypoint = null,
            accVersionName = null,
            accVersionCode = 0,
            daemonRunning = false,
            canReadInfo = false,
            canReadCurrentConfig = false,
            canReadDefaultConfig = false,
            canReadLogs = false,
            canExportDiagnostics = false,
            supportedChargingSwitches = emptyList(),
            preferredChargingSwitch = null,
            supportsCurrentControl = false,
            supportsVoltageControl = false,
            supportedCapacityModes = emptySet(),
            supportedTemperatureModes = emptySet()
        )
    )
}
