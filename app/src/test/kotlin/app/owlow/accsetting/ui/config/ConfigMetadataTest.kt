package app.owlow.accsetting.ui.config

import app.owlow.accsetting.R
import app.owlow.accsetting.acc.CapacityConfig
import app.owlow.accsetting.acc.ConfigGroupMode
import app.owlow.accsetting.acc.GroupedConfigRead
import app.owlow.accsetting.acc.TemperatureConfig
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
