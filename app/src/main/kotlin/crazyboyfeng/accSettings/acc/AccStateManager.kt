package crazyboyfeng.accSettings.acc

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import crazyboyfeng.accSettings.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AccSettingsSummary {
    NOT_INSTALLED,
    BROKEN_INSTALL,
    UPDATE_AVAILABLE,
    UP_TO_DATE
}

data class AccSettingsState(
    val summary: AccSettingsSummary,
    val daemonEnabled: Boolean,
    val daemonRunning: Boolean,
    val configEnabled: Boolean,
    val shouldServe: Boolean,
    val shouldScheduleFollowUpRefresh: Boolean
)

object AccStateManager {
    private const val TAG = "AccStateManager"
    private const val POLLING_INTERVAL_MS = 5000L

    private val _accStatus = MutableStateFlow<AccStatus?>(null)
    val accStatus: StateFlow<AccStatus?> = _accStatus.asStateFlow()

    private var monitoringJob: Job? = null
    private var isMonitoring = false
    private var appContext: Context? = null
    private var bridgeFactoryOverride: (() -> AccBridge)? = null

    fun startMonitoring(context: Context) {
        if (isMonitoring) {
            logDebug("Monitoring already started")
            return
        }

        appContext = context.applicationContext
        isMonitoring = true
        monitoringJob = CoroutineScope(Dispatchers.Default).launch {
            refreshNow()
            while (isActive) {
                delay(POLLING_INTERVAL_MS)
                refreshNow()
            }
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        monitoringJob?.cancel()
        monitoringJob = null
        isMonitoring = false
    }

    suspend fun refreshNow() {
        try {
            val status = bridge().readStatus()
            _accStatus.value = status
            logDebug("ACC status updated: installState=${status.installState}, daemonRunning=${status.daemonRunning}")
        } catch (e: Exception) {
            logError("Failed to refresh ACC status", e)
        }
    }

    suspend fun refreshStatus(): AccStatus? {
        refreshNow()
        return _accStatus.value
    }

    suspend fun setDaemonRunning(daemonRunning: Boolean): Boolean {
        val result = bridge().setDaemonRunning(daemonRunning)
        refreshNow()
        return result.success
    }

    suspend fun ensureInstalled(): LifecycleActionResult {
        val result = bridge().ensureInstalled()
        refreshNow()
        return result
    }

    suspend fun repair(): LifecycleActionResult {
        val result = bridge().repair()
        refreshNow()
        return result
    }

    suspend fun uninstall(): LifecycleActionResult {
        val result = bridge().uninstall()
        refreshNow()
        return result
    }

    suspend fun reinitialize(): LifecycleActionResult {
        val result = bridge().reinitialize()
        refreshNow()
        return result
    }

    suspend fun probeCapabilities(): AccCapability = bridge().probeCapabilities()

    fun getCurrentStatus(): AccStatus? = _accStatus.value

    fun isDaemonRunning(): Boolean = _accStatus.value?.daemonRunning ?: false

    fun toSettingsState(status: AccStatus): AccSettingsState = when (status.installState) {
        AccInstallState.NOT_INSTALLED -> AccSettingsState(
            summary = AccSettingsSummary.NOT_INSTALLED,
            daemonEnabled = false,
            daemonRunning = false,
            configEnabled = false,
            shouldServe = false,
            shouldScheduleFollowUpRefresh = true
        )
        AccInstallState.BROKEN_INSTALL -> AccSettingsState(
            summary = AccSettingsSummary.BROKEN_INSTALL,
            daemonEnabled = false,
            daemonRunning = false,
            configEnabled = false,
            shouldServe = false,
            shouldScheduleFollowUpRefresh = false
        )
        AccInstallState.UPDATE_AVAILABLE -> AccSettingsState(
            summary = AccSettingsSummary.UPDATE_AVAILABLE,
            daemonEnabled = status.canManageDaemon,
            daemonRunning = status.daemonRunning,
            configEnabled = true,
            shouldServe = true,
            shouldScheduleFollowUpRefresh = false
        )
        AccInstallState.UP_TO_DATE -> AccSettingsState(
            summary = AccSettingsSummary.UP_TO_DATE,
            daemonEnabled = status.canManageDaemon,
            daemonRunning = status.daemonRunning,
            configEnabled = true,
            shouldServe = true,
            shouldScheduleFollowUpRefresh = false
        )
    }

    fun cleanup() {
        stopMonitoring()
        _accStatus.value = null
        appContext = null
        bridgeFactoryOverride = null
    }

    internal fun resetForTesting(bridgeFactory: (() -> AccBridge)? = null) {
        cleanup()
        bridgeFactoryOverride = bridgeFactory
    }

    private fun bridge(): AccBridge {
        bridgeFactoryOverride?.let { return it() }
        val context = requireNotNull(appContext) { "AccStateManager requires an application context" }
        return buildBridge(context)
    }

    private fun buildBridge(context: Context): AccBridge {
        val handler = AccHandler()
        val capabilityProbe = AccCapabilityProbe {
            collectProbeFacts()
        }
        return AccBridge(
            capabilityProbe = { capabilityProbe.snapshot() },
            versionReader = { Command.getVersion() },
            daemonReader = { Command.isDaemonRunning() },
            currentConfigReader = { Command.getCurrentConfig() },
            defaultConfigReader = { Command.getDefaultConfig() },
            installAction = { handler.install(context) },
            upgradeAction = { handler.upgrade(context) },
            repairAction = { handler.repair() },
            uninstallAction = { handler.uninstall() },
            daemonToggleAction = { enabled ->
                handler.setDaemonRunning(enabled)
                true
            },
            reinitializeAction = { handler.reinitialize() },
            lifecycleCapabilityRefresh = { capabilityProbe.refresh(ProbeRefreshReason.RECHECK) },
            bundledVersionCodeProvider = { context.resources.getInteger(R.integer.acc_version_code) }
        )
    }

    private suspend fun collectProbeFacts(): AccProbeFacts {
        val hasRoot = try {
            Shell.rootAccess()
        } catch (_: Exception) {
            false
        }
        val availableEntrypoints = if (hasRoot) {
            Command.listAccExecutables(::pathExists)
        } else {
            emptyList()
        }
        val selectedEntrypoint = availableEntrypoints.firstOrNull()
        val (versionCode, versionName) = if (hasRoot) {
            try {
                Command.getVersion()
            } catch (_: Exception) {
                0 to null
            }
        } else {
            0 to null
        }
        val daemonRunning = if (hasRoot) {
            try {
                Command.isDaemonRunning()
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        return AccProbeFacts(
            hasRoot = hasRoot,
            availableEntrypoints = availableEntrypoints,
            selectedEntrypoint = selectedEntrypoint,
            accVersionName = versionName,
            accVersionCode = versionCode,
            daemonRunning = daemonRunning,
            canReadInfo = selectedEntrypoint != null,
            canReadCurrentConfig = selectedEntrypoint != null,
            canReadDefaultConfig = selectedEntrypoint != null,
            canReadLogs = hasRoot,
            canExportDiagnostics = hasRoot,
            supportedChargingSwitches = emptyList(),
            preferredChargingSwitch = null,
            supportsCurrentControl = false,
            supportsVoltageControl = false,
            supportedCapacityModes = setOf(CapacityMode.PERCENT),
            supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
        )
    }

    private fun pathExists(path: String): Boolean {
        val shell = Shell.getShell()
        if (!shell.isRoot) {
            return false
        }
        val escaped = path.replace("\"", "\\\"")
        return shell.newJob()
            .add("test -f \"$escaped\"")
            .to(mutableListOf(), mutableListOf())
            .exec()
            .isSuccess
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logError(message: String, throwable: Throwable) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
