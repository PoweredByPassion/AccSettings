package app.owlow.accsetting.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(
    state: ToolsUiState,
    onAction: (ToolAction) -> Unit,
    onConfirmAction: () -> Unit,
    onDismissConfirmation: () -> Unit,
    modifier: Modifier = Modifier
) {
    state.pendingConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text(text = confirmationTitle(action)) },
            text = { Text(text = confirmationMessage(action)) },
            confirmButton = {
                Button(onClick = onConfirmAction) {
                    Text(text = "Continue")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissConfirmation) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    Scaffold(modifier = modifier) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = innerPadding.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = innerPadding.calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tools",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Run maintenance actions, service controls, and diagnostics from one place.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.lastMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item { ToolSectionCard(section = state.installSection, isBusy = state.isBusy, onAction = onAction) }
            item { ToolSectionCard(section = state.serviceSection, isBusy = state.isBusy, onAction = onAction) }
            item { ToolSectionCard(section = state.diagnosticsSection, isBusy = state.isBusy, onAction = onAction) }
            item { ToolLogCard(section = state.logsSection) }
            item { ToolSectionCard(section = state.appInfoSection, isBusy = state.isBusy, onAction = onAction) }
        }
    }
}

@Composable
private fun ToolSectionCard(
    section: ToolSection,
    isBusy: Boolean,
    onAction: (ToolAction) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge
            )
            if (section.summary.isNotBlank()) {
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            section.details.forEach { detail ->
                Text(text = "${detail.label}: ${detail.value}")
            }
            section.actions.forEach { action ->
                Button(
                    onClick = { onAction(action.action) },
                    enabled = action.enabled && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = action.label)
                }
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolLogCard(section: ToolLogSection) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = section.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = section.content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

private fun confirmationTitle(action: ToolAction): String = when (action) {
    ToolAction.INSTALL_OR_UPDATE -> "Apply bundled ACC package?"
    ToolAction.REPAIR -> "Repair ACC installation?"
    ToolAction.RESTART_SERVICE -> "Restart ACC service?"
    ToolAction.FORCE_REDETECT -> "Refresh ACC state?"
    ToolAction.REFRESH -> "Refresh tools details?"
}

private fun confirmationMessage(action: ToolAction): String = when (action) {
    ToolAction.INSTALL_OR_UPDATE -> "This can change the ACC install on the device."
    ToolAction.REPAIR -> "This can restart ACC internals and interrupt current runtime state."
    ToolAction.RESTART_SERVICE -> "This can interrupt the current ACC daemon before it comes back."
    ToolAction.FORCE_REDETECT -> "This will ask ACC to reinitialize its runtime state."
    ToolAction.REFRESH -> "This reloads the latest tools state."
}
