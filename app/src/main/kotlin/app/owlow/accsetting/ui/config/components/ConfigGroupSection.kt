package app.owlow.accsetting.ui.config.components

import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.owlow.accsetting.R
import app.owlow.accsetting.ui.config.ConfigFieldKind
import app.owlow.accsetting.ui.config.ConfigFieldUiModel
import app.owlow.accsetting.ui.config.ConfigGroupUiModel

@Composable
fun ConfigGroupSection(
    group: ConfigGroupUiModel,
    onFieldChanged: (String, String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(group.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(group.summaryRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            group.fields.forEach { field ->
                ConfigFieldRow(
                    field = field,
                    onFieldChanged = onFieldChanged
                )
            }
        }
    }
}

@Composable
private fun ConfigFieldRow(
    field: ConfigFieldUiModel,
    onFieldChanged: (String, String) -> Unit
) {
    val context = LocalContext.current
    val supportingLines = buildList {
        field.helperTextRes?.let { add(stringResource(it)) }
        field.unitRes?.let { add(stringResource(R.string.config_unit_format, stringResource(it))) }
        if (field.minValue != null && field.maxValue != null) {
            add(stringResource(R.string.config_range_format, field.minValue, field.maxValue))
        }
    }

    when (field.kind) {
        ConfigFieldKind.PICKER -> {
            val pickerState = requireNotNull(field.pickerState)
            var showPicker by remember(field.key) { mutableStateOf(false) }
            var selectedIndex by remember(field.key, field.value, pickerState.options) {
                mutableIntStateOf(pickerState.options.indexOf(field.value.toIntOrNull()).coerceAtLeast(0))
            }

            if (showPicker) {
                AlertDialog(
                    onDismissRequest = { showPicker = false },
                    title = { Text(text = stringResource(field.labelRes)) },
                    text = {
                        AndroidView(
                            factory = {
                                NumberPicker(context).apply {
                                    wrapSelectorWheel = false
                                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                                }
                            },
                            update = { picker ->
                                val displayedValues = pickerState.options.map(Int::toString).toTypedArray()
                                picker.displayedValues = null
                                picker.minValue = 0
                                picker.maxValue = displayedValues.lastIndex
                                picker.displayedValues = displayedValues
                                picker.value = selectedIndex.coerceIn(0, displayedValues.lastIndex)
                                picker.setOnValueChangedListener { _, _, newValue ->
                                    selectedIndex = newValue
                                }
                            }
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            onFieldChanged(field.key, pickerState.options[selectedIndex].toString())
                            showPicker = false
                        }) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPicker = false }) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    }
                )
            }

            OutlinedTextField(
                value = field.value,
                onValueChange = {},
                enabled = field.enabled,
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = field.enabled) {
                        selectedIndex = pickerState.options.indexOf(field.value.toIntOrNull()).coerceAtLeast(0)
                        showPicker = true
                    },
                label = { Text(text = stringResource(field.labelRes)) },
                supportingText = if (supportingLines.isNotEmpty()) {
                    { Text(text = supportingLines.joinToString("\n")) }
                } else {
                    null
                }
            )
        }

        ConfigFieldKind.TOGGLE -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(field.labelRes),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = field.value.equals("true", ignoreCase = true),
                    onCheckedChange = { checked -> onFieldChanged(field.key, checked.toString()) }
                )
                if (supportingLines.isNotEmpty()) {
                    Text(
                        text = supportingLines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        ConfigFieldKind.NUMBER,
        ConfigFieldKind.TEXT -> {
            OutlinedTextField(
                value = field.value,
                onValueChange = { onFieldChanged(field.key, it) },
                enabled = field.enabled,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(field.labelRes)) },
                supportingText = if (supportingLines.isNotEmpty()) {
                    { Text(text = supportingLines.joinToString("\n")) }
                } else {
                    null
                },
                keyboardOptions = if (field.kind == ConfigFieldKind.NUMBER) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                }
            )
        }
    }
}
