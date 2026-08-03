package app.owlow.accsettings.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for the exact command strings [Command] sends to the ACC executable.
 *
 * These lock in the real-device-verified ACC CLI shape: a single `--set` option followed by
 * space-separated `prop=value` assignments, and no `--` prefix on individual assignments
 * (i.e. `acca --set sc=10`, never `acca --set --sc=10`).
 *
 * All tests short-circuit [Command.exec] via [Command.execOverride] so no real Shell is invoked,
 * and preset a known accPath via [Command.findAccExecutable] so path resolution never touches
 * the Shell either.
 */
class CommandContractTest {
    private val captured = mutableListOf<String>()

    @Before
    fun setUp() {
        captured.clear()
        Command.resetForTesting()
        // Preset the resolved ACC path so requireAccExecutable/findAccExecutable return it
        // without calling execTest() (which would hit the real Shell). The cache-revalidation
        // step re-checks pathExists on every call, so the path check is stubbed to keep the
        // whole resolution path off the Shell.
        val pathExists = { path: String -> path == "/dev/acca" }
        Command.execTestOverride = pathExists
        Command.findAccExecutable(pathExists)
        Command.execOverride = { cmd ->
            captured += cmd
            ""
        }
    }

    @Test
    fun setConfig_uses_set_without_double_dash() = runBlocking {
        Command.setConfig("sc", "10")
        assertEquals(listOf("/dev/acca --set sc=10"), captured)
        assertFalse(captured.single().contains("--sc="))
    }

    @Test
    fun setConfig_batch_single_dash_set() = runBlocking {
        // The (property, vararg values) signature carries extra prop=val tokens in values,
        // yielding a single --set with space-separated assignments and no per-arg -- prefix.
        Command.setConfig("sc", "10", "cc", "65")
        assertEquals(listOf("/dev/acca --set sc=10 cc=65"), captured)
        assertFalse(captured.single().contains("--cc="))
    }

    @Test
    fun getConfig_uses_print() = runBlocking {
        Command.getConfig("sc")
        assertEquals(listOf("/dev/acca --set --print sc"), captured)
    }

    @Test
    fun getCurrentConfig_uses_print() = runBlocking {
        Command.getCurrentConfig()
        assertEquals(listOf("/dev/acca --set --print"), captured)
    }

    @Test
    fun getVersion_uses_dash_v() = runBlocking {
        Command.getVersion()
        assertEquals(listOf("/dev/acca -v"), captured)
    }

    @Test
    fun isDaemonRunning_uses_daemon() = runBlocking {
        Command.isDaemonRunning()
        assertEquals(listOf("/dev/acca --daemon"), captured)
    }

    @Test
    fun getPropertyValue_parsesNormalSingleLineValue() = runBlocking {
        Command.execOverride = { "sc=10\n" }
        assertEquals("10", Command.getConfig("sc"))
    }

    @Test
    fun getPropertyValue_returnsEmptyForBlankValue() = runBlocking {
        Command.execOverride = { "sc=" }
        assertEquals("", Command.getConfig("sc"))
    }

    @Test
    fun getPropertyValue_returnsEmptyForQuotedEmptyValue() = runBlocking {
        // Real exec() trims stdout, so getPropertyValue receives "sc=\"\"" (no trailing newline).
        Command.execOverride = { "sc=\"\"" }
        assertEquals("", Command.getConfig("sc"))
    }

    @Test
    fun getPropertyValue_returnsWholeLineWhenNoEqualsSign() = runBlocking {
        // BUG-13 regression guard: a line without '=' used to crash with IndexOutOfBounds.
        Command.execOverride = { "malformed line" }
        assertEquals("malformed line", Command.getConfig("sc"))
    }

    @Test
    fun setConfig_rejectsOddNumberOfArguments() = runBlocking {
        try {
            Command.setConfig("sc", "10", "cc")
            org.junit.Assert.fail("Expected IllegalArgumentException for odd argument count")
        } catch (e: IllegalArgumentException) {
            assertEquals("setConfig requires property/value pairs", e.message)
        }
    }
}
