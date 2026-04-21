package app.owlow.accsettings.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import app.owlow.accsettings.R
import app.owlow.accsettings.acc.AccDraftState
import app.owlow.accsettings.acc.ApplyGroupedPatchResult
import app.owlow.accsettings.acc.CapacityConfig
import app.owlow.accsettings.acc.ConfigGroupMode
import app.owlow.accsettings.acc.DraftStatus
import app.owlow.accsettings.acc.GroupedConfigRead
import app.owlow.accsettings.acc.TemperatureConfig
import app.owlow.accsettings.data.ConfigDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties

private const val CHARGING_SWITCH_KEY = "charging_switch"
private const val MAX_CHARGING_CURRENT_KEY = "max_charging_current"
private const val MAX_CHARGING_VOLTAGE_KEY = "max_charging_voltage"
private const val COOLDOWN_CURRENT_KEY = "cooldown_current"
private const val TEMP_LEVEL_KEY = "temp_level"
private const val CURRENT_WORKAROUND_KEY = "current_workaround"
private const val CAPACITY_MASK_KEY = "set_capacity_mask"
private const val CAPACITY_PERCENT_MIN = 0
private const val CAPACITY_PERCENT_MAX = 100
private const val TEMP_MIN = 0
private const val TEMP_MAX = 100
private const val VOLT_MIN = 3000
private const val VOLT_MAX = 4200
private val INT_FIELD_KEYS = setOf(
    "set_shutdown_capacity",
    "set_cooldown_capacity",
    "set_resume_capacity",
    "set_pause_capacity",
    "set_cooldown_temp",
    "set_resume_temp",
    "set_max_temp",
    "set_shutdown_temp",
    TEMP_LEVEL_KEY,
    "amp_factor",
    "volt_factor"
)
private val TOGGLE_FIELD_KEYS = setOf(
    CURRENT_WORKAROUND_KEY,
    CAPACITY_MASK_KEY,
    "allow_idle_above_pcap",
    "prioritize_batt_idle_mode",
    "off_mid",
    "reboot_resume",
    "batt_status_workaround",
    "force_off"
)

data class ConfigDraftSnapshot(
    val applied: GroupedConfigRead,
    val draft: GroupedConfigRead,
    val draftStatus: DraftStatus,
    val applyFeedback: ConfigFeedback? = null
)

interface ConfigRepository {
    suspend fun loadSnapshot(): ConfigDraftSnapshot
    suspend fun updateField(key: String, value: String): ConfigDraftSnapshot
    suspend fun discardDraft(): ConfigDraftSnapshot
    suspend fun applyDraft(): ConfigDraftSnapshot
}

class ConfigViewModel(
    private val configRepository: ConfigRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(): Job = publishSnapshot { configRepository.loadSnapshot() }

    fun onFieldChanged(key: String, value: String): Job = publishSnapshot {
        configRepository.updateField(key, value)
    }

    fun discardDraft(): Job = publishSnapshot {
        configRepository.discardDraft()
    }

    fun applyChanges(): Job = publishSnapshot {
        _uiState.value = _uiState.value.copy(isApplying = true, applyFeedback = null)
        configRepository.applyDraft()
    }

    private fun publishSnapshot(block: suspend () -> ConfigDraftSnapshot): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, applyFeedback = null)
        _uiState.value = block().toUiState()
    }

    companion object {
        fun factory(
            configRepository: ConfigRepository? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val repository = configRepository ?: LiveConfigRepository(
                    this[APPLICATION_KEY]!!.applicationContext
                )
                ConfigViewModel(repository)
            }
        }
    }
}

private class LiveConfigRepository(
    private val context: Context
) : ConfigRepository {
    private val configStore = ConfigDataStore(context)

    override suspend fun loadSnapshot(): ConfigDraftSnapshot = withContext(Dispatchers.IO) {
        configStore.currentDraftState().toSnapshot()
    }

    override suspend fun updateField(key: String, value: String): ConfigDraftSnapshot = withContext(Dispatchers.IO) {
        when (key) {
            in INT_FIELD_KEYS -> configStore.putInt(key, value.toIntOrNull() ?: 0)
            in TOGGLE_FIELD_KEYS -> configStore.putBoolean(key, value.toBoolean())
            else -> configStore.putString(key, value)
        }
        configStore.currentDraftState().toSnapshot()
    }

    override suspend fun discardDraft(): ConfigDraftSnapshot = withContext(Dispatchers.IO) {
        configStore.discardDraft()
        configStore.currentDraftState().toSnapshot()
    }

    override suspend fun applyDraft(): ConfigDraftSnapshot = withContext(Dispatchers.IO) {
        val result = configStore.applyDraft()
        val feedback = when (result) {
            is ApplyGroupedPatchResult.ValidationFailed -> ConfigFeedback(
                message = result.errors.joinToString { error ->
                    when (error) {
                        "capacity ordering is invalid" -> context.getString(R.string.config_error_capacity_ordering)
                        "temperature ordering is invalid" -> context.getString(R.string.config_error_temperature_ordering)
                        else -> error
                    }
                },
                isError = true
            )
            is ApplyGroupedPatchResult.Partial -> ConfigFeedback(
                message = result.failedGroups.values.joinToString(),
                isError = true
            )
            is ApplyGroupedPatchResult.VerificationMismatch -> ConfigFeedback(
                message = context.getString(R.string.config_apply_verification_mismatch),
                isError = true
            )
            ApplyGroupedPatchResult.StaleBaseConfig -> ConfigFeedback(
                message = context.getString(R.string.config_apply_stale_base),
                isError = true
            )
            is ApplyGroupedPatchResult.Success -> ConfigFeedback(
                message = context.getString(R.string.config_apply_success),
                isError = false
            )
        }
        configStore.currentDraftState().toSnapshot(applyFeedback = feedback)
    }
}

private fun AccDraftState.toSnapshot(applyFeedback: ConfigFeedback? = null): ConfigDraftSnapshot = ConfigDraftSnapshot(
    applied = current,
    draft = draft,
    draftStatus = status,
    applyFeedback = applyFeedback
)

private fun ConfigDraftSnapshot.toUiState(): ConfigUiState = ConfigUiState(
    isLoading = false,
    groups = draft.toConfigGroups(),
    hasPendingChanges = !applied.isSameAs(draft),
    isApplying = false,
    applyFeedback = applyFeedback
)

internal fun GroupedConfigRead.toConfigGroups(): List<ConfigGroupUiModel> = listOf(
    ConfigGroupUiModel(
        titleRes = R.string.config_group_charge_thresholds_title,
        summaryRes = R.string.config_group_charge_thresholds_summary,
        fields = capacityFields()
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_temperature_title,
        summaryRes = R.string.config_group_temperature_summary,
        fields = temperatureFields()
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_charging_behavior_title,
        summaryRes = R.string.config_group_charging_behavior_summary,
        fields = chargingBehaviorFields()
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_current_voltage_title,
        summaryRes = R.string.config_group_current_voltage_summary,
        fields = currentVoltageFields()
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_automation_title,
        summaryRes = R.string.config_group_automation_summary,
        fields = automationFields()
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_advanced_title,
        summaryRes = R.string.config_group_advanced_summary,
        fields = advancedFields()
    )
)

private fun Properties.readTemplateValue(key: String, defaults: Properties): String =
    getProperty(key) ?: defaults.getProperty(key).orEmpty()

private fun GroupedConfigRead.capacityFields(): List<ConfigFieldUiModel> {
    val capacity = currentCapacity ?: defaultCapacity ?: CapacityConfig(
        shutdown = 0,
        cooldown = 70,
        resume = 72,
        pause = 80,
        maskAsFull = false,
        mode = ConfigGroupMode.NORMAL
    )
    val voltageMode = capacity.mode == ConfigGroupMode.VOLTAGE
    val unitRes = if (voltageMode) R.string.config_unit_millivolt else R.string.config_unit_percent
    
    // Use absolute ranges to avoid "locking" users when order is invalid
    // Use step 5 for capacity to make scrolling faster as requested
    val options = if (voltageMode) {
        voltageOptions()
    } else {
        (CAPACITY_PERCENT_MIN..CAPACITY_PERCENT_MAX step 5).toList()
    }

    return listOf(
        pickerField(
            key = "set_shutdown_capacity",
            labelRes = R.string.shutdown_below,
            selectedValue = capacity.shutdown,
            options = options,
            unitRes = unitRes,
            helperTextRes = R.string.hint_capacity_shutdown
        ),
        pickerField(
            key = "set_cooldown_capacity",
            labelRes = R.string.cooldown_above,
            selectedValue = capacity.cooldown,
            options = options,
            unitRes = unitRes
        ),
        pickerField(
            key = "set_resume_capacity",
            labelRes = R.string.charge_below,
            selectedValue = capacity.resume,
            options = options,
            unitRes = unitRes
        ),
        pickerField(
            key = "set_pause_capacity",
            labelRes = R.string.pause_above,
            selectedValue = capacity.pause,
            options = options,
            unitRes = unitRes
        ),
        toggleField(
            key = CAPACITY_MASK_KEY,
            labelRes = R.string.pause_as_full,
            value = capacity.maskAsFull.toString(),
            helperTextRes = R.string.hint_capacity_mask
        )
    )
}

private fun GroupedConfigRead.temperatureFields(): List<ConfigFieldUiModel> {
    val temperature = currentTemperature ?: defaultTemperature ?: TemperatureConfig(
        cooldown = 42,
        pause = 45,
        resume = 43,
        shutdown = 50,
        mode = ConfigGroupMode.NORMAL
    )
    val options = (TEMP_MIN..TEMP_MAX).toList()

    return listOf(
        pickerField(
            key = "set_cooldown_temp",
            labelRes = R.string.cooldown_above,
            selectedValue = temperature.cooldown,
            options = options,
            unitRes = R.string.config_unit_celsius
        ),
        pickerField(
            key = "set_resume_temp",
            labelRes = R.string.charge_below,
            selectedValue = temperature.resume,
            options = options,
            unitRes = R.string.config_unit_celsius
        ),
        pickerField(
            key = "set_max_temp",
            labelRes = R.string.pause_above,
            selectedValue = temperature.pause,
            options = options,
            unitRes = R.string.config_unit_celsius
        ),
        pickerField(
            key = "set_shutdown_temp",
            labelRes = R.string.shutdown_above,
            selectedValue = temperature.shutdown,
            options = options,
            unitRes = R.string.config_unit_celsius
        )
    )
}

private fun GroupedConfigRead.chargingBehaviorFields(): List<ConfigFieldUiModel> = listOf(
    textField(
        key = CHARGING_SWITCH_KEY,
        labelRes = R.string.charging_switch,
        value = current.readTemplateValue(CHARGING_SWITCH_KEY, defaults),
        helperTextRes = R.string.config_helper_charging_switch
    ),
    toggleField(
        key = "allow_idle_above_pcap",
        labelRes = R.string.allow_idle_above_pause_capacity,
        value = current.readTemplateValue("allow_idle_above_pcap", defaults).ifEmpty { "true" },
        helperTextRes = R.string.hint_allow_idle_above_pause_capacity
    ),
    toggleField(
        key = "prioritize_batt_idle_mode",
        labelRes = R.string.idle_mode_first,
        value = current.readTemplateValue("prioritize_batt_idle_mode", defaults).ifEmpty { "true" },
        helperTextRes = R.string.hint_prioritize_batt_idle_mode
    ),
    toggleField(
        key = "off_mid",
        labelRes = R.string.turn_off_charging_after_restart,
        value = current.readTemplateValue("off_mid", defaults).ifEmpty { "true" },
        helperTextRes = R.string.hint_off_mid
    ),
    toggleField(
        key = "reboot_resume",
        labelRes = R.string.reboot_to_resume_charging,
        value = current.readTemplateValue("reboot_resume", defaults).ifEmpty { "false" },
        helperTextRes = R.string.hint_reboot_resume
    ),
    toggleField(
        key = "batt_status_workaround",
        labelRes = R.string.battery_status_workaround,
        value = current.readTemplateValue("batt_status_workaround", defaults).ifEmpty { "true" },
        helperTextRes = R.string.hint_batt_status_workaround
    ),
    toggleField(
        key = "force_off",
        labelRes = R.string.force_charging_off,
        value = current.readTemplateValue("force_off", defaults).ifEmpty { "false" },
        helperTextRes = R.string.hint_force_off
    )
)

private fun GroupedConfigRead.currentVoltageFields(): List<ConfigFieldUiModel> = listOf(
    textField(
        key = MAX_CHARGING_CURRENT_KEY,
        labelRes = R.string.max_charging_current,
        value = current.readTemplateValue(MAX_CHARGING_CURRENT_KEY, defaults),
        helperTextRes = R.string.hint_max_charging_current
    ),
    ConfigFieldUiModel(
        key = MAX_CHARGING_VOLTAGE_KEY,
        labelRes = R.string.max_charging_voltage,
        value = current.readTemplateValue(MAX_CHARGING_VOLTAGE_KEY, defaults),
        kind = ConfigFieldKind.NUMBER,
        unitRes = R.string.config_unit_millivolt,
        minValue = 3000,
        maxValue = 5000,
        helperTextRes = R.string.hint_max_charging_voltage
    ),
    textField(
        key = COOLDOWN_CURRENT_KEY,
        labelRes = R.string.cooldown_current,
        value = current.readTemplateValue(COOLDOWN_CURRENT_KEY, defaults),
        helperTextRes = R.string.hint_cooldown_current
    ),
    ConfigFieldUiModel(
        key = TEMP_LEVEL_KEY,
        labelRes = R.string.temperature_level,
        value = current.readTemplateValue(TEMP_LEVEL_KEY, defaults),
        kind = ConfigFieldKind.NUMBER,
        unitRes = R.string.config_unit_percent,
        minValue = 0,
        maxValue = 100,
        helperTextRes = R.string.hint_temp_level
    )
)

private fun GroupedConfigRead.automationFields(): List<ConfigFieldUiModel> = listOf(
    textField(
        key = "apply_on_boot",
        labelRes = R.string.apply_on_boot,
        value = current.readTemplateValue("apply_on_boot", defaults),
        helperTextRes = R.string.hint_apply_on_boot
    ),
    textField(
        key = "apply_on_plug",
        labelRes = R.string.apply_on_plug,
        value = current.readTemplateValue("apply_on_plug", defaults),
        helperTextRes = R.string.hint_apply_on_plug
    ),
    textField(
        key = "idle_apps",
        labelRes = R.string.idle_apps,
        value = current.readTemplateValue("idle_apps", defaults),
        helperTextRes = R.string.hint_idle_apps
    ),
    textField(
        key = "run_cmd_on_pause",
        labelRes = R.string.run_on_pause,
        value = current.readTemplateValue("run_cmd_on_pause", defaults),
        helperTextRes = R.string.hint_run_on_pause
    ),
    textField(
        key = "batt_status_override",
        labelRes = R.string.battery_status_override,
        value = current.readTemplateValue("batt_status_override", defaults),
        helperTextRes = R.string.hint_batt_status_override
    )
)

private fun GroupedConfigRead.advancedFields(): List<ConfigFieldUiModel> = listOf(
    toggleField(
        key = CURRENT_WORKAROUND_KEY,
        labelRes = R.string.strict_current_control,
        value = current.readTemplateValue(CURRENT_WORKAROUND_KEY, defaults).ifEmpty { "false" },
        helperTextRes = R.string.hint_strict_current_control
    ),
    ConfigFieldUiModel(
        key = "amp_factor",
        labelRes = R.string.ampere_factor,
        value = current.readTemplateValue("amp_factor", defaults),
        kind = ConfigFieldKind.NUMBER,
        helperTextRes = R.string.hint_amp_factor
    ),
    ConfigFieldUiModel(
        key = "volt_factor",
        labelRes = R.string.volt_factor,
        value = current.readTemplateValue("volt_factor", defaults),
        kind = ConfigFieldKind.NUMBER,
        helperTextRes = R.string.hint_volt_factor
    )
)

private fun pickerField(
    key: String,
    labelRes: Int,
    selectedValue: Int,
    options: List<Int>,
    unitRes: Int,
    helperTextRes: Int? = null
): ConfigFieldUiModel {
    val resolvedOptions = ensureOptionSet(options, selectedValue)
    return ConfigFieldUiModel(
        key = key,
        labelRes = labelRes,
        value = selectedValue.toString(),
        kind = ConfigFieldKind.PICKER,
        pickerState = ConfigPickerUiModel(
            options = resolvedOptions,
            selectedValue = selectedValue,
            minValue = resolvedOptions.first(),
            maxValue = resolvedOptions.last()
        ),
        helperTextRes = helperTextRes,
        unitRes = unitRes,
        minValue = resolvedOptions.first(),
        maxValue = resolvedOptions.last()
    )
}

private fun toggleField(
    key: String,
    labelRes: Int,
    value: String,
    helperTextRes: Int? = null
): ConfigFieldUiModel = ConfigFieldUiModel(
    key = key,
    labelRes = labelRes,
    value = value,
    kind = ConfigFieldKind.TOGGLE,
    helperTextRes = helperTextRes
)

private fun textField(
    key: String,
    labelRes: Int,
    value: String,
    helperTextRes: Int? = null
): ConfigFieldUiModel = ConfigFieldUiModel(
    key = key,
    labelRes = labelRes,
    value = value,
    kind = ConfigFieldKind.TEXT,
    helperTextRes = helperTextRes
)

private fun voltageOptions(): List<Int> = listOf(0) + (VOLT_MIN..VOLT_MAX).toList()

private fun ensureOptionSet(options: List<Int>, selectedValue: Int): List<Int> {
    if (options.isEmpty()) {
        return listOf(selectedValue)
    }
    if (selectedValue in options) {
        return options
    }
    return (options + selectedValue).distinct().sorted()
}
