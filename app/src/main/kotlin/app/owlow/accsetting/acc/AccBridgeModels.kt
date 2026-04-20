package app.owlow.accsetting.acc

import java.util.Properties

data class GroupedConfigRead(
    val current: Properties,
    val defaults: Properties,
    val currentCapacity: CapacityConfig? = null,
    val defaultCapacity: CapacityConfig? = null,
    val currentTemperature: TemperatureConfig? = null,
    val defaultTemperature: TemperatureConfig? = null
)

sealed class GroupApplyWriteResult {
    data object Success : GroupApplyWriteResult()
    data class Failure(val message: String) : GroupApplyWriteResult()
}

sealed class ApplyGroupedPatchResult {
    data object StaleBaseConfig : ApplyGroupedPatchResult()
    data class ValidationFailed(val errors: List<String>) : ApplyGroupedPatchResult()
    data class Success(
        val appliedGroups: List<PatchGroup>,
        val verifiedConfig: GroupedConfigRead
    ) : ApplyGroupedPatchResult()
    data class Partial(
        val appliedGroups: List<PatchGroup>,
        val failedGroups: Map<PatchGroup, String>,
        val verifiedConfig: GroupedConfigRead
    ) : ApplyGroupedPatchResult()
    data class VerificationMismatch(
        val appliedGroups: List<PatchGroup>,
        val verifiedConfig: GroupedConfigRead
    ) : ApplyGroupedPatchResult()
}

enum class LifecycleDecision {
    INSTALL,
    UPGRADE,
    REPAIR,
    NO_OP,
    UNINSTALL,
    REINITIALIZE
}

data class LifecycleActionResult(
    val decision: LifecycleDecision,
    val capability: AccCapability
)

data class DaemonActionResult(
    val success: Boolean,
    val daemonRunning: Boolean
)
