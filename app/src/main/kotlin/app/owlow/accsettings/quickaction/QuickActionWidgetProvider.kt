package app.owlow.accsettings.quickaction

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.widget.RemoteViews
import app.owlow.accsettings.QuickActionReceiver
import app.owlow.accsettings.R

/**
 * Home-screen widget with buttons for the five canonical quick actions.
 *
 * Each button fires a [PendingIntent] that targets the shared [QuickActionReceiver]
 * using the same `quickaction:` URI scheme used by notifications and app shortcuts.
 */
class QuickActionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.quick_action_widget)

            setupButtonIntents(context, views)
            updateBatteryStatus(context, views)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, QuickActionWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }

    private fun setupButtonIntents(context: Context, views: RemoteViews) {
        val buttons = arrayOf(
            R.id.widget_pause_30m to "quickaction:pause/30m",
            R.id.widget_pause_1h to "quickaction:pause/1h",
            R.id.widget_force_full to "quickaction:force-full",
            R.id.widget_charge_to_85 to "quickaction:charge-to/85",
            R.id.widget_cancel to "quickaction:cancel"
        )

        buttons.forEachIndexed { requestCode, (viewId, uri) ->
            val intent = Intent(context, QuickActionReceiver::class.java)
                .setData(Uri.parse(uri))
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }

    private fun updateBatteryStatus(context: Context, views: RemoteViews) {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        if (pct >= 0) {
            views.setTextViewText(R.id.widget_status, "$pct%")
        } else {
            views.setTextViewText(R.id.widget_status, "")
        }
    }
}
