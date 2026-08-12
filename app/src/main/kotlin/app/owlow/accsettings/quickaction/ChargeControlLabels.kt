package app.owlow.accsettings.quickaction

import android.content.Context
import androidx.annotation.StringRes
import app.owlow.accsettings.R
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopState

/**
 * Shared helpers for formatting charging-control labels used by the notification, QS tile,
 * and in-app Overview card.
 */
object ChargeControlLabels {

    /** Returns the total duration in seconds for known duration conditions, or null. */
    fun durationTotalSeconds(condition: String?): Long? = when (condition) {
        "30m" -> 30 * 60L
        "1h" -> 60 * 60L
        "2h" -> 2 * 60 * 60L
        else -> null
    }

    /** Formats a remaining countdown as `Hh Mm Ss`, `Mm Ss`, or `Ss`. */
    fun formatRemainingTime(remainingSeconds: Long): String {
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        return when {
            hours > 0 -> "%dh %dm %ds".format(hours, minutes, seconds)
            minutes > 0 -> "%dm %ds".format(minutes, seconds)
            else -> "%ds".format(seconds)
        }
    }

    /** Returns the string-resource ID for a charge-to (`acc -e`) condition arg. */
    @StringRes
    fun chargeToConditionLabelRes(condition: String?): Int = when (condition) {
        "75%" -> R.string.overview_charge_to_target_75
        "80%" -> R.string.overview_charge_to_target_80
        "85%" -> R.string.overview_charge_to_target_85
        "90%" -> R.string.overview_charge_to_target_90
        "95%" -> R.string.overview_charge_to_target_95
        "30m" -> R.string.overview_charge_to_target_30m
        "1h" -> R.string.overview_charge_to_target_1h
        else -> R.string.overview_charge_to_target_now
    }

    /** Human-readable label for a charge-to (`acc -e`) condition arg. */
    fun chargeToConditionLabel(condition: String?, context: Context): String =
        context.getString(chargeToConditionLabelRes(condition))

    /** Concise display label for a configured quick-action slot (e.g. "Pause 45m", "Charge to 88%", "Force full", "Restore"). */
    fun slotLabel(context: Context, slot: QuickActionSlot): String = when (slot.type) {
        QuickActionSlotType.PAUSE -> context.getString(R.string.quick_actions_slot_pause, slotParamLabel(slot.param))
        QuickActionSlotType.CHARGE_TO -> context.getString(R.string.quick_actions_slot_charge_to, slotParamLabel(slot.param))
        QuickActionSlotType.FORCE_FULL -> context.getString(R.string.quick_actions_slot_force_full, slotParamLabel(slot.param))
        QuickActionSlotType.CANCEL -> context.getString(R.string.quick_actions_slot_cancel)
    }

    /** Renders a slot param for display: `"30m"` → `"30m"`, `"85%"` → `"85%"`, null → "…" (unconditional). */
    private fun slotParamLabel(param: String?): String = param ?: "…"

    // ---- Active-operation title / description -----------------------------------------

    /** Mode-specific title for the active charging-control operation. */
    fun activeTitle(context: Context, state: ForceStopState): String = when (state.mode) {
        ChargingControlMode.STOP -> context.getString(R.string.quick_action_notif_title_active)
        ChargingControlMode.CHARGE_TO -> context.getString(
            R.string.overview_charge_to_active,
            chargeToConditionLabel(state.condition, context)
        )
        ChargingControlMode.FORCE_FULL -> context.getString(
            R.string.overview_force_full_active,
            state.condition ?: "100"
        )
    }

    /** Description / subtitle for the active charging-control operation. */
    fun activeDescription(
        context: Context,
        state: ForceStopState,
        now: Long = System.currentTimeMillis()
    ): String = when (state.mode) {
        ChargingControlMode.STOP -> stopModeDescription(context, state, now)
        ChargingControlMode.CHARGE_TO -> chargeToModeDescription(context, state, now)
        ChargingControlMode.FORCE_FULL -> context.getString(R.string.overview_force_full_progress)
    }

    // ---- Private helpers ---------------------------------------------------------------

    private fun stopModeDescription(
        context: Context,
        state: ForceStopState,
        now: Long
    ): String {
        val remainingSeconds = durationTotalSeconds(state.condition)?.let { total ->
            val startedAt = state.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            return context.getString(
                R.string.overview_force_stop_remaining,
                formatRemainingTime(remainingSeconds)
            )
        }
        val fallbackRes = when (state.condition) {
            "30m" -> R.string.overview_force_stop_recover_30m
            "1h" -> R.string.overview_force_stop_recover_1h
            "2h" -> R.string.overview_force_stop_recover_2h
            "50%" -> R.string.overview_force_stop_recover_50
            "60%" -> R.string.overview_force_stop_recover_60
            "70%" -> R.string.overview_force_stop_recover_70
            else -> R.string.overview_force_stop_recover_manual
        }
        return context.getString(fallbackRes)
    }

    private fun chargeToModeDescription(
        context: Context,
        state: ForceStopState,
        now: Long
    ): String {
        val remainingSeconds = durationTotalSeconds(state.condition)?.let { total ->
            val startedAt = state.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            return context.getString(
                R.string.overview_charge_to_remaining,
                formatRemainingTime(remainingSeconds)
            )
        }
        return context.getString(
            R.string.overview_charge_to_target,
            chargeToConditionLabel(state.condition, context)
        )
    }
}
