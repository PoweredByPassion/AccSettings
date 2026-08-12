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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.owlow.accsettings.R
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.quickaction.ChargeControlLabels
import app.owlow.accsettings.ui.theme.*

@Composable
fun OverviewScreen(
    uiState: OverviewUiState,
    onAction: (String) -> Unit,
    onToggleAction: (String, Boolean) -> Unit,
    onForceStopAction: (ForceStopAction) -> Unit,
    onForceStopCondition: (String?) -> Unit,
    onForceFullCapacity: (Int) -> Unit,
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

        if (uiState.showChargeToDialog) {
            item {
                ChargeToConditionDialog(
                    colors = colors,
                    onDismiss = { onForceStopAction(ForceStopAction.DISMISS_CHARGE_TO_DIALOG) },
                    onCondition = onForceStopCondition
                )
            }
        }

        if (uiState.showForceFullDialog) {
            item {
                ForceFullCapacityDialog(
                    colors = colors,
                    onDismiss = { onForceStopAction(ForceStopAction.DISMISS_FORCE_FULL_DIALOG) },
                    onCapacity = onForceFullCapacity
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
 * Charging control card. When no operation is active it shows the three mutually-exclusive
 * operations:
 *  - Stop charging (`acc -d`, opens the condition dialog)
 *  - Resume charging to a target (`acc -e`, opens the condition dialog)
 *  - Force full charge (`acc -f`, opens the capacity dialog)
 * When one is active it shows ONLY that operation's status plus a cancel button — the other two
 * operations are hidden so the user cannot trigger a conflicting operation.
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
        Text(
            text = stringResource(R.string.overview_charge_control_title),
            style = AccTypography.labelMedium,
            color = colors.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        if (!forceStop.active) {
            ChargeActionButton(
                label = stringResource(R.string.overview_action_stop_charge),
                description = stringResource(R.string.overview_force_stop_hint),
                daemonBusy = daemonBusy,
                colors = colors,
                onClick = { onAction(ForceStopAction.REQUEST_ENABLE) }
            )
            ChargeActionButton(
                label = stringResource(R.string.overview_action_resume_charge),
                description = stringResource(R.string.overview_action_resume_hint),
                daemonBusy = daemonBusy,
                colors = colors,
                onClick = { onAction(ForceStopAction.REQUEST_CHARGE_TO) }
            )
            ChargeActionButton(
                label = stringResource(R.string.overview_action_force_full),
                description = stringResource(R.string.overview_action_force_full_hint),
                daemonBusy = daemonBusy,
                colors = colors,
                onClick = { onAction(ForceStopAction.REQUEST_FORCE_FULL) }
            )
        } else {
            val now = System.currentTimeMillis()
            Text(
                text = chargeControlActiveTitle(forceStop),
                style = AccTypography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = chargeControlActiveDescription(forceStop, now),
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

/** Active-state title for a charging-control operation, keyed by [ChargingControlMode]. */
@Composable
private fun chargeControlActiveTitle(forceStop: ForceStopUiState): String = when (forceStop.mode) {
    ChargingControlMode.STOP -> stringResource(R.string.overview_force_stop_active)
    ChargingControlMode.CHARGE_TO -> stringResource(
        R.string.overview_charge_to_active,
        chargeToConditionLabel(forceStop.condition)
    )
    ChargingControlMode.FORCE_FULL -> stringResource(
        R.string.overview_force_full_active,
        forceStop.condition ?: "100"
    )
}

/** Active-state description: a countdown for duration conditions, target text otherwise. */
@Composable
private fun chargeControlActiveDescription(forceStop: ForceStopUiState, now: Long): String = when (forceStop.mode) {
    ChargingControlMode.STOP -> forceStopRecoveryLabel(forceStop, now)
    ChargingControlMode.CHARGE_TO -> {
        val remainingSeconds = ChargeControlLabels.durationTotalSeconds(forceStop.condition)?.let { total ->
            val startedAt = forceStop.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            stringResource(R.string.overview_charge_to_remaining, ChargeControlLabels.formatRemainingTime(remainingSeconds))
        } else {
            stringResource(R.string.overview_charge_to_target, chargeToConditionLabel(forceStop.condition))
        }
    }
    ChargingControlMode.FORCE_FULL -> stringResource(R.string.overview_force_full_progress)
}

/** Human label for a charge-to (`acc -e`) condition arg. */
@Composable
private fun chargeToConditionLabel(condition: String?): String =
    ChargeControlLabels.chargeToConditionLabel(condition, LocalContext.current)

@Composable
private fun ChargeActionButton(
    label: String,
    description: String,
    daemonBusy: Boolean,
    colors: ColorScheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !daemonBusy, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = AccTypography.bodyLarge,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = AccTypography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        if (daemonBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.primary
            )
        } else {
            Text(
                text = "›",
                style = AccTypography.titleMedium,
                color = colors.primary
            )
        }
    }
}

@Composable
private fun forceStopRecoveryLabel(forceStop: ForceStopUiState, now: Long): String {
    // Duration conditions show a live remaining-time countdown; capacity/manual conditions and
    // an unknown start time fall back to the static recovery description.
    val remainingSeconds = ChargeControlLabels.durationTotalSeconds(forceStop.condition)?.let { total ->
        val startedAt = forceStop.startedAt ?: return@let null
        (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
    }
    if (remainingSeconds != null) {
        return stringResource(
            R.string.overview_force_stop_remaining,
            ChargeControlLabels.formatRemainingTime(remainingSeconds)
        )
    }
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

/** Dialog asking which target to resume charging to (`acc -e`). */
@Composable
private fun ChargeToConditionDialog(
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onCondition: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.overview_charge_to_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    null to R.string.overview_charge_to_cond_now,
                    "75%" to R.string.overview_charge_to_cond_75,
                    "80%" to R.string.overview_charge_to_cond_80,
                    "85%" to R.string.overview_charge_to_cond_85,
                    "90%" to R.string.overview_charge_to_cond_90,
                    "95%" to R.string.overview_charge_to_cond_95,
                    "30m" to R.string.overview_charge_to_cond_30m,
                    "1h" to R.string.overview_charge_to_cond_1h
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

/** Dialog asking which capacity to force-full charge to (`acc -f`). */
@Composable
private fun ForceFullCapacityDialog(
    colors: ColorScheme,
    onDismiss: () -> Unit,
    onCapacity: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.overview_force_full_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(100, 95, 90, 85, 80).forEach { capacity ->
                    TextButton(
                        onClick = { onCapacity(capacity) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.overview_force_full_capacity, capacity),
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
