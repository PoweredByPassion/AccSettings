package app.owlow.accsettings.ui.overview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsettings.R
import app.owlow.accsettings.acc.AccInstallState
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.AccStatus
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
}

class OverviewViewModel(
    private val context: Context,
    private val overviewRepository: OverviewRepository
) : ViewModel() {
    private var autoRefreshJob: Job? = null

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    fun refresh(): Job = viewModelScope.launch {
        reloadStatus(showLoading = true)
    }

    fun startService(): Job = viewModelScope.launch {
        // NOTE: no full-screen spinner on service start; keep the UI interactive while ACC starts.
        // A focused `daemonBusy` flag gives the daemon control busy feedback while the root command runs.
        _uiState.value = _uiState.value.copy(daemonBusy = true)
        runCatching { overviewRepository.startService() }
            .onSuccess { status -> _uiState.value = status.toUiState(context).copy(daemonBusy = false) }
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
            .onSuccess { status -> _uiState.value = status.toUiState(context).copy(daemonBusy = false) }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    daemonBusy = false,
                    warnings = _uiState.value.warnings + (error.localizedMessage ?: "Failed to toggle daemon")
                )
            }
    }

    fun startAutoRefresh(intervalMs: Long = BATTERY_REFRESH_INTERVAL_MS) {
        if (autoRefreshJob?.isActive == true) {
            return
        }
        autoRefreshJob = viewModelScope.launch {
            reloadStatus(showLoading = _uiState.value.runtimeFacts.isEmpty() && _uiState.value.batteryFacts.isEmpty())
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
        _uiState.value = status.toUiState(context, daemonBusy = _uiState.value.daemonBusy)
    }

    override fun onCleared() {
        stopAutoRefresh()
        super.onCleared()
    }

    companion object {
        private const val BATTERY_REFRESH_INTERVAL_MS = 15_000L

        fun factory(
            context: Context,
            overviewRepository: OverviewRepository = LiveOverviewRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OverviewViewModel(context.applicationContext, overviewRepository) as T
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

    val batteryFactsList = chargingInfo?.let { info ->
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
        }
    } ?: emptyList()

    return OverviewUiState(
        isLoading = false,
        daemonBusy = daemonBusy,
        statusHeadline = headline,
        runtimeFacts = facts,
        batteryFacts = batteryFactsList,
        primaryActions = actions,
        warnings = warnings
    )
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
