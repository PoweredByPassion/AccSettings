package app.owlow.accsetting.acc

import java.util.Properties

enum class ConfigGroupMode {
    NORMAL,
    VOLTAGE,
    MIXED_LEGACY,
    ADVANCED_CUSTOM
}

data class CapacityConfig(
    val shutdown: Int,
    val cooldown: Int,
    val resume: Int,
    val pause: Int,
    val maskAsFull: Boolean,
    val mode: ConfigGroupMode
) {
    fun serialize(): String = "($shutdown $cooldown $resume $pause $maskAsFull)"

    companion object {
        fun parse(raw: String?): CapacityConfig {
            val tokens = tokenize(raw)
            if (tokens.size != 5) {
                return CapacityConfig(0, 0, 0, 0, false, ConfigGroupMode.ADVANCED_CUSTOM)
            }

            val shutdown = tokens[0].toIntOrNull()
            val cooldown = tokens[1].toIntOrNull()
            val resume = tokens[2].toIntOrNull()
            val pause = tokens[3].toIntOrNull()
            val maskAsFull = tokens[4].toBooleanStrictOrNull()
            if (shutdown == null || cooldown == null || resume == null || pause == null || maskAsFull == null) {
                return CapacityConfig(0, 0, 0, 0, false, ConfigGroupMode.ADVANCED_CUSTOM)
            }

            val values = listOf(shutdown, cooldown, resume, pause)
            val mode = when {
                values.all { it in 0..100 } -> ConfigGroupMode.NORMAL
                values.any { it in 101..2999 } -> ConfigGroupMode.MIXED_LEGACY
                values.all { it == 0 || it >= 3000 } -> ConfigGroupMode.VOLTAGE
                else -> ConfigGroupMode.ADVANCED_CUSTOM
            }
            return CapacityConfig(shutdown, cooldown, resume, pause, maskAsFull, mode)
        }
    }
}

data class TemperatureConfig(
    val cooldown: Int,
    val pause: Int,
    val resume: Int,
    val shutdown: Int,
    val mode: ConfigGroupMode
) {
    fun serialize(): String = "($cooldown $pause $resume $shutdown)"

    companion object {
        fun parse(raw: String?): TemperatureConfig {
            val tokens = tokenize(raw)
            if (tokens.size != 4) {
                return TemperatureConfig(0, 0, 0, 0, ConfigGroupMode.ADVANCED_CUSTOM)
            }

            val values = tokens.map { it.toIntOrNull() }
            if (values.any { it == null }) {
                return TemperatureConfig(0, 0, 0, 0, ConfigGroupMode.ADVANCED_CUSTOM)
            }

            val parsed = values.filterNotNull()
            val mode = when {
                parsed.all { it in 0..100 } -> ConfigGroupMode.NORMAL
                parsed.all { it == 0 || it >= 3000 } -> ConfigGroupMode.VOLTAGE
                else -> ConfigGroupMode.ADVANCED_CUSTOM
            }
            return TemperatureConfig(
                cooldown = parsed[0],
                pause = parsed[1],
                resume = parsed[2],
                shutdown = parsed[3],
                mode = mode
            )
        }
    }
}

internal fun GroupedConfigRead.resolveGroups(): GroupedConfigRead = copy(
    currentCapacity = currentCapacity ?: current.toCapacityConfig(),
    defaultCapacity = defaultCapacity ?: defaults.toCapacityConfig(),
    currentTemperature = currentTemperature ?: current.toTemperatureConfig(),
    defaultTemperature = defaultTemperature ?: defaults.toTemperatureConfig()
)

private fun Properties.toCapacityConfig(): CapacityConfig? {
    getProperty("capacity")?.let { return CapacityConfig.parse(it) }

    val shutdown = getProperty("shutdown_capacity")?.toIntOrNull() ?: return null
    val cooldown = getProperty("cooldown_capacity")?.toIntOrNull() ?: return null
    val resume = getProperty("resume_capacity")?.toIntOrNull() ?: return null
    val pause = getProperty("pause_capacity")?.toIntOrNull() ?: return null
    val maskAsFull = getProperty("capacity_mask")?.toBooleanStrictOrNull() ?: false
    val values = listOf(shutdown, cooldown, resume, pause)
    val mode = when {
        values.all { it in 0..100 } -> ConfigGroupMode.NORMAL
        values.any { it in 101..2999 } -> ConfigGroupMode.MIXED_LEGACY
        values.all { it == 0 || it >= 3000 } -> ConfigGroupMode.VOLTAGE
        else -> ConfigGroupMode.ADVANCED_CUSTOM
    }
    return CapacityConfig(
        shutdown = shutdown,
        cooldown = cooldown,
        resume = resume,
        pause = pause,
        maskAsFull = maskAsFull,
        mode = mode
    )
}

private fun Properties.toTemperatureConfig(): TemperatureConfig? {
    getProperty("temperature")?.let { return TemperatureConfig.parse(it) }

    val cooldown = getProperty("cooldown_temp")?.toIntOrNull() ?: return null
    val pause = getProperty("max_temp")?.toIntOrNull() ?: return null
    val resume = getProperty("resume_temp")?.toIntOrNull() ?: return null
    val shutdown = getProperty("shutdown_temp")?.toIntOrNull() ?: return null
    val values = listOf(cooldown, pause, resume, shutdown)
    val mode = when {
        values.all { it in 0..100 } -> ConfigGroupMode.NORMAL
        values.all { it == 0 || it >= 3000 } -> ConfigGroupMode.VOLTAGE
        else -> ConfigGroupMode.ADVANCED_CUSTOM
    }
    return TemperatureConfig(
        cooldown = cooldown,
        pause = pause,
        resume = resume,
        shutdown = shutdown,
        mode = mode
    )
}

private fun tokenize(raw: String?): List<String> =
    raw
        ?.replace("(", " ")
        ?.replace(")", " ")
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.filter { it.isNotBlank() }
        .orEmpty()
