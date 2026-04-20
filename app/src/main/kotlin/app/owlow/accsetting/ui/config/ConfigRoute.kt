package app.owlow.accsetting.ui.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConfigRoute(
    configViewModel: ConfigViewModel = viewModel(
        factory = ConfigViewModel.factory(LocalContext.current)
    )
) {
    val uiState by configViewModel.uiState.collectAsStateWithLifecycle()

    ConfigScreen(
        state = uiState,
        onFieldChanged = { key, value -> configViewModel.onFieldChanged(key, value) },
        onDiscard = { configViewModel.discardDraft() },
        onApply = { configViewModel.applyChanges() }
    )
}
