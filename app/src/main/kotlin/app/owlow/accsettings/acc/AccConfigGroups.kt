package app.owlow.accsettings.acc

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
    fun serialize(): String {
        return "($shutdown $cooldown $resume $pause $maskAsFull)"
    }

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

            val mode = classifyCapacityMode(shutdown, cooldown, resume, pause)
            return CapacityConfig(shutdown, cooldown, resume, pause, maskAsFull, mode)
        }

        fun classifyCapacityMode(shutdown: Int, cooldown: Int, resume: Int, pause: Int): ConfigGroupMode {
            val effectiveShutdown = if (shutdown < 1) 0 else shutdown
            val effectiveCooldown = if (cooldown == 101) 0 else cooldown
            val values = listOf(effectiveShutdown, effectiveCooldown, resume, pause)
            return when {
                values.all { it in 0..100 } -> ConfigGroupMode.NORMAL
                values.all { it == 0 || it >= 3000 } -> ConfigGroupMode.VOLTAGE
                else -> ConfigGroupMode.ADVANCED_CUSTOM
            }
        }
    }
}

data class TemperatureConfig(
    val cooldown: Int,
    val pause: Int,
    val resume: Int,
    val shutdown: Int,
    val resumeTempByCooldown: Boolean = false,
    val mode: ConfigGroupMode
) {
    fun serialize(): String {
        val resumeStr = if (resumeTempByCooldown) "${resume}r" else resume.toString()
        return "($cooldown $pause $resumeStr $shutdown)"
    }

    companion object {
        fun parse(raw: String?): TemperatureConfig {
            val tokens = tokenize(raw)
            if (tokens.size != 4) {
                return TemperatureConfig(0, 0, 0, 0, false, ConfigGroupMode.ADVANCED_CUSTOM)
            }

            val rawResume = tokens[2]
            val resumeTempByCooldown = rawResume.endsWith("r")
            val resumeNum = rawResume.removeSuffix("r").toIntOrNull()

            val cooldown = tokens[0].toIntOrNull()
            val pause = tokens[1].toIntOrNull()
            val shutdown = tokens[3].toIntOrNull()

            if (cooldown == null || pause == null || resumeNum == null || shutdown == null) {
                return TemperatureConfig(0, 0, 0, 0, false, ConfigGroupMode.ADVANCED_CUSTOM)
            }

            val values = listOf(cooldown, pause, resumeNum, shutdown)
            val mode = when {
                values.all { it in 0..100 } -> ConfigGroupMode.NORMAL
                values.all { it == 0 || it >= 3000 } -> ConfigGroupMode.VOLTAGE
                else -> ConfigGroupMode.ADVANCED_CUSTOM
            }
            return TemperatureConfig(
                cooldown = cooldown,
                pause = pause,
                resume = resumeNum,
                shutdown = shutdown,
                resumeTempByCooldown = resumeTempByCooldown,
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

    val mode = CapacityConfig.classifyCapacityMode(shutdown, cooldown, resume, pause)
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
    val rawResume = getProperty("resume_temp") ?: return null
    val resumeTempByCooldown = rawResume.endsWith("r")
    val resume = rawResume.removeSuffix("r").toIntOrNull() ?: return null
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
        resumeTempByCooldown = resumeTempByCooldown,
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