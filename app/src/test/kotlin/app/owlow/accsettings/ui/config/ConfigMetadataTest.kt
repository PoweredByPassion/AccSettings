package app.owlow.accsettings.ui.config

import app.owlow.accsettings.R
import app.owlow.accsettings.acc.CapacityConfig
import app.owlow.accsettings.acc.ConfigGroupMode
import app.owlow.accsettings.acc.GroupedConfigRead
import app.owlow.accsettings.acc.TemperatureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class ConfigMetadataTest {
    @Test
    fun advancedFields_readCurrentAccPropertyNames() {
        val grouped = groupedConfig(
            properties = mapOf(
                "charging_switch" to "battery/input_suspend",
                "max_charging_voltage" to "4400",
                "current_workaround" to "true"
            )
        )

        val fields = grouped.toConfigGroups().flatMap { it.fields }.associateBy { it.key }

        assertEquals("battery/input_suspend", fields.getValue("charging_switch").value)
        assertEquals("4400", fields.getValue("max_charging_voltage").value)
        assertEquals("true", fields.getValue("current_workaround").value)
    }

    @Test
    fun numericFields_exposeUnitsAndRanges() {
        val grouped = groupedConfig()

        val fields = grouped.toConfigGroups().flatMap { it.fields }.associateBy { it.key }

        assertEquals(R.string.config_unit_percent, fields.getValue("set_pause_capacity").unitRes)
        assertEquals(ConfigFieldKind.PICKER, fields.getValue("set_pause_capacity").kind)
        assertEquals(80, fields.getValue("set_pause_capacity").pickerState!!.selectedValue)
        assertEquals(0, fields.getValue("set_pause_capacity").pickerState!!.minValue)
        assertEquals(100, fields.getValue("set_pause_capacity").pickerState!!.maxValue)
        assertEquals(R.string.config_unit_celsius, fields.getValue("set_max_temp").unitRes)
        assertEquals(ConfigFieldKind.PICKER, fields.getValue("set_max_temp").kind)
        assertEquals(0, fields.getValue("set_max_temp").pickerState!!.minValue)
        assertEquals(100, fields.getValue("set_max_temp").pickerState!!.maxValue)
        assertEquals(R.string.config_unit_millivolt, fields.getValue("max_charging_voltage").unitRes)
        assertTrue(fields.getValue("charging_switch").helperTextRes != null)
    }

    @Test
    fun voltageCapacityFields_preserveVoltageModeRanges() {
        val grouped = groupedConfig(
            capacity = CapacityConfig(0, 3600, 3800, 4000, false, ConfigGroupMode.VOLTAGE)
        )

        val fields = grouped.toConfigGroups().flatMap { it.fields }.associateBy { it.key }

        assertEquals(ConfigFieldKind.PICKER, fields.getValue("set_cooldown_capacity").kind)
        assertEquals(3600, fields.getValue("set_cooldown_capacity").pickerState!!.selectedValue)
        assertEquals(0, fields.getValue("set_shutdown_capacity").pickerState!!.minValue)
        assertEquals(4200, fields.getValue("set_pause_capacity").pickerState!!.maxValue)
    }

    @Test
    fun advancedFields_fallbackToDefaultTemplateValuesWhenCurrentMissing() {
        val grouped = GroupedConfigRead(
            current = Properties().apply {
                setProperty("capacity", "(5 70 72 80 false)")
                setProperty("temperature", "(42 45 43 50)")
            },
            defaults = Properties().apply {
                setProperty("charging_switch", "battery/input_suspend")
                setProperty("max_charging_voltage", "4400")
                setProperty("current_workaround", "true")
            },
            currentCapacity = CapacityConfig.parse("(5 70 72 80 false)"),
            currentTemperature = TemperatureConfig.parse("(42 45 43 50)")
        )

        val fields = grouped.toConfigGroups().flatMap { it.fields }.associateBy { it.key }

        assertEquals("battery/input_suspend", fields.getValue("charging_switch").value)
        assertEquals("4400", fields.getValue("max_charging_voltage").value)
        assertEquals("true", fields.getValue("current_workaround").value)
    }

    @Test
    fun coreAccFields_areExposedWithExpectedKindsAndValues() {
        val grouped = groupedConfig(
            capacity = CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL),
            properties = mapOf(
                "allow_idle_above_pcap" to "false",
                "prioritize_batt_idle_mode" to "true",
                "off_mid" to "false",
                "reboot_resume" to "true",
                "batt_status_workaround" to "false",
                "force_off" to "true",
                "max_charging_current" to "1200",
                "cooldown_current" to "500",
                "temp_level" to "15",
                "apply_on_boot" to "main/charge_current_max::500000",
                "apply_on_plug" to "battery/input_suspend::0",
                "idle_apps" to "maps,pokemon",
                "run_cmd_on_pause" to "logcat -d",
                "batt_status_override" to "Idle",
                "amp_factor" to "1000000",
                "volt_factor" to "1000000"
            )
        )

        val fields = grouped.toConfigGroups().flatMap { it.fields }.associateBy { it.key }

        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("set_capacity_mask").kind)
        assertEquals("true", fields.getValue("set_capacity_mask").value)
        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("allow_idle_above_pcap").kind)
        assertEquals("false", fields.getValue("allow_idle_above_pcap").value)
        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("prioritize_batt_idle_mode").kind)
        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("off_mid").kind)
        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("reboot_resume").kind)
        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("batt_status_workaround").kind)
        assertEquals(ConfigFieldKind.TOGGLE, fields.getValue("force_off").kind)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("max_charging_current").kind)
        assertEquals("1200", fields.getValue("max_charging_current").value)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("cooldown_current").kind)
        assertEquals("500", fields.getValue("cooldown_current").value)
        assertEquals(ConfigFieldKind.NUMBER, fields.getValue("temp_level").kind)
        assertEquals(R.string.config_unit_percent, fields.getValue("temp_level").unitRes)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("apply_on_boot").kind)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("apply_on_plug").kind)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("idle_apps").kind)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("run_cmd_on_pause").kind)
        assertEquals(ConfigFieldKind.TEXT, fields.getValue("batt_status_override").kind)
        assertEquals(ConfigFieldKind.NUMBER, fields.getValue("amp_factor").kind)
        assertEquals(ConfigFieldKind.NUMBER, fields.getValue("volt_factor").kind)
    }

    private fun groupedConfig(
        properties: Map<String, String> = emptyMap(),
        capacity: CapacityConfig = CapacityConfig.parse("(5 70 72 80 false)"),
        temperature: TemperatureConfig = TemperatureConfig.parse("(42 45 43 50)")
    ) = GroupedConfigRead(
        current = Properties().apply {
            setProperty("capacity", capacity.serialize())
            setProperty("temperature", temperature.serialize())
            properties.forEach { (key, value) -> setProperty(key, value) }
        },
        defaults = Properties(),
        currentCapacity = capacity,
        currentTemperature = temperature
    )
}
