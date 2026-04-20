package app.owlow.accsetting.ui.config

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
import app.owlow.accsetting.R
import app.owlow.accsetting.ui.config.components.ConfigGroupSection
import app.owlow.accsetting.ui.config.components.DraftActionBar
import app.owlow.accsetting.ui.config.components.LeaveWithDraftDialog
import app.owlow.accsetting.ui.theme.*

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
    if (showLeaveDialog) {
        LeaveWithDraftDialog(
            onKeepDraftAndLeave = onKeepDraftAndLeave,
            onDiscardAndLeave = onDiscardAndLeave,
            onDismiss = onDismissLeaveDialog
        )
    }

    Scaffold(
        containerColor = AccBackground,
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
                        color = Zinc950
                    )
                    Text(
                        text = stringResource(R.string.configuration_intro),
                        style = AccTypography.bodyLarge,
                        color = Zinc600,
                        lineHeight = 22.sp
                    )
                    state.applyError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = AccTypography.bodyMedium,
                            color = AccError,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
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
