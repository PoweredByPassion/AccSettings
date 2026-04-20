package app.owlow.accsetting.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Properties

class AccBridgeLifecycleTest {
    @Test
    fun install_decision_path_runs_install_when_not_installed() = runBlocking {
        val actions = mutableListOf<String>()
        val bridge = lifecycleBridge(
            capability = capability(versionCode = 0, entrypoint = null),
            installAction = { actions += "install" },
            upgradeAction = { actions += "upgrade" },
            repairAction = { actions += "repair" },
            refreshCapability = { capability(versionCode = 202505180) }
        )

        val result = bridge.ensureInstalled()

        assertEquals(LifecycleDecision.INSTALL, result.decision)
        assertEquals(listOf("install"), actions)
        assertEquals(202505180, result.capability.staticAvailability.accVersionCode)
    }

    @Test
    fun upgrade_decision_path_runs_upgrade_when_outdated() = runBlocking {
        val actions = mutableListOf<String>()
        val bridge = lifecycleBridge(
            capability = capability(versionCode = 202505170),
            bundledVersionCode = 202505180,
            installAction = { actions += "install" },
            upgradeAction = { actions += "upgrade" },
            repairAction = { actions += "repair" },
            refreshCapability = { capability(versionCode = 202505180) }
        )

        val result = bridge.ensureInstalled()

        assertEquals(LifecycleDecision.UPGRADE, result.decision)
        assertEquals(listOf("upgrade"), actions)
    }

    @Test
    fun repair_path_runs_when_requested() = runBlocking {
        val actions = mutableListOf<String>()
        val bridge = lifecycleBridge(
            capability = capability(versionCode = 202505180),
            installAction = { actions += "install" },
            upgradeAction = { actions += "upgrade" },
            repairAction = { actions += "repair" },
            refreshCapability = { capability(versionCode = 202505180) }
        )

        val result = bridge.repair()

        assertEquals(LifecycleDecision.REPAIR, result.decision)
        assertEquals(listOf("repair"), actions)
    }

    @Test
    fun daemon_state_action_returns_result() = runBlocking {
        val actions = mutableListOf<String>()
        val bridge = lifecycleBridge(
            capability = capability(versionCode = 202505180),
            daemonToggleAction = { running ->
                actions += "daemon:$running"
                true
            }
        )

        val result = bridge.setDaemonRunning(true)

        assertEquals(DaemonActionResult(success = true, daemonRunning = true), result)
        assertEquals(listOf("daemon:true"), actions)
    }

    @Test
    fun capability_refresh_happens_after_lifecycle_actions() = runBlocking {
        val bridge = lifecycleBridge(
            capability = capability(versionCode = 0, entrypoint = null),
            installAction = {},
            refreshCapability = { capability(versionCode = 202505180, entrypoint = "/dev/acca") }
        )

        val result = bridge.ensureInstalled()

        assertEquals("/dev/acca", result.capability.staticAvailability.selectedEntrypoint)
        assertEquals(202505180, result.capability.staticAvailability.accVersionCode)
    }

    private fun lifecycleBridge(
        capability: AccCapability,
        bundledVersionCode: Int = 202505180,
        installAction: suspend () -> Unit = {},
        upgradeAction: suspend () -> Unit = {},
        repairAction: suspend () -> Unit = {},
        daemonToggleAction: suspend (Boolean) -> Boolean = { it },
        refreshCapability: suspend () -> AccCapability = { capability }
    ): AccBridge = AccBridge(
        capabilityProbe = { capability },
        versionReader = {
            capability.staticAvailability.accVersionCode to capability.staticAvailability.accVersionName
        },
        daemonReader = { capability.runtimeCapability.daemonRunning },
        currentConfigReader = { Properties() },
        defaultConfigReader = { Properties() },
        bundledVersionCodeProvider = { bundledVersionCode },
        installAction = installAction,
        upgradeAction = upgradeAction,
        repairAction = repairAction,
        uninstallAction = {},
        daemonToggleAction = daemonToggleAction,
        reinitializeAction = {},
        lifecycleCapabilityRefresh = refreshCapability
    )

    private fun capability(
        versionCode: Int,
        versionName: String? = if (versionCode > 0) "2025.5.18-dev" else null,
        entrypoint: String? = if (versionCode > 0) "/dev/acca" else null
    ): AccCapability = AccCapability.from(
        AccProbeFacts(
            hasRoot = true,
            availableEntrypoints = listOfNotNull(entrypoint),
            selectedEntrypoint = entrypoint,
            accVersionName = versionName,
            accVersionCode = versionCode,
            daemonRunning = versionCode > 0,
            canReadInfo = versionCode > 0,
            canReadCurrentConfig = versionCode > 0,
            canReadDefaultConfig = versionCode > 0,
            canReadLogs = true,
            canExportDiagnostics = true,
            supportedChargingSwitches = emptyList(),
            preferredChargingSwitch = null,
            supportsCurrentControl = false,
            supportsVoltageControl = false,
            supportedCapacityModes = setOf(CapacityMode.PERCENT),
            supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
        )
    )
}
