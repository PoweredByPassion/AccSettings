package app.owlow.accsettings.ui.overview

data class OverviewUiState(
    val isLoading: Boolean = true,
    val daemonBusy: Boolean = false,
    val statusHeadline: String = "",
    val runtimeFacts: List<OverviewFact> = emptyList(),
    val chargingFacts: List<OverviewFact> = emptyList(),
    val primaryActions: List<OverviewAction> = emptyList(),
    val warnings: List<String> = emptyList(),
    val forceStop: ForceStopUiState = ForceStopUiState(),
    val showForceStopDialog: Boolean = false
)

/** UI state of the force-stop-charging toggle. */
data class ForceStopUiState(
    val active: Boolean = false,
    val condition: String? = null,
    val startedAt: Long? = null
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

/** User actions on the force-stop-charging card. */
enum class ForceStopAction {
    REQUEST_ENABLE,
    DISMISS_DIALOG,
    CANCEL
}
