package app.owlow.accsetting.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.owlow.accsetting.R
import app.owlow.accsetting.ui.theme.*

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
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = confirmationTitle(action),
                    style = AccTypography.titleLarge
                )
            },
            text = {
                Text(
                    text = confirmationMessage(action),
                    style = AccTypography.bodyLarge,
                    color = Zinc600
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmAction,
                    colors = ButtonDefaults.buttonColors(containerColor = AccPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmation) {
                    Text(text = stringResource(R.string.cancel), color = Zinc600)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = AccBackground
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.tools),
                        style = AccTypography.headlineMedium,
                        color = Zinc950
                    )
                    Text(
                        text = stringResource(R.string.tools_intro),
                        style = AccTypography.bodyLarge,
                        color = Zinc600,
                        lineHeight = 22.sp
                    )
                    state.lastMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = AccTypography.bodyMedium,
                            color = AccAccent,
                            fontWeight = FontWeight.Medium
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = section.title,
            style = AccTypography.titleLarge,
            color = Zinc900,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, AccDivider, RoundedCornerShape(24.dp))
                .padding(vertical = 8.dp)
        ) {
            if (section.summary.isNotBlank()) {
                Text(
                    text = section.summary,
                    style = AccTypography.bodyMedium,
                    color = Zinc500,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            section.details.forEach { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = detail.label,
                        style = AccTypography.labelMedium,
                        color = Zinc500
                    )
                    Text(
                        text = detail.value,
                        style = AccTypography.bodyLarge.copy(
                            fontFamily = MonospaceNumbers.fontFamily,
                            letterSpacing = MonospaceNumbers.letterSpacing
                        ),
                        color = Zinc900,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (section.actions.isNotEmpty()) {
                HorizontalDivider(color = AccDivider, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                
                section.actions.forEach { action ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAction(action.action) },
                            enabled = action.enabled && !isBusy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccPrimary)
                        ) {
                            Text(text = action.label, style = AccTypography.titleSmall)
                        }
                        Text(
                            text = action.description,
                            style = AccTypography.labelMedium,
                            color = Zinc500
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolLogCard(section: ToolLogSection) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = section.title,
            style = AccTypography.titleLarge,
            color = Zinc900,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Zinc950)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = section.summary,
                style = AccTypography.labelMedium,
                color = Zinc400
            )
            Text(
                text = section.content,
                style = AccTypography.bodySmall.copy(
                    fontFamily = MonospaceNumbers.fontFamily,
                    color = Zinc100,
                    lineHeight = 18.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
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
