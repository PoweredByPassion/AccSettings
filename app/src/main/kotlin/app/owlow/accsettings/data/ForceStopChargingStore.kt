package app.owlow.accsettings.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The persisted state of the force-stop-charging toggle.
 *
 * [active] is true while charging is force-disabled via `acc -d`. [condition] is the recovery
 * condition (ACC arg) chosen when it was enabled: a duration (`"30m"`, `"1h"`) or a capacity
 * threshold (`"50%"`), or null for unconditional (restore manually via the cancel button).
 * [startedAt] is the epoch-ms timestamp the toggle was enabled, used to show an elapsed/remaining
 * countdown when [condition] is a duration.
 */
data class ForceStopState(
    val active: Boolean = false,
    val condition: String? = null,
    val startedAt: Long? = null
)

/** SharedPreferences-backed persistence for [ForceStopState]. */
class ForceStopChargingStore(
    private val prefs: SharedPreferences
) {
    fun load(): ForceStopState = ForceStopState(
        active = prefs.getBoolean(KEY_ACTIVE, false),
        condition = prefs.getString(KEY_CONDITION, null),
        startedAt = prefs.getLong(KEY_STARTED_AT, 0L).takeIf { it > 0L }
    )

    fun save(state: ForceStopState) {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, state.active)
            .apply()
        if (state.active) {
            prefs.edit()
                .putString(KEY_CONDITION, state.condition)
                .putLong(KEY_STARTED_AT, state.startedAt ?: 0L)
                .apply()
        } else {
            prefs.edit()
                .remove(KEY_CONDITION)
                .remove(KEY_STARTED_AT)
                .apply()
        }
    }

    fun clear() = save(ForceStopState())

    companion object {
        private const val KEY_ACTIVE = "force_stop_active"
        private const val KEY_CONDITION = "force_stop_condition"
        private const val KEY_STARTED_AT = "force_stop_started_at"

        fun from(context: Context): ForceStopChargingStore =
            ForceStopChargingStore(context.getSharedPreferences("force_stop_charging", Context.MODE_PRIVATE))
    }
}
