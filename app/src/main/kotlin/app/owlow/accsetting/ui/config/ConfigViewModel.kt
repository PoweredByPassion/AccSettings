package app.owlow.accsetting.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsetting.R
import app.owlow.accsetting.acc.AccDraftState
import app.owlow.accsetting.acc.ApplyGroupedPatchResult
import app.owlow.accsetting.acc.DraftStatus
import app.owlow.accsetting.acc.GroupedConfigRead
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
private val INT_FIELD_KEYS = setOf(
    "set_shutdown_capacity",
    "set_cooldown_capacity",
    "set_resume_capacity",
    "set_pause_capacity",
    "set_cooldown_temp",
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
    context: Context
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
            is ApplyGroupedPatchResult.ValidationFailed -> result.errors.joinToString()
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
    hasPendingChanges = applied != draft,
    isApplying = false,
    applyError = applyError
)

internal fun GroupedConfigRead.toConfigGroups(): List<ConfigGroupUiModel> = listOf(
    ConfigGroupUiModel(
        titleRes = R.string.config_group_charge_thresholds_title,
        summaryRes = R.string.config_group_charge_thresholds_summary,
        fields = listOf(
            ConfigFieldUiModel(
                key = "set_shutdown_capacity",
                labelRes = R.string.shutdown_below,
                value = currentCapacity?.shutdown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                helperTextRes = R.string.hint_capacity_shutdown,
                unitRes = R.string.config_unit_percent,
                minValue = 0,
                maxValue = 100
            ),
            ConfigFieldUiModel(
                key = "set_cooldown_capacity",
                labelRes = R.string.cooldown_above,
                value = currentCapacity?.cooldown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_percent,
                minValue = 0,
                maxValue = 100
            ),
            ConfigFieldUiModel(
                key = "set_resume_capacity",
                labelRes = R.string.charge_below,
                value = currentCapacity?.resume?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_percent,
                minValue = 0,
                maxValue = 100
            ),
            ConfigFieldUiModel(
                key = "set_pause_capacity",
                labelRes = R.string.pause_above,
                value = currentCapacity?.pause?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_percent,
                minValue = 0,
                maxValue = 100
            )
        )
    ),
    ConfigGroupUiModel(
        titleRes = R.string.config_group_temperature_title,
        summaryRes = R.string.config_group_temperature_summary,
        fields = listOf(
            ConfigFieldUiModel(
                key = "set_cooldown_temp",
                labelRes = R.string.cooldown_above,
                value = currentTemperature?.cooldown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_celsius,
                minValue = 0,
                maxValue = 100
            ),
            ConfigFieldUiModel(
                key = "set_max_temp",
                labelRes = R.string.pause_above,
                value = currentTemperature?.pause?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_celsius,
                minValue = 0,
                maxValue = 100
            ),
            ConfigFieldUiModel(
                key = "set_shutdown_temp",
                labelRes = R.string.shutdown_above,
                value = currentTemperature?.shutdown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                unitRes = R.string.config_unit_celsius,
                minValue = 0,
                maxValue = 100
            )
        )
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
