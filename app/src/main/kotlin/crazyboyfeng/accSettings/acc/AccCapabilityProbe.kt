package crazyboyfeng.accSettings.acc

enum class ProbeRefreshReason {
    INSTALL,
    UPGRADE,
    REPAIR,
    RECHECK,
    APPLY_RECOVERY
}

class AccCapabilityProbe(
    private val factsCollector: suspend () -> AccProbeFacts
) {
    suspend fun snapshot(): AccCapability = AccCapability.from(factsCollector())

    suspend fun refresh(reason: ProbeRefreshReason): AccCapability {
        return when (reason) {
            ProbeRefreshReason.INSTALL,
            ProbeRefreshReason.UPGRADE,
            ProbeRefreshReason.REPAIR,
            ProbeRefreshReason.RECHECK,
            ProbeRefreshReason.APPLY_RECOVERY -> snapshot()
        }
    }
}
