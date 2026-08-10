package app.owlow.accsettings.acc

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingInfoParserTest {
    private val accInfo = """
        level 34%
        status Charging
        temp 34℃
        current_now 0.02A
        voltage_now 3.81V
        power_now 0.08W
        charge_type pc_port
        dc/online 0
        pc_port/online 1
        usb/online 0
    """.trimIndent()

    @Test
    fun parses_and_normalizes_acc_info_lines() {
        val info = ChargingInfoParser.parseAccInfo(accInfo)
        // Base fields are normalized to the system-API numeric encoding.
        assertEquals("34", info.level)          // percent
        assertEquals("Charging", info.status)
        assertEquals("340", info.temp)          // tenths of °C
        assertEquals("20000", info.current)     // µA
        assertEquals("3810", info.voltage)      // mV
        assertEquals("80000", info.power)       // µW
        assertEquals("pc_port", info.chargeType)
        assertEquals(true, info.powerConnected) // pc_port/online 1
    }

    @Test
    fun power_connected_is_false_when_all_offline() {
        val info = ChargingInfoParser.parseAccInfo(
            "dc/online 0\npc_port/online 0\nusb/online 0\n"
        )
        assertEquals(null, info.powerConnected)
    }

    @Test
    fun power_connected_is_true_when_any_online() {
        val info = ChargingInfoParser.parseAccInfo(
            "dc/online 0\npc_port/online 0\nusb/online 1\n"
        )
        assertEquals(true, info.powerConnected)
    }

    @Test
    fun parses_negative_ampere_and_watt_values() {
        val info = ChargingInfoParser.parseAccInfo("current_now -0.27A\nvoltage_now 3.78V\npower_now -1.02W\n")
        assertEquals("-270000", info.current)
        assertEquals("-1020000", info.power)
    }

    @Test
    fun missing_fields_are_null() {
        val info = ChargingInfoParser.parseAccInfo("level 34%\n")
        assertNull(info.status)
        assertNull(info.chargeType)
    }

    @Test
    fun merge_combines_base_and_handshake() {
        val base = ChargingInfo(
            level = "34", status = "Charging", temp = "340",
            current = "20000", voltage = "3810", power = "80000", chargeType = "pc_port"
        )
        val handshake = ChargingInfo(
            protocol = "USB_PD", realProtocol = "USB", pdActive = false,
            negotiatedCurrent = "500", negotiatedVoltage = "5000", negotiatedPower = "2.5 W", ccMode = "0"
        )
        val merged = ChargingInfoParser.mergeChargingInfo(base, handshake)
        assertEquals("34", merged?.level)
        assertEquals("USB_PD", merged?.protocol)
        assertEquals("2.5 W", merged?.negotiatedPower)
    }

    @Test
    fun merge_returns_null_when_base_is_null() {
        assertNull(ChargingInfoParser.mergeChargingInfo(null, ChargingInfo(protocol = "USB_PD")))
    }
}
