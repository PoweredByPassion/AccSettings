package crazyboyfeng.accSettings.acc

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandTest {
    @Test
    fun resolveAccExecutable_prefersOfficialFrontendBinary() {
        assertEquals(
            "/dev/acca",
            Command.resolveAccExecutable { path ->
                path == "/dev/acca" || path == "/data/adb/vr25/acc/acca.sh"
            }
        )
    }

    @Test
    fun resolveAccExecutable_fallsBackToInstalledAccaScript() {
        assertEquals(
            "/data/adb/vr25/acc/acca.sh",
            Command.resolveAccExecutable { path -> path == "/data/adb/vr25/acc/acca.sh" }
        )
    }

    @Test
    fun findAccExecutable_returnsNullWhenAccIsNotInstalled() {
        assertEquals(null, Command.findAccExecutable { false })
    }

    @Test
    fun buildReinitializeCommand_usesOfficialAccdWhenAvailable() {
        assertEquals(
            "/dev/accd --init",
            Command.buildReinitializeCommand { path -> path == "/dev/accd" }
        )
    }

    @Test
    fun buildReinitializeCommand_fallsBackToServiceScript() {
        assertEquals(
            "/data/adb/vr25/acc/service.sh --init",
            Command.buildReinitializeCommand { false }
        )
    }

    @Test
    fun parseVersionOutput_handlesLegacyFormat() {
        assertEquals(
            Pair(202206040, "2022.6.4"),
            Command.parseVersionOutput("v2022.6.4 (202206040)")
        )
    }

    @Test
    fun parseVersionOutput_handlesPrefixedModernFormat() {
        assertEquals(
            Pair(202505180, "2025.5.18-dev"),
            Command.parseVersionOutput("ACC v2025.5.18-dev (202505180)\n")
        )
    }

    @Test
    fun parseVersionOutput_returnsZeroForUnknownOutput() {
        assertEquals(Pair(0, null), Command.parseVersionOutput("not installed"))
    }
}
