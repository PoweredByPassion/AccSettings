package app.owlow.accsettings.acc

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.topjohnwu.superuser.Shell
import app.owlow.accsettings.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AccSettingSummary {
    NOT_INSTALLED,
    BROKEN_INSTALL,
    UPDATE_AVAILABLE,
    UP_TO_DATE
}

data class AccSettingState(
    val summary: AccSettingSummary,
    val daemonEnabled: Boolean,
    val daemonRunning: Boolean,
    val configEnabled: Boolean,
    val shouldServe: Boolean,
    val shouldScheduleFollowUpRefresh: Boolean
)

object AccStateManager {
    private const val TAG = "AccStateManager"

    private val _accStatus = MutableStateFlow<AccStatus?>(null)
    val accStatus: StateFlow<AccStatus?> = _accStatus.asStateFlow()

    private var isMonitoring = false
    private var appContext: Context? = null
    private var bridgeFactoryOverride: (() -> AccBridge)? = null
    private var cachedBridge: AccBridge? = null

    fun startMonitoring(context: Context) {
        if (isMonitoring) {
            logDebug("Monitoring already started")
            return
        }

        appContext = context.applicationContext
        isMonitoring = true
        // NOTE: The continuous monitoring job is disabled to reduce background CPU usage (from ~60% to near 0%).
        // ViewModels and other active components should call refreshStatus() or use the UI-triggered polling.
    }

    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        isMonitoring = false
    }

    suspend fun refreshNow() {
        try {
            val status = bridge().readStatus()
            _accStatus.value = status.copy(lastError = null)
            logDebug("ACC status updated: installState=${status.installState}, daemonRunning=${status.daemonRunning}")
        } catch (e: Exception) {
            logError("Failed to refresh ACC status", e)
            val errorMessage = e.toUserFacingMessage()
            val notInstalled = e is Command.NotInstalledException
            _accStatus.value = _accStatus.value?.copy(lastError = errorMessage)
                ?: AccStatus(
                    installState = if (notInstalled) AccInstallState.NOT_INSTALLED else AccInstallState.UP_TO_DATE,
                    installedVersionName = null,
                    daemonRunning = false,
                    canManageDaemon = false,
                    showInstallAction = notInstalled,
                    showUninstallAction = false,
                    lastError = errorMessage
                )
        }
    }

    private fun Throwable.toUserFacingMessage(): String = when (this) {
        is Command.NotInstalledException -> "ACC is not installed"
        is Command.NotRootException -> "Root permission required"
        is Command.AccException -> "ACC command failed"
        else -> localizedMessage ?: "Failed to refresh ACC status"
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

    fun toSettingsState(status: AccStatus): AccSettingState = when (status.installState) {
        AccInstallState.NOT_INSTALLED -> AccSettingState(
            summary = AccSettingSummary.NOT_INSTALLED,
            daemonEnabled = false,
            daemonRunning = false,
            configEnabled = false,
            shouldServe = false,
            shouldScheduleFollowUpRefresh = true
        )
        AccInstallState.BROKEN_INSTALL -> AccSettingState(
            summary = AccSettingSummary.BROKEN_INSTALL,
            daemonEnabled = false,
            daemonRunning = false,
            configEnabled = false,
            shouldServe = false,
            shouldScheduleFollowUpRefresh = false
        )
        AccInstallState.UPDATE_AVAILABLE -> AccSettingState(
            summary = AccSettingSummary.UPDATE_AVAILABLE,
            daemonEnabled = status.canManageDaemon,
            daemonRunning = status.daemonRunning,
            configEnabled = true,
            shouldServe = true,
            shouldScheduleFollowUpRefresh = false
        )
        AccInstallState.UP_TO_DATE -> AccSettingState(
            summary = AccSettingSummary.UP_TO_DATE,
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
        cachedBridge = null
    }

    internal fun resetForTesting(bridgeFactory: (() -> AccBridge)? = null) {
        cleanup()
        bridgeFactoryOverride = bridgeFactory
    }

    private fun bridge(): AccBridge {
        bridgeFactoryOverride?.let { return it() }
        cachedBridge?.let { return it }
        val context = requireNotNull(appContext) { "AccStateManager requires an application context" }
        return buildBridge(context).also { cachedBridge = it }
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
            chargingInfoReader = { fetchChargingInfo() },
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

    private suspend fun fetchChargingInfo(): ChargingInfo? {
        val context = appContext ?: return null
        val root = try {
            Shell.rootAccess()
        } catch (_: Exception) {
            false
        }
        if (root) {
            val accInfo = runCatching { Command.getInfoRaw() }.getOrNull()
            val base = accInfo?.takeIf { it.isNotBlank() }?.let { ChargingInfoParser.parseAccInfo(it) }
            if (base != null) {
                val handshake = SysfsChargingReader.read(::readSysfsNode)
                return ChargingInfoParser.mergeChargingInfo(base, handshake)
            }
        }
        // Fallback: system API for base fields, handshake stays null.
        return readSystemBatteryInfo(context)?.toChargingInfo()
    }

    private suspend fun readSysfsNode(path: String): String? =
        runCatching { Command.exec("cat \"$path\"").ifBlank { "" } }.getOrNull()

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
        val supportedChargingSwitches = if (hasRoot) {
            try {
                Command.listChargingSwitches()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        val supportsCurrentControl = if (hasRoot) {
            try {
                Command.readMaxChargingCurrent() != null
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val supportsVoltageControl = if (hasRoot) {
            try {
                Command.readMaxChargingVoltage() != null
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
            supportedChargingSwitches = supportedChargingSwitches,
            preferredChargingSwitch = supportedChargingSwitches.firstOrNull(),
            supportsCurrentControl = supportsCurrentControl,
            supportsVoltageControl = supportsVoltageControl,
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

    private data class SystemBatteryInfo(
        val level: String?,
        val status: String?,
        val temperature: String?,
        val current: String?,
        val voltage: String?,
        val power: String?
    )

    private fun readSystemBatteryInfo(context: Context): SystemBatteryInfo? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val batteryManager = context.getSystemService(BatteryManager::class.java)

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val levelPercent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toString()
        } else {
            null
        }

        val status = when (batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> null
        }

        val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
            ?.toString()

        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
            ?.toString()

        val currentMicroamps = batteryManager
            ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Long.MIN_VALUE && it != 0L }

        val powerMicrowatts = if (currentMicroamps != null && voltage != null) {
            voltage.toLongOrNull()?.let { voltageMillivolts ->
                (currentMicroamps * voltageMillivolts) / 1000L
            }
        } else {
            null
        }

        return SystemBatteryInfo(
            level = levelPercent,
            status = status,
            temperature = temperature,
            current = currentMicroamps?.toString(),
            voltage = voltage,
            power = powerMicrowatts?.toString()
        )
    }

    private fun SystemBatteryInfo.toChargingInfo(): ChargingInfo? = ChargingInfo(
        level = level,
        status = status,
        temp = temperature,
        current = current,
        voltage = voltage,
        power = power
    ).takeIf { chargingInfo ->
        listOf(
            chargingInfo.level,
            chargingInfo.status,
            chargingInfo.temp,
            chargingInfo.current,
            chargingInfo.voltage,
            chargingInfo.power
        ).any { !it.isNullOrBlank() }
    }
}
