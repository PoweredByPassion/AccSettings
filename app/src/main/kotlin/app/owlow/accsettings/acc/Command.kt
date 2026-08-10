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

    private var cachedAccPath: String? = null

    /** Test-only hook to capture/short-circuit the command line exec would run. */
    internal var execOverride: (suspend (String) -> String)? = null

    /** Test-only hook to short-circuit path existence checks without hitting a real Shell. */
    internal var execTestOverride: ((String) -> Boolean)? = null

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        runCatching { Log.v(TAG, "exec: $command") }
        execOverride?.let { return@withContext it(command) }
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
            if (result.code == 127) {
                cachedAccPath = null
            }
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

    /**
     * Applies ACC config assignments as `acca --set prop=value ...` (a single `--set` option
     * followed by space-separated `prop=value` tokens). Individual assignments must NOT carry a
     * `--` prefix: `acca --set sc=10` works, `acca --set --sc=10` fails with `export: --: unknown
     * option` on real devices.
     */
    suspend fun setConfig(property: String, vararg values: String?) {
        val args = (listOf(property) + values).filterNotNull()
        require(args.size % 2 == 0) { "setConfig requires property/value pairs" }
        val accPath = withContext(Dispatchers.IO) {
            requireAccExecutable { path -> execTest(path) }
        }
        val assignments = args.chunked(2).joinToString(" ") { (prop, value) -> "$prop=$value" }
        exec("$accPath --set $assignments")
    }

    /**
     * Lists the charging switches ACC knows about, via `{accPath} -s s:`. Real devices return
     * rc=0 with one `ctrl_file on off` line per switch (the switch list ACC itself maintains).
     * Returns an empty list on any failure so capability probing never blocks on this.
     */
    suspend fun listChargingSwitches(): List<String> = runCatching {
        val accPath = withContext(Dispatchers.IO) {
            requireAccExecutable { path -> execTest(path) }
        }
        exec("$accPath -s s:")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }.getOrDefault(emptyList())

    /**
     * Reads the max charging current (mcc) ACC is allowed to use, via the full config key
     * `max_charging_current` on `{accPath} --set --print <key>`. Returns null when the key is
     * absent (current/voltage control not supported) or the probe fails.
     */
    suspend fun readMaxChargingCurrent(): String? = runCatching {
        parsePrintValue("max_charging_current")
    }.getOrNull()

    /**
     * Reads the max charging voltage (mcv) ACC is allowed to use, via the full config key
     * `max_charging_voltage`. Returns null when the key is absent or the probe fails.
     */
    suspend fun readMaxChargingVoltage(): String? = runCatching {
        parsePrintValue("max_charging_voltage")
    }.getOrNull()

    private suspend fun parsePrintValue(key: String): String? {
        val accPath = withContext(Dispatchers.IO) {
            requireAccExecutable { path -> execTest(path) }
        }
        val output = exec("$accPath --set --print $key")
        return output.lineSequence()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

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

    /** Raw `acc --info` output, used by the charging-info reader. */
    suspend fun getInfoRaw(): String = execAcc("info")

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
        return parseVersionOutput(exec("$accPath -v"))
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

    /**
     * Force-disables charging on demand via `acc -d`.
     *
     * @param condition Optional recovery condition, passed straight through to ACC:
     *   - `null` → unconditional (`acc -d`), restore with [startDaemon]
     *   - a duration (`"30m"`, `"1h"`) or capacity threshold (`"50%"`) → runs ACC's
     *     recommended chained form `acc -d <condition>; accd`, so when the condition is met
     *     ACC re-enables charging AND restarts the daemon (regular settings return) —
     *     all automatically, no app-side timer needed.
     */
    suspend fun disableCharging(condition: String? = null) {
        val accPath = withContext(Dispatchers.IO) {
            requireAccExecutable { path -> execTest(path) }
        }
        if (condition == null) {
            exec("$accPath -d")
        } else {
            // The chained `acc -d <condition>; accd` blocks until the recovery condition is met
            // (e.g. acc -d 1h sleeps for an hour). Run it via `nohup ... &` so it is detached
            // from the shared libsu root shell — otherwise the blocking command would stall every
            // subsequent root command (the 3s overview polling) until the condition is met.
            val daemon = withContext(Dispatchers.IO) {
                findAccDaemon { path -> execTest(path) } ?: accPath
            }
            exec("nohup sh -c \"$accPath -d $condition; $daemon\" > /dev/null 2>&1 &")
        }
    }

    /**
     * Starts/restarts the ACC daemon via the `accd` command, which is ACC's documented way to
     * restore the regular pause/resume charging settings after a forced disable.
     */
    suspend fun startDaemon() {
        val daemonPath = withContext(Dispatchers.IO) {
            findAccDaemon { path -> execTest(path) }
                ?: findAccExecutable { path -> execTest(path) }
                ?: throw NotInstalledException()
        }
        exec(daemonPath)
    }

    suspend fun reinitialize() = exec(withContext(Dispatchers.IO) {
        buildReinitializeCommand { path -> execTest(path) }
    })

    internal fun resetForTesting() {
        cachedAccPath = null
        execOverride = null
        execTestOverride = null
    }

    private val VERSION_REGEX = Regex("""v([0-9][0-9A-Za-z.\-]*)\s*\((\d+)\)""")

    private fun execTest(path: String): Boolean {
        execTestOverride?.let { return it(path) }
        val shell = Shell.getShell()
        if (!shell.isRoot) return false
        return shell.newJob().add("test -f \"$path\"").to(mutableListOf(), mutableListOf()).exec().isSuccess
    }

    internal fun findAccExecutable(pathExists: (String) -> Boolean): String? {
        val cached = cachedAccPath
        if (cached != null && pathExists(cached)) return cached
        cachedAccPath = null
        val found = ACC_EXECUTABLE_CANDIDATES.firstOrNull(pathExists)
        cachedAccPath = found
        return found
    }

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
