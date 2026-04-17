package crazyboyfeng.accSettings.acc

import java.util.Properties

class AccBridge(
    private val capabilityProbe: suspend () -> AccCapability,
    private val versionReader: suspend () -> Pair<Int, String?>,
    private val daemonReader: suspend () -> Boolean,
    private val currentConfigReader: suspend () -> Properties,
    private val defaultConfigReader: suspend () -> Properties,
    private val groupedConfigReader: suspend (Properties, Properties) -> GroupedConfigRead = { current, defaults ->
        GroupedConfigRead(current = current, defaults = defaults)
    },
    private val bundledVersionCodeProvider: () -> Int = { 0 }
) {
    suspend fun probeCapabilities(): AccCapability = capabilityProbe()

    suspend fun readStatus(): AccStatus {
        val (installedVersionCode, installedVersionName) = versionReader()
        val daemonRunning = daemonReader()
        return AccStatusResolver.resolve(
            installedVersionCode = installedVersionCode,
            installedVersionName = installedVersionName,
            bundledVersionCode = bundledVersionCodeProvider(),
            daemonRunning = daemonRunning
        )
    }

    suspend fun readCurrentConfig(): Properties = currentConfigReader()

    suspend fun readDefaultConfig(): Properties = defaultConfigReader()

    suspend fun readGroupedConfig(): GroupedConfigRead {
        val current = readCurrentConfig()
        val defaults = readDefaultConfig()
        val base = groupedConfigReader(current, defaults)
        return base.copy(
            currentCapacity = current.getProperty("capacity")?.let(CapacityConfig::parse),
            defaultCapacity = defaults.getProperty("capacity")?.let(CapacityConfig::parse),
            currentTemperature = current.getProperty("temperature")?.let(TemperatureConfig::parse),
            defaultTemperature = defaults.getProperty("temperature")?.let(TemperatureConfig::parse)
        )
    }
}
