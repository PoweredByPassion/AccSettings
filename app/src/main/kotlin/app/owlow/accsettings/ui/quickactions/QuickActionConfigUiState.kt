package app.owlow.accsettings.ui.quickactions

import app.owlow.accsettings.quickaction.QuickActionSlotType

/** UI state for the quick-action configuration screen. */
data class QuickActionConfigUiState(
    val slots: List<QuickActionSlotUiState> = emptyList(),
    val showBatteryRow: Boolean = true,
    /** Index of the slot whose param picker is open, or null. */
    val editingSlotIndex: Int? = null,
    /** When set, the "add action" type-picker is open. */
    val pickingType: Boolean = false,
    val canAdd: Boolean = true
) {
    val isEmpty: Boolean get() = slots.isEmpty()
}

/** One editable quick-action slot row in the config UI. */
data class QuickActionSlotUiState(
    val index: Int,
    val type: QuickActionSlotType,
    val param: String?,
    val label: String,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)
