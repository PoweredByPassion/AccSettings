package app.owlow.accsettings.quickaction

import android.content.Context
import androidx.annotation.StringRes
import app.owlow.accsettings.R

/**
 * Shared helpers for formatting charging-control labels used by both the in-app Overview card
 * and the foreground-service notification.
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
}
