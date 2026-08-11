package app.owlow.accsettings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import app.owlow.accsettings.R
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.LiveOverviewRepository
import app.owlow.accsettings.quickaction.ChargingControlCoordinator
import app.owlow.accsettings.quickaction.FeedbackSink
import app.owlow.accsettings.quickaction.QuickAction
import app.owlow.accsettings.quickaction.ServiceControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared entry point for notification buttons, app shortcuts (Task 5), and the widget (Task 6).
 *
 * Reads the `quickaction:` URI from [Intent.getDataString], maps it to a [QuickAction], and
 * dispatches it through a [ChargingControlCoordinator] on a background thread.
 *
 * ### URI mapping
 *
 * | URI | QuickAction |
 * |---|---|
 * | `quickaction:pause/30m` | `Pause("30m")` |
 * | `quickaction:pause/1h`  | `Pause("1h")`  |
 * | `quickaction:force-full` | `ForceFull(100)` |
 * | `quickaction:charge-to/85` | `ChargeTo("85%")` |
 * | `quickaction:cancel`     | `Cancel` |
 */
class QuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uriString = intent.dataString ?: return
        val action = mapUriToAction(uriString) ?: return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val coordinator = buildCoordinator(appContext)
        val sink = ToastSink(appContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    coordinator.execute(action, sink)
                }.onFailure { error ->
                    sink.show(error.localizedMessage ?: "Action failed")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun mapUriToAction(uriString: String): QuickAction? {
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

    private companion object {
        fun buildCoordinator(context: Context): ChargingControlCoordinator {
            val store = ForceStopChargingStore.from(context)
            return ChargingControlCoordinator(
                repository = LiveOverviewRepository,
                store = store,
                serviceController = ServiceControllerImpl(context),
                bootTimestampMs = { System.currentTimeMillis() - SystemClock.elapsedRealtime() }
            )
        }
    }
}

/** A [FeedbackSink] that shows the result as a short [Toast] using localized strings. */
private class ToastSink(private val context: Context) : FeedbackSink {
    override fun show(message: String) {
        val resId = when (message) {
            "Done" -> R.string.toast_started
            "Charging restored" -> R.string.toast_cancelled
            else -> R.string.toast_error
        }
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
    }
}
