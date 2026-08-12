package app.owlow.accsettings

import android.content.Context
import app.owlow.accsettings.MainDispatcherRule
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.ChargingControlMode
import app.owlow.accsettings.data.ForceStopChargingStore
import app.owlow.accsettings.data.OverviewRepository
import app.owlow.accsettings.quickaction.ChargingControlCoordinator
import app.owlow.accsettings.quickaction.QuickAction
import app.owlow.accsettings.quickaction.ServiceController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class QuickActionDispatcherTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- mapUriToAction ---

    @Test
    fun mapUriToAction_mapsAllValidUris() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(QuickAction.Pause("30m"), QuickActionDispatcher.mapUriToAction("quickaction:pause/30m"))
        assertEquals(QuickAction.Pause("1h"), QuickActionDispatcher.mapUriToAction("quickaction:pause/1h"))
        assertEquals(QuickAction.ForceFull(100), QuickActionDispatcher.mapUriToAction("quickaction:force-full"))
        assertEquals(QuickAction.ChargeTo("85%"), QuickActionDispatcher.mapUriToAction("quickaction:charge-to/85"))
        assertEquals(QuickAction.Cancel, QuickActionDispatcher.mapUriToAction("quickaction:cancel"))
    }

    @Test
    fun mapUriToAction_unknownUri_returnsNull() {
        assertNull(QuickActionDispatcher.mapUriToAction("quickaction:unknown/thing"))
    }

    @Test
    fun mapUriToAction_emptyString_returnsNull() {
        assertNull(QuickActionDispatcher.mapUriToAction(""))
    }

    @Test
    fun mapUriToAction_malformedUri_returnsNull() {
        assertNull(QuickActionDispatcher.mapUriToAction("not a uri"))
    }

    // --- dispatchAndAwait ---

    @Test
    fun dispatchAndAwait_pause_returnsSuccessAndPersists() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())

        val result = QuickActionDispatcher.dispatchAndAwait(
            context = ApplicationProvider.getApplicationContext(),
            uriString = "quickaction:pause/30m",
            coordinatorFactory = { coordinator }
        )

        assertEquals(DispatchResult.Success, result)
        assertEquals("30m", repo.lastForceStopCondition)
        val loaded = store.load()
        assertTrue(loaded.active)
        assertEquals(ChargingControlMode.STOP, loaded.mode)
    }

    @Test
    fun dispatchAndAwait_cancel_returnsSuccessAndClears() = runTest {
        val repo = FakeOverviewRepository()
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())

        // Activate a pause first so cancel has something to cancel.
        coordinator.execute(QuickAction.Pause("30m"))
        assertTrue(coordinator.state.value.active)

        val result = QuickActionDispatcher.dispatchAndAwait(
            context = ApplicationProvider.getApplicationContext(),
            uriString = "quickaction:cancel",
            coordinatorFactory = { coordinator }
        )

        assertEquals(DispatchResult.Success, result)
        assertEquals(ChargingControlMode.STOP, repo.lastCancelledMode)
        assertTrue(!store.load().active)
    }

    @Test
    fun dispatchAndAwait_whenRepositoryFails_returnsFailure() = runTest {
        val repo = FakeOverviewRepository().apply { failNext = true }
        val store = testStore()
        val coordinator = ChargingControlCoordinator(repo, store, FakeServiceController())

        val result = QuickActionDispatcher.dispatchAndAwait(
            context = ApplicationProvider.getApplicationContext(),
            uriString = "quickaction:force-full",
            coordinatorFactory = { coordinator }
        )

        assertTrue(result is DispatchResult.Failure)
    }

    @Test
    fun dispatchAndAwait_unknownUri_returnsNull() = runTest {
        val coordinator = ChargingControlCoordinator(
            FakeOverviewRepository(), testStore(), FakeServiceController()
        )

        val result = QuickActionDispatcher.dispatchAndAwait(
            context = ApplicationProvider.getApplicationContext(),
            uriString = "quickaction:not-a-real-action",
            coordinatorFactory = { coordinator }
        )

        assertNull(result)
    }

    // --- helpers ---

    private fun testStore(): ForceStopChargingStore {
        val name = "dispatcher_test_${System.nanoTime()}"
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ForceStopChargingStore(
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        )
    }

    private class FakeOverviewRepository : OverviewRepository {
        var failNext = false
        var lastForceStopCondition: String? = null
        var lastCancelledMode: ChargingControlMode? = null

        override suspend fun loadStatus(): AccStatus? = null

        override suspend fun startService(): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            return null
        }

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            return null
        }

        override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            lastForceStopCondition = condition
            return null
        }

        override suspend fun enableCharging(condition: String?): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            return null
        }

        override suspend fun forceFullCharge(capacity: Int): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            return null
        }

        override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus? {
            if (failNext) throw RuntimeException("fail")
            lastCancelledMode = mode
            return null
        }
    }

    private class FakeServiceController : ServiceController {
        override fun start() {}
        override fun stop() {}
    }
}
