package app.owlow.accsettings.ui.quickactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.owlow.accsettings.R
import app.owlow.accsettings.quickaction.QuickActionSlotType
import app.owlow.accsettings.ui.theme.*

@Composable
fun QuickActionConfigScreen(
    state: QuickActionConfigUiState,
    onBack: () -> Unit,
    onAddSlot: (QuickActionSlotType) -> Unit,
    onRemoveSlot: (Int) -> Unit,
    onMoveSlotUp: (Int) -> Unit,
    onMoveSlotDown: (Int) -> Unit,
    onSetSlotParam: (Int, String?) -> Unit,
    onToggleBatteryRow: () -> Unit,
    onShowTypePicker: () -> Unit,
    onDismissEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.quick_actions_config_title),
                    style = AccTypography.headlineMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 40.dp)
                )
                Text(
                    text = stringResource(R.string.quick_actions_config_subtitle),
                    style = AccTypography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }

        // Battery status row toggle.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.quick_actions_battery_row),
                        style = AccTypography.bodyLarge,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = state.showBatteryRow,
                    onCheckedChange = { onToggleBatteryRow() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary
                    )
                )
            }
        }

        // Slot list.
        state.slots.forEach { slot ->
            item(key = "slot_${slot.index}") {
                SlotRow(
                    slot = slot,
                    colors = colors,
                    onMoveUp = { onMoveSlotUp(slot.index) },
                    onMoveDown = { onMoveSlotDown(slot.index) },
                    onRemove = { onRemoveSlot(slot.index) },
                    onTap = { /* param edit handled via dialog below */ }
                )
            }
        }

        // Empty state.
        if (state.isEmpty) {
            item {
                Text(
                    text = stringResource(R.string.quick_actions_empty),
                    style = AccTypography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Add action.
        item {
            Button(
                onClick = onShowTypePicker,
                enabled = state.canAdd,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) { Text(stringResource(R.string.quick_actions_add)) }
            if (!state.canAdd) {
                Text(
                    text = stringResource(R.string.quick_actions_max_reached),
                    style = AccTypography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    // Add-action type picker.
    if (state.pickingType) {
        SlotTypePickerDialog(
            colors = colors,
            onDismiss = onDismissEdit,
            onSelect = onAddSlot
        )
    }

    // Param picker for a specific slot.
    state.editingSlotIndex?.let { index ->
        val slot = state.slots.getOrNull(index) ?: return
        SlotParamPickerDialog(
            slotType = slot.type,
            colors = colors,
            onDismiss = onDismissEdit,
            onParam = { param -> onSetSlotParam(index, param) }
        )
    }
}

@Composable
private fun SlotRow(
    slot: QuickActionSlotUiState,
    colors: ColorScheme,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = slot.label,
            style = AccTypography.bodyLarge,
            color = colors.onSurface,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconTextButton("↑", enabled = slot.canMoveUp, onClick = onMoveUp, colors = colors)
            IconTextButton("↓", enabled = slot.canMoveDown, onClick = onMoveDown, colors = colors)
            IconTextButton("✕", enabled = true, onClick = onRemove, colors = colors, destructive = true)
        }
    }
}

@Composable
private fun IconTextButton(
    glyph: String,
    enabled: Boolean,
    onClick: () -> Unit,
    colors: ColorScheme,
    destructive: Boolean = false
) {
    Text(
        text = glyph,
        style = AccTypography.titleMedium,
        color = if (destructive) colors.error else if (enabled) colors.primary else colors.outline,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun SlotTypePickerDialog(
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onSelect: (QuickActionSlotType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.quick_actions_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SlotTypeButton(stringResource(R.string.quick_actions_slot_pause, "…"), QuickActionSlotType.PAUSE, onSelect, colors)
                SlotTypeButton(stringResource(R.string.quick_actions_slot_charge_to, "…"), QuickActionSlotType.CHARGE_TO, onSelect, colors)
                SlotTypeButton(stringResource(R.string.quick_actions_slot_force_full, "…"), QuickActionSlotType.FORCE_FULL, onSelect, colors)
                SlotTypeButton(stringResource(R.string.quick_actions_slot_cancel), QuickActionSlotType.CANCEL, onSelect, colors)
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun SlotTypeButton(
    label: String,
    type: QuickActionSlotType,
    onSelect: (QuickActionSlotType) -> Unit,
    colors: ColorScheme
) {
    TextButton(onClick = { onSelect(type) }, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.fillMaxWidth(), color = colors.onSurface)
    }
}

/** Options offered when picking a parameter for a slot type. Null param = unconditional/default. */
private fun paramOptions(type: QuickActionSlotType): List<String?> = when (type) {
    QuickActionSlotType.PAUSE -> listOf(null, "30m", "1h", "2h", "50%", "60%", "70%")
    QuickActionSlotType.CHARGE_TO -> listOf(null, "75%", "80%", "85%", "90%", "95%", "30m", "1h")
    QuickActionSlotType.FORCE_FULL -> listOf("100", "95", "90", "85", "80")
    QuickActionSlotType.CANCEL -> emptyList()
}

/** Human label for a param option in the picker. */
private fun paramLabel(type: QuickActionSlotType, param: String?): String = when (type) {
    QuickActionSlotType.PAUSE -> when (param) {
        null -> "…"
        "30m" -> "30 minutes"
        "1h" -> "1 hour"
        "2h" -> "2 hours"
        "50%" -> "below 50%"
        "60%" -> "below 60%"
        "70%" -> "below 70%"
        else -> param
    }
    QuickActionSlotType.CHARGE_TO -> when (param) {
        null -> "now"
        "30m" -> "for 30 minutes"
        "1h" -> "for 1 hour"
        else -> "at ${param?.removeSuffix("%")}%"
    }
    QuickActionSlotType.FORCE_FULL -> param?.let { "to ${it}%" } ?: "to 100%"
    QuickActionSlotType.CANCEL -> ""
}

@Composable
private fun SlotParamPickerDialog(
    slotType: QuickActionSlotType,
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onParam: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.quick_actions_param_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                paramOptions(slotType).forEach { param ->
                    TextButton(
                        onClick = { onParam(param) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = paramLabel(slotType, param),
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
