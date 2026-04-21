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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface OverviewRepository {
    suspend fun loadStatus(): AccStatus?
    suspend fun startService(): AccStatus?
}

class OverviewViewModel(
    private val context: Context,
    private val overviewRepository: OverviewRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    fun refresh(): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val status = overviewRepository.loadStatus()
        _uiState.value = status.toUiState(context)
    }

    fun startService(): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val status = overviewRepository.startService()
        _uiState.value = status.toUiState(context)
    }

    companion object {
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
}

private fun AccStatus?.toUiState(context: Context): OverviewUiState {
    if (this == null) {
        return OverviewUiState(
            isLoading = false,
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
        add(OverviewFact(context.getString(R.string.overview_fact_install_state), installState.label(context)))
        add(OverviewFact(context.getString(R.string.overview_fact_daemon), if (daemonRunning) {
            context.getString(R.string.tools_value_running)
        } else {
            context.getString(R.string.tools_value_stopped)
        }))
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
    }

    return OverviewUiState(
        isLoading = false,
        statusHeadline = headline,
        runtimeFacts = facts,
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
