package app.owlow.accsettings.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val pathExists = { path: String -> path == "/dev/acca" || path == "/dev/accd" }
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
    fun disableCharging_uses_dash_d() = runBlocking {
        Command.disableCharging()
        assertEquals(listOf("/dev/acca -d"), captured)
    }

    @Test
    fun disableCharging_with_duration_uses_chained_command() = runBlocking {
        Command.disableCharging("1h")
        assertEquals(listOf("sh -c \"/dev/acca -d 1h; /dev/accd\""), captured)
    }

    @Test
    fun disableCharging_with_capacity_uses_chained_command() = runBlocking {
        Command.disableCharging("50%")
        assertEquals(listOf("sh -c \"/dev/acca -d 50%; /dev/accd\""), captured)
    }

    @Test
    fun startDaemon_uses_accd_command() = runBlocking {
        Command.startDaemon()
        assertEquals(listOf("/dev/accd"), captured)
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

    @Test
    fun listChargingSwitches_uses_dash_s_s_colon() = runBlocking {
        Command.execOverride = { cmd ->
            captured += cmd
            "battery/charging_enabled 1 0\ninput_suspend 1 0\n"
        }
        assertEquals(
            listOf("battery/charging_enabled 1 0", "input_suspend 1 0"),
            Command.listChargingSwitches()
        )
        assertEquals(listOf("/dev/acca -s s:"), captured)
    }

    @Test
    fun listChargingSwitches_trims_and_skips_blank_lines() = runBlocking {
        Command.execOverride = { "  battery/charging_enabled  1 0  \n\n  \n" }
        assertEquals(listOf("battery/charging_enabled  1 0"), Command.listChargingSwitches())
    }

    @Test
    fun listChargingSwitches_returnsEmptyOnFailure() = runBlocking {
        Command.execOverride = { throw Command.NotRootException() }
        assertEquals(emptyList<String>(), Command.listChargingSwitches())
    }

    @Test
    fun readMaxChargingCurrent_uses_full_key_print() = runBlocking {
        Command.execOverride = { cmd ->
            captured += cmd
            "max_charging_current=500\n"
        }
        assertEquals("500", Command.readMaxChargingCurrent())
        assertEquals(listOf("/dev/acca --set --print max_charging_current"), captured)
    }

    @Test
    fun readMaxChargingVoltage_uses_full_key_print() = runBlocking {
        Command.execOverride = { cmd ->
            captured += cmd
            "max_charging_voltage=4200\n"
        }
        assertEquals("4200", Command.readMaxChargingVoltage())
        assertEquals(listOf("/dev/acca --set --print max_charging_voltage"), captured)
    }

    @Test
    fun readMaxChargingCurrent_ignoresUnrelatedLines() = runBlocking {
        // --set --print emits the whole config; only the exact key= line is used.
        Command.execOverride = { "acc_version=v2025.5.18-dev (202505180)\n\nmax_charging_current=500\n" }
        assertEquals("500", Command.readMaxChargingCurrent())
    }

    @Test
    fun readMaxChargingCurrent_returnsNullWhenKeyAbsent() = runBlocking {
        Command.execOverride = { "acc_version=v2025.5.18-dev (202505180)\n" }
        assertNull(Command.readMaxChargingCurrent())
    }
}
