package app.owlow.accsettings.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SysfsChargingReaderTest {
    private val usbNodes = mapOf(
        "/sys/class/power_supply/usb/type" to "USB_PD",
        "/sys/class/power_supply/usb/real_type" to "USB",
        "/sys/class/power_supply/usb/pd_active" to "0",
        "/sys/class/power_supply/usb/current_max" to "500000",
        "/sys/class/power_supply/usb/voltage_max" to "5000000",
        "/sys/class/power_supply/usb/adapter_cc_mode" to "0"
    )

    @Test
    fun reads_protocol_and_pd_state() = runBlocking {
        val info = SysfsChargingReader.read({ usbNodes[it] }, ports = listOf("usb"))
        assertEquals("USB_PD", info.protocol)
        assertEquals("USB", info.realProtocol)
        assertEquals(false, info.pdActive)
    }

    @Test
    fun computes_negotiated_power() = runBlocking {
        val info = SysfsChargingReader.read({ usbNodes[it] }, ports = listOf("usb"))
        assertEquals("5000", info.negotiatedVoltage)   // µV -> mV display-ready
        assertEquals("500", info.negotiatedCurrent)     // µA -> mA display-ready
        assertEquals("2.5 W", info.negotiatedPower)
    }

    @Test
    fun falls_back_to_next_port_with_values() = runBlocking {
        val mainNodes = mapOf(
            "/sys/class/power_supply/main/type" to "USB",
            "/sys/class/power_supply/main/current_max" to "300000",
            "/sys/class/power_supply/main/voltage_max" to "5000000"
        )
        val info = SysfsChargingReader.read(
            // usb/type is explicitly absent so the reader falls through to main.
            { path -> if (path.endsWith("/usb/type")) null else mainNodes[path] ?: usbNodes[path] },
            ports = listOf("usb", "main")
        )
        // Falls to main and uses its values.
        assertEquals("USB", info.protocol)
        assertEquals("300", info.negotiatedCurrent)
    }

    @Test
    fun missing_nodes_yield_null_and_no_power() = runBlocking {
        val info = SysfsChargingReader.read({ null }, ports = listOf("usb"))
        assertNull(info.protocol)
        assertNull(info.negotiatedPower)
    }

    @Test
    fun low_power_never_renders_bare_dot() = runBlocking {
        val lowPower = mapOf(
            "/sys/class/power_supply/usb/type" to "USB_PD",
            "/sys/class/power_supply/usb/current_max" to "50000",
            "/sys/class/power_supply/usb/voltage_max" to "5000000"
        )
        val info = SysfsChargingReader.read({ lowPower[it] }, ports = listOf("usb"))
        // 50000µA x 5000000µV = 0.25 W -> rounds via %.1f to "0.3 W"; never "0. W".
        assertEquals("0.3 W", info.negotiatedPower)
    }
}
