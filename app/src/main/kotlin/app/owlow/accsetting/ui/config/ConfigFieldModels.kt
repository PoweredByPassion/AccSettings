package app.owlow.accsetting.ui.config

import androidx.annotation.StringRes

enum class ConfigFieldKind {
    NUMBER,
    TEXT,
    TOGGLE
}

data class ConfigFieldUiModel(
    val key: String,
    @StringRes val labelRes: Int,
    val value: String,
    val kind: ConfigFieldKind,
    @StringRes val helperTextRes: Int? = null,
    @StringRes val unitRes: Int? = null,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val enabled: Boolean = true
)

data class ConfigGroupUiModel(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val fields: List<ConfigFieldUiModel>
)
