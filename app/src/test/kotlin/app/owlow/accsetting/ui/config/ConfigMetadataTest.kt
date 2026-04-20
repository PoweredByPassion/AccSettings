package app.owlow.accsetting.ui.config

import app.owlow.accsetting.R
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
        assertEquals(0, fields.getValue("set_pause_capacity").minValue)
        assertEquals(100, fields.getValue("set_pause_capacity").maxValue)
        assertEquals(R.string.config_unit_celsius, fields.getValue("set_max_temp").unitRes)
        assertEquals(0, fields.getValue("set_max_temp").minValue)
        assertEquals(100, fields.getValue("set_max_temp").maxValue)
        assertEquals(R.string.config_unit_millivolt, fields.getValue("max_charging_voltage").unitRes)
        assertTrue(fields.getValue("charging_switch").helperTextRes != null)
    }

    private fun groupedConfig(
        properties: Map<String, String> = emptyMap()
    ) = app.owlow.accsetting.acc.GroupedConfigRead(
        current = Properties().apply {
            setProperty("capacity", "(5 70 72 80 false)")
            setProperty("temperature", "(42 45 43 50)")
            properties.forEach { (key, value) -> setProperty(key, value) }
        },
        defaults = Properties(),
        currentCapacity = app.owlow.accsetting.acc.CapacityConfig.parse("(5 70 72 80 false)"),
        currentTemperature = app.owlow.accsetting.acc.TemperatureConfig.parse("(42 45 43 50)")
    )
}
