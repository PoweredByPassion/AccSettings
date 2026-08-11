package app.owlow.accsettings.quickaction

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import app.owlow.accsettings.R
import app.owlow.accsettings.SettingsActivity
import app.owlow.accsettings.data.ForceStopState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single static QS tile that reflects the current charging-control operation.
 *
 * When no operation is active the tile shows the default "Charge control" label and tapping it
 * opens the app's Overview. When an operation is active the label/subtitle update to show a
 * live status (mode + remaining duration) and a tap cancels the operation.
 */
class QuickActionTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var coordinator: ChargingControlCoordinator
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        coordinator = ChargingControlCoordinator.forContext(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        stateJob?.cancel()
        stateJob = tileScope.launch {
            coordinator.state.collect { state ->
                updateTile(state)
            }
        }
    }

    override fun onStopListening() {
        stateJob?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val currentState = coordinator.state.value
        if (currentState.active) {
            tileScope.launch(Dispatchers.IO) {
                runCatching {
                    coordinator.execute(QuickAction.Cancel)
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@QuickActionTileService,
                            error.localizedMessage ?: "Action failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } else {
            startActivityAndCollapse(
                Intent(this, SettingsActivity::class.java)
            )
        }
    }

    override fun onDestroy() {
        stateJob?.cancel()
        tileScope.cancel()
        super.onDestroy()
    }

    // ---- Tile state --------------------------------------------------------------------

    private fun updateTile(state: ForceStopState) {
        if (state.active) {
            qsTile.label = ChargeControlLabels.activeTitle(this, state)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.subtitle = ChargeControlLabels.activeDescription(this, state)
            }
            qsTile.state = android.service.quicksettings.Tile.STATE_ACTIVE
        } else {
            qsTile.label = getString(R.string.quick_action_tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.subtitle = getString(R.string.quick_action_tile_desc)
            }
            qsTile.state = android.service.quicksettings.Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }
}
