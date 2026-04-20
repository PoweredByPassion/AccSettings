package app.owlow.accsetting.ui.config.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.owlow.accsetting.R
import app.owlow.accsetting.ui.theme.*

@Composable
fun DraftActionBar(
    isApplying: Boolean,
    onDiscard: () -> Unit,
    onApply: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            color = Color.White.copy(alpha = 0.9f),
            shadowElevation = 12.dp,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Zinc600),
                    enabled = !isApplying
                ) {
                    Text(
                        text = stringResource(R.string.discard),
                        style = AccTypography.titleSmall
                    )
                }

                Button(
                    onClick = onApply,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccPrimary,
                        contentColor = AccOnPrimary
                    ),
                    enabled = !isApplying
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AccOnPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.apply_changes),
                            style = AccTypography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
