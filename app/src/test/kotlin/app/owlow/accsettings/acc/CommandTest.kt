package app.owlow.accsettings.acc

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandTest {
    @Test
    fun requireAccExecutable_prefersOfficialFrontendBinary() {
        assertEquals(
            "/dev/acca",
            Command.requireAccExecutable { path ->
                path == "/dev/acca" || path == "/data/adb/vr25/acc/acca.sh"
            }
        )
    }

    @Test
    fun resolveAccExecutable_fallsBackToInstalledAccaScript() {
        assertEquals(
            "/data/adb/vr25/acc/acca.sh",
            Command.requireAccExecutable { path -> path == "/data/adb/vr25/acc/acca.sh" }
        )
    }

    @Test
    fun findAccExecutable_returnsNullWhenAccIsNotInstalled() {
        assertEquals(null, Command.findAccExecutable { false })
    }

    @Test
    fun requireAccExecutable_throwsWhenAccIsNotInstalled() {
        try {
            Command.requireAccExecutable { false }
            org.junit.Assert.fail("Expected missing ACC executable")
        } catch (e: Command.NotInstalledException) {
            assertEquals("ACC is not installed", e.message)
        }
    }

    @Test
    fun buildReinitializeCommand_usesOfficialAccdWhenAvailable() {
        assertEquals(
            "/dev/accd --init",
            Command.buildReinitializeCommand { path -> path == "/dev/accd" }
        )
    }

    @Test
    fun buildReinitializeCommand_fallsBackToLegacyAccdWhenNeeded() {
        assertEquals(
            "/dev/.vr25/acc/accd --init",
            Command.buildReinitializeCommand { path -> path == "/dev/.vr25/acc/accd" }
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

    @Test
    fun findAccExecutable_prefersFrontendOverLegacyEntrypointsInPriorityOrder() {
        assertEquals(
            "/dev/.vr25/acc/acca",
            Command.findAccExecutable { path ->
                path == "/dev/.vr25/acc/acca" || path == "/data/adb/vr25/acc/acc.sh"
            }
        )
    }
}
