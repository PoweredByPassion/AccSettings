package app.owlow.accsettings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.owlow.accsettings.ui.theme.AccSettingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Transparent, no-title target for app shortcuts and the widget.
 *
 * Android launches a shortcut's `<intent>` with `startActivity`, which cannot start a
 * [BroadcastReceiver]. So shortcuts point here instead. It shows a centered feedback card:
 * a loading spinner while the quick action runs, then a success/failure checkmark, then
 * finishes automatically. Widget buttons also launch this Activity (via `getActivity`) so
 * they get the same visible feedback.
 */
class QuickActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = intent.dataString
        if (uriString == null) {
            finish()
            return
        }
        setContent {
            AccSettingTheme {
                QuickActionCard(
                    uriString = uriString,
                    onFinished = {
                        if (!isFinishing) finish()
                    }
                )
            }
        }
    }
}

/** Minimum time the loading spinner stays visible so it is perceptible (execute is fast). */
private const val MIN_LOADING_MS = 750L

/** How long the success/failure result stays visible before the Activity finishes. */
private const val RESULT_VISIBLE_MS = 1200L

private sealed interface CardState {
    data object Loading : CardState
    data object Success : CardState
    data class Failure(val message: String) : CardState
}

@Composable
private fun QuickActionCard(uriString: String, onFinished: () -> Unit) {
    var state by remember { mutableStateOf<CardState>(CardState.Loading) }
    val context = LocalContext.current

    LaunchedEffect(uriString) {
        val startTime = System.currentTimeMillis()
        state = CardState.Loading

        val result = withContext(Dispatchers.IO) {
            QuickActionDispatcher.dispatchAndAwait(context = context, uriString = uriString)
        }

        // Unknown URI: nothing to show — finish immediately.
        if (result == null) {
            onFinished()
            return@LaunchedEffect
        }

        // Guarantee the loading spinner is visible for a perceptible minimum.
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed < MIN_LOADING_MS) {
            delay(MIN_LOADING_MS - elapsed)
        }

        state = when (result) {
            is DispatchResult.Success -> CardState.Success
            is DispatchResult.Failure -> CardState.Failure(result.message)
        }

        delay(RESULT_VISIBLE_MS)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state) {
                    CardState.Loading -> {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.quick_action_loading),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    CardState.Success -> {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.quick_action_success),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    is CardState.Failure -> {
                        val failure = state as CardState.Failure
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = failure.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
