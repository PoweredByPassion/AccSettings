package app.owlow.accsettings.acc

import kotlin.math.roundToInt

/**
 * Parses `acc --info` output (space-separated `key value` lines) into a [ChargingInfo].
 *
 * Base fields are normalized to the Android system-API numeric encoding so the Overview
 * formatters are identical for both data sources:
 *  - level: percent (e.g. "34%" -> "34")
 *  - temp:  tenths of degree Celsius (e.g. "34℃" -> "340")
 *  - current: microamperes (e.g. "0.02A" -> "20000", "-0.27A" -> "-270000")
 *  - voltage: millivolts (e.g. "3.81V" -> "3810")
 *  - power: microwatts (e.g. "0.08W" -> "80000", "-1.02W" -> "-1020000")
 */
object ChargingInfoParser {
    fun parseAccInfo(raw: String): ChargingInfo {
        var level: String? = null
        var status: String? = null
        var temp: String? = null
        var current: String? = null
        var voltage: String? = null
        var power: String? = null
        var chargeType: String? = null
        var powerConnected: Boolean? = null
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@forEach
            val idx = trimmed.indexOf(' ')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx)
            val value = trimmed.substring(idx + 1).trim()
            when (key) {
                "level" -> level = value.removeSuffix("%")
                "status" -> status = value
                "temp" -> temp = value.removeSuffix("℃").toDoubleOrNull()?.let { (it * 10).roundToInt().toString() }
                "current_now" -> current = normalizeAmps(value)
                "voltage_now" -> voltage = normalizeVolts(value)
                "power_now" -> power = normalizeWatts(value)
                "charge_type" -> chargeType = value
                "dc/online", "pc_port/online", "usb/online" ->
                    if (value == "1") powerConnected = true
            }
        }
        return ChargingInfo(
            level = level, status = status, temp = temp,
            current = current, voltage = voltage, power = power,
            chargeType = chargeType,
            powerConnected = powerConnected
        )
    }

    /** Merges acc-sourced base fields with sysfs handshake fields; null when base is absent. */
    fun mergeChargingInfo(base: ChargingInfo?, handshake: ChargingInfo): ChargingInfo? =
        base?.copy(
            protocol = handshake.protocol,
            realProtocol = handshake.realProtocol,
            pdActive = handshake.pdActive,
            negotiatedCurrent = handshake.negotiatedCurrent,
            negotiatedVoltage = handshake.negotiatedVoltage,
            negotiatedPower = handshake.negotiatedPower,
            ccMode = handshake.ccMode
        )

    /** "0.02A" -> "20000", "-0.27A" -> "-270000" (A -> µA). */
    private fun normalizeAmps(value: String): String? {
        val num = value.removeSuffix("A").removeSuffix("a").toDoubleOrNull() ?: return null
        return (num * 1_000_000).roundToInt().toString()
    }

    /** "3.81V" -> "3810" (V -> mV). */
    private fun normalizeVolts(value: String): String? {
        val num = value.removeSuffix("V").removeSuffix("v").toDoubleOrNull() ?: return null
        return (num * 1000).roundToInt().toString()
    }

    /** "0.08W" -> "80000", "-1.02W" -> "-1020000" (W -> µW). */
    private fun normalizeWatts(value: String): String? {
        val num = value.removeSuffix("W").removeSuffix("w").toDoubleOrNull() ?: return null
        return (num * 1_000_000).roundToInt().toString()
    }
}
