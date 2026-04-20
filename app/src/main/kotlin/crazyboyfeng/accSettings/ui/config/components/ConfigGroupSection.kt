package crazyboyfeng.accSettings.ui.config.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import crazyboyfeng.accSettings.ui.config.ConfigFieldUiModel
import crazyboyfeng.accSettings.ui.config.ConfigGroupUiModel

@Composable
fun ConfigGroupSection(
    group: ConfigGroupUiModel,
    onChargingSwitchChanged: (String) -> Unit
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
                text = group.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = group.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            group.fields.forEach { field ->
                ConfigFieldRow(
                    field = field,
                    onChargingSwitchChanged = onChargingSwitchChanged
                )
            }
        }
    }
}

@Composable
private fun ConfigFieldRow(
    field: ConfigFieldUiModel,
    onChargingSwitchChanged: (String) -> Unit
) {
    if (field.key == "set_charging_switch") {
        OutlinedTextField(
            value = field.value,
            onValueChange = onChargingSwitchChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = field.label) },
            supportingText = field.helperText?.let { helper ->
                { Text(text = helper) }
            }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = field.value.ifBlank { "Not set" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
