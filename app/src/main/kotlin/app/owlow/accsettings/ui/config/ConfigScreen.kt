package app.owlow.accsettings.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.owlow.accsettings.R
import app.owlow.accsettings.ui.config.components.ConfigGroupSection
import app.owlow.accsettings.ui.config.components.DraftActionBar
import app.owlow.accsettings.ui.config.components.LeaveWithDraftDialog
import app.owlow.accsettings.ui.theme.*

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onFieldChanged: (String, String) -> Unit = { _, _ -> },
    onDiscard: () -> Unit = {},
    onApply: () -> Unit = {},
    showLeaveDialog: Boolean = false,
    onKeepDraftAndLeave: () -> Unit = {},
    onDiscardAndLeave: () -> Unit = {},
    onDismissLeaveDialog: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    if (showLeaveDialog) {
        LeaveWithDraftDialog(
            onKeepDraftAndLeave = onKeepDraftAndLeave,
            onDiscardAndLeave = onDiscardAndLeave,
            onDismiss = onDismissLeaveDialog
        )
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
            ) {
                state.applyFeedback?.let { feedback ->
                    Text(
                        text = feedback.message,
                        style = AccTypography.bodyMedium,
                        color = if (feedback.isError) colors.error else colors.tertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                if (state.hasPendingChanges) {
                    DraftActionBar(
                        isApplying = state.isApplying,
                        onDiscard = onDiscard,
                        onApply = onApply
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = innerPadding.calculateTopPadding() + 40.dp,
                end = 24.dp,
                bottom = innerPadding.calculateBottomPadding() + 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.configuration_title),
                        style = AccTypography.headlineMedium,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.configuration_intro),
                        style = AccTypography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
            items(state.groups) { group ->
                ConfigGroupSection(
                    group = group,
                    onFieldChanged = onFieldChanged
                )
            }
        }
    }
}
