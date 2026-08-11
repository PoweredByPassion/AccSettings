package app.owlow.accsettings.quickaction

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.widget.Toast
import app.owlow.accsettings.R
import app.owlow.accsettings.SettingsActivity
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.ForceStopState
import app.owlow.accsettings.data.LiveOverviewRepository
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
        coordinator = ChargingControlCoordinator(
            repository = LiveOverviewRepository,
            store = ForceStopChargingStore.from(applicationContext),
            serviceController = ServiceControllerImpl(applicationContext),
            bootTimestampMs = { System.currentTimeMillis() - SystemClock.elapsedRealtime() }
        )
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
            qsTile.label = activeTitle(state)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                qsTile.subtitle = activeDescription(state)
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

    // ---- Labels (mirror QuickActionService) --------------------------------------------

    private fun activeTitle(state: ForceStopState): String = when (state.mode) {
        ChargingControlMode.STOP -> getString(R.string.quick_action_notif_title_active)
        ChargingControlMode.CHARGE_TO -> getString(
            R.string.overview_charge_to_active,
            ChargeControlLabels.chargeToConditionLabel(state.condition, this)
        )
        ChargingControlMode.FORCE_FULL -> getString(
            R.string.overview_force_full_active,
            state.condition ?: "100"
        )
    }

    private fun activeDescription(state: ForceStopState): String {
        val now = System.currentTimeMillis()
        return when (state.mode) {
            ChargingControlMode.STOP -> stopModeDescription(state, now)
            ChargingControlMode.CHARGE_TO -> chargeToModeDescription(state, now)
            ChargingControlMode.FORCE_FULL -> getString(R.string.overview_force_full_progress)
        }
    }

    private fun stopModeDescription(state: ForceStopState, now: Long): String {
        val remainingSeconds = ChargeControlLabels.durationTotalSeconds(state.condition)?.let { total ->
            val startedAt = state.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            return getString(
                R.string.overview_force_stop_remaining,
                ChargeControlLabels.formatRemainingTime(remainingSeconds)
            )
        }
        val fallbackRes = when (state.condition) {
            "30m" -> R.string.overview_force_stop_recover_30m
            "1h" -> R.string.overview_force_stop_recover_1h
            "2h" -> R.string.overview_force_stop_recover_2h
            "50%" -> R.string.overview_force_stop_recover_50
            "60%" -> R.string.overview_force_stop_recover_60
            "70%" -> R.string.overview_force_stop_recover_70
            else -> R.string.overview_force_stop_recover_manual
        }
        return getString(fallbackRes)
    }

    private fun chargeToModeDescription(state: ForceStopState, now: Long): String {
        val remainingSeconds = ChargeControlLabels.durationTotalSeconds(state.condition)?.let { total ->
            val startedAt = state.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            return getString(
                R.string.overview_charge_to_remaining,
                ChargeControlLabels.formatRemainingTime(remainingSeconds)
            )
        }
        return getString(
            R.string.overview_charge_to_target,
            ChargeControlLabels.chargeToConditionLabel(state.condition, this)
        )
    }
}
