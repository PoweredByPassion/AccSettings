package app.owlow.accsettings.acc

import java.util.Properties

data class GroupedConfigRead(
    val current: Properties,
    val defaults: Properties,
    val currentCapacity: CapacityConfig? = null,
    val defaultCapacity: CapacityConfig? = null,
    val currentTemperature: TemperatureConfig? = null,
    val defaultTemperature: TemperatureConfig? = null
) {
    /**
     * Compares two configs for logical equivalence, ignoring storage-format differences inside the
     * Properties objects (e.g. individual fields vs. parenthesized groups).
     */
    fun isSameAs(other: GroupedConfigRead): Boolean {
        if (this === other) return true

        // Core structured data must match
        if (currentCapacity != other.currentCapacity) return false
        if (currentTemperature != other.currentTemperature) return false

        // Remaining misc Properties fields are compared too,
        // excluding the keys already captured by the structured capacity/temperature groups.
        val keysToIgnore = setOf(
            "capacity", "temperature",
            "shutdown_capacity", "cooldown_capacity", "resume_capacity", "pause_capacity", "capacity_mask",
            "cooldown_temp", "resume_temp", "max_temp", "shutdown_temp"
        )
        
        val thisKeys = current.stringPropertyNames().filter { it !in keysToIgnore }
        val otherKeys = other.current.stringPropertyNames().filter { it !in keysToIgnore }
        
        if (thisKeys.size != otherKeys.size) return false
        
        for (key in thisKeys) {
            if (current.getProperty(key) != other.current.getProperty(key)) return false
        }
        
        return true
    }
}

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
