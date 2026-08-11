package app.owlow.accsettings.quickaction

import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForceStopReconcilerTest {

    private val now = 1_000_000L
    private val bootTimestampMs = 0L

    @Test
    fun inactiveState_returnsAsIs() {
        val state = ForceStopState(active = false)
        val result = ForceStopReconciler.reconcile(state, "not_charging", null, bootTimestampMs, now)
        assertFalse(result.active)
    }

    @Test
    fun activePause_keepsActive_withElapsedWithinDuration() {
        val startedAt = now - 10_000L
        val state = ForceStopState(active = true, condition = "30m", startedAt = startedAt)
        val result = ForceStopReconciler.reconcile(state, "not_charging", "40", bootTimestampMs, now)
        assertTrue(result.active)
        assertEquals("30m", result.condition)
    }

    @Test
    fun activePause_clears_whenDurationExpired() {
        val startedAt = now - 31 * 60 * 1000L
        val state = ForceStopState(active = true, condition = "30m", startedAt = startedAt)
        val result = ForceStopReconciler.reconcile(state, "not_charging", "40", bootTimestampMs, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun activePause_clears_whenCapacityThresholdReached() {
        val state = ForceStopState(active = true, condition = "50%", startedAt = now - 5_000L)
        val result = ForceStopReconciler.reconcile(state, "not_charging", "50", bootTimestampMs, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun activePause_clears_whenChargingResumedPastGrace() {
        val startedAt = now - 20_000L // > 15s grace
        val state = ForceStopState(active = true, condition = null, startedAt = startedAt)
        val result = ForceStopReconciler.reconcile(state, "charging", "40", bootTimestampMs, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun activePause_keepsActive_withinGracePeriod() {
        val state = ForceStopState(active = true, condition = "1h", startedAt = now)
        val result = ForceStopReconciler.reconcile(state, "charging", "40", bootTimestampMs, now)
        assertTrue(result.active)
    }

    @Test
    fun activeChargeTo_clears_whenTargetLevelReached() {
        val state = ForceStopState(
            active = true, mode = ChargingControlMode.CHARGE_TO,
            condition = "75%", startedAt = now - 5_000L
        )
        val result = ForceStopReconciler.reconcile(state, "charging", "75", bootTimestampMs, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun activeForceFull_clears_whenTargetCapacityReached() {
        val state = ForceStopState(
            active = true, mode = ChargingControlMode.FORCE_FULL,
            condition = "90", startedAt = now - 5_000L
        )
        val result = ForceStopReconciler.reconcile(state, "charging", "95", bootTimestampMs, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun activeForceFull_clears_whenChargingStoppedPastGrace() {
        val state = ForceStopState(
            active = true, mode = ChargingControlMode.FORCE_FULL,
            condition = "95", startedAt = now - 20_000L
        )
        val result = ForceStopReconciler.reconcile(state, "not_charging", null, bootTimestampMs, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun startedBeforeReboot_clearsRegardless() {
        val bootTime = now - 10 * 60 * 1000L // booted 10 min ago
        val startedAt = bootTime - 60_000L // started 1 minute BEFORE boot
        val state = ForceStopState(active = true, condition = "1h", startedAt = startedAt)
        val result = ForceStopReconciler.reconcile(state, "not_charging", "40", bootTime, now)
        assertFalse(result.active)
        assertTrue(result.recovered)
    }

    @Test
    fun startedAfterReboot_keepsActive() {
        val bootTime = now - 10 * 60 * 1000L
        val startedAt = bootTime + 60_000L // started 1 minute AFTER boot
        val later = bootTime + 120_000L // now is 2 minutes after boot
        val state = ForceStopState(active = true, condition = "1h", startedAt = startedAt)
        val result = ForceStopReconciler.reconcile(state, "not_charging", "40", bootTime, later)
        assertTrue(result.active)
    }
}
