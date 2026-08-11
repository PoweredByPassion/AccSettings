package app.owlow.accsettings.acc

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

    @Test
    fun chargingInfo_carries_all_fields() {
        val info = ChargingInfo(
            level = "34%", status = "Charging", temp = "34℃",
            current = "0.02A", voltage = "3.81V", power = "0.08W",
            chargeType = "pc_port",
            protocol = "USB_PD", realProtocol = "USB", pdActive = false,
            negotiatedCurrent = "500 mA", negotiatedVoltage = "5 V",
            negotiatedPower = "2.5 W", ccMode = "0"
        )
        assertEquals("USB_PD", info.protocol)
        assertEquals("500 mA", info.negotiatedCurrent)
        assertEquals("2.5 W", info.negotiatedPower)
        assertEquals(false, info.pdActive)
    }
}
