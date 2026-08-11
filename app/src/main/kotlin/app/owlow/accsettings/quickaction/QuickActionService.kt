package app.owlow.accsettings.quickaction

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import app.owlow.accsettings.R
import app.owlow.accsettings.QuickActionReceiver
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.ForceStopState
import app.owlow.accsettings.data.LiveOverviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the live charging-control notification.
 *
 * A 3-second polling loop monitors ACC status and reconciles the active operation. When recovery
 * is detected (duration elapsed / capacity reached / charging resumed) the coordinator's
 * [ServiceController.stop] triggers [Context.stopService], which tears down the poll loop via
 * [onDestroy].
 *
 * Started via [ServiceControllerImpl.start] (called from [ChargingControlCoordinator.execute]).
 * Returns [START_STICKY] so the system recreates this service after a process kill mid-operation;
 * [onStartCommand] re-reads the [ForceStopChargingStore] and calls [stopSelf] if no operation is
 * active.
 */
class QuickActionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null

    private lateinit var store: ForceStopChargingStore
    private lateinit var coordinator: ChargingControlCoordinator
    private lateinit var notificationManager: NotificationManager
    private lateinit var serviceController: ServiceControllerImpl

    override fun onCreate() {
        super.onCreate()
        val appCtx = applicationContext
        store = ForceStopChargingStore.from(appCtx)
        serviceController = ServiceControllerImpl(appCtx)
        coordinator = ChargingControlCoordinator(
            repository = LiveOverviewRepository,
            store = store,
            serviceController = serviceController,
            bootTimestampMs = { System.currentTimeMillis() - SystemClock.elapsedRealtime() }
        )
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = store.load()
        if (!state.active) {
            stopSelf()
            return START_STICKY
        }

        // Show the notification immediately (foreground-service timeout on Android 14+ is ~5s).
        val notification = buildNotification(state, null, /* now = */ System.currentTimeMillis())
        startForegroundCompat(NOTIFICATION_ID, notification)

        startPolling()
        return START_STICKY
    }

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Notification channel ------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.quick_action_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.quick_action_service_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // ---- Polling loop --------------------------------------------------------------------

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val status = AccStateManager.refreshStatus()
                val now = System.currentTimeMillis()
                val reconciled = coordinator.reconcile(status, now)

                if (!reconciled.active) {
                    // Recovery detected — the coordinator already triggered serviceController.stop(),
                    // which calls stopService() and leads to onDestroy(). Break out so we don't
                    // fight the teardown.
                    break
                }

                val level = status?.chargingInfo?.level
                val notification = buildNotification(reconciled, level, now)
                notificationManager.notify(NOTIFICATION_ID, notification)

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // ---- Notification builders -----------------------------------------------------------

    private fun buildNotification(
        state: ForceStopState,
        level: String?,
        now: Long
    ): Notification {
        val title = activeTitle(state)
        val description = activeDescription(state, now)
        val body = buildNotificationBody(description, level)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Action buttons: all canonical presets minus the currently-active one, plus Cancel.
        buildActionButtons(state).forEach { (label, uri) ->
            val actionIntent = Intent(this, QuickActionReceiver::class.java).apply {
                data = Uri.parse(uri)
            }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getBroadcast(
                this, uri.hashCode(), actionIntent, flags
            )
            builder.addAction(NotificationCompat.Action.Builder(null, label, pending).build())
        }

        return builder.build()
    }

    private fun buildNotificationBody(description: String, level: String?): String {
        val levelText = level?.toDoubleOrNull()?.let { "${it.toInt()}%" } ?: level
        return if (levelText != null) {
            "$description  |  $levelText"
        } else {
            description
        }
    }

    // ---- Titles and descriptions (mirror OverviewScreen.kt logic) ------------------------

    private fun activeTitle(state: ForceStopState): String = when (state.mode) {
        ChargingControlMode.STOP -> getString(R.string.quick_action_notif_title_active)
        ChargingControlMode.CHARGE_TO -> getString(
            R.string.overview_charge_to_active,
            chargeToConditionLabel(state.condition)
        )
        ChargingControlMode.FORCE_FULL -> getString(
            R.string.overview_force_full_active,
            state.condition ?: "100"
        )
    }

    private fun activeDescription(state: ForceStopState, now: Long): String = when (state.mode) {
        ChargingControlMode.STOP -> stopModeDescription(state, now)
        ChargingControlMode.CHARGE_TO -> chargeToModeDescription(state, now)
        ChargingControlMode.FORCE_FULL -> getString(R.string.overview_force_full_progress)
    }

    // ---- STOP mode: duration countdown or static recovery label -------------------------

    private fun stopModeDescription(state: ForceStopState, now: Long): String {
        val remainingSeconds = durationTotal(state.condition)?.let { total ->
            val startedAt = state.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            return getString(R.string.overview_force_stop_remaining, formatRemainingTime(remainingSeconds))
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

    // ---- CHARGE_TO mode: duration countdown or target text ------------------------------

    private fun chargeToModeDescription(state: ForceStopState, now: Long): String {
        val remainingSeconds = durationTotal(state.condition)?.let { total ->
            val startedAt = state.startedAt ?: return@let null
            (total - (now - startedAt) / 1000L).coerceAtLeast(0L)
        }
        if (remainingSeconds != null) {
            return getString(R.string.overview_charge_to_remaining, formatRemainingTime(remainingSeconds))
        }
        return getString(R.string.overview_charge_to_target, chargeToConditionLabel(state.condition))
    }

    // ---- Action buttons -----------------------------------------------------------------

    /**
     * Returns the list of (label, uri) pairs to show as notification action buttons.
     * All canonical presets are included EXCEPT the currently-active operation; Cancel is always
     * included.
     */
    private fun buildActionButtons(state: ForceStopState): List<Pair<String, String>> {
        val buttons = mutableListOf<Pair<String, String>>()

        // Pause 30m — skip when it IS the active operation
        if (!(state.mode == ChargingControlMode.STOP && state.condition == "30m")) {
            buttons.add(getString(R.string.quick_action_pause_30m) to "quickaction:pause/30m")
        }
        // Pause 1h — skip when it IS the active operation
        if (!(state.mode == ChargingControlMode.STOP && state.condition == "1h")) {
            buttons.add(getString(R.string.quick_action_pause_1h) to "quickaction:pause/1h")
        }
        // Force full — skip when it IS the active operation
        if (state.mode != ChargingControlMode.FORCE_FULL) {
            buttons.add(getString(R.string.quick_action_force_full) to "quickaction:force-full")
        }
        // Charge to 85% — skip when it IS the active operation
        if (!(state.mode == ChargingControlMode.CHARGE_TO && state.condition == "85%")) {
            buttons.add(getString(R.string.quick_action_charge_to_85) to "quickaction:charge-to/85")
        }
        // Cancel — always included
        buttons.add(getString(R.string.quick_action_cancel) to "quickaction:cancel")

        return buttons
    }

    // ---- Helpers copied/adapted from OverviewScreen.kt -----------------------------------

    /** Returns the total duration in seconds for known duration conditions, or null. */
    private fun durationTotal(condition: String?): Long? = when (condition) {
        "30m" -> 30 * 60L
        "1h" -> 60 * 60L
        "2h" -> 2 * 60 * 60L
        else -> null
    }

    /** Formats a remaining countdown as `Hh Mm Ss`, `Mm Ss`, or `Ss`. */
    private fun formatRemainingTime(remainingSeconds: Long): String {
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        return when {
            hours > 0 -> "%dh %dm %ds".format(hours, minutes, seconds)
            minutes > 0 -> "%dm %ds".format(minutes, seconds)
            else -> "%ds".format(seconds)
        }
    }

    /** Human-readable label for a charge-to (acc -e) condition arg. */
    private fun chargeToConditionLabel(condition: String?): String = when (condition) {
        "75%" -> getString(R.string.overview_charge_to_target_75)
        "80%" -> getString(R.string.overview_charge_to_target_80)
        "85%" -> getString(R.string.overview_charge_to_target_85)
        "90%" -> getString(R.string.overview_charge_to_target_90)
        "95%" -> getString(R.string.overview_charge_to_target_95)
        "30m" -> getString(R.string.overview_charge_to_target_30m)
        "1h" -> getString(R.string.overview_charge_to_target_1h)
        else -> getString(R.string.overview_charge_to_target_now)
    }

    // ---- Utility ------------------------------------------------------------------------

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(id, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "quick_action"
        private const val NOTIFICATION_ID = 2001
        private const val POLL_INTERVAL_MS = 3_000L
    }
}
