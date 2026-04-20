package app.owlow.accsetting.ui.config

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

private const val CHARGING_SWITCH_KEY = "set_charging_switch"
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
    "set_current_workaround"
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

private fun GroupedConfigRead.toConfigGroups(): List<ConfigGroupUiModel> = listOf(
    ConfigGroupUiModel(
        title = "Charge Thresholds",
        summary = "Pause and resume behavior for normal charging.",
        fields = listOf(
            ConfigFieldUiModel(
                key = "set_shutdown_capacity",
                label = "Shutdown below",
                value = currentCapacity?.shutdown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER,
                helperText = "Current shutdown threshold."
            ),
            ConfigFieldUiModel(
                key = "set_cooldown_capacity",
                label = "Cooldown above",
                value = currentCapacity?.cooldown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER
            ),
            ConfigFieldUiModel(
                key = "set_resume_capacity",
                label = "Charge below",
                value = currentCapacity?.resume?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER
            ),
            ConfigFieldUiModel(
                key = "set_pause_capacity",
                label = "Pause above",
                value = currentCapacity?.pause?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER
            )
        )
    ),
    ConfigGroupUiModel(
        title = "Temperature Protection",
        summary = "Heat guardrails that limit or stop charging.",
        fields = listOf(
            ConfigFieldUiModel(
                key = "set_cooldown_temp",
                label = "Cooldown above",
                value = currentTemperature?.cooldown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER
            ),
            ConfigFieldUiModel(
                key = "set_max_temp",
                label = "Pause above",
                value = currentTemperature?.pause?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER
            ),
            ConfigFieldUiModel(
                key = "set_shutdown_temp",
                label = "Shutdown above",
                value = currentTemperature?.shutdown?.toString().orEmpty(),
                kind = ConfigFieldKind.NUMBER
            )
        )
    ),
    ConfigGroupUiModel(
        title = "Current and Voltage Behavior",
        summary = "Charging switch and related live control overrides.",
        fields = listOf(
            ConfigFieldUiModel(
                key = CHARGING_SWITCH_KEY,
                label = "Charging switch",
                value = current.getProperty(CHARGING_SWITCH_KEY).orEmpty(),
                kind = ConfigFieldKind.TEXT
            ),
            ConfigFieldUiModel(
                key = "set_max_charging_voltage",
                label = "Max charging voltage",
                value = current.getProperty("set_max_charging_voltage").orEmpty(),
                kind = ConfigFieldKind.TEXT
            )
        )
    ),
    ConfigGroupUiModel(
        title = "Advanced Options",
        summary = "Compatibility toggles that should be changed deliberately.",
        fields = listOf(
            ConfigFieldUiModel(
                key = "set_current_workaround",
                label = "Strict current control",
                value = current.getProperty("set_current_workaround").orEmpty(),
                kind = ConfigFieldKind.TOGGLE
            )
        )
    )
)
