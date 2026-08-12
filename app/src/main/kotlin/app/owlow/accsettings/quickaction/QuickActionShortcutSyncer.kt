package app.owlow.accsettings.quickaction

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import app.owlow.accsettings.QuickActionActivity
import app.owlow.accsettings.R
import app.owlow.accsettings.data.QuickActionConfigStore

/**
 * Keeps the launcher's dynamic app shortcuts in sync with the user's [QuickActionConfig].
 *
 * Static manifest shortcuts cannot change at runtime, so quick-action shortcuts are pushed
 * dynamically. On API 25+ the platform [ShortcutManager] replaces the whole set atomically;
 * below that (API 23-24) dynamic shortcuts are unavailable and the call is a no-op.
 */
object QuickActionShortcutSyncer {
    private const val ACTION = "app.owlow.accsettings.action.QUICK_ACTION"

    fun sync(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return // 25+

        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        val config = QuickActionConfigStore.from(context).load()

        val shortcuts = config.slots.mapIndexed { index, slot ->
            val label = ChargeControlLabels.slotLabel(context, slot)
            ShortcutInfo.Builder(context, "qa_slot_$index")
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_launcher))
                .setIntent(
                    Intent(context, QuickActionActivity::class.java)
                        .setAction(ACTION)
                        .setData(Uri.parse(slot.toUri()))
                )
                .build()
        }

        shortcutManager.setDynamicShortcuts(shortcuts)
    }
}
