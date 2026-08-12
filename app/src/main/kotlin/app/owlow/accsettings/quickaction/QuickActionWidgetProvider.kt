package app.owlow.accsettings.quickaction

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.view.View
import android.widget.RemoteViews
import app.owlow.accsettings.QuickActionActivity
import app.owlow.accsettings.R
import app.owlow.accsettings.data.QuickActionConfigStore

/**
 * Home-screen widget with a rounded-card layout: an optional battery status row and one
 * button per configured quick-action slot. The buttons and battery row are rendered
 * dynamically from the user's [QuickActionConfig], so their count, order, and labels follow
 * the config (0-5 slots), and a `showBatteryRow` toggle hides the status line.
 */
class QuickActionWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val config = QuickActionConfigStore.from(context).load()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.quick_action_widget)

            // Battery row: honor the config toggle.
            if (config.showBatteryRow) {
                views.setViewVisibility(R.id.widget_battery_row, View.VISIBLE)
                updateBatteryStatus(context, views)
            } else {
                views.setViewVisibility(R.id.widget_battery_row, View.GONE)
            }

            // Rebuild the button list (the container is empty in the layout). All configured
            // slots are shown; the widget grows to fit their height.
            views.removeAllViews(R.id.widget_buttons_container)
            config.slots.forEachIndexed { index, slot ->
                val button = RemoteViews(context.packageName, R.layout.widget_action_button)
                button.setTextViewText(R.id.widget_button, ChargeControlLabels.slotLabel(context, slot))

                val intent = Intent(context, QuickActionActivity::class.java)
                    .setData(Uri.parse(slot.toUri()))
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    index, // unique requestCode per button
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                button.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

                views.addView(R.id.widget_buttons_container, button)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateBatteryStatus(context: Context, views: RemoteViews) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        views.setTextViewText(R.id.widget_status, if (pct >= 0) "$pct%" else "--")
    }
}
