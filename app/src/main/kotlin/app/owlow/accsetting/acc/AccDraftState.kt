package app.owlow.accsetting.acc

enum class DraftStatus {
    CLEAN,
    MODIFIED,
    ADVANCED_MODIFIED,
    APPLY_FAILED,
    PARTIALLY_APPLIED
}

data class AccDraftState(
    val defaults: GroupedConfigRead,
    val current: GroupedConfigRead,
    val draft: GroupedConfigRead,
    val status: DraftStatus,
    val baseCurrent: GroupedConfigRead,
    val protectedAdvancedGroups: Set<PatchGroup> = emptySet()
) {
    fun updateCapacity(capacityConfig: CapacityConfig): AccDraftState {
        val nextDraft = draft.copy(currentCapacity = capacityConfig)
        val advancedGroup = capacityConfig.mode == ConfigGroupMode.MIXED_LEGACY ||
            capacityConfig.mode == ConfigGroupMode.ADVANCED_CUSTOM
        return copy(
            draft = nextDraft,
            status = if (advancedGroup) DraftStatus.ADVANCED_MODIFIED else DraftStatus.MODIFIED,
            protectedAdvancedGroups = if (advancedGroup) {
                protectedAdvancedGroups + PatchGroup.CAPACITY
            } else {
                protectedAdvancedGroups - PatchGroup.CAPACITY
            }
        )
    }

    fun canOverwrite(group: PatchGroup, allowProtectedGroupRebuild: Boolean): Boolean {
        return allowProtectedGroupRebuild || group !in protectedAdvancedGroups
    }

    fun isStaleAgainst(latestCurrent: GroupedConfigRead): Boolean = latestCurrent != baseCurrent

    companion object {
        fun from(current: GroupedConfigRead, defaults: GroupedConfigRead): AccDraftState = AccDraftState(
            defaults = defaults,
            current = current,
            draft = current.copy(),
            status = DraftStatus.CLEAN,
            baseCurrent = current.copy()
        )
    }
}
