package app.owlow.accsettings.quickaction

import android.content.Context
import android.os.SystemClock
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.ForceStopState
import app.owlow.accsettings.data.LiveOverviewRepository
import app.owlow.accsettings.data.OverviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns start/stop of the foreground service that hosts the live notification.
 *
 * Implementations must support idempotent [stop] — it is safe to call even when the service
 * was never started (e.g. cancelling with no active operation).
 */
interface ServiceController {
    fun start()
    fun stop()
}

class ChargingControlCoordinator(
    private val repository: OverviewRepository,
    private val store: ForceStopChargingStore,
    private val serviceController: ServiceController,
    private val bootTimestampMs: () -> Long = { 0L }
) {
    private val _state = MutableStateFlow(store.load())
    val state: StateFlow<ForceStopState> = _state.asStateFlow()

    /**
     * Execute a quick action. Auto-cancels any active operation first.
     *
     * The whole read-check-write sequence is guarded by a process-level [Mutex] so that
     * concurrent executions from different surfaces (each creates its own coordinator) cannot
     * both see an inactive store and both start ACC commands. All instances share the same
     * lock via [executionMutex].
     */
    suspend fun execute(action: QuickAction, sink: FeedbackSink? = null): ForceStopState =
        executionMutex.withLock {
            when (action) {
                is QuickAction.Pause -> runStart({ repository.setForceStopCharging(true, action.condition) }, ChargingControlMode.STOP, action.condition, sink)
                is QuickAction.ChargeTo -> runStart({ repository.enableCharging(action.target) }, ChargingControlMode.CHARGE_TO, action.target, sink)
                is QuickAction.ForceFull -> runStart({ repository.forceFullCharge(action.capacity) }, ChargingControlMode.FORCE_FULL, action.capacity.toString(), sink)
                // cancelAnyLocked, not cancelAny — the lock is already held here (Mutex is non-reentrant).
                QuickAction.Cancel -> cancelAnyLocked(sink)
                is QuickAction.StartDaemon -> runDaemon({ repository.setDaemonRunning(true) }, sink)
                is QuickAction.StopDaemon -> runDaemon({ repository.setDaemonRunning(false) }, sink)
            }
        }

    /** Cancel the active operation (if any) and restore normal charging. Mutex-guarded like [execute]. */
    suspend fun cancelAny(sink: FeedbackSink? = null): ForceStopState = executionMutex.withLock {
        cancelAnyLocked(sink)
    }

    /** Cancel body, to be called only while [executionMutex] is held. */
    private suspend fun cancelAnyLocked(sink: FeedbackSink?): ForceStopState {
        val active = store.load()
        if (active.active) {
            repository.cancelChargeAction(active.mode)
        }
        val cleared = ForceStopState(active = false)
        store.clear()
        _state.value = cleared
        serviceController.stop()
        sink?.show("Charging restored")
        return cleared
    }

    private suspend fun runStart(
        call: suspend () -> Unit,
        mode: ChargingControlMode,
        condition: String?,
        sink: FeedbackSink?
    ): ForceStopState {
        val current = store.load()
        if (current.active) {
            // Auto-cancel first. If cancelling the active op fails, do NOT start the new one —
            // two blocking acc commands must never fight for ACC's lock.
            repository.cancelChargeAction(current.mode)
        }
        call()
        val active = ForceStopState(
            active = true,
            mode = mode,
            condition = condition,
            startedAt = System.currentTimeMillis()
        )
        store.save(active)
        _state.value = active
        serviceController.start()
        sink?.show("Done")
        return active
    }

    private suspend fun runDaemon(call: suspend () -> Unit, sink: FeedbackSink?): ForceStopState {
        call()
        sink?.show("Done")
        return store.load()
    }

    /** Reconcile the persisted state against fresh ACC status; returns the reconciled state. */
    suspend fun reconcile(
        status: app.owlow.accsettings.acc.AccStatus?,
        now: Long = System.currentTimeMillis()
    ): ForceStopState {
        val current = store.load()
        val chargingStatus = status?.chargingInfo?.status
        val level = status?.chargingInfo?.level
        val result = ForceStopReconciler.reconcile(current, chargingStatus, level, bootTimestampMs(), now)
        if (result.recovered) {
            store.clear()
            _state.value = ForceStopState(active = false)
            serviceController.stop()
        } else {
            _state.value = current
        }
        return _state.value
    }

    companion object {
        /**
         * Process-level lock serializing charging-control mutations across ALL coordinator
         * instances (each surface creates its own via [forContext], but they share this lock).
         * Without it, two surfaces dispatching concurrently could both read an inactive store
         * and both start ACC commands that fight for the daemon lock.
         */
        private val executionMutex = Mutex()

        /** Standard surface wiring: live repository + SharedPreferences store + real service controller. */
        fun forContext(context: Context) = ChargingControlCoordinator(
            repository = LiveOverviewRepository,
            store = ForceStopChargingStore.from(context.applicationContext),
            serviceController = ServiceControllerImpl(context.applicationContext),
            bootTimestampMs = { System.currentTimeMillis() - SystemClock.elapsedRealtime() }
        )
    }
}
