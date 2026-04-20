package crazyboyfeng.accSettings.acc

enum class PatchGroup {
    CAPACITY,
    TEMPERATURE,
    COOLDOWN,
    CHARGING_CONTROL,
    CURRENT_VOLTAGE,
    RUNTIME_HOOKS,
    STATS_RESET
}

data class ApplyGroupedPatchRequest(
    val base: GroupedConfigRead,
    val target: GroupedConfigRead,
    val groups: Set<PatchGroup>,
    val allowProtectedGroupRebuild: Boolean = false
)
