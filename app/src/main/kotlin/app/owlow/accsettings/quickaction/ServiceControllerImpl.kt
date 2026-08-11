package app.owlow.accsettings.quickaction

import android.content.Context
import android.content.Intent

/**
 * Real [ServiceController] that drives [QuickActionService] (the foreground notification).
 *
 * [stop] is idempotent — safe to call even when the service was never started.
 */
class ServiceControllerImpl(context: Context) : ServiceController {
    private val appContext: Context = context.applicationContext

    override fun start() {
        val intent = Intent(appContext, QuickActionService::class.java)
        appContext.startForegroundService(intent)
    }

    override fun stop() {
        val intent = Intent(appContext, QuickActionService::class.java)
        appContext.stopService(intent)
    }
}
