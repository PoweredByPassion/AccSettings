package crazyboyfeng.accSettings.acc

import org.junit.Assert.assertEquals
import org.junit.Test

class AccStatusTest {
    @Test
    fun homeSummary_notInstalled() {
        val status = AccStatusResolver.resolve(
            installedVersionCode = 0,
            installedVersionName = null,
            bundledVersionCode = 202505180,
            daemonRunning = false
        )

        assertEquals(AccInstallState.NOT_INSTALLED, status.installState)
        assertEquals(null, status.installedVersionName)
        assertEquals(false, status.canManageDaemon)
    }

    @Test
    fun homeSummary_upToDateVersionDisablesUpdate() {
        val status = AccStatusResolver.resolve(
            installedVersionCode = 202505180,
            installedVersionName = "2025.5.18-dev",
            bundledVersionCode = 202505180,
            daemonRunning = true
        )

        assertEquals(AccInstallState.UP_TO_DATE, status.installState)
        assertEquals(false, status.showInstallAction)
        assertEquals(true, status.showUninstallAction)
        assertEquals(true, status.canManageDaemon)
    }

    @Test
    fun homeSummary_olderVersionShowsUpdate() {
        val status = AccStatusResolver.resolve(
            installedVersionCode = 202206040,
            installedVersionName = "2022.6.4",
            bundledVersionCode = 202505180,
            daemonRunning = false
        )

        assertEquals(AccInstallState.UPDATE_AVAILABLE, status.installState)
        assertEquals(true, status.showInstallAction)
        assertEquals(true, status.showUninstallAction)
        assertEquals(false, status.daemonRunning)
    }
}
