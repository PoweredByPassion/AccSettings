package app.owlow.accsettings.ui.overview

import app.owlow.accsettings.acc.ChargingControlMode

data class OverviewUiState(
    val isLoading: Boolean = true,
    val daemonBusy: Boolean = false,
    val statusHeadline: String = "",
    val runtimeFacts: List<OverviewFact> = emptyList(),
    val chargingFacts: List<OverviewFact> = emptyList(),
    val primaryActions: List<OverviewAction> = emptyList(),
    val warnings: List<String> = emptyList(),
    val forceStop: ForceStopUiState = ForceStopUiState(),
    val showForceStopDialog: Boolean = false,
    val showChargeToDialog: Boolean = false,
    val showForceFullDialog: Boolean = false
)

/** UI state of the charging-control operation. */
data class ForceStopUiState(
    val active: Boolean = false,
    val mode: ChargingControlMode = ChargingControlMode.STOP,
    val condition: String? = null,
    val startedAt: Long? = null,
    /** Seconds since [startedAt], derived from the wall clock on each status refresh. */
    val elapsedSeconds: Long = 0L
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

/** User actions on the charging-control card. */
enum class ForceStopAction {
    REQUEST_ENABLE,
    DISMISS_DIALOG,
    CANCEL,
    REQUEST_CHARGE_TO,
    DISMISS_CHARGE_TO_DIALOG,
    REQUEST_FORCE_FULL,
    DISMISS_FORCE_FULL_DIALOG
}
