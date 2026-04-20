package crazyboyfeng.accSettings.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.ui.config.components.ConfigGroupSection
import crazyboyfeng.accSettings.ui.config.components.DraftActionBar
import crazyboyfeng.accSettings.ui.config.components.LeaveWithDraftDialog

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onChargingSwitchChanged: (String) -> Unit = {},
    onDiscard: () -> Unit = {},
    onApply: () -> Unit = {},
    showLeaveDialog: Boolean = false,
    onKeepDraftAndLeave: () -> Unit = {},
    onDiscardAndLeave: () -> Unit = {},
    onDismissLeaveDialog: () -> Unit = {}
) {
    if (showLeaveDialog) {
        LeaveWithDraftDialog(
            onKeepDraftAndLeave = onKeepDraftAndLeave,
            onDiscardAndLeave = onDiscardAndLeave,
            onDismiss = onDismissLeaveDialog
        )
    }

    Scaffold(
        bottomBar = {
            if (state.hasPendingChanges) {
                DraftActionBar(
                    isApplying = state.isApplying,
                    onDiscard = onDiscard,
                    onApply = onApply
                )
            }
        }
    ) { innerPadding ->
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.configuration_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.configuration_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.applyError?.let { error ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            items(state.groups) { group ->
                ConfigGroupSection(
                    group = group,
                    onChargingSwitchChanged = onChargingSwitchChanged
                )
            }
        }
    }
}
