package app.owlow.accsetting.ui.config.components

import android.widget.NumberPicker
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.owlow.accsetting.R
import app.owlow.accsetting.ui.config.ConfigFieldKind
import app.owlow.accsetting.ui.config.ConfigFieldUiModel
import app.owlow.accsetting.ui.config.ConfigGroupUiModel
import app.owlow.accsetting.ui.theme.*

@Composable
fun ConfigGroupSection(
    group: ConfigGroupUiModel,
    onFieldChanged: (String, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(group.titleRes),
                style = AccTypography.titleLarge,
                color = Zinc950
            )
            Text(
                text = stringResource(group.summaryRes),
                style = AccTypography.bodyMedium,
                color = Zinc500,
                lineHeight = 18.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, AccDivider, RoundedCornerShape(24.dp))
                .padding(vertical = 8.dp)
        ) {
            group.fields.forEachIndexed { index, field ->
                ConfigFieldRow(
                    field = field,
                    onFieldChanged = onFieldChanged,
                    isLast = index == group.fields.size - 1
                )
            }
        }
    }
}

@Composable
private fun ConfigFieldRow(
    field: ConfigFieldUiModel,
    onFieldChanged: (String, String) -> Unit,
    isLast: Boolean
) {
    val context = LocalContext.current
    val supportingLines = buildList {
        field.helperTextRes?.let { add(stringResource(it)) }
        field.unitRes?.let { add(stringResource(R.string.config_unit_format, stringResource(it))) }
        if (field.minValue != null && field.maxValue != null) {
            add(stringResource(R.string.config_range_format, field.minValue, field.maxValue))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(field.labelRes),
                    style = AccTypography.bodyLarge,
                    color = Zinc900,
                    fontWeight = FontWeight.Medium
                )
                if (supportingLines.isNotEmpty()) {
                    Text(
                        text = supportingLines.first(), // Show first line as primary hint
                        style = AccTypography.labelMedium,
                        color = Zinc500,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            FieldControl(
                field = field,
                onFieldChanged = onFieldChanged
            )
        }
    }

    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = AccDivider,
            thickness = 1.dp
        )
    }
}

@Composable
private fun FieldControl(
    field: ConfigFieldUiModel,
    onFieldChanged: (String, String) -> Unit
) {
    val context = LocalContext.current
    
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
                    shape = RoundedCornerShape(28.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            text = stringResource(field.labelRes),
                            style = AccTypography.titleLarge
                        )
                    },
                    text = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
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
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onFieldChanged(field.key, pickerState.options[selectedIndex].toString())
                                showPicker = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPicker = false }) {
                            Text(text = stringResource(android.R.string.cancel), color = Zinc600)
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Zinc100)
                    .clickable(enabled = field.enabled) { showPicker = true }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = field.value,
                    style = AccTypography.bodyLarge.copy(
                        fontFamily = MonospaceNumbers.fontFamily,
                        letterSpacing = MonospaceNumbers.letterSpacing
                    ),
                    color = Zinc950,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ConfigFieldKind.TOGGLE -> {
            Switch(
                checked = field.value.equals("true", ignoreCase = true),
                onCheckedChange = { checked -> onFieldChanged(field.key, checked.toString()) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccAccent,
                    uncheckedThumbColor = Zinc400,
                    uncheckedTrackColor = Zinc200,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }

        ConfigFieldKind.NUMBER,
        ConfigFieldKind.TEXT -> {
            OutlinedTextField(
                value = field.value,
                onValueChange = { onFieldChanged(field.key, it) },
                enabled = field.enabled,
                modifier = Modifier.width(120.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = AccTypography.bodyLarge.copy(
                    fontFamily = if (field.kind == ConfigFieldKind.NUMBER) MonospaceNumbers.fontFamily else null
                ),
                keyboardOptions = if (field.kind == ConfigFieldKind.NUMBER) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Zinc100,
                    focusedContainerColor = Zinc100,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = AccPrimary
                )
            )
        }
    }
}
