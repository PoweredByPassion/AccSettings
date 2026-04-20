package app.owlow.accsetting.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccCapabilityProbeTest {
    @Test
    fun snapshot_exposes_static_availability_and_selected_entrypoint() {
        val snapshot = AccCapability.from(fakeFacts())

        assertTrue(snapshot.staticAvailability.hasRoot)
        assertEquals(
            listOf("/dev/acca", "/data/adb/vr25/acc/acc.sh"),
            snapshot.staticAvailability.availableEntrypoints
        )
        assertEquals("/dev/acca", snapshot.staticAvailability.selectedEntrypoint)
        assertEquals("2025.5.18-dev", snapshot.staticAvailability.accVersionName)
        assertEquals(202505180, snapshot.staticAvailability.accVersionCode)
        assertTrue(snapshot.staticAvailability.canInstallBundledAcc)
        assertTrue(snapshot.staticAvailability.canUpgradeBundledAcc)
        assertTrue(snapshot.staticAvailability.canRepairAcc)
    }

    @Test
    fun snapshot_exposes_runtime_readability_flags() {
        val snapshot = AccCapability.from(
            fakeFacts(
                daemonRunning = false,
                canReadInfo = false,
                canReadCurrentConfig = true,
                canReadDefaultConfig = false,
                canReadLogs = true,
                canExportDiagnostics = false
            )
        )

        assertFalse(snapshot.runtimeCapability.daemonRunning)
        assertFalse(snapshot.runtimeCapability.canReadInfo)
        assertTrue(snapshot.runtimeCapability.canReadCurrentConfig)
        assertFalse(snapshot.runtimeCapability.canReadDefaultConfig)
        assertTrue(snapshot.runtimeCapability.canReadLogs)
        assertFalse(snapshot.runtimeCapability.canExportDiagnostics)
    }

    @Test
    fun snapshot_exposes_device_control_flags_from_probe_results() {
        val snapshot = AccCapability.from(
            fakeFacts(
                supportedChargingSwitches = listOf("battery_charging_enabled", "input_suspend"),
                preferredChargingSwitch = "input_suspend",
                supportsCurrentControl = true,
                supportsVoltageControl = true,
                supportedCapacityModes = setOf<CapacityMode>(
                    CapacityMode.PERCENT,
                    CapacityMode.ADVANCED_CUSTOM
                ),
                supportedTemperatureModes = setOf<TemperatureMode>(
                    TemperatureMode.CELSIUS,
                    TemperatureMode.ADVANCED_CUSTOM
                )
            )
        )

        assertEquals(
            listOf("battery_charging_enabled", "input_suspend"),
            snapshot.deviceControlCapability.supportedChargingSwitches
        )
        assertEquals("input_suspend", snapshot.deviceControlCapability.preferredChargingSwitch)
        assertTrue(snapshot.deviceControlCapability.supportsCurrentControl)
        assertTrue(snapshot.deviceControlCapability.supportsVoltageControl)
        assertEquals(
            setOf(CapacityMode.PERCENT, CapacityMode.ADVANCED_CUSTOM),
            snapshot.deviceControlCapability.supportedCapacityModes
        )
        assertEquals(
            setOf(TemperatureMode.CELSIUS, TemperatureMode.ADVANCED_CUSTOM),
            snapshot.deviceControlCapability.supportedTemperatureModes
        )
    }

    @Test
    fun snapshot_derives_availability_values_from_raw_facts() {
        val snapshot = AccCapability.from(
            fakeFacts(
                hasRoot = false,
                availableEntrypoints = emptyList(),
                selectedEntrypoint = null,
                accVersionName = null,
                accVersionCode = 0
            )
        )

        assertFalse(snapshot.staticAvailability.hasRoot)
        assertEquals(emptyList<String>(), snapshot.staticAvailability.availableEntrypoints)
        assertEquals(null, snapshot.staticAvailability.selectedEntrypoint)
        assertFalse(snapshot.staticAvailability.canInstallBundledAcc)
        assertFalse(snapshot.staticAvailability.canUpgradeBundledAcc)
        assertFalse(snapshot.staticAvailability.canRepairAcc)
    }

    @Test
    fun probe_selects_entrypoint_from_collected_candidates() = runBlocking {
        val probe = AccCapabilityProbe {
            fakeFacts(
                availableEntrypoints = listOf("/dev/acca", "/data/adb/vr25/acc/acc.sh"),
                selectedEntrypoint = "/dev/acca"
            )
        }

        val snapshot = probe.snapshot()

        assertEquals("/dev/acca", snapshot.staticAvailability.selectedEntrypoint)
        assertEquals(
            listOf("/dev/acca", "/data/adb/vr25/acc/acc.sh"),
            snapshot.staticAvailability.availableEntrypoints
        )
    }

    @Test
    fun probe_maps_runtime_readability_fields() = runBlocking {
        val probe = AccCapabilityProbe {
            fakeFacts(
                daemonRunning = false,
                canReadInfo = true,
                canReadCurrentConfig = false,
                canReadDefaultConfig = true,
                canReadLogs = false,
                canExportDiagnostics = true
            )
        }

        val snapshot = probe.snapshot()

        assertFalse(snapshot.runtimeCapability.daemonRunning)
        assertTrue(snapshot.runtimeCapability.canReadInfo)
        assertFalse(snapshot.runtimeCapability.canReadCurrentConfig)
        assertTrue(snapshot.runtimeCapability.canReadDefaultConfig)
        assertFalse(snapshot.runtimeCapability.canReadLogs)
        assertTrue(snapshot.runtimeCapability.canExportDiagnostics)
    }

    @Test
    fun probe_derives_device_control_capabilities_from_detected_switches() = runBlocking {
        val probe = AccCapabilityProbe {
            fakeFacts(
                supportedChargingSwitches = listOf("battery_charging_enabled", "input_suspend"),
                preferredChargingSwitch = "input_suspend",
                supportsCurrentControl = true,
                supportsVoltageControl = false
            )
        }

        val snapshot = probe.snapshot()

        assertEquals(
            listOf("battery_charging_enabled", "input_suspend"),
            snapshot.deviceControlCapability.supportedChargingSwitches
        )
        assertEquals("input_suspend", snapshot.deviceControlCapability.preferredChargingSwitch)
        assertTrue(snapshot.deviceControlCapability.supportsCurrentControl)
        assertFalse(snapshot.deviceControlCapability.supportsVoltageControl)
    }

    @Test
    fun refresh_reasons_trigger_full_probe_rebuild() = runBlocking {
        var invocationCount = 0
        val probe = AccCapabilityProbe {
            invocationCount += 1
            if (invocationCount == 1) {
                fakeFacts(selectedEntrypoint = "/dev/acca", accVersionCode = 202505180)
            } else {
                fakeFacts(selectedEntrypoint = "/data/adb/vr25/acc/acc.sh", accVersionCode = 202505181)
            }
        }

        val initial = probe.snapshot()
        val refreshed = probe.refresh(ProbeRefreshReason.REPAIR)

        assertEquals("/dev/acca", initial.staticAvailability.selectedEntrypoint)
        assertEquals("/data/adb/vr25/acc/acc.sh", refreshed.staticAvailability.selectedEntrypoint)
        assertEquals(202505181, refreshed.staticAvailability.accVersionCode)
        assertEquals(2, invocationCount)
    }

    private fun fakeFacts(
        hasRoot: Boolean = true,
        availableEntrypoints: List<String> = listOf("/dev/acca", "/data/adb/vr25/acc/acc.sh"),
        selectedEntrypoint: String? = "/dev/acca",
        accVersionName: String? = "2025.5.18-dev",
        accVersionCode: Int = 202505180,
        daemonRunning: Boolean = true,
        canReadInfo: Boolean = true,
        canReadCurrentConfig: Boolean = true,
        canReadDefaultConfig: Boolean = true,
        canReadLogs: Boolean = true,
        canExportDiagnostics: Boolean = true,
        supportedChargingSwitches: List<String> = listOf("battery_charging_enabled"),
        preferredChargingSwitch: String? = "battery_charging_enabled",
        supportsCurrentControl: Boolean = false,
        supportsVoltageControl: Boolean = false,
        supportedCapacityModes: Set<CapacityMode> = setOf<CapacityMode>(CapacityMode.PERCENT),
        supportedTemperatureModes: Set<TemperatureMode> = setOf<TemperatureMode>(TemperatureMode.CELSIUS)
    ): AccProbeFacts = AccProbeFacts(
        hasRoot = hasRoot,
        availableEntrypoints = availableEntrypoints,
        selectedEntrypoint = selectedEntrypoint,
        accVersionName = accVersionName,
        accVersionCode = accVersionCode,
        daemonRunning = daemonRunning,
        canReadInfo = canReadInfo,
        canReadCurrentConfig = canReadCurrentConfig,
        canReadDefaultConfig = canReadDefaultConfig,
        canReadLogs = canReadLogs,
        canExportDiagnostics = canExportDiagnostics,
        supportedChargingSwitches = supportedChargingSwitches,
        preferredChargingSwitch = preferredChargingSwitch,
        supportsCurrentControl = supportsCurrentControl,
        supportsVoltageControl = supportsVoltageControl,
        supportedCapacityModes = supportedCapacityModes,
        supportedTemperatureModes = supportedTemperatureModes
    )
}
