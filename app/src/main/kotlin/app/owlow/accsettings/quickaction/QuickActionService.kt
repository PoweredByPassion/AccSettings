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
import androidx.core.app.NotificationCompat
import app.owlow.accsettings.R
import app.owlow.accsettings.QuickActionReceiver
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.ForceStopState
import app.owlow.accsettings.data.QuickActionConfigStore
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

    override fun onCreate() {
        super.onCreate()
        val appCtx = applicationContext
        store = ForceStopChargingStore.from(appCtx)
        coordinator = ChargingControlCoordinator.forContext(appCtx)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        val title = ChargeControlLabels.activeTitle(this, state)
        val description = ChargeControlLabels.activeDescription(this, state, now)
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

    // ---- Action buttons -----------------------------------------------------------------

    /**
     * Returns the list of (label, uri) pairs to show as notification action buttons.
     * Uses the user's [QuickActionConfig]: each configured slot becomes a button, EXCEPT the one
     * matching the currently-active operation. Cancel is always included as an escape hatch.
     */
    private fun buildActionButtons(state: ForceStopState): List<Pair<String, String>> {
        val config = QuickActionConfigStore.from(this).load()
        val buttons = mutableListOf<Pair<String, String>>()

        config.slots.forEach { slot ->
            if (!slot.matchesActiveState(state)) {
                buttons.add(ChargeControlLabels.slotLabel(this, slot) to slot.toUri())
            }
        }

        // Cancel is always available so the user can restore charging regardless of config.
        if (config.slots.none { it.type == QuickActionSlotType.CANCEL }) {
            buttons.add(getString(R.string.quick_action_cancel) to "quickaction:cancel")
        }
        return buttons
    }

    /** Whether a configured slot corresponds to the currently-active operation (hide that button). */
    private fun QuickActionSlot.matchesActiveState(state: ForceStopState): Boolean {
        if (!state.active) return false
        return when (type) {
            QuickActionSlotType.PAUSE ->
                state.mode == ChargingControlMode.STOP && state.condition == param
            QuickActionSlotType.CHARGE_TO ->
                state.mode == ChargingControlMode.CHARGE_TO && state.condition == param
            QuickActionSlotType.FORCE_FULL ->
                state.mode == ChargingControlMode.FORCE_FULL && state.condition == param
            QuickActionSlotType.CANCEL -> false
        }
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
