package app.owlow.accsetting.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsetting.R
import app.owlow.accsetting.acc.AccDraftState
import app.owlow.accsetting.acc.ApplyGroupedPatchResult
import app.owlow.accsetting.acc.CapacityConfig
import app.owlow.accsetting.acc.ConfigGroupMode
import app.owlow.accsetting.acc.DraftStatus
import app.owlow.accsetting.acc.GroupedConfigRead
import app.owlow.accsetting.acc.TemperatureConfig
import app.owlow.accsetting.data.ConfigDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Properties

private const val CHARGING_SWITCH_KEY = "charging_switch"
private const val MAX_CHARGING_VOLTAGE_KEY = "max_charging_voltage"
private const val CURRENT_WORKAROUND_KEY = "current_workaround"
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
    "set_shutdown_temp"
)
private val TOGGLE_FIELD_KEYS = setOf(
    CURRENT_WORKAROUND_KEY
)

data class ConfigDraftSnapshot(
    val applied: GroupedConfigRead,
    val draft: GroupedConfigRead,
    val draftStatus: DraftStatus,
    val applyError: String? = null
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
        _uiState.value = _uiState.value.copy(isApplying = true, applyError = null)
        configRepository.applyDraft()
    }

    private fun publishSnapshot(block: suspend () -> ConfigDraftSnapshot): Job = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)
        _uiState.value = block().toUiState()
    }

    companion object {
        fun factory(
            context: Context,
            configRepository: ConfigRepository = LiveConfigRepository(context.applicationContext)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConfigViewModel(configRepository) as T
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
            in TOGGLE_FIELD_KEYS -> configStore.putString(key, value)
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
        val error = when (result) {
            is ApplyGroupedPatchResult.ValidationFailed -> result.errors.joinToString { error ->
                when (error) {
                    "capacity ordering is invalid" -> context.getString(R.string.config_error_capacity_ordering)
                    "temperature ordering is invalid" -> context.getString(R.string.config_error_temperature_ordering)
                    else -> error
                }
            }
            is ApplyGroupedPatchResult.Partial -> result.failedGroups.values.joinToString()
            is ApplyGroupedPatchResult.VerificationMismatch -> "Applied values did not fully match the device readback."
            ApplyGroupedPatchResult.StaleBaseConfig -> "Configuration changed on device. Refresh and try again."
            is ApplyGroupedPatchResult.Success -> null
        }
        configStore.currentDraftState().toSnapshot(applyError = error)
    }
}

private fun AccDraftState.toSnapshot(applyError: String? = null): ConfigDraftSnapshot = ConfigDraftSnapshot(
    applied = current,
    draft = draft,
    draftStatus = status,
    applyError = applyError
)

private fun ConfigDraftSnapshot.toUiState(): ConfigUiState = ConfigUiState(
    isLoading = false,
    groups = draft.toConfigGroups(),
    hasPendingChanges = !applied.isSameAs(draft),
    isApplying = false,
    applyError = applyError
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
        titleRes = R.string.config_group_current_voltage_title,
        summaryRes = R.string.config_group_current_voltage_summary,
        fields = listOf(
            ConfigFieldUiModel(
                key = CHARGING_SWITCH_KEY,
                labelRes = R.string.charging_switch,
                value = current.readTemplateValue(CHARGING_SWITCH_KEY, defaults),
                kind = ConfigFieldKind.TEXT,
                helperTextRes = R.string.config_helper_charging_switch
            ),
            ConfigFieldUiModel(
                key = MAX_CHARGING_VOLTAGE_KEY,
                labelRes = R.string.max_charging_voltage,
                value = current.readTemplateValue(MAX_CHARGING_VOLTAGE_KEY, defaults),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_millivolt,
                minValue = 3000,
                maxValue = 5000
            )
        )
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_advanced_title,
        summaryRes = R.string.config_group_advanced_summary,
        fields = listOf(
            ConfigFieldUiModel(
                key = CURRENT_WORKAROUND_KEY,
                labelRes = R.string.strict_current_control,
                value = current.readTemplateValue(CURRENT_WORKAROUND_KEY, defaults),
                kind = ConfigFieldKind.TOGGLE,
                helperTextRes = R.string.hint_strict_current_control
            )
        )
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
