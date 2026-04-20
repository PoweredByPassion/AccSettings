package app.owlow.accsetting.ui.config.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.owlow.accsetting.R

@Composable
fun LeaveWithDraftDialog(
    onKeepDraftAndLeave: () -> Unit,
    onDiscardAndLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.leave_with_draft_title))
        },
        text = {
            Text(text = stringResource(R.string.leave_with_draft_message))
        },
        confirmButton = {
            TextButton(onClick = onKeepDraftAndLeave) {
                Text(text = stringResource(R.string.keep_draft_and_leave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscardAndLeave) {
                Text(text = stringResource(R.string.discard))
            }
        }
    )
}
