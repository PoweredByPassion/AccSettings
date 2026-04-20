package app.owlow.accsetting.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsetting.acc.AccInstallState
import app.owlow.accsetting.acc.AccStateManager
import app.owlow.accsetting.acc.AccStatus
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
    private val overviewRepository: OverviewRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    fun refresh(): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val status = overviewRepository.loadStatus()
        _uiState.value = status.toUiState()
    }

    fun startService(): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val status = overviewRepository.startService()
        _uiState.value = status.toUiState()
    }

    companion object {
        fun factory(
            overviewRepository: OverviewRepository = LiveOverviewRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OverviewViewModel(overviewRepository) as T
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

private fun AccStatus?.toUiState(): OverviewUiState {
    if (this == null) {
        return OverviewUiState(
            isLoading = false,
            statusHeadline = "ACC status is unavailable",
            primaryActions = listOf(OverviewAction("refresh", "Refresh state")),
            warnings = listOf("Unable to read ACC status.")
        )
    }

    val headline = when (installState) {
        AccInstallState.NOT_INSTALLED -> "ACC is not installed"
        AccInstallState.BROKEN_INSTALL -> "ACC needs repair"
        AccInstallState.UPDATE_AVAILABLE -> {
            if (daemonRunning) "ACC is running with an update available"
            else "ACC is installed but stopped"
        }
        AccInstallState.UP_TO_DATE -> {
            if (daemonRunning) "ACC is running"
            else "ACC is installed but stopped"
        }
    }

    val facts = buildList {
        add(OverviewFact("Install state", installState.name.lowercase().replace('_', ' ')))
        add(OverviewFact("Daemon", if (daemonRunning) "Running" else "Stopped"))
        installedVersionName?.takeIf { it.isNotBlank() }?.let { version ->
            add(OverviewFact("Version", version))
        }
    }

    val actions = buildList {
        add(OverviewAction("refresh", "Refresh state"))
        if (canManageDaemon && !daemonRunning) {
            add(OverviewAction("start", "Start service"))
        }
        add(
            when (installState) {
                AccInstallState.NOT_INSTALLED,
                AccInstallState.BROKEN_INSTALL -> OverviewAction("tools", "Open tools")
                AccInstallState.UPDATE_AVAILABLE,
                AccInstallState.UP_TO_DATE -> OverviewAction("configuration", "Open configuration")
            }
        )
    }

    val warnings = buildList {
        if (installState == AccInstallState.UPDATE_AVAILABLE) {
            add("A newer bundled ACC version is available.")
        }
        if (installState == AccInstallState.BROKEN_INSTALL) {
            add("Repair is required before normal control can resume.")
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
