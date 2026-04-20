package app.owlow.accsetting.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OverviewRoute(
    onOpenConfiguration: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
    overviewViewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.factory())
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        overviewViewModel.refresh().join()
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
        modifier = modifier
    )
}
