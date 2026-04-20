package app.owlow.accsetting.ui.config.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.owlow.accsetting.R

@Composable
fun DraftActionBar(
    isApplying: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.draft_changes_ready),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDiscard,
                    enabled = !isApplying
                ) {
                    Text(text = stringResource(R.string.discard))
                }
                Button(
                    onClick = onApply,
                    enabled = !isApplying
                ) {
                    Text(
                        text = if (isApplying) {
                            stringResource(R.string.initializing)
                        } else {
                            stringResource(R.string.apply_changes)
                        }
                    )
                }
            }
        }
    }
}
