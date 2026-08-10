package app.owlow.accsettings.ui.overview

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.owlow.accsettings.R
import app.owlow.accsettings.ui.theme.*

@Composable
fun OverviewScreen(
    uiState: OverviewUiState,
    onAction: (String) -> Unit,
    onToggleAction: (String, Boolean) -> Unit,
    onForceStopAction: (ForceStopAction) -> Unit,
    onForceStopCondition: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            OverviewHeader(
                headline = stringResource(R.string.overview),
                status = uiState.statusHeadline,
                colors = colors,
                modifier = Modifier.padding(top = 40.dp)
            )
        }

        if (uiState.warnings.isNotEmpty()) {
            items(uiState.warnings) { warning ->
                WarningCard(text = warning, colors = colors)
            }
        }

        item {
            FactsGrid(
                facts = uiState.runtimeFacts,
                daemonBusy = uiState.daemonBusy,
                colors = colors,
                onToggleAction = onToggleAction
            )
        }

        item {
            ForceStopChargingCard(
                forceStop = uiState.forceStop,
                daemonBusy = uiState.daemonBusy,
                colors = colors,
                onAction = onForceStopAction
            )
        }

        if (uiState.showForceStopDialog) {
            item {
                ForceStopConditionDialog(
                    colors = colors,
                    onDismiss = { onForceStopAction(ForceStopAction.DISMISS_DIALOG) },
                    onCondition = onForceStopCondition
                )
            }
        }

        if (uiState.chargingFacts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.battery_info_title),
                        style = AccTypography.titleLarge,
                        color = colors.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    FactsGrid(
                        facts = uiState.chargingFacts,
                        colors = colors,
                        onToggleAction = onToggleAction
                    )
                }
            }
        }

        item {
            ActionsSection(
                actions = uiState.primaryActions,
                actionsEnabled = !uiState.daemonBusy,
                colors = colors,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun OverviewHeader(
    headline: String,
    status: String,
    colors: ColorScheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = headline,
            style = AccTypography.headlineMedium,
            color = colors.onSurface
        )
        Text(
            text = status,
            style = AccTypography.bodyLarge,
            color = colors.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun FactsGrid(
    facts: List<OverviewFact>,
    daemonBusy: Boolean = false,
    colors: ColorScheme,
    onToggleAction: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(vertical = 12.dp)
    ) {
        facts.forEachIndexed { index, fact ->
            FactRow(
                fact = fact,
                daemonBusy = daemonBusy,
                isLast = index == facts.size - 1,
                colors = colors,
                onToggleAction = onToggleAction
            )
        }
    }
}

@Composable
private fun FactRow(
    fact: OverviewFact,
    daemonBusy: Boolean,
    isLast: Boolean,
    colors: ColorScheme,
    onToggleAction: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = fact.label,
            style = AccTypography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = fact.value,
                style = AccTypography.bodyLarge.copy(
                    fontFamily = MonospaceNumbers.fontFamily,
                    letterSpacing = MonospaceNumbers.letterSpacing
                ),
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (fact.actionId != null && fact.actionValue != null) {
                if (daemonBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary
                    )
                } else {
                    Switch(
                        checked = fact.actionValue,
                        onCheckedChange = { onToggleAction(fact.actionId, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.onSurfaceVariant,
                            uncheckedTrackColor = colors.surfaceVariant,
                            uncheckedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = colors.outlineVariant,
            thickness = 1.dp
        )
    }
}

/**
 * Force-stop-charging card. When inactive it shows a switch that opens the condition dialog;
 * when active it shows a status card with the recovery target and a cancel button.
 */
@Composable
private fun ForceStopChargingCard(
    forceStop: ForceStopUiState,
    daemonBusy: Boolean,
    colors: ColorScheme,
    onAction: (ForceStopAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!forceStop.active) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.overview_fact_force_stop),
                        style = AccTypography.labelMedium,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.overview_force_stop_hint),
                        style = AccTypography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                if (daemonBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary
                    )
                } else {
                    Switch(
                        checked = false,
                        onCheckedChange = { onAction(ForceStopAction.REQUEST_ENABLE) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.onSurfaceVariant,
                            uncheckedTrackColor = colors.surfaceVariant,
                            uncheckedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.overview_force_stop_active),
                style = AccTypography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = forceStopRecoveryLabel(forceStop),
                style = AccTypography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Button(
                onClick = { onAction(ForceStopAction.CANCEL) },
                enabled = !daemonBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                )
            ) {
                Text(stringResource(R.string.overview_force_stop_cancel))
            }
        }
    }
}

@Composable
private fun forceStopRecoveryLabel(forceStop: ForceStopUiState): String {
    val target = when (forceStop.condition) {
        "30m" -> R.string.overview_force_stop_recover_30m
        "1h" -> R.string.overview_force_stop_recover_1h
        "2h" -> R.string.overview_force_stop_recover_2h
        "50%" -> R.string.overview_force_stop_recover_50
        "60%" -> R.string.overview_force_stop_recover_60
        "70%" -> R.string.overview_force_stop_recover_70
        else -> R.string.overview_force_stop_recover_manual
    }
    return stringResource(target)
}

/** Dialog asking which recovery condition to use when enabling force-stop charging. */
@Composable
private fun ForceStopConditionDialog(
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onCondition: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.overview_force_stop_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    null to R.string.overview_force_stop_cond_none,
                    "30m" to R.string.overview_force_stop_cond_30m,
                    "1h" to R.string.overview_force_stop_cond_1h,
                    "2h" to R.string.overview_force_stop_cond_2h,
                    "50%" to R.string.overview_force_stop_cond_50,
                    "60%" to R.string.overview_force_stop_cond_60,
                    "70%" to R.string.overview_force_stop_cond_70
                ).forEach { (condition, labelRes) ->
                    TextButton(
                        onClick = { onCondition(condition) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(labelRes),
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

@Composable
private fun WarningCard(text: String, colors: ColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.error.copy(alpha = 0.08f))
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = AccTypography.bodyMedium,
            color = colors.error,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActionsSection(
    actions: List<OverviewAction>,
    actionsEnabled: Boolean,
    colors: ColorScheme,
    onAction: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        actions.forEach { action ->
            AccActionButton(
                label = action.label,
                enabled = actionsEnabled,
                colors = colors,
                onClick = { onAction(action.id) }
            )
        }
    }
}

@Composable
private fun AccActionButton(
    label: String,
    enabled: Boolean,
    colors: ColorScheme,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(if (enabled) scale else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            style = AccTypography.titleMedium,
            color = colors.onPrimary
        )
    }
}
