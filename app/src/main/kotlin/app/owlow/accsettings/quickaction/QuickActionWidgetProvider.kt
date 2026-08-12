package app.owlow.accsettings.quickaction

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.widget.RemoteViews
import app.owlow.accsettings.QuickActionActivity
import app.owlow.accsettings.R

/**
 * Home-screen widget with buttons for the five canonical quick actions.
 *
 * Each button launches the transparent [QuickActionActivity] (via `getActivity`) with a
 * `quickaction:` URI, so tapping a button shows the same loading/result feedback card as app
 * shortcuts.
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

    private fun setupButtonIntents(context: Context, views: RemoteViews) {
        val buttons = arrayOf(
            R.id.widget_pause_30m to "quickaction:pause/30m",
            R.id.widget_pause_1h to "quickaction:pause/1h",
            R.id.widget_force_full to "quickaction:force-full",
            R.id.widget_charge_to_85 to "quickaction:charge-to/85",
            R.id.widget_cancel to "quickaction:cancel"
        )

        buttons.forEachIndexed { requestCode, (viewId, uri) ->
            val intent = Intent(context, QuickActionActivity::class.java)
                .setData(Uri.parse(uri))
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }

    private fun updateBatteryStatus(context: Context, views: RemoteViews) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        views.setTextViewText(R.id.widget_status, if (pct >= 0) "$pct%" else "")
    }
}
