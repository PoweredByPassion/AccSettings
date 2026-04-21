package app.owlow.accsettings.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ToolsRoute(
    toolsViewModel: ToolsViewModel = viewModel(
        factory = ToolsViewModel.factory(LocalContext.current)
    )
) {
    val uiState by toolsViewModel.uiState.collectAsStateWithLifecycle()

    ToolsScreen(
        state = uiState,
        onAction = { action ->
            if (action == ToolAction.REFRESH) {
                toolsViewModel.refresh()
            } else {
                toolsViewModel.requestAction(action)
            }
        },
        onConfirmAction = { toolsViewModel.confirmPendingAction() },
        onDismissConfirmation = { toolsViewModel.dismissConfirmation() }
    )
}
