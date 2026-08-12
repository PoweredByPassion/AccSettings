package app.owlow.accsettings.ui.quickactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun QuickActionConfigRoute(
    onBack: () -> Unit,
    viewModel: QuickActionConfigViewModel = viewModel(
        factory = QuickActionConfigViewModel.factory(LocalContext.current)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    QuickActionConfigScreen(
        state = uiState,
        onBack = onBack,
        onAddSlot = { viewModel.addSlot(it) },
        onRemoveSlot = { viewModel.removeSlot(it) },
        onMoveSlotUp = { viewModel.moveSlotUp(it) },
        onMoveSlotDown = { viewModel.moveSlotDown(it) },
        onSetSlotParam = { index, param -> viewModel.setSlotParam(index, param) },
        onToggleBatteryRow = { viewModel.toggleBatteryRow() },
        onShowTypePicker = { viewModel.showTypePicker() },
        onDismissEdit = { viewModel.dismissEdit() }
    )
}
