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

        if (uiState.batteryFacts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.battery_info_title),
                        style = AccTypography.titleLarge,
                        color = colors.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    FactsGrid(
                        facts = uiState.batteryFacts,
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
