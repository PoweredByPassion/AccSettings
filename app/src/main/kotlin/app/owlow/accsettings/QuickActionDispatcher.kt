package app.owlow.accsettings

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.owlow.accsettings.R
import app.owlow.accsettings.quickaction.ChargingControlCoordinator
import app.owlow.accsettings.quickaction.FeedbackSink
import app.owlow.accsettings.quickaction.QuickAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared logic for handling a `quickaction:` URI from any entry point (broadcast receiver,
 * transparent activity). Maps the URI to a [QuickAction] and dispatches it through a
 * [ChargingControlCoordinator] on a background thread, reporting results as toasts.
 */
object QuickActionDispatcher {
    /** Dispatches [uriString] (e.g. `quickaction:force-full`) via a coordinator. No-op for unknown URIs. */
    fun dispatch(context: Context, uriString: String, onFinished: () -> Unit = {}) {
        val action = mapUriToAction(uriString) ?: run { onFinished(); return }
        val appContext = context.applicationContext
        val coordinator = ChargingControlCoordinator.forContext(appContext)
        val sink = ToastSink(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    coordinator.execute(action, sink)
                }.onFailure { error ->
                    sink.show(error.localizedMessage ?: "Action failed")
                }
            } finally {
                onFinished()
            }
        }
    }

    /**
     * Maps a `quickaction:` URI to a [QuickAction].
     *
     * | URI | QuickAction |
     * |---|---|
     * | `quickaction:pause/30m` | `Pause("30m")` |
     * | `quickaction:pause/1h`  | `Pause("1h")`  |
     * | `quickaction:force-full` | `ForceFull(100)` |
     * | `quickaction:charge-to/85` | `ChargeTo("85%")` |
     * | `quickaction:cancel`     | `Cancel` |
     */
    fun mapUriToAction(uriString: String): QuickAction? {
        val parsed = Uri.parse(uriString)
        val ssp = parsed.schemeSpecificPart ?: return null
        return when (ssp) {
            "cancel" -> QuickAction.Cancel
            "force-full" -> QuickAction.ForceFull(100)
            "pause/30m" -> QuickAction.Pause("30m")
            "pause/1h" -> QuickAction.Pause("1h")
            "charge-to/85" -> QuickAction.ChargeTo("85%")
            else -> null
        }
    }
}

/**
 * A [FeedbackSink] that shows the result as a short [Toast] using localized strings.
 *
 * [show] may be called from a background coroutine (the dispatcher runs on `Dispatchers.IO`),
 * but [Toast] must be shown on a thread with a Looper. Post to the main looper so the toast is
 * always safe.
 */
private class ToastSink(private val context: Context) : FeedbackSink {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun show(message: String) {
        val resId = when (message) {
            "Done" -> R.string.toast_started
            "Charging restored" -> R.string.toast_cancelled
            else -> R.string.toast_error
        }
        val text = context.getString(resId)
        mainHandler.post {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}
