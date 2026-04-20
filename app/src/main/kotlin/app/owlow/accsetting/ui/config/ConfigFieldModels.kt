package app.owlow.accsetting.ui.config

enum class ConfigFieldKind {
    NUMBER,
    TEXT,
    TOGGLE
}

data class ConfigFieldUiModel(
    val key: String,
    val label: String,
    val value: String,
    val kind: ConfigFieldKind,
    val helperText: String? = null,
    val enabled: Boolean = true
)

data class ConfigGroupUiModel(
    val title: String,
    val summary: String,
    val fields: List<ConfigFieldUiModel>
)
