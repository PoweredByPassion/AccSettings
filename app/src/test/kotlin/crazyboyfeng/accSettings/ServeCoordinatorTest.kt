package crazyboyfeng.accSettings

import crazyboyfeng.accSettings.acc.AccInstallState
import crazyboyfeng.accSettings.acc.AccStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServeCoordinatorTest {
    @Test
    fun serves_only_once_for_same_installed_state() {
        val coordinator = ServeCoordinator()
        val status = installedStatus(AccInstallState.UP_TO_DATE, "2026.4.17", daemonRunning = false)

        assertTrue(coordinator.shouldServe(status))
        assertFalse(coordinator.shouldServe(status))
    }

    @Test
    fun reset_after_not_installed_allows_future_serve() {
        val coordinator = ServeCoordinator()

        assertTrue(coordinator.shouldServe(installedStatus(AccInstallState.UP_TO_DATE, "2026.4.17", daemonRunning = false)))
        assertFalse(coordinator.shouldServe(installedStatus(AccInstallState.UP_TO_DATE, "2026.4.17", daemonRunning = false)))
        assertFalse(coordinator.shouldServe(installedStatus(AccInstallState.NOT_INSTALLED, null)))
        assertTrue(coordinator.shouldServe(installedStatus(AccInstallState.UPDATE_AVAILABLE, "2026.4.18", daemonRunning = false)))
    }

    @Test
    fun does_not_serve_when_daemon_is_already_running() {
        val coordinator = ServeCoordinator()

        assertFalse(coordinator.shouldServe(installedStatus(AccInstallState.UP_TO_DATE, "2026.4.17", daemonRunning = true)))
    }

    private fun installedStatus(
        state: AccInstallState,
        version: String?,
        daemonRunning: Boolean = true
    ): AccStatus = AccStatus(
        installState = state,
        installedVersionName = version,
        daemonRunning = daemonRunning,
        canManageDaemon = true,
        showInstallAction = state != AccInstallState.UP_TO_DATE,
        showUninstallAction = state != AccInstallState.NOT_INSTALLED
    )
}
