package crazyboyfeng.accSettings.ui.overview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OverviewRoute(
    modifier: Modifier = Modifier,
    overviewViewModel: OverviewViewModel = viewModel(factory = OverviewViewModel.factory())
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        overviewViewModel.refresh().join()
    }

    OverviewScreen(
        uiState = uiState,
        onRefresh = { overviewViewModel.refresh() },
        modifier = modifier
    )
}
