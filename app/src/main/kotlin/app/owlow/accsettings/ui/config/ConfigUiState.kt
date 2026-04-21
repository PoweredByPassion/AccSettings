package app.owlow.accsettings.ui.config

data class ConfigUiState(
    val isLoading: Boolean = true,
    val groups: List<ConfigGroupUiModel> = emptyList(),
    val hasPendingChanges: Boolean = false,
    val isApplying: Boolean = false,
    val applyError: String? = null
)
