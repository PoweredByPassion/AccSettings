package crazyboyfeng.accSettings.acc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class AccPresetsTest {
    @Test
    fun registers_the_initial_preset_set() {
        assertEquals(
            setOf(
                "battery_care",
                "desk_docked",
                "thermal_guard",
                "always_on_device",
                "full_charge_priority"
            ),
            AccPresets.presets.map { it.id }.toSet()
        )
    }

    @Test
    fun battery_care_generates_a_capacity_focused_draft() {
        val current = groupedConfig(
            capacity = CapacityConfig(5, 101, 70, 75, false, ConfigGroupMode.MIXED_LEGACY),
            temperature = TemperatureConfig(45, 50, 48, 55, ConfigGroupMode.NORMAL)
        )

        val candidate = AccPresets.generateDraft(
            id = "battery_care",
            current = current,
            defaults = current,
            capability = supportedCapability()
        )

        assertEquals(setOf("capacity"), candidate.appliedFeatures)
        assertEquals(emptySet<String>(), candidate.skippedFeatures)
        assertEquals(ConfigGroupMode.NORMAL, candidate.draft.currentCapacity!!.mode)
        assertEquals(80, candidate.draft.currentCapacity!!.pause)
        assertEquals(current.currentTemperature, candidate.draft.currentTemperature)
    }

    @Test
    fun thermal_guard_generates_a_temperature_focused_draft() {
        val current = groupedConfig(
            capacity = CapacityConfig(5, 70, 72, 85, false, ConfigGroupMode.NORMAL),
            temperature = TemperatureConfig(45, 50, 48, 60, ConfigGroupMode.NORMAL)
        )

        val candidate = AccPresets.generateDraft(
            id = "thermal_guard",
            current = current,
            defaults = current,
            capability = supportedCapability()
        )

        assertEquals(setOf("temperature"), candidate.appliedFeatures)
        assertEquals(40, candidate.draft.currentTemperature!!.cooldown)
        assertEquals(47, candidate.draft.currentTemperature!!.shutdown)
        assertEquals(current.currentCapacity, candidate.draft.currentCapacity)
    }

    @Test
    fun capability_aware_trimming_skips_unsupported_current_voltage_features() {
        val current = groupedConfig()

        val candidate = AccPresets.generateDraft(
            id = "always_on_device",
            current = current,
            defaults = current,
            capability = supportedCapability(
                supportsCurrentControl = false,
                supportsVoltageControl = false
            )
        )

        assertTrue(candidate.appliedFeatures.contains("temperature"))
        assertFalse(candidate.appliedFeatures.contains("current_voltage"))
        assertTrue(candidate.skippedFeatures.contains("current_voltage"))
    }

    @Test
    fun full_charge_priority_keeps_existing_values_for_trimmed_features() {
        val current = groupedConfig().copy(
            current = propertiesOf("charging_current" to "1200"),
            defaults = propertiesOf("charging_current" to "1200")
        )

        val candidate = AccPresets.generateDraft(
            id = "full_charge_priority",
            current = current,
            defaults = current,
            capability = supportedCapability(
                supportsCurrentControl = false,
                supportsVoltageControl = false
            )
        )

        assertEquals("1200", candidate.draft.current.getProperty("charging_current"))
        assertTrue(candidate.skippedFeatures.contains("current_voltage"))
    }

    private fun groupedConfig(
        capacity: CapacityConfig = CapacityConfig(5, 70, 72, 80, false, ConfigGroupMode.NORMAL),
        temperature: TemperatureConfig = TemperatureConfig(42, 45, 43, 50, ConfigGroupMode.NORMAL)
    ): GroupedConfigRead {
        val current = propertiesOf(
            "capacity" to capacity.serialize(),
            "temperature" to temperature.serialize()
        )
        return GroupedConfigRead(
            current = current,
            defaults = Properties().apply { putAll(current) },
            currentCapacity = capacity,
            defaultCapacity = capacity,
            currentTemperature = temperature,
            defaultTemperature = temperature
        )
    }

    private fun supportedCapability(
        supportsCurrentControl: Boolean = true,
        supportsVoltageControl: Boolean = false
    ): AccCapability = AccCapability.from(
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
            supportedChargingSwitches = listOf("switch"),
            preferredChargingSwitch = "switch",
            supportsCurrentControl = supportsCurrentControl,
            supportsVoltageControl = supportsVoltageControl,
            supportedCapacityModes = setOf(CapacityMode.PERCENT),
            supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
        )
    )

    private fun propertiesOf(vararg entries: Pair<String, String>): Properties = Properties().apply {
        entries.forEach { (key, value) -> setProperty(key, value) }
    }
}
