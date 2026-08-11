package app.owlow.accsettings.acc

import java.util.Locale

/**
 * Reads charging handshake fields from sysfs nodes under `/sys/class/power_supply/`.
 *
 * The active USB supply's node name is device-specific, so [read] tries each candidate
 * port in order and uses the first one whose `type` node exists.
 */
object SysfsChargingReader {
    private const val PS_BASE = "/sys/class/power_supply"
    private val DEFAULT_PORTS = listOf("usb", "main")

    suspend fun read(readNode: suspend (String) -> String?, ports: List<String> = DEFAULT_PORTS): ChargingInfo {
        suspend fun node(port: String, name: String): String? =
            "$PS_BASE/$port/$name".let { readNode(it)?.trim()?.takeIf { v -> v.isNotBlank() } }

        var result = ChargingInfo()
        for (port in ports) {
            val type = node(port, "type")
            if (type != null) {
                val currentMax = node(port, "current_max")?.toLongOrNull()   // µA
                val voltageMax = node(port, "voltage_max")?.toLongOrNull()   // µV
                val pdActive = node(port, "pd_active")?.let { it == "1" }
                result = ChargingInfo(
                    protocol = type,
                    realProtocol = node(port, "real_type"),
                    pdActive = pdActive,
                    // Display-ready strings: µA -> mA, µV -> mV, power in W.
                    negotiatedCurrent = currentMax?.let { (it / 1000).toString() },
                    negotiatedVoltage = voltageMax?.let { (it / 1000).toString() },
                    negotiatedPower = computePower(currentMax, voltageMax),
                    ccMode = node(port, "adapter_cc_mode")
                )
                break
            }
        }
        return result
    }

    /** µA × µV = 1e-12 W, e.g. 500000 × 5000000 = 2.5 W. */
    private fun computePower(currentMax: Long?, voltageMax: Long?): String? {
        if (currentMax == null || voltageMax == null) return null
        val watts = (currentMax.toDouble() * voltageMax.toDouble()) / 1_000_000_000_000.0
        // Format WITHOUT the unit, trim, then append " W" exactly once.
        // This guarantees "0.0 W" never becomes "0. W" and no double unit appears.
        return String.format(Locale.US, "%.1f", watts)
            .trimEnd('0')
            .trimEnd('.')
            .plus(" W")
    }
}
