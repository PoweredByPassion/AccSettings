package app.owlow.accsettings.acc

enum class InstallAccess {
    AVAILABLE,
    UNAVAILABLE
}

enum class RuntimeAccess {
    AVAILABLE,
    UNAVAILABLE
}

enum class ConfigAccess {
    AVAILABLE,
    UNAVAILABLE
}

enum class LogAccess {
    AVAILABLE,
    UNAVAILABLE
}

enum class ControlAccess {
    AVAILABLE,
    UNAVAILABLE
}

enum class CapacityMode {
    PERCENT,
    ADVANCED_CUSTOM
}

enum class TemperatureMode {
    CELSIUS,
    ADVANCED_CUSTOM
}

data class AccProbeFacts(
    val hasRoot: Boolean,
    val availableEntrypoints: List<String>,
    val selectedEntrypoint: String?,
    val accVersionName: String?,
    val accVersionCode: Int,
    val daemonRunning: Boolean,
    val canReadInfo: Boolean,
    val canReadCurrentConfig: Boolean,
    val canReadDefaultConfig: Boolean,
    val canReadLogs: Boolean,
    val canExportDiagnostics: Boolean,
    val supportedChargingSwitches: List<String>,
    val preferredChargingSwitch: String?,
    val supportsCurrentControl: Boolean,
    val supportsVoltageControl: Boolean,
    val supportedCapacityModes: Set<CapacityMode>,
    val supportedTemperatureModes: Set<TemperatureMode>
)

data class AccCapability(
    val staticAvailability: StaticAvailability,
    val runtimeCapability: RuntimeCapability,
    val deviceControlCapability: DeviceControlCapability
) {
    companion object {
        fun from(facts: AccProbeFacts): AccCapability = AccCapability(
            staticAvailability = StaticAvailability(
                hasRoot = facts.hasRoot,
                availableEntrypoints = facts.availableEntrypoints,
                selectedEntrypoint = facts.selectedEntrypoint,
                accVersionName = facts.accVersionName,
                accVersionCode = facts.accVersionCode,
                canInstallBundledAcc = facts.hasRoot,
                canUpgradeBundledAcc = facts.hasRoot &&
                    facts.selectedEntrypoint != null &&
                    facts.accVersionCode > 0,
                canRepairAcc = facts.hasRoot && facts.availableEntrypoints.isNotEmpty()
            ),
            runtimeCapability = RuntimeCapability(
                daemonRunning = facts.daemonRunning,
                canReadInfo = facts.canReadInfo,
                canReadCurrentConfig = facts.canReadCurrentConfig,
                canReadDefaultConfig = facts.canReadDefaultConfig,
                canReadLogs = facts.canReadLogs,
                canExportDiagnostics = facts.canExportDiagnostics
            ),
            deviceControlCapability = DeviceControlCapability(
                supportedChargingSwitches = facts.supportedChargingSwitches,
                preferredChargingSwitch = facts.preferredChargingSwitch,
                supportsCurrentControl = facts.supportsCurrentControl,
                supportsVoltageControl = facts.supportsVoltageControl,
                supportedCapacityModes = facts.supportedCapacityModes,
                supportedTemperatureModes = facts.supportedTemperatureModes
            )
        )
    }
}

data class StaticAvailability(
    val hasRoot: Boolean,
    val availableEntrypoints: List<String>,
    val selectedEntrypoint: String?,
    val accVersionName: String?,
    val accVersionCode: Int,
    val canInstallBundledAcc: Boolean,
    val canUpgradeBundledAcc: Boolean,
    val canRepairAcc: Boolean
)

data class RuntimeCapability(
    val daemonRunning: Boolean,
    val canReadInfo: Boolean,
    val canReadCurrentConfig: Boolean,
    val canReadDefaultConfig: Boolean,
    val canReadLogs: Boolean,
    val canExportDiagnostics: Boolean
)

data class DeviceControlCapability(
    val supportedChargingSwitches: List<String>,
    val preferredChargingSwitch: String?,
    val supportsCurrentControl: Boolean,
    val supportsVoltageControl: Boolean,
    val supportedCapacityModes: Set<CapacityMode>,
    val supportedTemperatureModes: Set<TemperatureMode>
)
