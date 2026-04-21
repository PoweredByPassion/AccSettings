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
    modifier: Modifier = Modifier
) {
    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AccPrimary, strokeWidth = 2.dp)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AccBackground)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            OverviewHeader(
                headline = stringResource(R.string.overview),
                status = uiState.statusHeadline,
                modifier = Modifier.padding(top = 40.dp)
            )
        }

        if (uiState.warnings.isNotEmpty()) {
            items(uiState.warnings) { warning ->
                WarningCard(text = warning)
            }
        }

        item {
            FactsGrid(facts = uiState.runtimeFacts)
        }

        if (uiState.batteryFacts.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.battery_info_title),
                        style = AccTypography.titleLarge,
                        color = Zinc900,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    FactsGrid(facts = uiState.batteryFacts)
                }
            }
        }

        item {
            ActionsSection(
                actions = uiState.primaryActions,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun OverviewHeader(
    headline: String,
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = headline,
            style = AccTypography.headlineMedium,
            color = Zinc950
        )
        Text(
            text = status,
            style = AccTypography.bodyLarge,
            color = Zinc600,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun FactsGrid(
    facts: List<OverviewFact>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(vertical = 12.dp)
    ) {
        facts.forEachIndexed { index, fact ->
            FactRow(
                fact = fact,
                isLast = index == facts.size - 1
            )
        }
    }
}

@Composable
private fun FactRow(
    fact: OverviewFact,
    isLast: Boolean
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
            color = Zinc500,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = fact.value,
            style = AccTypography.bodyLarge.copy(
                fontFamily = MonospaceNumbers.fontFamily,
                letterSpacing = MonospaceNumbers.letterSpacing
            ),
            color = Zinc900,
            fontWeight = FontWeight.SemiBold
        )
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = AccDivider,
            thickness = 1.dp
        )
    }
}

@Composable
private fun WarningCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AccError.copy(alpha = 0.08f))
            .padding(16.dp)
    ) {
        Text(
            text = text,
            style = AccTypography.bodyMedium,
            color = AccError,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ActionsSection(
    actions: List<OverviewAction>,
    onAction: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        actions.forEach { action ->
            AccActionButton(
                label = action.label,
                onClick = { onAction(action.id) }
            )
        }
    }
}

@Composable
private fun AccActionButton(
    label: String,
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
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccPrimary,
            contentColor = AccOnPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Text(
            text = label,
            style = AccTypography.titleMedium,
            color = AccOnPrimary
        )
    }
}
