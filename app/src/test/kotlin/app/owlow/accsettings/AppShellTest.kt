package app.owlow.accsettings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellTest {
    @Test
    fun configureDefaultShell_runsConfiguratorOnlyOnce() {
        val calls = mutableListOf<String>()

        AppShell.resetForTesting {
            calls += "configured"
        }

        AppShell.configureDefaultShell()
        AppShell.configureDefaultShell()

        assertEquals(listOf("configured"), calls)
    }
}
