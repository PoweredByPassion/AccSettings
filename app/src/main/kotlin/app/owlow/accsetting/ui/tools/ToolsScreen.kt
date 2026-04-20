package app.owlow.accsetting.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.owlow.accsetting.R

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
                    Text(text = stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismissConfirmation) {
                    Text(text = stringResource(R.string.cancel))
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
                        text = stringResource(R.string.tools),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.tools_intro),
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
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun confirmationTitle(action: ToolAction): String = when (action) {
    ToolAction.INSTALL_OR_UPDATE -> stringResource(R.string.tools_confirm_install_title)
    ToolAction.REPAIR -> stringResource(R.string.tools_confirm_repair_title)
    ToolAction.RESTART_SERVICE -> stringResource(R.string.tools_confirm_restart_title)
    ToolAction.FORCE_REDETECT -> stringResource(R.string.tools_confirm_redetect_title)
    ToolAction.REFRESH -> stringResource(R.string.tools_confirm_refresh_title)
}

@Composable
private fun confirmationMessage(action: ToolAction): String = when (action) {
    ToolAction.INSTALL_OR_UPDATE -> stringResource(R.string.tools_confirm_install_message)
    ToolAction.REPAIR -> stringResource(R.string.tools_confirm_repair_message)
    ToolAction.RESTART_SERVICE -> stringResource(R.string.tools_confirm_restart_message)
    ToolAction.FORCE_REDETECT -> stringResource(R.string.tools_confirm_redetect_message)
    ToolAction.REFRESH -> stringResource(R.string.tools_confirm_refresh_message)
}
