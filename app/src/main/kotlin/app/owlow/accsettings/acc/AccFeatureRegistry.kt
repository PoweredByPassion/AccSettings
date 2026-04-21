package app.owlow.accsettings.acc

object AccFeatureRegistry {
    val features: List<AccFeature> = listOf(
        AccFeature(
            key = "capacity",
            group = FeatureGroup.CAPACITY,
            exposeLevel = ExposeLevel.STANDARD,
            serializer = FeatureSerializer { value -> (value as CapacityConfig).serialize() }
        ),
        AccFeature(
            key = "temperature",
            group = FeatureGroup.TEMPERATURE,
            exposeLevel = ExposeLevel.STANDARD,
            serializer = FeatureSerializer { value -> (value as TemperatureConfig).serialize() }
        ),
        AccFeature(
            key = "cooldown",
            group = FeatureGroup.COOLDOWN,
            exposeLevel = ExposeLevel.STANDARD
        ),
        AccFeature(
            key = "charging_control",
            group = FeatureGroup.CHARGING_CONTROL,
            exposeLevel = ExposeLevel.STANDARD,
            isAvailable = { it.deviceControlCapability.supportedChargingSwitches.isNotEmpty() }
        ),
        AccFeature(
            key = "runtime_behavior",
            group = FeatureGroup.RUNTIME_BEHAVIOR,
            exposeLevel = ExposeLevel.STANDARD
        ),
        AccFeature(
            key = "current_voltage",
            group = FeatureGroup.CURRENT_VOLTAGE,
            exposeLevel = ExposeLevel.STANDARD,
            isAvailable = {
                it.deviceControlCapability.supportsCurrentControl ||
                    it.deviceControlCapability.supportsVoltageControl
            }
        ),
        AccFeature(
            key = "stats_reset",
            group = FeatureGroup.STATS_RESET,
            exposeLevel = ExposeLevel.STANDARD
        ),
        AccFeature(
            key = "raw_patch",
            group = FeatureGroup.RUNTIME_BEHAVIOR,
            exposeLevel = ExposeLevel.ADVANCED
        )
    )

    fun require(key: String): AccFeature = features.first { it.key == key }

    fun isAvailable(key: String, capability: AccCapability): Boolean = require(key).isAvailable(capability)

    fun serializerFor(key: String): FeatureSerializer? = require(key).serializer
}
