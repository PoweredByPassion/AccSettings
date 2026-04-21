package app.owlow.accsettings.acc

import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class AccHandlerTest {
    @Test
    fun buildInstallCommand_runsFromScriptDirectory() {
        val script = File("/data/user/0/app.owlow.accsettings/cache/install-tarball.sh")

        assertEquals(
            "cd '/data/user/0/app.owlow.accsettings/cache' && sh './install-tarball.sh' acc",
            AccHandler.buildInstallCommand(script)
        )
    }

    @Test
    fun buildUninstallCommand_usesShellInterpreter() {
        assertEquals(
            "sh '/data/adb/vr25/acc/uninstall.sh'",
            AccHandler.buildUninstallCommand(File("/data/adb/vr25/acc/uninstall.sh"))
        )
    }

    @Test
    fun buildServeCommand_matchesOfficialFrontendDocumentation() {
        assertEquals(
            "test -f /dev/acca || /data/adb/vr25/acc/service.sh",
            AccHandler.buildServeCommand()
        )
    }

    @Test
    fun buildBundledArchiveName_usesSourceArchiveFormat() {
        assertEquals(
            "acc_v2025.5.18-dev_202505180.tgz",
            AccHandler.buildBundledArchiveName("2025.5.18-dev", 202505180)
        )
    }

    @Test
    fun buildInstallerArchiveName_usesInstallerExpectedSourceName() {
        assertEquals(
            "acc-2025.5.18-dev.tgz",
            AccHandler.buildInstallerArchiveName("2025.5.18-dev")
        )
    }

    @Test
    fun buildCacheCleanupCommand_removesAllCacheEntries() {
        assertEquals(
            "find '/data/user/0/app.owlow.accsettings/cache' -mindepth 1 -maxdepth 1 -exec rm -rf {} +",
            AccHandler.buildCacheCleanupCommand(File("/data/user/0/app.owlow.accsettings/cache"))
        )
    }

    @Test
    fun buildPostUninstallCleanupCommand_removesDanglingLinks() {
        assertEquals(
            "rm -rf /data/adb/vr25/acc /dev/acca /dev/accd /dev/.vr25/acc",
            AccHandler.buildPostUninstallCleanupCommand()
        )
    }

    @Test
    fun awaitInstalledVersion_acceptsVersionAfterRetry() = runBlocking {
        var attempt = 0

        AccHandler.awaitInstalledVersion(
            bundledVersionCode = 202505180,
            maxAttempts = 3,
            delayMillis = 0
        ) {
            attempt++
            if (attempt < 3) 0 else 202505180
        }

        assertEquals(3, attempt)
    }

    @Test
    fun awaitInstalledVersion_failsWhenVersionNeverAppears() = runBlocking {
        try {
            AccHandler.awaitInstalledVersion(
                bundledVersionCode = 202505180,
                maxAttempts = 2,
                delayMillis = 0
            ) { 0 }
            fail("Expected install verification to fail")
        } catch (e: Command.AccException) {
            assertEquals("ACC installation could not be verified", e.message)
        }
    }
}
