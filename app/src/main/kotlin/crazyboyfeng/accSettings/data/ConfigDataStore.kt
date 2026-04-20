package crazyboyfeng.accSettings.data

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceDataStore
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccBridge
import crazyboyfeng.accSettings.acc.AccCapability
import crazyboyfeng.accSettings.acc.AccDraftState
import crazyboyfeng.accSettings.acc.AccProbeFacts
import crazyboyfeng.accSettings.acc.ApplyGroupedPatchRequest
import crazyboyfeng.accSettings.acc.ApplyGroupedPatchResult
import crazyboyfeng.accSettings.acc.CapacityConfig
import crazyboyfeng.accSettings.acc.CapacityMode
import crazyboyfeng.accSettings.acc.Command
import crazyboyfeng.accSettings.acc.ConfigGroupMode
import crazyboyfeng.accSettings.acc.DraftStatus
import crazyboyfeng.accSettings.acc.GroupApplyWriteResult
import crazyboyfeng.accSettings.acc.GroupedConfigRead
import crazyboyfeng.accSettings.acc.PatchGroup
import crazyboyfeng.accSettings.acc.TemperatureConfig
import crazyboyfeng.accSettings.acc.TemperatureMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Properties

open class ConfigDataStore internal constructor(
    private val supportInVoltageKey: String,
    private val currentConfigLoader: suspend () -> GroupedConfigRead,
    private val patchApplier: suspend (ApplyGroupedPatchRequest) -> ApplyGroupedPatchResult,
    private val daemonRestartAction: suspend () -> Unit,
    private val reinitializeAction: suspend () -> Unit,
    initialGroupedConfig: GroupedConfigRead? = null
) : PreferenceDataStore() {
    constructor(context: Context, initialGroupedConfig: GroupedConfigRead? = null) : this(
        supportInVoltageKey = context.getString(R.string.support_in_voltage),
        currentConfigLoader = { readGroupedConfigDirect() },
        patchApplier = { request: ApplyGroupedPatchRequest -> buildBridge().applyGroupedPatch(request) },
        daemonRestartAction = {
            Command.restartDaemon()
            Unit
        },
        reinitializeAction = {
            Command.reinitialize()
            Unit
        },
        initialGroupedConfig = initialGroupedConfig
    )

    private var supportInVoltage = false
    private var draftState: AccDraftState? = null
    private val configCache = mutableMapOf<String, String>()
    private val protectedGroupRebuilds = mutableSetOf<PatchGroup>()
    private val pendingSideEffects = linkedSetOf<PendingSideEffect>()

    init {
        initialGroupedConfig?.let {
            draftState = initializeDraftState(it)
            rebuildCache()
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        logVerbose("putBoolean: $key=$value")
        ensureDraftLoaded()
        when (key) {
            supportInVoltageKey -> {
                supportInVoltage = value
                configCache[key] = value.toString()
            }
            capacityMaskKey() -> {
                updateCapacity(maskAsFull = value)
            }
            else -> {
                updateProperty(key, value.toString())
            }
        }
        onConfigChangeListener?.onConfigChanged(key)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        logVerbose("getBoolean: $key=$defValue?")
        ensureDraftLoaded()
        return when (key) {
            supportInVoltageKey -> supportInVoltage
            else -> configCache[key]?.toBoolean() ?: defValue
        }
    }

    override fun putInt(key: String, value: Int) {
        logVerbose("putInt: $key=$value")
        ensureDraftLoaded()
        when (key) {
            shutdownCapacityKey() -> updateCapacity(shutdown = value)
            cooldownCapacityKey() -> updateCapacity(cooldown = value)
            resumeCapacityKey() -> updateCapacity(resume = value)
            pauseCapacityKey() -> updateCapacity(pause = value)
            cooldownTempKey() -> updateTemperature(cooldown = value)
            maxTempKey() -> updateTemperature(pause = value)
            shutdownTempKey() -> updateTemperature(shutdown = value)
            else -> updateProperty(key, value.toString())
        }
        onConfigChangeListener?.onConfigChanged(key)
    }

    override fun getInt(key: String, defValue: Int): Int {
        logVerbose("getInt: $key=$defValue?")
        ensureDraftLoaded()
        return configCache[key]?.toIntOrNull() ?: defValue
    }

    override fun putString(key: String, value: String?) {
        logVerbose("putString: $key=$value")
        ensureDraftLoaded()
        updateProperty(key, value.orEmpty())
        onConfigChangeListener?.onConfigChanged(key)
        when (key) {
            chargingSwitchKey() -> pendingSideEffects += PendingSideEffect.RESTART_DAEMON
            currentWorkaroundKey() -> pendingSideEffects += PendingSideEffect.REINITIALIZE
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        logVerbose("getString: $key=$defValue?")
        ensureDraftLoaded()
        return configCache[key].orEmpty().ifEmpty { defValue }
    }

    fun applyDraft(): ApplyGroupedPatchResult {
        ensureDraftLoaded()
        val state = requireNotNull(draftState)
        val groups = diffGroups(state.current, state.draft)
        if (groups.isEmpty()) {
            return ApplyGroupedPatchResult.Success(emptyList(), state.current)
        }
        val result = runBlocking {
            patchApplier(
                ApplyGroupedPatchRequest(
                    base = state.baseCurrent,
                    target = state.draft,
                    groups = groups.toSet(),
                    allowProtectedGroupRebuild = protectedGroupRebuilds.containsAll(state.protectedAdvancedGroups)
                )
            )
        }
        draftState = when (result) {
            is ApplyGroupedPatchResult.Success -> initializeDraftState(result.verifiedConfig)
            is ApplyGroupedPatchResult.Partial -> state.copy(
                current = result.verifiedConfig,
                status = DraftStatus.PARTIALLY_APPLIED
            )
            is ApplyGroupedPatchResult.VerificationMismatch,
            is ApplyGroupedPatchResult.ValidationFailed,
            ApplyGroupedPatchResult.StaleBaseConfig -> state.copy(status = DraftStatus.APPLY_FAILED)
        }
        if (result is ApplyGroupedPatchResult.Success || result is ApplyGroupedPatchResult.Partial) {
            triggerPendingSideEffects()
        }
        protectedGroupRebuilds.clear()
        rebuildCache()
        return result
    }

    fun discardDraft() {
        ensureDraftLoaded()
        val state = requireNotNull(draftState)
        draftState = initializeDraftState(state.current)
        protectedGroupRebuilds.clear()
        pendingSideEffects.clear()
        rebuildCache()
    }

    fun requestProtectedGroupRebuild(group: PatchGroup) {
        protectedGroupRebuilds += group
    }

    fun currentDraftStateForTesting(): AccDraftState {
        ensureDraftLoaded()
        return requireNotNull(draftState)
    }

    fun interface OnConfigChangeListener {
        fun onConfigChanged(key: String)
    }

    var onConfigChangeListener: OnConfigChangeListener? = null

    private fun ensureDraftLoaded() {
        if (draftState != null) {
            return
        }
        draftState = initializeDraftState(runBlocking { currentConfigLoader() })
        rebuildCache()
    }

    private fun initializeDraftState(groupedConfig: GroupedConfigRead): AccDraftState {
        var state = AccDraftState.from(groupedConfig, groupedConfig)
        val protectedGroups = buildSet {
            if (groupedConfig.currentCapacity?.mode in setOf(ConfigGroupMode.MIXED_LEGACY, ConfigGroupMode.ADVANCED_CUSTOM)) {
                add(PatchGroup.CAPACITY)
            }
            if (groupedConfig.currentTemperature?.mode == ConfigGroupMode.ADVANCED_CUSTOM) {
                add(PatchGroup.TEMPERATURE)
            }
        }
        if (protectedGroups.isNotEmpty()) {
            state = state.copy(
                status = DraftStatus.ADVANCED_MODIFIED,
                protectedAdvancedGroups = protectedGroups
            )
        }
        return state
    }

    private fun rebuildCache() {
        val state = requireNotNull(draftState)
        configCache.clear()
        state.draft.current.forEach { key, value ->
            configCache[key as String] = value as String
        }
        state.draft.currentCapacity?.let { capacity ->
            configCache[shutdownCapacityKey()] = capacity.shutdown.toString()
            configCache[cooldownCapacityKey()] = capacity.cooldown.toString()
            configCache[resumeCapacityKey()] = capacity.resume.toString()
            configCache[pauseCapacityKey()] = capacity.pause.toString()
            configCache[capacityMaskKey()] = capacity.maskAsFull.toString()
            supportInVoltage = capacity.mode == ConfigGroupMode.VOLTAGE
        }
        configCache[supportInVoltageKey] = supportInVoltage.toString()
        state.draft.currentTemperature?.let { temperature ->
            configCache[cooldownTempKey()] = temperature.cooldown.toString()
            configCache[maxTempKey()] = temperature.pause.toString()
            configCache[shutdownTempKey()] = temperature.shutdown.toString()
        }
    }

    private fun updateCapacity(
        shutdown: Int? = null,
        cooldown: Int? = null,
        resume: Int? = null,
        pause: Int? = null,
        maskAsFull: Boolean? = null
    ) {
        val state = requireNotNull(draftState)
        if (!state.canOverwrite(PatchGroup.CAPACITY, PatchGroup.CAPACITY in protectedGroupRebuilds)) {
            rebuildCache()
            return
        }
        val current = state.draft.currentCapacity ?: CapacityConfig(0, 0, 0, 0, false, ConfigGroupMode.NORMAL)
        val next = CapacityConfig(
            shutdown = shutdown ?: current.shutdown,
            cooldown = cooldown ?: current.cooldown,
            resume = resume ?: current.resume,
            pause = pause ?: current.pause,
            maskAsFull = maskAsFull ?: current.maskAsFull,
            mode = if (supportInVoltage) ConfigGroupMode.VOLTAGE else ConfigGroupMode.NORMAL
        )
        draftState = state.updateCapacity(next)
        rebuildCache()
    }

    private fun updateTemperature(
        cooldown: Int? = null,
        pause: Int? = null,
        shutdown: Int? = null
    ) {
        val state = requireNotNull(draftState)
        if (!state.canOverwrite(PatchGroup.TEMPERATURE, PatchGroup.TEMPERATURE in protectedGroupRebuilds)) {
            rebuildCache()
            return
        }
        val current = state.draft.currentTemperature ?: TemperatureConfig(0, 0, 0, 0, ConfigGroupMode.NORMAL)
        val next = TemperatureConfig(
            cooldown = cooldown ?: current.cooldown,
            pause = pause ?: current.pause,
            resume = current.resume,
            shutdown = shutdown ?: current.shutdown,
            mode = ConfigGroupMode.NORMAL
        )
        draftState = state.copy(
            draft = state.draft.copy(currentTemperature = next).withProperty("temperature", next.serialize()),
            status = DraftStatus.MODIFIED,
            protectedAdvancedGroups = state.protectedAdvancedGroups - PatchGroup.TEMPERATURE
        )
        rebuildCache()
    }

    private fun updateProperty(key: String, value: String) {
        val state = requireNotNull(draftState)
        draftState = state.copy(
            draft = state.draft.withProperty(key, value),
            status = DraftStatus.MODIFIED
        )
        rebuildCache()
    }

    private fun triggerPendingSideEffects() {
        if (pendingSideEffects.isEmpty()) {
            return
        }
        val effects = pendingSideEffects.toList()
        pendingSideEffects.clear()
        CoroutineScope(Dispatchers.Default).launch {
            effects.forEach { effect ->
                when (effect) {
                    PendingSideEffect.RESTART_DAEMON -> daemonRestartAction()
                    PendingSideEffect.REINITIALIZE -> reinitializeAction()
                }
            }
        }
    }

    private fun diffGroups(current: GroupedConfigRead, draft: GroupedConfigRead): List<PatchGroup> {
        val groups = linkedSetOf<PatchGroup>()
        if (current.currentCapacity != draft.currentCapacity) {
            groups += PatchGroup.CAPACITY
        }
        if (current.currentTemperature != draft.currentTemperature) {
            groups += PatchGroup.TEMPERATURE
        }
        draft.current.stringPropertyNames()
            .filter { current.current.getProperty(it) != draft.current.getProperty(it) }
            .forEach { key -> groups += inferGroup(key) }
        return groups.toList()
    }

    private fun inferGroup(key: String): PatchGroup = when {
        key.contains("cooldown") -> PatchGroup.COOLDOWN
        key.contains("charging_switch") || key.contains("batt_idle") -> PatchGroup.CHARGING_CONTROL
        key.contains("charging_current") || key.contains("charging_voltage") -> PatchGroup.CURRENT_VOLTAGE
        key.contains("reset_batt_stats") -> PatchGroup.STATS_RESET
        else -> PatchGroup.RUNTIME_HOOKS
    }

    private fun shutdownCapacityKey(): String = "set_shutdown_capacity"
    private fun cooldownCapacityKey(): String = "set_cooldown_capacity"
    private fun resumeCapacityKey(): String = "set_resume_capacity"
    private fun pauseCapacityKey(): String = "set_pause_capacity"
    private fun capacityMaskKey(): String = "set_capacity_mask"
    private fun cooldownTempKey(): String = "set_cooldown_temp"
    private fun maxTempKey(): String = "set_max_temp"
    private fun shutdownTempKey(): String = "set_shutdown_temp"
    private fun chargingSwitchKey(): String = "set_charging_switch"
    private fun currentWorkaroundKey(): String = "set_current_workaround"

    companion object {
        const val TAG = "ConfigDataStore"

        suspend fun readGroupedConfigDirect(): GroupedConfigRead {
            val current = Command.getCurrentConfig()
            val defaults = Command.getDefaultConfig()
            return GroupedConfigRead(
                current = current,
                defaults = defaults,
                currentCapacity = current.getProperty("capacity")?.let(CapacityConfig::parse),
                defaultCapacity = defaults.getProperty("capacity")?.let(CapacityConfig::parse),
                currentTemperature = current.getProperty("temperature")?.let(TemperatureConfig::parse),
                defaultTemperature = defaults.getProperty("temperature")?.let(TemperatureConfig::parse)
            )
        }

        fun buildBridge(): AccBridge = AccBridge(
            capabilityProbe = {
                AccCapability.from(
                    AccProbeFacts(
                        hasRoot = true,
                        availableEntrypoints = emptyList(),
                        selectedEntrypoint = null,
                        accVersionName = null,
                        accVersionCode = 0,
                        daemonRunning = false,
                        canReadInfo = true,
                        canReadCurrentConfig = true,
                        canReadDefaultConfig = true,
                        canReadLogs = false,
                        canExportDiagnostics = false,
                        supportedChargingSwitches = emptyList(),
                        preferredChargingSwitch = null,
                        supportsCurrentControl = false,
                        supportsVoltageControl = false,
                        supportedCapacityModes = setOf(CapacityMode.PERCENT),
                        supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
                    )
                )
            },
            versionReader = { Command.getVersion() },
            daemonReader = { Command.isDaemonRunning() },
            currentConfigReader = { Command.getCurrentConfig() },
            defaultConfigReader = { Command.getDefaultConfig() },
            latestGroupedConfigReader = { readGroupedConfigDirect() },
            groupedPatchWriter = { group, target ->
                try {
                    when (group) {
                        PatchGroup.CAPACITY -> target.currentCapacity?.let {
                            Command.setConfig("capacity", it.serialize())
                        }
                        PatchGroup.TEMPERATURE -> target.currentTemperature?.let {
                            Command.setConfig("temperature", it.serialize())
                        }
                        else -> writePropertyGroup(group, target.current)
                    }
                    GroupApplyWriteResult.Success
                } catch (e: Exception) {
                    GroupApplyWriteResult.Failure(e.localizedMessage ?: "Failed to apply $group")
                }
            },
            verificationGroupedConfigReader = { readGroupedConfigDirect() }
        )

        suspend fun writePropertyGroup(group: PatchGroup, properties: Properties) {
            properties.stringPropertyNames()
                .filter { key -> when (group) {
                    PatchGroup.COOLDOWN -> key.contains("cooldown")
                    PatchGroup.CHARGING_CONTROL -> key.contains("charging_switch") || key.contains("batt_idle")
                    PatchGroup.CURRENT_VOLTAGE -> key.contains("charging_current") || key.contains("charging_voltage")
                    PatchGroup.STATS_RESET -> key.contains("reset_batt_stats")
                    PatchGroup.RUNTIME_HOOKS -> key !in setOf("capacity", "temperature") &&
                        !key.contains("cooldown") &&
                        !key.contains("charging_switch") &&
                        !key.contains("batt_idle") &&
                        !key.contains("charging_current") &&
                        !key.contains("charging_voltage") &&
                        !key.contains("reset_batt_stats")
                    PatchGroup.CAPACITY,
                    PatchGroup.TEMPERATURE -> false
                } }
                .forEach { key ->
                    Command.setConfig(key, properties.getProperty(key))
                }
    }

    private fun logVerbose(message: String) {
        runCatching { Log.v(TAG, message) }
    }

    private enum class PendingSideEffect {
        RESTART_DAEMON,
        REINITIALIZE
    }
}

private fun GroupedConfigRead.withProperty(key: String, value: String): GroupedConfigRead {
    val nextCurrent = Properties().apply {
        putAll(current)
        setProperty(key, value)
    }
    return copy(current = nextCurrent)
}
