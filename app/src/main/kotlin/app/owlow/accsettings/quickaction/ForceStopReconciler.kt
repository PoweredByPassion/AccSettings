package app.owlow.accsettings.quickaction

import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopState

/**
 * Shared recovery detection for charging-control operations.
 *
 * Both the app Overview VM and the notification service call this on every status refresh to
 * decide whether an in-effect operation has ended (duration elapsed / capacity reached /
 * charging resumed past the grace period / started before last boot) and can be cleared.
 */
object ForceStopReconciler {
    /** Seconds after enabling before a `Charging` status is trusted as recovery. */
    const val RECOVERY_GRACE_SECONDS = 15L

    data class ReconcileResult(
        val active: Boolean,
        val mode: ChargingControlMode,
        val condition: String?,
        val startedAt: Long?,
        val recovered: Boolean
    )

    fun reconcile(
        current: ForceStopState,
        chargingStatus: String?,
        level: String?,
        bootTimestampMs: Long,
        now: Long
    ): ReconcileResult {
        if (!current.active) {
            return current.toResult(recovered = false)
        }
        val elapsed = current.startedAt?.let { ((now - it) / 1000L).coerceAtLeast(0L) } ?: 0L
        // Started before last boot: ACC's detached timer and the sysfs switch die on reboot, and
        // ACC's boot service restarts the daemon with the normal config, so the state is stale.
        if (current.startedAt != null && current.startedAt < bootTimestampMs) {
            return current.toResult(recovered = true)
        }
        if (isRecovered(current, chargingStatus, level, elapsed)) {
            return current.toResult(recovered = true)
        }
        return current.toResult(recovered = false)
    }

    private fun isRecovered(
        current: ForceStopState,
        chargingStatus: String?,
        level: String?,
        elapsed: Long
    ): Boolean {
        val charging = chargingStatus?.trim()?.equals("charging", ignoreCase = true) == true
        val levelInt = level?.trim()?.toIntOrNull()
        return when (current.mode) {
            ChargingControlMode.STOP -> {
                val durationSeconds = when (current.condition) {
                    "30m" -> 30 * 60L
                    "1h" -> 60 * 60L
                    "2h" -> 2 * 60 * 60L
                    else -> null
                }
                val capacityThreshold = when (current.condition) {
                    "50%" -> 50
                    "60%" -> 60
                    "70%" -> 70
                    else -> null
                }
                when {
                    charging && elapsed >= RECOVERY_GRACE_SECONDS -> true
                    durationSeconds != null && elapsed >= durationSeconds -> true
                    capacityThreshold != null && levelInt != null && levelInt <= capacityThreshold -> true
                    else -> false
                }
            }
            ChargingControlMode.CHARGE_TO -> {
                val durationSeconds = when (current.condition) {
                    "30m" -> 30 * 60L
                    "1h" -> 60 * 60L
                    else -> null
                }
                val targetLevel = when (current.condition) {
                    "75%" -> 75
                    "80%" -> 80
                    "85%" -> 85
                    "90%" -> 90
                    "95%" -> 95
                    else -> null
                }
                when {
                    durationSeconds != null && elapsed >= durationSeconds -> true
                    targetLevel != null && levelInt != null && levelInt >= targetLevel -> true
                    else -> false
                }
            }
            ChargingControlMode.FORCE_FULL -> {
                val targetLevel = current.condition?.toIntOrNull()
                when {
                    targetLevel != null && levelInt != null && levelInt >= targetLevel -> true
                    !charging && chargingStatus != null && elapsed >= RECOVERY_GRACE_SECONDS -> true
                    else -> false
                }
            }
        }
    }

    private fun ForceStopState.toResult(recovered: Boolean) = ReconcileResult(
        active = active && !recovered,
        mode = mode,
        condition = condition,
        startedAt = startedAt,
        recovered = recovered
    )
}
