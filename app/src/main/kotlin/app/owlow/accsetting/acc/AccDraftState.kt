package app.owlow.accsetting.acc

import java.util.Properties

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
        val nextCurrent = Properties()
        nextCurrent.putAll(draft.current)
        nextCurrent.remove("capacity")
        nextCurrent.setProperty("shutdown_capacity", capacityConfig.shutdown.toString())
        nextCurrent.setProperty("cooldown_capacity", capacityConfig.cooldown.toString())
        nextCurrent.setProperty("resume_capacity", capacityConfig.resume.toString())
        nextCurrent.setProperty("pause_capacity", capacityConfig.pause.toString())
        nextCurrent.setProperty("capacity_mask", capacityConfig.maskAsFull.toString())

        val nextDraft = draft.copy(
            currentCapacity = capacityConfig,
            current = nextCurrent
        )
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

    fun updateTemperature(temperatureConfig: TemperatureConfig): AccDraftState {
        val nextCurrent = Properties()
        nextCurrent.putAll(draft.current)
        nextCurrent.remove("temperature")
        nextCurrent.setProperty("cooldown_temp", temperatureConfig.cooldown.toString())
        nextCurrent.setProperty("resume_temp", temperatureConfig.resume.toString())
        nextCurrent.setProperty("max_temp", temperatureConfig.pause.toString())
        nextCurrent.setProperty("shutdown_temp", temperatureConfig.shutdown.toString())

        val nextDraft = draft.copy(
            currentTemperature = temperatureConfig,
            current = nextCurrent
        )
        val advancedGroup = temperatureConfig.mode == ConfigGroupMode.ADVANCED_CUSTOM
        return copy(
            draft = nextDraft,
            status = if (advancedGroup) DraftStatus.ADVANCED_MODIFIED else DraftStatus.MODIFIED,
            protectedAdvancedGroups = if (advancedGroup) {
                protectedAdvancedGroups + PatchGroup.TEMPERATURE
            } else {
                protectedAdvancedGroups - PatchGroup.TEMPERATURE
            }
        )
    }

    fun canOverwrite(group: PatchGroup, allowProtectedGroupRebuild: Boolean): Boolean {
        return allowProtectedGroupRebuild || group !in protectedAdvancedGroups
    }

    fun isStaleAgainst(latestCurrent: GroupedConfigRead): Boolean = !latestCurrent.isSameAs(baseCurrent)

    companion object {
        fun from(current: GroupedConfigRead, defaults: GroupedConfigRead): AccDraftState = AccDraftState(
            defaults = defaults.resolveGroups(),
            current = current.resolveGroups(),
            draft = current.resolveGroups().copy(),
            status = DraftStatus.CLEAN,
            baseCurrent = current.resolveGroups().copy()
        )
    }
}
