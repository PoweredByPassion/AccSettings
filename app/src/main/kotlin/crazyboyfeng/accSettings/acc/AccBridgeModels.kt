package crazyboyfeng.accSettings.acc

import java.util.Properties

data class GroupedConfigRead(
    val current: Properties,
    val defaults: Properties,
    val currentCapacity: CapacityConfig? = null,
    val defaultCapacity: CapacityConfig? = null,
    val currentTemperature: TemperatureConfig? = null,
    val defaultTemperature: TemperatureConfig? = null
)
