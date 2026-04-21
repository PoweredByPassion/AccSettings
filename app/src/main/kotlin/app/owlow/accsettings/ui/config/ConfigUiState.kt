package app.owlow.accsettings.ui.config

data class ConfigFeedback(
    val message: String,
    val isError: Boolean
)

data class ConfigUiState(
    val isLoading: Boolean = true,
    val groups: List<ConfigGroupUiModel> = emptyList(),
    val hasPendingChanges: Boolean = false,
    val isApplying: Boolean = false,
    val applyFeedback: ConfigFeedback? = null
)
