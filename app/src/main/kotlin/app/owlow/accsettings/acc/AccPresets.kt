package app.owlow.accsettings.acc

import java.util.Properties

data class AccPreset(
    val id: String,
    val title: String,
    val goal: String,
    val editableFields: Set<String>,
    val lockedFields: Set<String>,
    val riskNote: String? = null,
    val featureAppliers: Map<String, (GroupedConfigRead) -> GroupedConfigRead>
)

data class PresetDraftCandidate(
    val preset: AccPreset,
    val draft: GroupedConfigRead,
    val appliedFeatures: Set<String>,
    val skippedFeatures: Set<String>
)

object AccPresets {
    val presets: List<AccPreset> = listOf(
        AccPreset(
            id = "battery_care",
            title = "Battery Care",
            goal = "Favor long-term battery health.",
            editableFields = setOf("capacity"),
            lockedFields = emptySet(),
            riskNote = "May keep the battery below a full charge for daily use.",
            featureAppliers = mapOf(
                "capacity" to { draft ->
                    draft.withCapacity(
                        CapacityConfig(5, 70, 75, 80, false, ConfigGroupMode.NORMAL)
                    )
                }
            )
        ),
        AccPreset(
            id = "desk_docked",
            title = "Desk Docked",
            goal = "Hold charge near a desk-friendly ceiling.",
            editableFields = setOf("capacity"),
            lockedFields = emptySet(),
            riskNote = "Low pause values can reduce unplugged runtime.",
            featureAppliers = mapOf(
                "capacity" to { draft ->
                    draft.withCapacity(
                        CapacityConfig(5, 55, 58, 60, false, ConfigGroupMode.NORMAL)
                    )
                }
            )
        ),
        AccPreset(
            id = "thermal_guard",
            title = "Thermal Guard",
            goal = "React earlier to charging heat.",
            editableFields = setOf("temperature"),
            lockedFields = emptySet(),
            riskNote = "Aggressive thermal limits may slow charging.",
            featureAppliers = mapOf(
                "temperature" to { draft ->
                    draft.withTemperature(
                        TemperatureConfig(40, 43, 41, 47, ConfigGroupMode.NORMAL)
                    )
                }
            )
        ),
        AccPreset(
            id = "always_on_device",
            title = "Always On Device",
            goal = "Prefer steady charging for devices that stay plugged in.",
            editableFields = setOf("temperature", "current_voltage"),
            lockedFields = emptySet(),
            riskNote = "Higher sustained charging current may increase heat.",
            featureAppliers = mapOf(
                "temperature" to { draft ->
                    draft.withTemperature(
                        TemperatureConfig(41, 44, 42, 48, ConfigGroupMode.NORMAL)
                    )
                },
                "current_voltage" to { draft ->
                    draft.withProperty("charging_current", "1500")
                }
            )
        ),
        AccPreset(
            id = "full_charge_priority",
            title = "Full Charge Priority",
            goal = "Reach and keep a full battery more aggressively.",
            editableFields = setOf("capacity", "current_voltage"),
            lockedFields = emptySet(),
            riskNote = "Frequent full charging may increase battery wear.",
            featureAppliers = mapOf(
                "capacity" to { draft ->
                    draft.withCapacity(
                        CapacityConfig(0, 95, 97, 100, false, ConfigGroupMode.NORMAL)
                    )
                },
                "current_voltage" to { draft ->
                    draft.withProperty("charging_current", "2500")
                }
            )
        )
    )

    fun require(id: String): AccPreset = presets.first { it.id == id }

    fun generateDraft(
        id: String,
        current: GroupedConfigRead,
        defaults: GroupedConfigRead,
        capability: AccCapability
    ): PresetDraftCandidate {
        val preset = require(id)
        var draft = current.copyForDraft(defaults)
        val appliedFeatures = linkedSetOf<String>()
        val skippedFeatures = linkedSetOf<String>()

        preset.featureAppliers.forEach { (featureKey, applyFeature) ->
            if (AccFeatureRegistry.isAvailable(featureKey, capability)) {
                draft = applyFeature(draft)
                appliedFeatures += featureKey
            } else {
                skippedFeatures += featureKey
            }
        }

        return PresetDraftCandidate(
            preset = preset,
            draft = draft,
            appliedFeatures = appliedFeatures,
            skippedFeatures = skippedFeatures
        )
    }
}

private fun GroupedConfigRead.copyForDraft(defaults: GroupedConfigRead): GroupedConfigRead = GroupedConfigRead(
    current = current.copyProperties(),
    defaults = defaults.defaults.copyProperties(),
    currentCapacity = currentCapacity,
    defaultCapacity = defaults.defaultCapacity ?: defaults.currentCapacity,
    currentTemperature = currentTemperature,
    defaultTemperature = defaults.defaultTemperature ?: defaults.currentTemperature
)

private fun GroupedConfigRead.withCapacity(capacity: CapacityConfig): GroupedConfigRead {
    val nextCurrent = current.copyProperties().apply {
        setProperty("capacity", capacity.serialize())
    }
    return copy(current = nextCurrent, currentCapacity = capacity)
}

private fun GroupedConfigRead.withTemperature(temperature: TemperatureConfig): GroupedConfigRead {
    val nextCurrent = current.copyProperties().apply {
        setProperty("temperature", temperature.serialize())
    }
    return copy(current = nextCurrent, currentTemperature = temperature)
}

private fun GroupedConfigRead.withProperty(key: String, value: String): GroupedConfigRead {
    val nextCurrent = current.copyProperties().apply {
        setProperty(key, value)
    }
    return copy(current = nextCurrent)
}

private fun Properties.copyProperties(): Properties = Properties().apply {
    putAll(this@copyProperties)
}
