package app.owlow.accsettings.quickaction

import android.content.Context
import app.owlow.accsettings.MainDispatcherRule
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.acc.ChargingInfo
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.OverviewRepository
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ChargingControlCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- execute(Pause) ---

    @Test
    fun execute_pause_callsSetForceStopChargingAndPersists() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val sink = FakeFeedbackSink()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        coordinator.execute(QuickAction.Pause("30m"), sink)

        assertEquals(true, repo.lastForceStopEnabled)
        assertEquals("30m", repo.lastForceStopCondition)
        val loaded = store.load()
        assertTrue(loaded.active)
        assertEquals(ChargingControlMode.STOP, loaded.mode)
        assertEquals("30m", loaded.condition)
        assertNotNull(loaded.startedAt)
        assertTrue(coordinator.state.value.active)
        assertEquals(ChargingControlMode.STOP, coordinator.state.value.mode)
        assertEquals("30m", coordinator.state.value.condition)
        assertTrue(serviceCtrl.startCalled)
        assertEquals("Done", sink.lastMessage)
    }

    @Test
    fun execute_chargeTo_setsModeChargeTo() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        coordinator.execute(QuickAction.ChargeTo("85%"))

        assertEquals("85%", repo.lastChargeToTarget)
        assertTrue(coordinator.state.value.active)
        assertEquals(ChargingControlMode.CHARGE_TO, coordinator.state.value.mode)
        assertEquals("85%", coordinator.state.value.condition)
        assertTrue(serviceCtrl.startCalled)
    }

    @Test
    fun execute_forceFull_setsModeForceFull() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        coordinator.execute(QuickAction.ForceFull(100))

        assertEquals(100, repo.lastForceFullCapacity)
        assertTrue(coordinator.state.value.active)
        assertEquals(ChargingControlMode.FORCE_FULL, coordinator.state.value.mode)
        assertEquals("100", coordinator.state.value.condition)
        assertTrue(serviceCtrl.startCalled)
    }

    // --- execute auto-cancels active op first ---

    @Test
    fun execute_whenActive_autoCancelsFirst() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())

        // Activate a STOP first.
        coordinator.execute(QuickAction.Pause("1h"))
        assertEquals(ChargingControlMode.STOP, coordinator.state.value.mode)

        // Start a CHARGE_TO; the coordinator must auto-cancel the STOP first.
        coordinator.execute(QuickAction.ChargeTo("80%"))

        assertEquals(ChargingControlMode.STOP, repo.lastCancelledMode)
        assertEquals("80%", repo.lastChargeToTarget)
        assertEquals(ChargingControlMode.CHARGE_TO, coordinator.state.value.mode)
    }

    @Test
    fun execute_cancel_clearsStateAndCancels() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val sink = FakeFeedbackSink()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        // Activate first.
        coordinator.execute(QuickAction.Pause("30m"))
        assertTrue(coordinator.state.value.active)

        // Cancel.
        coordinator.execute(QuickAction.Cancel, sink)

        assertEquals(ChargingControlMode.STOP, repo.lastCancelledMode)
        assertFalse(coordinator.state.value.active)
        assertFalse(store.load().active)
        assertTrue(serviceCtrl.stopCalled)
        assertEquals("Charging restored", sink.lastMessage)
    }

    // --- error handling ---

    @Test
    fun execute_whenRepositoryFails_stateStaysInactive() = runTest {
        val repo = FakeOverviewRepository().apply { failNext = true }
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())

        runCatching { coordinator.execute(QuickAction.Pause("30m")) }

        assertFalse(coordinator.state.value.active)
        assertFalse(store.load().active)
    }

    // --- cancelAny ---

    @Test
    fun cancelAny_clearsStateAndRecordsCancel() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val sink = FakeFeedbackSink()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        // Activate first.
        coordinator.execute(QuickAction.ChargeTo("80%"))
        assertTrue(coordinator.state.value.active)

        // cancelAny.
        coordinator.cancelAny(sink)

        assertEquals(ChargingControlMode.CHARGE_TO, repo.lastCancelledMode)
        assertFalse(coordinator.state.value.active)
        assertFalse(store.load().active)
        assertTrue(serviceCtrl.stopCalled)
        assertEquals("Charging restored", sink.lastMessage)
    }

    @Test
    fun cancelAny_whenNotActive_isNoop() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        coordinator.cancelAny()

        assertEquals(null, repo.lastCancelledMode)
        assertFalse(coordinator.state.value.active)
        // stop() is always called (harmless when service is already stopped).
        assertTrue(serviceCtrl.stopCalled)
    }

    // --- reconcile ---

    @Test
    fun reconcile_whenRecovered_clearsStoreAndStopsService() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        // Start a 30m force-stop.
        coordinator.execute(QuickAction.Pause("30m"))
        assertTrue(coordinator.state.value.active)
        assertTrue(serviceCtrl.startCalled)

        // Now simulate that ACC has restored charging (status=charging, elapsed > grace).
        val status = AccStatus(
            installState = app.owlow.accsettings.acc.AccInstallState.UP_TO_DATE,
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
        // Advance time so elapsed > grace period (15s).
        val now = (store.load().startedAt ?: 0L) + 20_000L
        val result = coordinator.reconcile(status, now)

        assertFalse(result.active)
        assertFalse(coordinator.state.value.active)
        assertFalse(store.load().active)
        assertTrue(serviceCtrl.stopCalled)
    }

    @Test
    fun reconcile_whenNotRecovered_keepsState() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, store, serviceCtrl)

        // Start a 1h force-stop.
        coordinator.execute(QuickAction.Pause("1h"))
        assertTrue(coordinator.state.value.active)

        // Status still not_charging, within the duration.
        val status = AccStatus(
            installState = app.owlow.accsettings.acc.AccInstallState.UP_TO_DATE,
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
        val now = (store.load().startedAt ?: 0L) + 10_000L
        val result = coordinator.reconcile(status, now)

        assertTrue(result.active)
        assertTrue(coordinator.state.value.active)
        assertTrue(store.load().active)
    }

    // --- service controller lifecycle ---

    @Test
    fun startOperation_callsServiceControllerStart() = runTest {
        val repo = FakeOverviewRepository()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, testStore(), serviceCtrl)

        coordinator.execute(QuickAction.Pause("30m"))
        assertTrue(serviceCtrl.startCalled)
        assertFalse(serviceCtrl.stopCalled)
    }

    @Test
    fun cancelOperation_callsServiceControllerStop() = runTest {
        val repo = FakeOverviewRepository()
        val serviceCtrl = FakeServiceController()
        val coordinator = ChargingControlCoordinator(repo, testStore(), serviceCtrl)

        coordinator.execute(QuickAction.Pause("30m"))
        assertTrue(serviceCtrl.startCalled)

        coordinator.execute(QuickAction.Cancel)
        assertTrue(serviceCtrl.stopCalled)
    }

    @Test
    fun execute_startDaemonAndStopDaemon_callSetDaemonRunning() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())
        val initial = coordinator.state.value

        coordinator.execute(QuickAction.StartDaemon)
        assertEquals(true, repo.lastDaemonEnabled)

        coordinator.execute(QuickAction.StopDaemon)
        assertEquals(false, repo.lastDaemonEnabled)

        // Daemon toggles must not alter the charging-control state flow.
        assertEquals(initial, coordinator.state.value)
    }

    @Test
    fun execute_whenAutoCancelFails_doesNotStartNewOp() = runTest {
        val repo = FakeOverviewRepository().apply { failCancelNext = true }
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())

        // Seed an active STOP so auto-cancel triggers.
        coordinator.execute(QuickAction.Pause("1h"))
        assertTrue(coordinator.state.value.active)

        // Now auto-cancel will throw; the new op must NOT start.
        val error = runCatching {
            coordinator.execute(QuickAction.ForceFull(100))
        }.exceptionOrNull()

        assertNotNull(error)
        // forceFullCharge must never have been called.
        assertEquals(null, repo.lastForceFullCapacity)
        // The old operation must still be active (no rollback on cancel failure).
        assertTrue(coordinator.state.value.active)
        assertEquals(ChargingControlMode.STOP, coordinator.state.value.mode)
    }

    // --- helpers ---

    private fun testStore(): ForceStopChargingStore {
        val name = "coordinator_test_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ForceStopChargingStore(
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        )
    }

    private class FakeOverviewRepository : OverviewRepository {
        var failNext = false
        var failCancelNext = false

        var lastForceStopEnabled: Boolean? = null
        var lastForceStopCondition: String? = null
        var lastChargeToTarget: String? = null
        var lastForceFullCapacity: Int? = null
        var lastCancelledMode: ChargingControlMode? = null
        var lastDaemonEnabled: Boolean? = null

        override suspend fun loadStatus(): AccStatus? = null

        override suspend fun startService(): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            return null
        }

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            lastDaemonEnabled = enabled
            return null
        }

        override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            lastForceStopEnabled = enabled
            lastForceStopCondition = condition
            return null
        }

        override suspend fun enableCharging(condition: String?): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            lastChargeToTarget = condition
            return null
        }

        override suspend fun forceFullCharge(capacity: Int): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            lastForceFullCapacity = capacity
            return null
        }

        override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus? {
            if (failNext || failCancelNext) throw RuntimeException("fail")
            lastCancelledMode = mode
            return null
        }
    }

    private class FakeServiceController : ServiceController {
        var startCalled = false
        var stopCalled = false

        override fun start() { startCalled = true }
        override fun stop() { stopCalled = true }
    }

    private class FakeFeedbackSink : FeedbackSink {
        var lastMessage: String? = null

        override fun show(message: String) { lastMessage = message }
    }
}
