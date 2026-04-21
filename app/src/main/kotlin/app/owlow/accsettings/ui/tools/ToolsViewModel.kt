package app.owlow.accsettings.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsettings.R
import app.owlow.accsettings.acc.AccCapability
import app.owlow.accsettings.acc.AccHandler
import app.owlow.accsettings.acc.AccInstallState
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.Command
import app.owlow.accsettings.acc.LifecycleDecision
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
    val bundledAccVersion: String,
    val runtimeLog: String,
    val packageName: String
)

interface ToolsRepository {
    suspend fun loadSnapshot(): ToolsSnapshot
    suspend fun installOrUpdate(): String
    suspend fun repair(): String
    suspend fun restartService(): String
    suspend fun forceRedetect(): String
}

class ToolsViewModel(
    private val context: Context,
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
                context = context,
                pendingConfirmation = _uiState.value.pendingConfirmation
            )
        }.getOrElse { error ->
            _uiState.value.copy(
                isBusy = false,
                diagnosticsSection = _uiState.value.diagnosticsSection.copy(
                    statusMessage = ToolStatusMessage(
                        message = error.toToolsMessage(context, R.string.tools_refresh_failed),
                        isError = true
                    )
                )
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
        _uiState.value = _uiState.value.copy(isBusy = true)
        val actionMessage = runCatching {
            when (action) {
                ToolAction.INSTALL_OR_UPDATE -> toolsRepository.installOrUpdate()
                ToolAction.REPAIR -> toolsRepository.repair()
                ToolAction.RESTART_SERVICE -> toolsRepository.restartService()
                ToolAction.FORCE_REDETECT -> toolsRepository.forceRedetect()
                ToolAction.REFRESH -> null
            }
        }.getOrElse { error ->
            val message = ToolStatusMessage(
                message = error.toToolsMessage(context, R.string.tools_action_failed),
                isError = true
            )
            _uiState.value = toolsRepository.loadSnapshot().toUiState(
                context = context,
                previousMessage = message,
                lastAction = action
            )
            return@launch
        }

        _uiState.value = runCatching {
            toolsRepository.loadSnapshot().toUiState(
                context = context,
                previousMessage = actionMessage?.let {
                    ToolStatusMessage(message = it, isError = false)
                },
                lastAction = action
            )
        }.getOrElse { error ->
            _uiState.value.copy(
                isBusy = false,
                diagnosticsSection = _uiState.value.diagnosticsSection.copy(
                    statusMessage = ToolStatusMessage(
                        message = error.toToolsMessage(
                            context = context,
                            fallbackRes = R.string.tools_refresh_failed,
                            fallbackText = actionMessage
                        ),
                        isError = true
                    )
                )
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
                    context = context.applicationContext,
                    toolsRepository = LiveToolsRepository(context.applicationContext)
                ) as T
            }
        }
    }
}

private class LiveToolsRepository(
    private val context: Context
) : ToolsRepository {
    private val handler = AccHandler()

    override suspend fun loadSnapshot(): ToolsSnapshot = withContext(Dispatchers.IO) {
        ToolsSnapshot(
            status = AccStateManager.refreshStatus(),
            capability = AccStateManager.probeCapabilities(),
            appVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: context.getString(R.string.tools_unknown),
            bundledAccVersion = context.getString(R.string.acc_version_name),
            runtimeLog = handler.readRuntimeLogs(),
            packageName = context.packageName
        )
    }

    override suspend fun installOrUpdate(): String = withContext(Dispatchers.IO) {
        when (AccStateManager.ensureInstalled().decision) {
            LifecycleDecision.INSTALL -> context.getString(R.string.tools_install_success)
            LifecycleDecision.UPGRADE -> context.getString(R.string.tools_update_success)
            LifecycleDecision.NO_OP -> context.getString(R.string.tools_already_up_to_date)
            else -> context.getString(R.string.tools_install_refreshed)
        }
    }

    override suspend fun repair(): String = withContext(Dispatchers.IO) {
        AccStateManager.repair()
        context.getString(R.string.tools_repair_success)
    }

    override suspend fun restartService(): String = withContext(Dispatchers.IO) {
        val success = AccStateManager.setDaemonRunning(true)
        if (success) {
            context.getString(R.string.tools_service_restart_success)
        } else {
            context.getString(R.string.tools_service_restart_incomplete)
        }
    }

    override suspend fun forceRedetect(): String = withContext(Dispatchers.IO) {
        AccStateManager.reinitialize()
        context.getString(R.string.tools_redetect_success)
    }
}

private fun ToolsSnapshot.toUiState(
    context: Context,
    previousMessage: ToolStatusMessage? = null,
    pendingConfirmation: ToolAction? = null,
    lastAction: ToolAction? = null
): ToolsUiState {
    val installSummary = when (status?.installState) {
        AccInstallState.NOT_INSTALLED -> context.getString(R.string.tools_install_summary_not_installed)
        AccInstallState.BROKEN_INSTALL -> context.getString(R.string.tools_install_summary_broken)
        AccInstallState.UPDATE_AVAILABLE -> context.getString(R.string.tools_install_summary_update)
        AccInstallState.UP_TO_DATE -> context.getString(R.string.tools_install_summary_ok)
        null -> context.getString(R.string.tools_install_summary_unknown)
    }
    val installActions = buildList {
        add(
            ToolActionState(
                action = ToolAction.INSTALL_OR_UPDATE,
                label = if (status?.installState == AccInstallState.UPDATE_AVAILABLE) {
                    context.getString(R.string.tools_action_update_acc)
                } else {
                    context.getString(R.string.tools_action_install_acc)
                },
                description = context.getString(R.string.tools_action_install_desc),
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
                    label = context.getString(R.string.tools_action_repair),
                    description = context.getString(R.string.tools_action_repair_desc),
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
            label = if (serviceRunning) context.getString(R.string.tools_action_restart_service) else context.getString(R.string.tools_action_start_service),
            description = context.getString(R.string.tools_action_service_desc),
            enabled = status?.canManageDaemon == true,
            requiresConfirmation = serviceRunning
        )
    )
    val diagnosticsActions = listOf(
        ToolActionState(
            action = ToolAction.FORCE_REDETECT,
            label = context.getString(R.string.tools_action_redetect),
            description = context.getString(R.string.tools_action_redetect_desc)
        ),
        ToolActionState(
            action = ToolAction.REFRESH,
            label = context.getString(R.string.tools_action_refresh),
            description = context.getString(R.string.tools_action_refresh_desc)
        )
    )

    return ToolsUiState(
        installSection = ToolSection(
            title = context.getString(R.string.tools_section_install_title),
            summary = installSummary,
            details = listOf(
                ToolDetail(context.getString(R.string.tools_detail_installed_acc), status?.installedVersionName ?: context.getString(R.string.tools_value_not_installed)),
                ToolDetail(context.getString(R.string.tools_detail_bundled_acc), bundledAccVersion)
            ),
            actions = installActions,
            statusMessage = if (lastAction == ToolAction.INSTALL_OR_UPDATE || lastAction == ToolAction.REPAIR) previousMessage else null
        ),
        serviceSection = ToolSection(
            title = context.getString(R.string.tools_section_service_title),
            summary = if (serviceRunning) context.getString(R.string.tools_section_service_running) else context.getString(R.string.tools_section_service_stopped),
            details = listOf(
                ToolDetail(context.getString(R.string.tools_detail_service_status), if (serviceRunning) context.getString(R.string.tools_value_running) else context.getString(R.string.tools_value_stopped)),
                ToolDetail(context.getString(R.string.tools_detail_manual_control), if (status?.canManageDaemon == true) context.getString(R.string.tools_value_available) else context.getString(R.string.tools_value_unavailable))
            ),
            actions = serviceActions,
            statusMessage = if (lastAction == ToolAction.RESTART_SERVICE) previousMessage else null
        ),
        diagnosticsSection = ToolSection(
            title = context.getString(R.string.tools_section_diagnostics_title),
            summary = context.getString(R.string.tools_section_diagnostics_summary),
            details = listOf(
                ToolDetail(context.getString(R.string.tools_detail_root_access), if (capability?.staticAvailability?.hasRoot == true) context.getString(R.string.tools_value_available) else context.getString(R.string.tools_value_unavailable)),
                ToolDetail(context.getString(R.string.tools_detail_entrypoint), capability?.staticAvailability?.selectedEntrypoint ?: context.getString(R.string.tools_value_not_detected)),
                ToolDetail(context.getString(R.string.tools_detail_runtime_info), if (capability?.runtimeCapability?.canReadInfo == true) context.getString(R.string.tools_value_readable) else context.getString(R.string.tools_value_unavailable))
            ),
            actions = diagnosticsActions,
            statusMessage = if (lastAction == ToolAction.FORCE_REDETECT || lastAction == ToolAction.REFRESH) previousMessage else null
        ),
        logsSection = ToolLogSection(
            title = context.getString(R.string.tools_section_logs_title),
            summary = context.getString(R.string.tools_section_logs_summary),
            content = runtimeLog
        ),
        appInfoSection = ToolSection(
            title = context.getString(R.string.tools_section_app_title),
            summary = context.getString(R.string.tools_section_app_summary),
            details = listOf(
                ToolDetail(context.getString(R.string.tools_detail_app_version), appVersion),
                ToolDetail(context.getString(R.string.tools_detail_bundled_acc), bundledAccVersion),
                ToolDetail(context.getString(R.string.tools_detail_installed_acc), status?.installedVersionName ?: context.getString(R.string.tools_value_not_installed)),
                ToolDetail(context.getString(R.string.tools_detail_package), packageName)
            )
        ),
        isBusy = false,
        pendingConfirmation = pendingConfirmation
    )
}

private fun Throwable.toToolsMessage(
    context: Context,
    fallbackRes: Int,
    fallbackText: String? = null
): String = when (this) {
    is Command.NotRootException -> context.getString(R.string.need_root_permission)
    is Command.NotInstalledException -> context.getString(R.string.tools_error_not_installed)
    is Command.NoBusyboxException,
    is Command.FailedException,
    is Command.AccException -> context.getString(R.string.command_failed)
    else -> localizedMessage?.takeIf { it.isNotBlank() }
        ?: fallbackText
        ?: context.getString(fallbackRes)
}
