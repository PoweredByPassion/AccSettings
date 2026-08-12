package app.owlow.accsettings.data

import android.content.Context
import android.content.SharedPreferences
import app.owlow.accsettings.acc.ChargingControlMode

/**
 * The persisted state of the charging-control toggle.
 *
 * [active] is true while one of the charging-control operations is in effect. [mode] distinguishes
 * which operation: `STOP` (`acc -d`), `CHARGE_TO` (`acc -e`) or `FORCE_FULL` (`acc -f`). [condition]
 * is the ACC arg chosen when it was enabled (a duration `"30m"`, `"1h"` or a capacity threshold
 * `"50%"`/`"75%"`), or null for unconditional. [startedAt] is the epoch-ms timestamp the toggle was
 * enabled, used to show an elapsed/remaining countdown when [condition] is a duration.
 */
data class ForceStopState(
    val active: Boolean = false,
    val mode: ChargingControlMode = ChargingControlMode.STOP,
    val condition: String? = null,
    val startedAt: Long? = null
)

/** SharedPreferences-backed persistence for [ForceStopState]. */
class ForceStopChargingStore(
    private val prefs: SharedPreferences
) {
    fun load(): ForceStopState = ForceStopState(
        active = prefs.getBoolean(KEY_ACTIVE, false),
        mode = prefs.getString(KEY_MODE, null)?.let { stored ->
            runCatching { ChargingControlMode.valueOf(stored) }.getOrDefault(ChargingControlMode.STOP)
        } ?: ChargingControlMode.STOP,
        condition = prefs.getString(KEY_CONDITION, null),
        startedAt = prefs.getLong(KEY_STARTED_AT, 0L).takeIf { it > 0L }
    )

    fun save(state: ForceStopState) {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .apply()
        if (state.active) {
            prefs.edit()
                .putString(KEY_MODE, state.mode.name)
                .putString(KEY_CONDITION, state.condition)
                .putLong(KEY_STARTED_AT, state.startedAt ?: 0L)
                .apply()
        } else {
            prefs.edit()
                .remove(KEY_MODE)
                .remove(KEY_CONDITION)
                .remove(KEY_STARTED_AT)
                .apply()
        }
    }

    fun clear() = save(ForceStopState())

    companion object {
        private const val KEY_ACTIVE = "force_stop_active"
        private const val KEY_MODE = "force_stop_mode"
        private const val KEY_CONDITION = "force_stop_condition"
        private const val KEY_STARTED_AT = "force_stop_started_at"

        fun from(context: Context): ForceStopChargingStore =
            ForceStopChargingStore(context.getSharedPreferences("force_stop_charging", Context.MODE_PRIVATE))
    }
}
