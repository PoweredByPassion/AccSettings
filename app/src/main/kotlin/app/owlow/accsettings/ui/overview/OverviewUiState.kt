package app.owlow.accsettings.ui.overview

data class OverviewUiState(
    val isLoading: Boolean = true,
    val daemonBusy: Boolean = false,
    val statusHeadline: String = "",
    val runtimeFacts: List<OverviewFact> = emptyList(),
    val chargingFacts: List<OverviewFact> = emptyList(),
    val primaryActions: List<OverviewAction> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class OverviewFact(
    val label: String,
    val value: String,
    val actionId: String? = null,
    val actionValue: Boolean? = null
)

data class OverviewAction(
    val id: String,
    val label: String
)
