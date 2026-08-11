package app.owlow.accsettings.ui.overview

import android.content.Context
import app.owlow.accsettings.MainDispatcherRule
import app.owlow.accsettings.acc.AccInstallState
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.acc.ChargingInfo
import app.owlow.accsettings.acc.Command
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.ForceStopState
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_mapsAccStatusIntoOverviewSections() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.statusHeadline.contains("running", ignoreCase = true))
        assertTrue(viewModel.uiState.value.primaryActions.isNotEmpty())
    }

    @Test
    fun refresh_exposesDistinctOverviewActions() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.refresh().join()

        assertEquals(
            listOf("refresh", "start", "configuration"),
            viewModel.uiState.value.primaryActions.map { it.id }
        )
    }

    @Test
    fun refresh_formatsBatteryFactsIntoUserFacingUnits() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "83",
                        status = "charging",
                        temp = "315",
                        current = "1543000",
                        voltage = "4187",
                        power = "6459000"
                    )
                )
            )
        )

        viewModel.refresh().join()

        assertEquals(
            listOf("83%", "Charging", "31.5°C", "1.54 A", "4187 mV", "6.46 W"),
            viewModel.uiState.value.chargingFacts.map { it.value }
        )
    }

    @Test
    fun refresh_formatsSmallNegativePowerWithoutInflatingUnits() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "83",
                        status = "discharging",
                        temp = "315",
                        current = "-65900",
                        voltage = "4197",
                        power = "-276432"
                    )
                )
            )
        )

        viewModel.refresh().join()

        assertEquals(
            listOf("83%", "Discharging", "31.5°C", "-65.9 mA", "4197 mV", "-0.28 W"),
            viewModel.uiState.value.chargingFacts.map { it.value }
        )
    }

    @Test
    fun refresh_formatsBatteryCurrentWithCorrectUnitScale() = runTest {
        val cases = listOf(
            "800" to "800 µA",
            "1200" to "1.2 mA",
            "1500000" to "1.5 A"
        )
        cases.forEach { (current, expected) ->
            val viewModel = OverviewViewModel(
                context = ApplicationProvider.getApplicationContext(),
                overviewRepository = FakeOverviewRepository(
                    status = AccStatus(
                        installState = AccInstallState.UP_TO_DATE,
                        installedVersionName = "2025.5.18-dev",
                        daemonRunning = true,
                        canManageDaemon = true,
                        showInstallAction = false,
                        showUninstallAction = true,
                        chargingInfo = ChargingInfo(
                            level = null,
                            status = null,
                            temp = null,
                            current = current,
                            voltage = null,
                            power = null
                        )
                    )
                )
            )

            viewModel.refresh().join()

            assertEquals(expected, viewModel.uiState.value.chargingFacts.single().value)
        }
    }

    @Test
    fun refresh_renders_handshake_rows_from_charging_info() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "34", status = "Charging", temp = "340",
                        current = "20000", voltage = "3810", power = "80000",
                        chargeType = "pc_port", powerConnected = true,
                        protocol = "USB_PD", realProtocol = "USB", pdActive = false,
                        negotiatedCurrent = "500", negotiatedVoltage = "5000",
                        negotiatedPower = "2.5 W", ccMode = "0"
                    )
                )
            )
        )

        viewModel.refresh().join()

        val rows = viewModel.uiState.value.chargingFacts
        assertTrue(rows.any { it.label == "Charge type" && it.value == "PC port" })
        assertTrue(rows.any { it.label == "Protocol" && it.value == "USB_PD" })
        assertTrue(rows.any { it.label == "PD negotiation" && it.value == "No" })
        assertTrue(rows.any { it.label == "Negotiated power" && it.value == "2.5 W" })
    }

    @Test
    fun refresh_hidesHandshakeRows_whenNoPowerConnected() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "70", status = "Discharging", temp = "360",
                        current = "-590000", voltage = "3980", power = "-2350000",
                        chargeType = "pc_port", powerConnected = null,
                        protocol = "USB_PD", negotiatedPower = "2.5 W"
                    )
                )
            )
        )

        viewModel.refresh().join()

        val rows = viewModel.uiState.value.chargingFacts
        // Base discharge fields remain.
        assertTrue(rows.any { it.label == "Level" })
        // Handshake + charge type hidden when unplugged.
        assertTrue(rows.none { it.label == "Charge type" })
        assertTrue(rows.none { it.label == "Protocol" })
        assertTrue(rows.none { it.label == "Negotiated power" })
    }

    @Test
    fun autoRefresh_reloadsBatteryStateUntilStopped() = runTest {
        val repository = CountingOverviewRepository()
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = repository
        )

        viewModel.startAutoRefresh(intervalMs = 3_000L)
        runCurrent()
        assertEquals(1, repository.loadCount)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(2, repository.loadCount)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(3, repository.loadCount)

        viewModel.stopAutoRefresh()
        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(3, repository.loadCount)
    }

    @Test
    fun toggleDaemon_whenAccUnavailable_appendsWarningInsteadOfCrashing() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            ).apply { failDaemonToggle = true }
        )

        viewModel.toggleDaemon(enabled = true).join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.warnings.any { it == "Failed to toggle daemon" })
    }

    @Test
    fun startService_whenAccUnavailable_appendsWarningInsteadOfCrashing() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            ).apply { failStartService = true }
        )

        viewModel.startService().join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.warnings.any { it == "Failed to start service" })
    }

    @Test
    fun enableForceStopCharging_reflectsActiveState() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.refresh().join()
        // Daemon running => force-stop not active.
        assertEquals(false, viewModel.uiState.value.forceStop.active)

        viewModel.enableForceStopCharging(condition = "1h").join()

        // Force-stop on => active with the recovery condition.
        assertEquals(true, viewModel.uiState.value.forceStop.active)
        assertEquals("1h", viewModel.uiState.value.forceStop.condition)
        assertEquals(false, viewModel.uiState.value.runtimeFacts.first { it.actionId == "toggle_daemon" }.actionValue)

        viewModel.cancelForceStopCharging().join()

        // Cancel => restored.
        assertEquals(false, viewModel.uiState.value.forceStop.active)
        assertNull(viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun startingNewOperation_replacesActiveForceStop() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        // Force-stop first.
        viewModel.enableForceStopCharging(condition = "1h").join()
        assertEquals(ChargingControlMode.STOP, viewModel.uiState.value.forceStop.mode)

        // Starting force-full replaces it (mutually exclusive).
        viewModel.forceFullCharge(capacity = 95).join()

        assertEquals(ChargingControlMode.FORCE_FULL, viewModel.uiState.value.forceStop.mode)
        assertEquals("95", viewModel.uiState.value.forceStop.condition)

        // Starting charge-to replaces it again.
        viewModel.resumeChargingTo(condition = "80%").join()

        assertEquals(ChargingControlMode.CHARGE_TO, viewModel.uiState.value.forceStop.mode)
        assertEquals("80%", viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun refresh_clearsChargeTo_whenTargetReached() = runTest {
        val startedAt = System.currentTimeMillis() - 5_000L
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "80", status = "charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, mode = ChargingControlMode.CHARGE_TO, condition = "75%", startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        // Level reached the 75% target => charge-to done, state cleared.
        assertFalse(viewModel.uiState.value.forceStop.active)
    }

    @Test
    fun refresh_clearsForceFull_whenTargetCapacityReached() = runTest {
        val startedAt = System.currentTimeMillis() - 5_000L
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "95", status = "charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, mode = ChargingControlMode.FORCE_FULL, condition = "90", startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        // Level reached the 90% target => force-full done, state cleared.
        assertFalse(viewModel.uiState.value.forceStop.active)
    }

    @Test
    fun cancelForceStopCharging_cancelsByMode() = runTest {
        val repository = CancelingOverviewRepository(
            status = AccStatus(
                installState = AccInstallState.UP_TO_DATE,
                installedVersionName = "2025.5.18-dev",
                daemonRunning = true,
                canManageDaemon = true,
                showInstallAction = false,
                showUninstallAction = true
            )
        )
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = repository
        )

        viewModel.resumeChargingTo(condition = "80%").join()
        assertEquals(ChargingControlMode.CHARGE_TO, viewModel.uiState.value.forceStop.mode)

        viewModel.cancelForceStopCharging().join()

        // The cancel must have been routed with the active mode.
        assertEquals(ChargingControlMode.CHARGE_TO, repository.lastCancelledMode)
        assertFalse(viewModel.uiState.value.forceStop.active)
    }

    @Test
    fun refresh_keepsForceStopDialogOpen() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.showForceStopDialog()
        assertEquals(true, viewModel.uiState.value.showForceStopDialog)

        // A status refresh (e.g. the 3s auto-refresh) must not dismiss the open dialog.
        viewModel.refresh().join()

        assertEquals(true, viewModel.uiState.value.showForceStopDialog)
    }

    @Test
    fun refresh_recomputesElapsedWhileForceStopActive() = runTest {
        val startedAt = System.currentTimeMillis() - 5_000L
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "40", status = "not_charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = "30m", startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        val forceStop = viewModel.uiState.value.forceStop
        assertTrue(forceStop.active)
        assertEquals("30m", forceStop.condition)
        // Elapsed is recomputed from the wall clock (>= 5s).
        assertTrue(forceStop.elapsedSeconds >= 5L)
    }

    @Test
    fun refresh_clearsForceStop_whenDurationExpired() = runTest {
        // Force-stop started 31 minutes ago, well past the 30m recovery condition.
        val startedAt = System.currentTimeMillis() - (31 * 60 * 1000L)
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "40", status = "not_charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = "30m", startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        // The duration condition has been met (ACC sleeps on the wall clock), so the card returns
        // to inactive even though the poll caught the status before it flipped back to Charging.
        assertFalse(viewModel.uiState.value.forceStop.active)
        assertNull(viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun refresh_clearsForceStop_whenCapacityThresholdReached() = runTest {
        val startedAt = System.currentTimeMillis()
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "50", status = "not_charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = "50%", startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        // Level dropped to the 50% threshold => ACC's until-loop has ended and charging restarted.
        assertFalse(viewModel.uiState.value.forceStop.active)
    }

    @Test
    fun refresh_clearsForceStop_whenChargingResumed() = runTest {
        // Started 20s ago (past the 15s grace) and ACC reports Charging again => recovered.
        val startedAt = System.currentTimeMillis() - 20_000L
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "40", status = "charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = null, startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        // Unconditional restore (or any condition that re-enabled charging).
        assertFalse(viewModel.uiState.value.forceStop.active)
    }

    @Test
    fun refresh_keepsForceStop_whenChargingWithinGracePeriod() = runTest {
        val startedAt = System.currentTimeMillis()
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "40", status = "charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = "1h", startedAt = startedAt)
            ),
            bootTimestampMs = { 0L }
        )

        viewModel.refresh().join()

        // Just enabled (elapsed < grace): the status may still read Charging before the detached
        // acc -d command cuts the switch, so the card must stay active.
        assertTrue(viewModel.uiState.value.forceStop.active)
    }

    @Test
    fun refresh_clearsForceStop_whenStartedBeforeReboot() = runTest {
        // Force-stop was started before the most recent boot. ACC's timer and the sysfs charging
        // switch both die on reboot, and ACC's boot service restarts the daemon with the normal
        // config, so the stored state is stale regardless of current charging status.
        val now = System.currentTimeMillis()
        val bootTime = now - 10 * 60 * 1000L // device booted 10 minutes ago
        val startedAt = bootTime - 5 * 60 * 1000L // force-stop started 5 min before boot
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "40", status = "not_charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = "1h", startedAt = startedAt)
            ),
            bootTimestampMs = { bootTime }
        )

        viewModel.refresh().join()

        // Reboot invalidates the force-stop: cleared even though status is still not_charging.
        assertFalse(viewModel.uiState.value.forceStop.active)
        assertNull(viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun refresh_keepsForceStop_whenStartedAfterReboot() = runTest {
        // Force-stop started after the last boot, still within its duration and not charging.
        val now = System.currentTimeMillis()
        val bootTime = now - 10 * 60 * 1000L
        val startedAt = bootTime + 60 * 1000L // started 1 minute after boot
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    chargingInfo = ChargingInfo(
                        level = "40", status = "not_charging", temp = null,
                        current = null, voltage = null, power = null
                    )
                )
            ),
            forceStopStore = prefsStore(
                ForceStopState(active = true, condition = "1h", startedAt = startedAt)
            ),
            bootTimestampMs = { bootTime }
        )

        viewModel.refresh().join()

        // Started after boot and not yet recovered => still active.
        assertTrue(viewModel.uiState.value.forceStop.active)
        assertEquals("1h", viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun resumeChargingTo_activatesChargeToState() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.resumeChargingTo(condition = "75%").join()

        // Charge-to activates with its own mode, distinct from force-stop.
        assertTrue(viewModel.uiState.value.forceStop.active)
        assertEquals(ChargingControlMode.CHARGE_TO, viewModel.uiState.value.forceStop.mode)
        assertEquals("75%", viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun forceFullCharge_activatesForceFullStateAndClosesDialog() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.showForceFullDialog()
        assertTrue(viewModel.uiState.value.showForceFullDialog)

        viewModel.forceFullCharge(capacity = 95).join()

        assertFalse(viewModel.uiState.value.showForceFullDialog)
        assertTrue(viewModel.uiState.value.forceStop.active)
        assertEquals(ChargingControlMode.FORCE_FULL, viewModel.uiState.value.forceStop.mode)
        assertEquals("95", viewModel.uiState.value.forceStop.condition)
    }

    @Test
    fun forceFullCharge_whenRepositoryFails_clearsBusyWithoutActivating() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            ).apply { failDaemonToggle = true }
        )

        viewModel.forceFullCharge(capacity = 100).join()

        assertFalse(viewModel.uiState.value.forceStop.active)
        assertFalse(viewModel.uiState.value.daemonBusy)
        assertTrue(viewModel.uiState.value.warnings.isNotEmpty())
    }

    private fun prefsStore(state: ForceStopState): ForceStopChargingStore {
        val name = "force_stop_test_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ForceStopChargingStore(
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        )
        store.save(state)
        return store
    }

    @Test
    fun toggleDaemon_marksDaemonBusyWhileRepositoryCallIsInFlight() = runTest {
        val repository = GatedOverviewRepository(
            status = AccStatus(
                installState = AccInstallState.UP_TO_DATE,
                installedVersionName = "2025.5.18-dev",
                daemonRunning = false,
                canManageDaemon = true,
                showInstallAction = false,
                showUninstallAction = true
            )
        )
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = repository
        )

        val job = viewModel.toggleDaemon(enabled = true)
        runCurrent()

        assertTrue(viewModel.uiState.value.daemonBusy)
        assertTrue(viewModel.uiState.value.runtimeFacts.none { it.actionId == "toggle_daemon" })

        repository.releaseDaemonToggle()
        job.join()

        assertFalse(viewModel.uiState.value.daemonBusy)
        assertTrue(
            viewModel.uiState.value.runtimeFacts
                .first { it.actionId == "toggle_daemon" }
                .actionValue == true
        )
    }

    @Test
    fun toggleDaemon_clearsDaemonBusyWhenRepositoryCallFails() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            ).apply { failDaemonToggle = true }
        )

        viewModel.toggleDaemon(enabled = true).join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.daemonBusy)
        assertTrue(state.warnings.any { it == "Failed to toggle daemon" })
    }

    @Test
    fun startService_marksDaemonBusyWhileRepositoryCallIsInFlight() = runTest {
        val repository = GatedOverviewRepository(
            status = AccStatus(
                installState = AccInstallState.UP_TO_DATE,
                installedVersionName = "2025.5.18-dev",
                daemonRunning = false,
                canManageDaemon = true,
                showInstallAction = false,
                showUninstallAction = true
            )
        )
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = repository
        )

        val job = viewModel.startService()
        runCurrent()

        assertTrue(viewModel.uiState.value.daemonBusy)

        repository.releaseStartService()
        job.join()

        assertFalse(viewModel.uiState.value.daemonBusy)
        assertTrue(viewModel.uiState.value.primaryActions.none { it.id == "start" })
    }

    @Test
    fun refresh_exposesLastErrorAsWarning() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    lastError = "Root permission required"
                )
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.warnings.any { it == "Root permission required" })
    }

    @Test
    fun refresh_omitsBlankLastErrorFromWarnings() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    lastError = "   "
                )
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.warnings.none { it == "   " })
    }

    private class FakeOverviewRepository(
        private val status: AccStatus?
    ) : OverviewRepository {
        var failStartService = false
        var failDaemonToggle = false

        override suspend fun loadStatus(): AccStatus? = status

        override suspend fun startService(): AccStatus? {
            if (failStartService) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = true)
        }

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus? {
            if (failDaemonToggle) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = enabled)
        }

        override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus? {
            if (failDaemonToggle) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = !enabled)
        }

        override suspend fun enableCharging(condition: String?): AccStatus? {
            if (failDaemonToggle) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = true)
        }

        override suspend fun forceFullCharge(capacity: Int): AccStatus? {
            if (failDaemonToggle) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = true)
        }

        override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus? {
            if (failDaemonToggle) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = true)
        }
    }

    private class CancelingOverviewRepository(
        private val status: AccStatus
    ) : OverviewRepository {
        var lastCancelledMode: ChargingControlMode? = null

        override suspend fun loadStatus(): AccStatus = status

        override suspend fun startService(): AccStatus = status.copy(daemonRunning = true)

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus = status.copy(daemonRunning = enabled)

        override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus =
            status.copy(daemonRunning = !enabled)

        override suspend fun enableCharging(condition: String?): AccStatus =
            status.copy(daemonRunning = true)

        override suspend fun forceFullCharge(capacity: Int): AccStatus =
            status.copy(daemonRunning = true)

        override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus {
            lastCancelledMode = mode
            return status.copy(daemonRunning = true)
        }
    }

    private class CountingOverviewRepository : OverviewRepository {
        var loadCount = 0

        override suspend fun loadStatus(): AccStatus {
            loadCount += 1
            return AccStatus(
                installState = AccInstallState.UP_TO_DATE,
                installedVersionName = "2025.5.18-dev",
                daemonRunning = true,
                canManageDaemon = true,
                showInstallAction = false,
                showUninstallAction = true,
                chargingInfo = ChargingInfo(
                    level = "83",
                    status = "charging",
                    temp = "315",
                    current = "1543000",
                    voltage = "4187000",
                    power = "6459000"
                )
            )
        }

        override suspend fun startService(): AccStatus = loadStatus().copy(daemonRunning = true)

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus = loadStatus().copy(daemonRunning = enabled)

        override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus = loadStatus().copy(daemonRunning = !enabled)

        override suspend fun enableCharging(condition: String?): AccStatus = loadStatus().copy(daemonRunning = true)

        override suspend fun forceFullCharge(capacity: Int): AccStatus = loadStatus().copy(daemonRunning = true)

        override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus = loadStatus().copy(daemonRunning = true)
    }

    private class GatedOverviewRepository(
        private val status: AccStatus
    ) : OverviewRepository {
        private var startGate = CompletableDeferred<Unit>()
        private var daemonToggleGate = CompletableDeferred<Unit>()

        fun releaseStartService() {
            startGate.complete(Unit)
        }

        fun releaseDaemonToggle() {
            daemonToggleGate.complete(Unit)
        }

        override suspend fun loadStatus(): AccStatus = status

        override suspend fun startService(): AccStatus {
            startGate.await()
            return status.copy(daemonRunning = true)
        }

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus {
            daemonToggleGate.await()
            return status.copy(daemonRunning = enabled)
        }

        override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus =
            status.copy(daemonRunning = !enabled)

        override suspend fun enableCharging(condition: String?): AccStatus =
            status.copy(daemonRunning = true)

        override suspend fun forceFullCharge(capacity: Int): AccStatus =
            status.copy(daemonRunning = true)

        override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus =
            status.copy(daemonRunning = true)
    }
}
