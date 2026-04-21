package app.owlow.accsettings.ui.overview

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.owlow.accsettings.SettingsActivity
import app.owlow.accsettings.test.DeviceTestHarness
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverviewDeviceTest {
    private val device = DeviceTestHarness()

    @Before
    fun wakeDevice() {
        device.wakeAndUnlock()
    }

    @Test
    fun app_launchesIntoOverview_withoutCrash() {
        ActivityScenario.launch<SettingsActivity>(
            SettingsActivity.createIntent(device.targetContext)
        ).use {
            device.assertTextVisible("Overview")
        }
    }
}
