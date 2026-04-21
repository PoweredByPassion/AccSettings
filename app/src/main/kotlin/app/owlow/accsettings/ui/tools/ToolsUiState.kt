package app.owlow.accsettings.ui.tools

data class ToolSection(
    val title: String = "",
    val summary: String = "",
    val details: List<ToolDetail> = emptyList(),
    val actions: List<ToolActionState> = emptyList()
)

data class ToolLogSection(
    val title: String = "",
    val summary: String = "",
    val content: String = ""
)

data class ToolDetail(
    val label: String,
    val value: String
)

data class ToolActionState(
    val action: ToolAction,
    val label: String,
    val description: String,
    val enabled: Boolean = true,
    val requiresConfirmation: Boolean = false
)

enum class ToolAction {
    INSTALL_OR_UPDATE,
    REPAIR,
    RESTART_SERVICE,
    FORCE_REDETECT,
    REFRESH
}

data class ToolsUiState(
    val installSection: ToolSection = ToolSection(),
    val serviceSection: ToolSection = ToolSection(),
    val diagnosticsSection: ToolSection = ToolSection(),
    val logsSection: ToolLogSection = ToolLogSection(),
    val appInfoSection: ToolSection = ToolSection(),
    val isBusy: Boolean = false,
    val lastMessage: String? = null,
    val pendingConfirmation: ToolAction? = null
)
