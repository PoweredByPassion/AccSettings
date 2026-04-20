package crazyboyfeng.accSettings.ui.config

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import crazyboyfeng.accSettings.test.DeviceTestHarness
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigScreenTest {
    private val device = DeviceTestHarness()

    @Before
    fun wakeDevice() {
        device.wakeAndUnlock()
    }

    @Test
    fun dirtyDraft_showsApplyAndDiscardBar() {
        ActivityScenario.launch(ConfigScreenTestActivity::class.java).use {
            device.assertTextVisible("Apply Changes")
            device.assertTextVisible("Discard")
        }
    }
}
