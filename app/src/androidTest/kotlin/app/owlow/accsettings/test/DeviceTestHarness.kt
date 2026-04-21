package app.owlow.accsettings.test

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue

private const val DEFAULT_TIMEOUT_MS = 5_000L

class DeviceTestHarness {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    fun wakeAndUnlock() {
        if (!device.isScreenOn) {
            device.wakeUp()
        }
        device.executeShellCommand("wm dismiss-keyguard")
        device.pressHome()
        SystemClock.sleep(500)
    }

    fun assertTextVisible(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        assertTrue(
            "Expected to find text: $text",
            device.wait(Until.hasObject(By.text(text)), timeoutMs)
        )
    }

    fun assertTextAbsent(text: String, timeoutMs: Long = 1_500L) {
        val appeared = device.wait(Until.hasObject(By.text(text)), timeoutMs)
        assertTrue("Expected text to stay absent: $text", !appeared)
    }

    fun tapText(text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        assertTextVisible(text, timeoutMs)
        val node = device.findObject(By.text(text))
        checkNotNull(node) { "Unable to find object for text: $text" }
        node.click()
        instrumentation.waitForIdleSync()
    }
}
