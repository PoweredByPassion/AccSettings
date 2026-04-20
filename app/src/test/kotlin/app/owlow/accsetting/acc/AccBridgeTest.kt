package app.owlow.accsetting.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Properties

class AccBridgeTest {
    @Test
    fun probeCapabilities_returns_probe_snapshot() = runBlocking {
        val capability = AccCapability.from(
            AccProbeFacts(
                hasRoot = true,
                availableEntrypoints = listOf("/dev/acca"),
                selectedEntrypoint = "/dev/acca",
                accVersionName = "2025.5.18-dev",
                accVersionCode = 202505180,
                daemonRunning = true,
                canReadInfo = true,
                canReadCurrentConfig = true,
                canReadDefaultConfig = true,
                canReadLogs = true,
                canExportDiagnostics = true,
                supportedChargingSwitches = listOf("battery_charging_enabled"),
                preferredChargingSwitch = "battery_charging_enabled",
                supportsCurrentControl = false,
                supportsVoltageControl = false,
                supportedCapacityModes = setOf(CapacityMode.PERCENT),
                supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
            )
        )
        val bridge = AccBridge(
            capabilityProbe = { capability },
            versionReader = { Pair(202505180, "2025.5.18-dev") },
            daemonReader = { true },
            currentConfigReader = { propertiesOf("capacity" to "80") },
            defaultConfigReader = { propertiesOf("capacity" to "75") }
        )

        assertSame(capability, bridge.probeCapabilities())
    }

    @Test
    fun readStatus_resolves_install_state_from_readers() = runBlocking {
        val bridge = AccBridge(
            capabilityProbe = { capabilityWithDefaults() },
            versionReader = { Pair(202505170, "2025.5.17") },
            daemonReader = { true },
            currentConfigReader = { Properties() },
            defaultConfigReader = { Properties() },
            bundledVersionCodeProvider = { 202505180 }
        )

        assertEquals(
            AccStatus(
                installState = AccInstallState.UPDATE_AVAILABLE,
                installedVersionName = "2025.5.17",
                daemonRunning = true,
                canManageDaemon = true,
                showInstallAction = true,
                showUninstallAction = true
            ),
            bridge.readStatus()
        )
    }

    @Test
    fun readCurrentConfig_returns_current_properties() = runBlocking {
        val currentConfig = propertiesOf("capacity" to "80", "cooldown" to "70")
        val bridge = AccBridge(
            capabilityProbe = { capabilityWithDefaults() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { currentConfig },
            defaultConfigReader = { Properties() }
        )

        assertSame(currentConfig, bridge.readCurrentConfig())
    }

    @Test
    fun readDefaultConfig_returns_default_properties() = runBlocking {
        val defaultConfig = propertiesOf("capacity" to "75")
        val bridge = AccBridge(
            capabilityProbe = { capabilityWithDefaults() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { Properties() },
            defaultConfigReader = { defaultConfig }
        )

        assertSame(defaultConfig, bridge.readDefaultConfig())
    }

    @Test
    fun readGroupedConfig_returns_combined_current_and_default_config() = runBlocking {
        val currentConfig = propertiesOf("capacity" to "80")
        val defaultConfig = propertiesOf("capacity" to "75")
        val bridge = AccBridge(
            capabilityProbe = { capabilityWithDefaults() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { currentConfig },
            defaultConfigReader = { defaultConfig }
        )

        val groupedConfig = bridge.readGroupedConfig()

        assertSame(currentConfig, groupedConfig.current)
        assertSame(defaultConfig, groupedConfig.defaults)
    }

    private fun capabilityWithDefaults(): AccCapability = AccCapability.from(
        AccProbeFacts(
            hasRoot = true,
            availableEntrypoints = listOf("/dev/acca"),
            selectedEntrypoint = "/dev/acca",
            accVersionName = "2025.5.18-dev",
            accVersionCode = 202505180,
            daemonRunning = true,
            canReadInfo = true,
            canReadCurrentConfig = true,
            canReadDefaultConfig = true,
            canReadLogs = true,
            canExportDiagnostics = true,
            supportedChargingSwitches = listOf("battery_charging_enabled"),
            preferredChargingSwitch = "battery_charging_enabled",
            supportsCurrentControl = false,
            supportsVoltageControl = false,
            supportedCapacityModes = setOf(CapacityMode.PERCENT),
            supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
        )
    )

    private fun propertiesOf(vararg entries: Pair<String, String>): Properties = Properties().apply {
        entries.forEach { (key, value) -> setProperty(key, value) }
    }
}
