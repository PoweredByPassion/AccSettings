package app.owlow.accsettings.data

import android.content.Context
import android.content.SharedPreferences
import app.owlow.accsettings.quickaction.QuickActionConfig
import app.owlow.accsettings.quickaction.QuickActionSlot
import app.owlow.accsettings.quickaction.QuickActionSlotType
import org.json.JSONArray

/**
 * SharedPreferences-backed persistence for the user-customized [QuickActionConfig].
 *
 * Slots are stored as a JSON array under [KEY_SLOTS] (each element `TYPE:param`, e.g.
 * `PAUSE:30m`); the battery-row toggle under [KEY_BATTERY_ROW]. On first launch (no saved
 * data) [load] returns [QuickActionConfig.DEFAULT] so existing users keep the original action
 * list. Corrupted data degrades gracefully to the default.
 */
class QuickActionConfigStore(
    private val prefs: SharedPreferences
) {
    fun load(): QuickActionConfig {
        val raw = prefs.getString(KEY_SLOTS, null)
        val slots = when {
            // First launch (no saved data) → the original action list.
            raw == null -> QuickActionConfig.DEFAULT.slots
            // Saved as `[]` → a valid empty config (0 slots), keep it.
            raw == "[]" -> emptyList()
            // Corrupt saved data → graceful fallback to the default.
            else -> parseSlots(raw).ifEmpty { QuickActionConfig.DEFAULT.slots }
        }.take(QuickActionSlot.MAX_SLOTS)

        return QuickActionConfig(
            slots = slots,
            showBatteryRow = prefs.getBoolean(KEY_BATTERY_ROW, true)
        )
    }

    fun save(config: QuickActionConfig) {
        val slots = config.slots.take(QuickActionSlot.MAX_SLOTS)
        val json = JSONArray()
        slots.forEach { slot ->
            json.put("${slot.type.name}:${slot.param.orEmpty()}")
        }
        prefs.edit()
            .putString(KEY_SLOTS, json.toString())
            .putBoolean(KEY_BATTERY_ROW, config.showBatteryRow)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_SLOTS)
            .putBoolean(KEY_BATTERY_ROW, true)
            .apply()
    }

    private fun parseSlots(raw: String): List<QuickActionSlot> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val element = array.optString(i)
                val type = element.substringBefore(':')
                val param = element.substringAfter(':', "").takeIf { it.isNotBlank() }
                val slotType = runCatching { QuickActionSlotType.valueOf(type) }.getOrNull() ?: continue
                add(QuickActionSlot(slotType, param))
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_SLOTS = "quick_action_slots"
        private const val KEY_BATTERY_ROW = "quick_action_battery_row"

        fun from(context: Context): QuickActionConfigStore =
            QuickActionConfigStore(context.getSharedPreferences("quick_action_config", Context.MODE_PRIVATE))
    }
}
