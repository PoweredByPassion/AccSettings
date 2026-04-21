package app.owlow.accsettings.ui.config

import androidx.annotation.StringRes

enum class ConfigFieldKind {
    PICKER,
    NUMBER,
    TEXT,
    TOGGLE
}

data class ConfigPickerUiModel(
    val options: List<Int>,
    val selectedValue: Int,
    val minValue: Int,
    val maxValue: Int
)

data class ConfigFieldUiModel(
    val key: String,
    @StringRes val labelRes: Int,
    val value: String,
    val kind: ConfigFieldKind,
    val pickerState: ConfigPickerUiModel? = null,
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
