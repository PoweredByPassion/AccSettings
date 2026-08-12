package app.owlow.accsettings.ui.quickactions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.owlow.accsettings.quickaction.ChargeControlLabels
import app.owlow.accsettings.quickaction.QuickActionConfig
import app.owlow.accsettings.quickaction.QuickActionShortcutSyncer
import app.owlow.accsettings.quickaction.QuickActionSlot
import app.owlow.accsettings.quickaction.QuickActionSlotType
import app.owlow.accsettings.quickaction.QuickActionWidgetProvider
import app.owlow.accsettings.data.QuickActionConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuickActionConfigViewModel(
    private val context: Context,
    private val store: QuickActionConfigStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickActionConfigUiState())
    val uiState: StateFlow<QuickActionConfigUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        val config = store.load()
        _uiState.value = config.toUiState()
    }

    fun addSlot(type: QuickActionSlotType) {
        if (_uiState.value.canAdd.not()) return
        val config = store.load()
        // Open the param picker for the newly added slot; Cancel needs no param.
        val slots = config.slots + QuickActionSlot(type, null)
        val newConfig = config.copy(slots = slots)
        persistAndSync(newConfig)
        _uiState.value = newConfig.toUiState(
            editingSlotIndex = if (type == QuickActionSlotType.CANCEL) null else slots.lastIndex
        )
    }

    fun removeSlot(index: Int) {
        val config = store.load()
        val slots = config.slots.filterIndexed { i, _ -> i != index }
        persistAndSync(config.copy(slots = slots))
        _uiState.value = config.copy(slots = slots).toUiState()
    }

    fun moveSlotUp(index: Int) {
        if (index <= 0) return
        val config = store.load()
        val slots = config.slots.toMutableList()
        java.util.Collections.swap(slots, index, index - 1)
        persistAndSync(config.copy(slots = slots))
        _uiState.value = config.copy(slots = slots).toUiState()
    }

    fun moveSlotDown(index: Int) {
        val config = store.load()
        if (index >= config.slots.lastIndex) return
        val slots = config.slots.toMutableList()
        java.util.Collections.swap(slots, index, index + 1)
        persistAndSync(config.copy(slots = slots))
        _uiState.value = config.copy(slots = slots).toUiState()
    }

    fun setSlotParam(index: Int, param: String?) {
        val config = store.load()
        val slots = config.slots.toMutableList()
        slots[index] = slots[index].copy(param = param)
        val newConfig = config.copy(slots = slots)
        persistAndSync(newConfig)
        _uiState.value = newConfig.toUiState()
    }

    fun toggleBatteryRow() {
        val config = store.load()
        val newConfig = config.copy(showBatteryRow = !config.showBatteryRow)
        persistAndSync(newConfig)
        _uiState.value = newConfig.toUiState()
    }

    fun dismissEdit() {
        _uiState.value = _uiState.value.copy(editingSlotIndex = null, pickingType = false)
    }

    fun showTypePicker() {
        _uiState.value = _uiState.value.copy(pickingType = true)
    }

    /** Re-opens the param picker for an existing slot so its parameter can be changed. */
    fun editSlotParam(index: Int) {
        if (index !in store.load().slots.indices) return
        _uiState.value = _uiState.value.copy(editingSlotIndex = index)
    }

    /** Persists a config and pushes it to every surface (widget, shortcuts). */
    private fun persistAndSync(config: QuickActionConfig) {
        store.save(config)
        QuickActionShortcutSyncer.sync(context)
        requestWidgetUpdate(context)
    }

    private fun requestWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, QuickActionWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        if (ids.isNotEmpty()) {
            // Rebuild the widget so the buttons follow the new config.
            val intent = Intent(context, QuickActionWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    private fun QuickActionConfig.toUiState(
        editingSlotIndex: Int? = null
    ): QuickActionConfigUiState {
        val last = slots.lastIndex
        return QuickActionConfigUiState(
            slots = slots.mapIndexed { index, slot ->
                QuickActionSlotUiState(
                    index = index,
                    type = slot.type,
                    param = slot.param,
                    label = ChargeControlLabels.slotLabel(context, slot),
                    canMoveUp = index > 0,
                    canMoveDown = index < last
                )
            },
            showBatteryRow = showBatteryRow,
            editingSlotIndex = editingSlotIndex,
            canAdd = slots.size < QuickActionSlot.MAX_SLOTS
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return QuickActionConfigViewModel(
                    context.applicationContext,
                    QuickActionConfigStore.from(context.applicationContext)
                ) as T
            }
        }
    }
}
