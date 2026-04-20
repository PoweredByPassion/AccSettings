package crazyboyfeng.accSettings.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccCapability
import crazyboyfeng.accSettings.acc.AccInstallState
import crazyboyfeng.accSettings.acc.AccStateManager
import crazyboyfeng.accSettings.acc.AccStatus
import crazyboyfeng.accSettings.acc.LifecycleDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToolsSnapshot(
    val status: AccStatus?,
    val capability: AccCapability?,
    val appVersion: String,
    val bundledAccVersion: String
)

interface ToolsRepository {
    suspend fun loadSnapshot(): ToolsSnapshot
    suspend fun installOrUpdate(): String
    suspend fun repair(): String
    suspend fun restartService(): String
    suspend fun forceRedetect(): String
}

class ToolsViewModel(
    private val toolsRepository: ToolsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isBusy = true)
        _uiState.value = runCatching {
            toolsRepository.loadSnapshot().toUiState(
                previousMessage = _uiState.value.lastMessage,
                pendingConfirmation = _uiState.value.pendingConfirmation
            )
        }.getOrElse { error ->
            _uiState.value.copy(
                isBusy = false,
                lastMessage = error.localizedMessage ?: "Unable to refresh tools state"
            )
        }
    }

    fun requestAction(action: ToolAction) {
        val requiresConfirmation = currentActions()
            .firstOrNull { it.action == action }
            ?.requiresConfirmation
            ?: false

        if (requiresConfirmation) {
            _uiState.value = _uiState.value.copy(pendingConfirmation = action)
        } else {
            perform(action)
        }
    }

    fun dismissConfirmation() {
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
    }

    fun confirmPendingAction() {
        val action = _uiState.value.pendingConfirmation ?: return
        _uiState.value = _uiState.value.copy(pendingConfirmation = null)
        perform(action)
    }

    fun repair(): Job = perform(ToolAction.REPAIR)

    private fun perform(action: ToolAction): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isBusy = true, lastMessage = null)
        val actionMessage = runCatching {
            when (action) {
                ToolAction.INSTALL_OR_UPDATE -> toolsRepository.installOrUpdate()
                ToolAction.REPAIR -> toolsRepository.repair()
                ToolAction.RESTART_SERVICE -> toolsRepository.restartService()
                ToolAction.FORCE_REDETECT -> toolsRepository.forceRedetect()
                ToolAction.REFRESH -> null
            }
        }.getOrElse { error ->
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                lastMessage = error.localizedMessage ?: "Action failed"
            )
            return@launch
        }

        _uiState.value = runCatching {
            toolsRepository.loadSnapshot().toUiState(previousMessage = actionMessage)
        }.getOrElse { error ->
            _uiState.value.copy(
                isBusy = false,
                lastMessage = error.localizedMessage ?: actionMessage ?: "Unable to refresh tools state"
            )
        }
    }

    private fun currentActions(): List<ToolActionState> = buildList {
        addAll(_uiState.value.installSection.actions)
        addAll(_uiState.value.serviceSection.actions)
        addAll(_uiState.value.diagnosticsSection.actions)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ToolsViewModel(
                    toolsRepository = LiveToolsRepository(context.applicationContext)
                ) as T
            }
        }
    }
}

private class LiveToolsRepository(
    private val context: Context
) : ToolsRepository {
    override suspend fun loadSnapshot(): ToolsSnapshot = withContext(Dispatchers.IO) {
        ToolsSnapshot(
            status = AccStateManager.refreshStatus(),
            capability = AccStateManager.probeCapabilities(),
            appVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "unknown",
            bundledAccVersion = context.getString(R.string.acc_version_name)
        )
    }

    override suspend fun installOrUpdate(): String = withContext(Dispatchers.IO) {
        when (AccStateManager.ensureInstalled().decision) {
            LifecycleDecision.INSTALL -> "ACC installed successfully"
            LifecycleDecision.UPGRADE -> "ACC updated successfully"
            LifecycleDecision.NO_OP -> "ACC is already up to date"
            else -> "ACC install state refreshed"
        }
    }

    override suspend fun repair(): String = withContext(Dispatchers.IO) {
        AccStateManager.repair()
        "ACC repaired successfully"
    }

    override suspend fun restartService(): String = withContext(Dispatchers.IO) {
        val success = AccStateManager.setDaemonRunning(true)
        if (success) {
            "Service restarted successfully"
        } else {
            "Service restart did not complete"
        }
    }

    override suspend fun forceRedetect(): String = withContext(Dispatchers.IO) {
        AccStateManager.reinitialize()
        "ACC state re-detected"
    }
}

private fun ToolsSnapshot.toUiState(
    previousMessage: String? = null,
    pendingConfirmation: ToolAction? = null
): ToolsUiState {
    val installSummary = when (status?.installState) {
        AccInstallState.NOT_INSTALLED -> "Bundled ACC is available but not installed."
        AccInstallState.BROKEN_INSTALL -> "ACC is present but needs repair."
        AccInstallState.UPDATE_AVAILABLE -> "An installed ACC version can be updated from the bundled package."
        AccInstallState.UP_TO_DATE -> "Installed ACC matches the bundled package."
        null -> "ACC install state is unavailable."
    }
    val installActions = buildList {
        add(
            ToolActionState(
                action = ToolAction.INSTALL_OR_UPDATE,
                label = if (status?.installState == AccInstallState.UPDATE_AVAILABLE) "Update ACC" else "Install ACC",
                description = "Use the bundled ACC package for install or upgrade.",
                enabled = capability?.staticAvailability?.canInstallBundledAcc ?: true,
                requiresConfirmation = true
            )
        )
        if (capability?.staticAvailability?.canRepairAcc == true ||
            status?.installState == AccInstallState.BROKEN_INSTALL
        ) {
            add(
                ToolActionState(
                    action = ToolAction.REPAIR,
                    label = "Repair install",
                    description = "Recover a broken ACC setup and refresh runtime wiring.",
                    enabled = true,
                    requiresConfirmation = true
                )
            )
        }
    }
    val serviceRunning = status?.daemonRunning == true
    val serviceActions = listOf(
        ToolActionState(
            action = ToolAction.RESTART_SERVICE,
            label = if (serviceRunning) "Restart service" else "Start service",
            description = "Bring the ACC daemon back online.",
            enabled = status?.canManageDaemon == true,
            requiresConfirmation = serviceRunning
        )
    )
    val diagnosticsActions = listOf(
        ToolActionState(
            action = ToolAction.FORCE_REDETECT,
            label = "Force re-detect",
            description = "Re-scan runtime state and refresh capability details."
        ),
        ToolActionState(
            action = ToolAction.REFRESH,
            label = "Refresh details",
            description = "Reload the latest maintenance and diagnostics facts."
        )
    )

    return ToolsUiState(
        installSection = ToolSection(
            title = "Install and Repair",
            summary = installSummary,
            details = listOf(
                ToolDetail("Installed ACC", status?.installedVersionName ?: "Not installed"),
                ToolDetail("Bundled ACC", bundledAccVersion)
            ),
            actions = installActions
        ),
        serviceSection = ToolSection(
            title = "Service Control",
            summary = if (serviceRunning) "The ACC daemon is currently running." else "The ACC daemon is currently stopped.",
            details = listOf(
                ToolDetail("Service status", if (serviceRunning) "Running" else "Stopped"),
                ToolDetail("Manual control", if (status?.canManageDaemon == true) "Available" else "Unavailable")
            ),
            actions = serviceActions
        ),
        diagnosticsSection = ToolSection(
            title = "Diagnostics",
            summary = "Inspect current ACC access and refresh device state when needed.",
            details = listOf(
                ToolDetail("Root access", if (capability?.staticAvailability?.hasRoot == true) "Available" else "Unavailable"),
                ToolDetail("Detected entrypoint", capability?.staticAvailability?.selectedEntrypoint ?: "Not detected"),
                ToolDetail("Runtime info", if (capability?.runtimeCapability?.canReadInfo == true) "Readable" else "Unavailable")
            ),
            actions = diagnosticsActions
        ),
        appInfoSection = ToolSection(
            title = "App and Version",
            summary = "Version details for the app and bundled ACC package.",
            details = listOf(
                ToolDetail("App version", appVersion),
                ToolDetail("Bundled ACC", bundledAccVersion),
                ToolDetail("Installed ACC", status?.installedVersionName ?: "Not installed")
            )
        ),
        isBusy = false,
        lastMessage = previousMessage,
        pendingConfirmation = pendingConfirmation
    )
}
