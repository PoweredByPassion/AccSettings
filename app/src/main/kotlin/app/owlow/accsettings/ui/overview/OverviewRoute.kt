package app.owlow.accsettings.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OverviewRoute(
    onOpenConfiguration: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
    overviewViewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.factory(LocalContext.current))
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        overviewViewModel.startAutoRefresh()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        overviewViewModel.stopAutoRefresh()
    }

    OverviewScreen(
        uiState = uiState,
        onAction = { actionId ->
            when (actionId) {
                "refresh" -> overviewViewModel.refresh()
                "start" -> overviewViewModel.startService()
                "configuration" -> onOpenConfiguration()
                "tools" -> onOpenTools()
            }
        },
        onToggleAction = { actionId, enabled ->
            when (actionId) {
                "toggle_daemon" -> overviewViewModel.toggleDaemon(enabled)
            }
        },
        onForceStopAction = { action ->
            when (action) {
                ForceStopAction.REQUEST_ENABLE -> overviewViewModel.showForceStopDialog()
                ForceStopAction.DISMISS_DIALOG -> overviewViewModel.dismissForceStopDialog()
                ForceStopAction.CANCEL -> overviewViewModel.cancelForceStopCharging()
                ForceStopAction.REQUEST_CHARGE_TO -> overviewViewModel.showChargeToDialog()
                ForceStopAction.DISMISS_CHARGE_TO_DIALOG -> overviewViewModel.dismissChargeToDialog()
                ForceStopAction.REQUEST_FORCE_FULL -> overviewViewModel.showForceFullDialog()
                ForceStopAction.DISMISS_FORCE_FULL_DIALOG -> overviewViewModel.dismissForceFullDialog()
            }
        },
        onForceStopCondition = { condition ->
            // The condition dialog is shared by force-stop (-d) and resume-charge-to (-e);
            // which one it targets is decided by which dialog is open.
            if (uiState.showForceStopDialog) {
                overviewViewModel.enableForceStopCharging(condition)
            } else if (uiState.showChargeToDialog) {
                overviewViewModel.resumeChargingTo(condition)
            }
        },
        onForceFullCapacity = { capacity ->
            overviewViewModel.forceFullCharge(capacity)
        },
        modifier = modifier
    )
}
