package app.owlow.accsettings.acc

import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

object Command {
    private const val TAG = "Command"

    open class AccException : Exception {
        constructor()
        constructor(message: String) : super(message)
    }

    class FailedException : AccException {
        constructor()
        constructor(message: String) : super(message)
    }
    class NotInstalledException : AccException {
        constructor() : super("ACC is not installed")
        constructor(message: String) : super(message)
    }
    class IncorrectSyntaxException : AccException()
    class NoBusyboxException : AccException()
    class NotRootException : AccException()
    class DisableChargingFailedException : AccException()
    class DaemonExistsException : AccException()
    class DaemonNotExistsException : AccException()
    class TestFailedException : AccException()
    class ECurrentOutOfRangeException : AccException()
    class InitFailedException : AccException()
    class LockFailedException : AccException()
    class ModuleDisabledException : AccException()

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        Log.v(TAG, "exec: $command")
        val shell = Shell.getShell()
        if (!shell.isRoot) {
            throw NotRootException()
        }
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val result = shell.newJob().add(command).to(stdout, stderr).exec()
        if (result.isSuccess) {
            return@withContext stdout.joinToString("\n").trim()
        } else {
            val outputMsg = stdout.joinToString("\n").trim()
            val errorMsg = stderr.joinToString("\n").trim()
            val details = listOf(outputMsg, errorMsg).filter { it.isNotBlank() }.joinToString("\n")
            Log.e(TAG, "Command failed: $command. Exit code: ${result.code}, Output: $details")
            throw when (result.code) {
                1 -> FailedException(details.ifBlank { "Exit code: 1" })
                2 -> IncorrectSyntaxException()
                3 -> NoBusyboxException()
                4 -> NotRootException()
                7 -> DisableChargingFailedException()
                8 -> DaemonExistsException()
                9 -> DaemonNotExistsException()
                10 -> TestFailedException()
                11 -> ECurrentOutOfRangeException()
                12 -> InitFailedException()
                13 -> LockFailedException()
                14 -> ModuleDisabledException()
                127 -> NotInstalledException(
                    if (details.isNotBlank()) details else "ACC is not installed"
                )
                else -> AccException(
                    buildString {
                        append("Exit code: ${result.code}")
                        if (details.isNotBlank()) {
                            append('\n')
                            append(details)
                        }
                    }
                )
            }
        }
    }

    private suspend fun execAcc(vararg options: String): String {
        val accPath = withContext(Dispatchers.IO) {
            requireAccExecutable { path -> execTest(path) }
        }
        val command = buildString {
            append(accPath)
            for (option in options) {
                append(" --")
                append(option)
            }
        }
        return exec(command)
    }

    suspend fun setConfig(property: String, vararg values: String?) =
        execAcc("set \"$property=${values.joinToString(" ")}\"")

    private fun getPropertyValue(property: String): String =
        if (property.endsWith('=') || property.endsWith("\"\"")) "" else property.split('=', '\n')[1]

    suspend fun getConfig(property: String): String =
        getPropertyValue(execAcc("set", "print $property"))

    suspend fun getDefaultConfig(): Properties {
        val properties = Properties()
        @Suppress("BlockingMethodInNonBlockingContext")
        properties.load(execAcc("set", "print-default").reader())
        return properties
    }

    suspend fun getCurrentConfig(): Properties {
        val properties = Properties()
        @Suppress("BlockingMethodInNonBlockingContext")
        properties.load(execAcc("set", "print").reader())
        return properties
    }

    suspend fun getInfo(): Properties {
        val properties = Properties()
        @Suppress("BlockingMethodInNonBlockingContext")
        properties.load(execAcc("info").reader())
        return properties
    }

    internal fun parseVersionOutput(version: String): Pair<Int, String?> {
        val match = VERSION_REGEX.find(version.trim()) ?: return Pair(0, null)
        val versionName = match.groupValues[1]
        val versionCode = match.groupValues[2].toIntOrNull() ?: return Pair(0, null)
        return Pair(versionCode, versionName)
    }

    suspend fun getVersion(): Pair<Int, String?> {
        val accPath = withContext(Dispatchers.IO) {
            findAccExecutable { path -> execTest(path) }
        } ?: return Pair(0, null)
        return parseVersionOutput(exec("$accPath --version"))
    }

    private suspend fun setDaemon(option: String) = try {
        execAcc("daemon $option")
    } catch (e: DaemonExistsException) {
        Log.i(TAG, "daemon exists")
    } catch (e: DaemonNotExistsException) {
        Log.i(TAG, "daemon not exists")
    }

    suspend fun setDaemonRunning(daemonRunning: Boolean) =
        setDaemon(if (daemonRunning) "start" else "stop")

    suspend fun isDaemonRunning(): Boolean = try {
        execAcc("daemon")
        true
    } catch (e: NotInstalledException) {
        false
    } catch (e: DaemonNotExistsException) {
        false
    }

    suspend fun restartDaemon() = setDaemon("restart")

    suspend fun reinitialize() = exec(withContext(Dispatchers.IO) {
        buildReinitializeCommand { path -> execTest(path) }
    })

    private val VERSION_REGEX = Regex("""v([0-9][0-9A-Za-z.\-]*)\s*\((\d+)\)""")

    private fun execTest(path: String): Boolean {
        val shell = Shell.getShell()
        if (!shell.isRoot) return false
        return shell.newJob().add("test -f \"$path\"").to(mutableListOf(), mutableListOf()).exec().isSuccess
    }

    internal fun findAccExecutable(pathExists: (String) -> Boolean): String? =
        ACC_EXECUTABLE_CANDIDATES.firstOrNull(pathExists)

    internal fun listAccExecutables(pathExists: (String) -> Boolean): List<String> =
        ACC_EXECUTABLE_CANDIDATES.filter(pathExists)

    internal fun requireAccExecutable(pathExists: (String) -> Boolean): String =
        findAccExecutable(pathExists) ?: throw NotInstalledException()

    internal fun buildReinitializeCommand(pathExists: (String) -> Boolean): String =
        when {
            pathExists("/dev/accd") -> "/dev/accd --init"
            pathExists("/dev/.vr25/acc/accd") -> "/dev/.vr25/acc/accd --init"
            else -> "/data/adb/vr25/acc/service.sh --init"
        }

    internal fun findAccDaemon(pathExists: (String) -> Boolean): String? =
        ACC_DAEMON_CANDIDATES.firstOrNull(pathExists)

    private val ACC_EXECUTABLE_CANDIDATES = listOf(
        "/dev/acca",
        "/dev/.vr25/acc/acca",
        "/data/adb/vr25/acc/acc.sh",
        "/data/adb/vr25/acc/acca.sh",
        DEFAULT_ACC_EXECUTABLE
    )

    private const val DEFAULT_ACC_EXECUTABLE = "/data/adb/vr25/acc/acc.sh"

    private val ACC_DAEMON_CANDIDATES = listOf(
        "/dev/accd",
        "/dev/.vr25/acc/accd",
        "/data/adb/vr25/acc/service.sh"
    )
}
