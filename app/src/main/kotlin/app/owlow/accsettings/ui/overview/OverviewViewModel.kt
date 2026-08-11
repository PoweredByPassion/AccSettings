package app.owlow.accsettings.ui.overview

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsettings.R
import app.owlow.accsettings.acc.AccInstallState
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.ForceStopState
import app.owlow.accsettings.quickaction.ForceStopReconciler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

interface OverviewRepository {
    suspend fun loadStatus(): AccStatus?
    suspend fun startService(): AccStatus?
    suspend fun setDaemonRunning(enabled: Boolean): AccStatus?
    suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus?
    suspend fun enableCharging(condition: String?): AccStatus?
    suspend fun forceFullCharge(capacity: Int): AccStatus?
    suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus?
}

class OverviewViewModel(
    private val context: Context,
    private val overviewRepository: OverviewRepository,
    private val forceStopStore: ForceStopChargingStore = ForceStopChargingStore.from(context),
    /** Epoch-ms timestamp of the most recent boot; injected so reboot detection is testable. */
    private val bootTimestampMs: () -> Long = { System.currentTimeMillis() - SystemClock.elapsedRealtime() }
) : ViewModel() {
    private var autoRefreshJob: Job? = null

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        // Restore the persisted force-stop state so the UI shows the correct toggle/card after
        // a process restart.
        val persisted = forceStopStore.load()
        if (persisted.active) {
            _uiState.value = _uiState.value.copy(
                forceStop = ForceStopUiState(
                    active = true,
                    mode = persisted.mode,
                    condition = persisted.condition,
                    startedAt = persisted.startedAt
                )
            )
        }
    }

    fun refresh(): Job = viewModelScope.launch {
        reloadStatus(showLoading = true)
    }

    fun startService(): Job = viewModelScope.launch {
        // NOTE: no full-screen spinner on service start; keep the UI interactive while ACC starts.
        // A focused `daemonBusy` flag gives the daemon control busy feedback while the root command runs.
        _uiState.value = _uiState.value.copy(daemonBusy = true)
        runCatching { overviewRepository.startService() }
            .onSuccess { status -> _uiState.value = status.toUiState(context).copy(daemonBusy = false, forceStop = _uiState.value.forceStop) }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to start service")
                )
            }
    }

    fun toggleDaemon(enabled: Boolean): Job = viewModelScope.launch {
        // NOTE: no full-screen spinner on daemon toggle; keep the UI interactive while ACC reacts.
        _uiState.value = _uiState.value.copy(daemonBusy = true)
        runCatching { overviewRepository.setDaemonRunning(enabled) }
            .onSuccess { status -> _uiState.value = status.toUiState(context).copy(daemonBusy = false, forceStop = _uiState.value.forceStop) }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to toggle daemon")
                )
            }
    }

    fun showForceStopDialog() {
        _uiState.value = _uiState.value.copy(showForceStopDialog = true)
    }

    fun dismissForceStopDialog() {
        _uiState.value = _uiState.value.copy(showForceStopDialog = false)
    }

    /** Enables force-stop charging (`acc -d`) with the chosen recovery [condition]. */
    fun enableForceStopCharging(condition: String?): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(daemonBusy = true, showForceStopDialog = false)
        // Mutually exclusive: cancel any other in-effect charging control first.
        cancelIfActive()
        runCatching { overviewRepository.setForceStopCharging(true, condition) }
            .onSuccess { status ->
                val forceStop = ForceStopUiState(
                    active = true,
                    mode = ChargingControlMode.STOP,
                    condition = condition,
                    startedAt = System.currentTimeMillis(),
                    elapsedSeconds = 0L
                )
                persistForceStop(forceStop)
                _uiState.value = status.toUiState(context).copy(daemonBusy = false, forceStop = forceStop)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to force stop charging")
                )
            }
    }

    /** Resumes charging to a target condition (`acc -e`). */
    fun resumeChargingTo(condition: String?): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(daemonBusy = true, showChargeToDialog = false)
        cancelIfActive()
        runCatching { overviewRepository.enableCharging(condition) }
            .onSuccess { status ->
                val forceStop = ForceStopUiState(
                    active = true,
                    mode = ChargingControlMode.CHARGE_TO,
                    condition = condition,
                    startedAt = System.currentTimeMillis(),
                    elapsedSeconds = 0L
                )
                persistForceStop(forceStop)
                _uiState.value = status.toUiState(context).copy(daemonBusy = false, forceStop = forceStop)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to resume charging")
                )
            }
    }

    /** One-shot force-full charge (`acc -f <capacity>`). */
    fun forceFullCharge(capacity: Int): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(daemonBusy = true, showForceFullDialog = false)
        cancelIfActive()
        runCatching { overviewRepository.forceFullCharge(capacity) }
            .onSuccess { status ->
                val forceStop = ForceStopUiState(
                    active = true,
                    mode = ChargingControlMode.FORCE_FULL,
                    condition = capacity.toString(),
                    startedAt = System.currentTimeMillis(),
                    elapsedSeconds = 0L
                )
                persistForceStop(forceStop)
                _uiState.value = status.toUiState(context).copy(daemonBusy = false, forceStop = forceStop)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to force full charge")
                )
            }
    }

    /** Cancels whichever charging-control operation is active and restores regular charging. */
    fun cancelForceStopCharging(): Job = viewModelScope.launch {
        val active = _uiState.value.forceStop
        if (!active.active) return@launch
        _uiState.value = _uiState.value.copy(daemonBusy = true)
        runCatching { overviewRepository.cancelChargeAction(active.mode) }
            .onSuccess { status ->
                forceStopStore.clear()
                _uiState.value = status.toUiState(context).copy(daemonBusy = false, forceStop = ForceStopUiState())
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to restore charging")
                )
            }
    }

    /**
     * Cancels any currently active charging-control operation before a new one is started.
     * Guarantees the operations stay mutually exclusive on the ACC side (no two blocking commands
     * fighting for the lock).
     */
    private suspend fun cancelIfActive() {
        val active = _uiState.value.forceStop
        if (!active.active) return
        runCatching { overviewRepository.cancelChargeAction(active.mode) }
    }

    /** Persists a newly-activated charging-control state. */
    private fun persistForceStop(forceStop: ForceStopUiState) {
        forceStopStore.save(
            ForceStopState(
                active = true,
                mode = forceStop.mode,
                condition = forceStop.condition,
                startedAt = forceStop.startedAt
            )
        )
    }

    fun showChargeToDialog() {
        _uiState.value = _uiState.value.copy(showChargeToDialog = true)
    }

    fun dismissChargeToDialog() {
        _uiState.value = _uiState.value.copy(showChargeToDialog = false)
    }

    fun showForceFullDialog() {
        _uiState.value = _uiState.value.copy(showForceFullDialog = true)
    }

    fun dismissForceFullDialog() {
        _uiState.value = _uiState.value.copy(showForceFullDialog = false)
    }

    fun startAutoRefresh(intervalMs: Long = CHARGING_REFRESH_INTERVAL_MS) {
        if (autoRefreshJob?.isActive == true) {
            return
        }
        autoRefreshJob = viewModelScope.launch {
            reloadStatus(showLoading = _uiState.value.runtimeFacts.isEmpty() && _uiState.value.chargingFacts.isEmpty())
            while (isActive) {
                delay(intervalMs)
                reloadStatus(showLoading = false)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private suspend fun reloadStatus(showLoading: Boolean) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(isLoading = true)
        }
        val status = overviewRepository.loadStatus()
        val forceStop = reconcileForceStop(_uiState.value.forceStop, status)
        _uiState.value = status.toUiState(context, daemonBusy = _uiState.value.daemonBusy)
            .copy(
                forceStop = forceStop,
                showForceStopDialog = _uiState.value.showForceStopDialog,
                showChargeToDialog = _uiState.value.showChargeToDialog,
                showForceFullDialog = _uiState.value.showForceFullDialog
            )
    }

    /**
     * Keeps the persisted force-stop state aligned with reality on each status refresh.
     *
     * While force-stop is active the app cannot see ACC's own timer, so we derive the true state
     * from the freshly loaded [status] every poll:
     *  - A force-stop whose [ForceStopUiState.startedAt] predates the last boot is cleared
     *    immediately: ACC's detached timer and the sysfs charging switch both die on reboot, and
     *    ACC's boot service restarts the daemon with the normal config.
     *  - [ForceStopUiState.elapsedSeconds] is recomputed from [ForceStopUiState.startedAt] to
     *    drive the live remaining-time countdown.
     *  - Recovery is detected for every condition type ACC supports:
     *    - duration (`"30m"`, `"1h"`, `"2h"`): ACC sleeps on the wall clock, so once
     *      `elapsed >= duration` the condition has been met — charging is re-enabled.
     *    - capacity (`"50%"`, `"60%"`, `"70%"`): once the reported level is `<= threshold` ACC's
     *      `until` loop has ended and charging is re-enabled.
     *    - any condition: when the charging status is `Charging` again, ACC has flipped the
     *      charging switch back on (it always calls `enable_charging` when a condition is met,
     *      and also for an unconditional restore via `acc -e`). A short grace period avoids
     *      clearing the card in the brief window right after enabling, before the detached
     *      `acc -d` command has actually cut the switch.
     *
     * Note the daemon state is intentionally NOT used here: `acc -d <condition>` keeps holding
     * ACC's lock (and thus reports "daemon running") for the whole force-stop, so it cannot tell
     * force-stopped apart from recovered.
     */
    private fun reconcileForceStop(current: ForceStopUiState, status: AccStatus?): ForceStopUiState {
        if (!current.active) {
            return current
        }
        val now = System.currentTimeMillis()
        val elapsed = current.startedAt?.let { ((now - it) / 1000L).coerceAtLeast(0L) } ?: 0L
        // A force-stop started before the last boot can no longer be in effect: ACC's detached
        // timer and the sysfs charging switch it toggled both die on reboot, and ACC's boot
        // service restarts the daemon with the normal charging config. Clear the stale state so
        // the card doesn't keep claiming "charging stopped" after a reboot.
        if (current.startedAt != null && current.startedAt < bootTimestampMs()) {
            forceStopStore.clear()
            return ForceStopUiState()
        }
        if (isForceStopRecovered(current, status, elapsed)) {
            forceStopStore.clear()
            return ForceStopUiState()
        }
        return current.copy(elapsedSeconds = elapsed)
    }

    /** Whether ACC has already restored charging given [current]'s mode/condition and [status]. */
    private fun isForceStopRecovered(current: ForceStopUiState, status: AccStatus?, elapsed: Long): Boolean {
        val chargingStatus = status?.chargingInfo?.status
        val level = status?.chargingInfo?.level
        return ForceStopReconciler.reconcile(
            current = current.toPersisted(),
            chargingStatus = chargingStatus,
            level = level,
            bootTimestampMs = bootTimestampMs(),
            now = System.currentTimeMillis()
        ).recovered
    }

    private fun ForceStopUiState.toPersisted(): ForceStopState = ForceStopState(
        active = active,
        mode = mode,
        condition = condition,
        startedAt = startedAt
    )

    override fun onCleared() {
        stopAutoRefresh()
        super.onCleared()
    }

    companion object {
        private const val CHARGING_REFRESH_INTERVAL_MS = 3_000L

        fun factory(
            context: Context,
            overviewRepository: OverviewRepository = LiveOverviewRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OverviewViewModel(
                    context.applicationContext,
                    overviewRepository,
                    ForceStopChargingStore.from(context.applicationContext)
                ) as T
            }
        }
    }
}

private object LiveOverviewRepository : OverviewRepository {
    override suspend fun loadStatus(): AccStatus? = AccStateManager.refreshStatus()

    override suspend fun startService(): AccStatus? {
        AccStateManager.setDaemonRunning(true)
        return AccStateManager.refreshStatus()
    }

    override suspend fun setDaemonRunning(enabled: Boolean): AccStatus? {
        AccStateManager.setDaemonRunning(enabled)
        return AccStateManager.refreshStatus()
    }

    override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus? {
        AccStateManager.setForceStopCharging(enabled, condition)
        return AccStateManager.refreshStatus()
    }

    override suspend fun enableCharging(condition: String?): AccStatus? {
        AccStateManager.enableCharging(condition)
        return AccStateManager.refreshStatus()
    }

    override suspend fun forceFullCharge(capacity: Int): AccStatus? {
        AccStateManager.forceFullCharge(capacity)
        return AccStateManager.refreshStatus()
    }

    override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus? {
        AccStateManager.cancelChargeAction(mode)
        return AccStateManager.refreshStatus()
    }
}

private fun AccStatus?.toUiState(context: Context, daemonBusy: Boolean = false): OverviewUiState {
    if (this == null) {
        return OverviewUiState(
            isLoading = false,
            daemonBusy = daemonBusy,
            statusHeadline = context.getString(R.string.overview_status_unavailable),
            primaryActions = listOf(OverviewAction("refresh", context.getString(R.string.overview_action_refresh))),
            warnings = listOf(context.getString(R.string.overview_warning_unavailable))
        )
    }

    val headline = when (installState) {
        AccInstallState.NOT_INSTALLED -> context.getString(R.string.overview_headline_not_installed)
        AccInstallState.BROKEN_INSTALL -> context.getString(R.string.overview_headline_broken)
        AccInstallState.UPDATE_AVAILABLE -> {
            if (daemonRunning) context.getString(R.string.overview_headline_running_update)
            else context.getString(R.string.overview_headline_stopped)
        }
        AccInstallState.UP_TO_DATE -> {
            if (daemonRunning) context.getString(R.string.overview_headline_running)
            else context.getString(R.string.overview_headline_stopped)
        }
    }

    val facts = buildList {
        add(
            OverviewFact(
                label = context.getString(R.string.overview_fact_daemon),
                value = if (daemonRunning) {
                    context.getString(R.string.tools_value_running)
                } else {
                    context.getString(R.string.tools_value_stopped)
                },
                actionId = if (canManageDaemon) "toggle_daemon" else null,
                actionValue = if (canManageDaemon) daemonRunning else null
            )
        )
        add(OverviewFact(context.getString(R.string.overview_fact_install_state), installState.label(context)))
        installedVersionName?.takeIf { it.isNotBlank() }?.let { version ->
            add(OverviewFact(context.getString(R.string.overview_fact_version), version))
        }
    }

    val actions = buildList {
        add(OverviewAction("refresh", context.getString(R.string.overview_action_refresh)))
        if (canManageDaemon && !daemonRunning) {
            add(OverviewAction("start", context.getString(R.string.overview_action_start)))
        }
        add(
            when (installState) {
                AccInstallState.NOT_INSTALLED,
                AccInstallState.BROKEN_INSTALL -> OverviewAction("tools", context.getString(R.string.overview_action_open_tools))
                AccInstallState.UPDATE_AVAILABLE,
                AccInstallState.UP_TO_DATE -> OverviewAction("configuration", context.getString(R.string.overview_action_open_configuration))
            }
        )
    }

    val warnings = buildList {
        if (installState == AccInstallState.UPDATE_AVAILABLE) {
            add(context.getString(R.string.overview_warning_update_available))
        }
        if (installState == AccInstallState.BROKEN_INSTALL) {
            add(context.getString(R.string.overview_warning_repair_required))
        }
        lastError?.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    val chargingFactsList = chargingInfo?.let { info ->
        buildList {
            info.level?.formatBatteryPercent()?.let {
                add(OverviewFact(context.getString(R.string.battery_level), it))
            }
            info.status?.formatBatteryStatus(context)?.let {
                add(OverviewFact(context.getString(R.string.battery_charging_status), it))
            }
            info.temp?.formatBatteryTemperature()?.let {
                add(OverviewFact(context.getString(R.string.battery_temperature), it))
            }
            info.current?.formatBatteryCurrent()?.let {
                add(OverviewFact(context.getString(R.string.battery_current), it))
            }
            info.voltage?.formatBatteryVoltage()?.let {
                add(OverviewFact(context.getString(R.string.battery_voltage), it))
            }
            info.power?.formatBatteryPower()?.let {
                add(OverviewFact(context.getString(R.string.battery_power), it))
            }
            // Handshake fields and the charge type are only meaningful while a power source is
            // connected; when unplugged the USB driver keeps stale values, so hide them.
            if (info.powerConnected == true) {
                info.chargeType?.let { type ->
                    add(OverviewFact(context.getString(R.string.battery_charge_type), formatChargeType(type, context)))
                }
                info.protocol?.let {
                    add(OverviewFact(context.getString(R.string.battery_protocol), it))
                }
                info.pdActive?.let { active ->
                    add(
                        OverviewFact(
                            context.getString(R.string.battery_pd_active),
                            context.getString(if (active) R.string.battery_yes else R.string.battery_no)
                        )
                    )
                }
                info.negotiatedCurrent?.let {
                    add(OverviewFact(context.getString(R.string.battery_negotiated_current), "$it mA"))
                }
                info.negotiatedVoltage?.let {
                    add(OverviewFact(context.getString(R.string.battery_negotiated_voltage), "$it mV"))
                }
                info.negotiatedPower?.let {
                    add(OverviewFact(context.getString(R.string.battery_negotiated_power), it))
                }
                info.ccMode?.let {
                    add(OverviewFact(context.getString(R.string.battery_cc_mode), it))
                }
            }
        }
    } ?: emptyList()

    return OverviewUiState(
        isLoading = false,
        daemonBusy = daemonBusy,
        statusHeadline = headline,
        runtimeFacts = facts,
        chargingFacts = chargingFactsList,
        primaryActions = actions,
        warnings = warnings
    )
}

private fun formatChargeType(raw: String, context: Context): String = when (raw) {
    "pc_port" -> context.getString(R.string.battery_charge_type_pc_port)
    "usb" -> context.getString(R.string.battery_charge_type_usb)
    "dc" -> context.getString(R.string.battery_charge_type_dc)
    else -> context.getString(R.string.battery_charge_type_unknown)
}

private fun AccInstallState.label(context: Context): String = when (this) {
    AccInstallState.NOT_INSTALLED -> context.getString(R.string.overview_install_state_not_installed)
    AccInstallState.BROKEN_INSTALL -> context.getString(R.string.overview_install_state_broken)
    AccInstallState.UPDATE_AVAILABLE -> context.getString(R.string.overview_install_state_update)
    AccInstallState.UP_TO_DATE -> context.getString(R.string.overview_install_state_up_to_date)
}

private fun String.formatBatteryPercent(): String? =
    toDoubleOrNull()?.let { "${trimTrailingZeros(it)}%" }

private fun String.formatBatteryTemperature(): String? =
    toDoubleOrNull()?.let { value ->
        val celsius = if (kotlin.math.abs(value) >= 100) value / 10.0 else value
        "${formatDecimal(celsius, 1)}°C"
    }

private fun String.formatBatteryCurrent(): String? =
    toDoubleOrNull()?.let { value ->
        val abs = kotlin.math.abs(value)
        when {
            abs >= 1_000_000 -> "${formatDecimal(value / 1_000_000.0, 2)} A"
            abs >= 1_000 -> "${trimTrailingZeros(value / 1000.0)} mA"
            else -> "${trimTrailingZeros(value)} µA"
        }
    }

private fun String.formatBatteryVoltage(): String? =
    toDoubleOrNull()?.let { value ->
        // Android's EXTRA_VOLTAGE is always reported in millivolts (3000-5000 on real devices),
        // and official ACC does the same. There is no microvolts/volts encoding to branch on,
        // so treat the value as millivolts directly.
        "${trimTrailingZeros(value)} mV"
    }

private fun String.formatBatteryPower(): String? =
    toDoubleOrNull()?.let { microwatts ->
        val watts = microwatts / 1_000_000.0
        "${formatDecimal(watts, 2)} W"
    }

private fun String.formatBatteryStatus(context: Context): String? = when (trim().lowercase(Locale.US)) {
    "charging" -> context.getString(R.string.battery_status_charging)
    "discharging" -> context.getString(R.string.battery_status_discharging)
    "full" -> context.getString(R.string.battery_status_full)
    "not_charging", "not charging" -> context.getString(R.string.battery_status_not_charging)
    "unknown" -> context.getString(R.string.battery_status_unknown)
    else -> takeIf { it.isNotBlank() }
}

private fun trimTrailingZeros(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else formatDecimal(value, 1)

private fun formatDecimal(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
