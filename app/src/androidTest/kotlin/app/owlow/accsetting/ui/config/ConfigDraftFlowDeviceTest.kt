package app.owlow.accsetting.ui.config

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.owlow.accsetting.test.DeviceTestHarness
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigDraftFlowDeviceTest {
    private val device = DeviceTestHarness()

    @Before
    fun wakeDevice() {
        device.wakeAndUnlock()
    }

    @Test
    fun configChange_requiresExplicitApply() {
        ActivityScenario.launch<ConfigScreenTestActivity>(
            ConfigScreenTestActivity.createIntent(
                context = device.targetContext,
                hasPendingChanges = false
            )
        ).use {
            device.assertTextVisible("Configuration")
            device.assertTextAbsent("Apply Changes")
        }
    }
}
