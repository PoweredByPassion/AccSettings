package app.owlow.accsettings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Shared entry point for notification buttons and the widget (Task 6).
 *
 * Reads the `quickaction:` URI from [Intent.getDataString] and dispatches it via
 * [QuickActionDispatcher] on a background thread. (App shortcuts target the transparent
 * [QuickActionActivity] instead — Android launches shortcut intents with `startActivity`, which
 * cannot start a BroadcastReceiver.)
 */
class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uriString = intent.dataString ?: return
        val pendingResult = goAsync()
        QuickActionDispatcher.dispatch(context, uriString) {
            pendingResult.finish()
        }
    }
}
