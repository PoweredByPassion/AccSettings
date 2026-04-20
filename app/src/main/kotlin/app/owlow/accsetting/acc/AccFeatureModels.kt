package app.owlow.accsetting.acc

enum class FeatureGroup {
    CAPACITY,
    TEMPERATURE,
    COOLDOWN,
    CHARGING_CONTROL,
    RUNTIME_BEHAVIOR,
    CURRENT_VOLTAGE,
    STATS_RESET
}

enum class ExposeLevel {
    PRESET,
    STANDARD,
    ADVANCED,
    HIDDEN
}

fun interface FeatureSerializer {
    fun serialize(value: Any): String
}

data class AccFeature(
    val key: String,
    val group: FeatureGroup,
    val exposeLevel: ExposeLevel,
    val isAvailable: (AccCapability) -> Boolean = { true },
    val serializer: FeatureSerializer? = null
)
