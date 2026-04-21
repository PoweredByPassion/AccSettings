package app.owlow.accsettings.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Properties

class AccConfigGroupsTest {
    @Test
    fun parseCapacity_handles_special_upstream_tuple() {
        assertEquals(
            CapacityConfig(
                shutdown = 5,
                cooldown = 101,
                resume = 70,
                pause = 75,
                maskAsFull = false,
                mode = ConfigGroupMode.MIXED_LEGACY
            ),
            CapacityConfig.parse("(5 101 70 75 false)")
        )
    }

    @Test
    fun parseTemperature_reads_grouped_values() {
        assertEquals(
            TemperatureConfig(
                cooldown = 39,
                pause = 45,
                resume = 42,
                shutdown = 50,
                mode = ConfigGroupMode.NORMAL
            ),
            TemperatureConfig.parse("(39 45 42 50)")
        )
    }

    @Test
    fun parseCapacity_marks_unstructured_values_as_advanced_custom() {
        assertEquals(
            ConfigGroupMode.ADVANCED_CUSTOM,
            CapacityConfig.parse("(foo bar baz)").mode
        )
    }

    @Test
    fun grouped_serialization_round_trips() {
        val capacity = CapacityConfig(
            shutdown = 5,
            cooldown = 70,
            resume = 72,
            pause = 80,
            maskAsFull = true,
            mode = ConfigGroupMode.NORMAL
        )
        val temperature = TemperatureConfig(
            cooldown = 39,
            pause = 45,
            resume = 42,
            shutdown = 50,
            mode = ConfigGroupMode.NORMAL
        )

        assertEquals(capacity, CapacityConfig.parse(capacity.serialize()))
        assertEquals(temperature, TemperatureConfig.parse(temperature.serialize()))
    }

    @Test
    fun readGroupedConfig_uses_group_models() = runBlocking {
        val current = propertiesOf(
            "capacity" to "(5 101 70 75 false)",
            "temperature" to "(39 45 42 50)"
        )
        val defaults = propertiesOf("capacity" to "(5 70 72 80 true)")
        val bridge = AccBridge(
            capabilityProbe = { capabilityWithDefaults() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { current },
            defaultConfigReader = { defaults }
        )

        val grouped = bridge.readGroupedConfig()

        assertEquals(ConfigGroupMode.MIXED_LEGACY, grouped.currentCapacity?.mode)
        assertEquals(75, grouped.currentCapacity?.pause)
        assertEquals(ConfigGroupMode.NORMAL, grouped.currentTemperature?.mode)
        assertEquals(50, grouped.currentTemperature?.shutdown)
        assertEquals(ConfigGroupMode.NORMAL, grouped.defaultCapacity?.mode)
        assertNull(grouped.defaultTemperature)
    }

    @Test
    fun readGroupedConfig_supports_split_threshold_properties() = runBlocking {
        val current = propertiesOf(
            "shutdown_capacity" to "5",
            "cooldown_capacity" to "70",
            "resume_capacity" to "72",
            "pause_capacity" to "80",
            "capacity_mask" to "false",
            "cooldown_temp" to "45",
            "max_temp" to "50",
            "resume_temp" to "40",
            "shutdown_temp" to "55"
        )
        val defaults = propertiesOf(
            "shutdown_capacity" to "5",
            "cooldown_capacity" to "101",
            "resume_capacity" to "70",
            "pause_capacity" to "75",
            "capacity_mask" to "false",
            "cooldown_temp" to "45",
            "max_temp" to "50",
            "resume_temp" to "40",
            "shutdown_temp" to "55"
        )
        val bridge = AccBridge(
            capabilityProbe = { capabilityWithDefaults() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { current },
            defaultConfigReader = { defaults }
        )

        val grouped = bridge.readGroupedConfig()

        assertEquals(
            CapacityConfig(5, 70, 72, 80, false, ConfigGroupMode.NORMAL),
            grouped.currentCapacity
        )
        assertEquals(
            TemperatureConfig(45, 50, 40, 55, ConfigGroupMode.NORMAL),
            grouped.currentTemperature
        )
        assertEquals(
            CapacityConfig(5, 101, 70, 75, false, ConfigGroupMode.MIXED_LEGACY),
            grouped.defaultCapacity
        )
        assertEquals(
            TemperatureConfig(45, 50, 40, 55, ConfigGroupMode.NORMAL),
            grouped.defaultTemperature
        )
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
