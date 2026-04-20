package app.owlow.accsetting.acc

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import app.owlow.accsetting.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class AccHandler {
    suspend fun install(context: Context) {
        suspend fun cacheAssetFile(assetName: String, cachedName: String = assetName): File = withContext(Dispatchers.IO) {
            val cachedFile = File(context.cacheDir, cachedName)
            @Suppress("BlockingMethodInNonBlockingContext")
            context.assets.open(assetName).use { input ->
                FileOutputStream(cachedFile).use { output ->
                    input.copyTo(output)
                }
            }
            cachedFile.setExecutable(true)
            cachedFile
        }

        val resources = context.resources
        val accVersionCode = resources.getInteger(R.integer.acc_version_code)
        val accVersionName = resources.getString(R.string.acc_version_name)
        try {
            Log.d(TAG, "Starting install...")
            Command.exec(buildCacheCleanupCommand(context.cacheDir))
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            val installerArchiveName = buildInstallerArchiveName(accVersionName)
            val installerArchiveFile = cacheAssetFile(
                assetName = buildBundledArchiveName(accVersionName, accVersionCode),
                cachedName = installerArchiveName
            )
            val installShFile = cacheAssetFile("install-tarball.sh")

            require(installerArchiveFile.exists()) { "Bundled ACC archive is missing" }
            val command = buildInstallCommand(installShFile)
            Command.exec(command)
            serve()
            awaitInstalledVersion(accVersionCode) { Command.getVersion().first }
            Log.d(TAG, "Install command finished.")
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            val diagnostics = readInstallDiagnostics(context)
            val baseMessage = e.localizedMessage ?: "Install failed"
            val detailedMessage = if (diagnostics.isBlank()) {
                baseMessage
            } else {
                "$baseMessage\n\n$diagnostics"
            }
            throw Command.AccException(detailedMessage)
        }
    }

    @Throws(Command.AccException::class)
    suspend fun serve() {
        Command.exec(buildServeCommand())
    }

    @Throws(Command.AccException::class)
    suspend fun initial(context: Context) {
        try {
            val installedVersionCode = Command.getVersion().first
            val bundledVersionCode = context.resources.getInteger(R.integer.acc_version_code)
            if (bundledVersionCode <= installedVersionCode) {
                return
            }
        } catch (e: Command.AccException) {
//            e.printStackTrace()
        }
        install(context)
    }

    @Throws(Command.AccException::class)
    suspend fun uninstall() {
        // Check if ACC is installed
        val accInstalled = try {
            Command.exec("test -d /data/adb/vr25/acc")
            true
        } catch (e: Command.AccException) {
            false
        }

        if (!accInstalled) {
            Log.w(TAG, "ACC is not installed, nothing to uninstall")
            return
        }

        // Check if uninstall script exists
        val scriptExists = try {
            Command.exec("test -f /data/adb/vr25/acc/uninstall.sh")
            true
        } catch (e: Command.AccException) {
            false
        }

        if (scriptExists) {
            // Use official uninstall script
            Log.d(TAG, "Using official uninstall script")
            Command.exec(buildUninstallCommand(File("/data/adb/vr25/acc/uninstall.sh")))
        } else {
            // Fallback: manually remove ACC directories
            Log.w(TAG, "Uninstall script not found, removing directories manually")
            Command.exec("rm -rf /data/adb/vr25/acc /dev/.vr25/acc")
        }
        Command.exec(buildPostUninstallCleanupCommand())
        Log.d(TAG, "uninstall end")
    }

    @Throws(Command.AccException::class)
    suspend fun upgrade(context: Context) {
        install(context)
    }

    @Throws(Command.AccException::class)
    suspend fun repair() {
        Command.reinitialize()
    }

    @Throws(Command.AccException::class)
    suspend fun setDaemonRunning(daemonRunning: Boolean) {
        Command.setDaemonRunning(daemonRunning)
    }

    @Throws(Command.AccException::class)
    suspend fun reinitialize() {
        Command.reinitialize()
    }

    suspend fun readRuntimeLogs(): String = withContext(Dispatchers.IO) {
        val shellResult = Shell.cmd(
            """
            for f in \
              /data/adb/vr25/acc-data/logs/accd.log \
              /data/adb/vr25/acc-data/logs/daemon.log \
              /data/adb/vr25/acc-data/logs/install.log \
              /data/adb/vr25/acc-data/logs/install-tarball.sh.log
            do
              if [ -f "${'$'}f" ]; then
                echo "==> ${'$'}f <=="
                tail -n 80 "${'$'}f"
                echo
              fi
            done
            """.trimIndent()
        ).exec()
        val output = (shellResult.out + shellResult.err).joinToString("\n").trim()
        output.ifBlank { "No ACC logs were found on this device." }.takeLast(MAX_LOG_CHARS)
    }

    companion object {
        const val TAG = "AccHandler"

        private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

        internal fun buildInstallCommand(
            installScript: File,
            moduleId: String = "acc"
        ): String {
            val parentDir = installScript.parentFile
                ?: throw IllegalArgumentException("Install script has no parent directory")
            return "cd ${shellQuote(parentDir.absolutePath)} && sh './${installScript.name}' $moduleId"
        }

        internal fun buildUninstallCommand(uninstallScript: File): String =
            "sh ${shellQuote(uninstallScript.absolutePath)}"

        internal fun buildServeCommand(): String =
            "test -f /dev/acca || /data/adb/vr25/acc/service.sh"

        internal fun buildCacheCleanupCommand(cacheDir: File): String =
            "find ${shellQuote(cacheDir.absolutePath)} -mindepth 1 -maxdepth 1 -exec rm -rf {} +"

        internal fun buildPostUninstallCleanupCommand(): String =
            "rm -rf /data/adb/vr25/acc /dev/acca /dev/accd /dev/.vr25/acc"

        internal fun buildBundledArchiveName(versionName: String, versionCode: Int): String =
            "acc_v${versionName}_$versionCode.tgz"

        internal fun buildInstallerArchiveName(versionName: String): String =
            "acc-$versionName.tgz"

        internal suspend fun awaitInstalledVersion(
            bundledVersionCode: Int,
            maxAttempts: Int = 5,
            delayMillis: Long = 500,
            versionProvider: suspend () -> Int
        ) {
            repeat(maxAttempts) { attempt ->
                if (versionProvider() >= bundledVersionCode) {
                    return
                }
                if (attempt < maxAttempts - 1) {
                    delay(delayMillis)
                }
            }
            throw Command.AccException("ACC installation could not be verified")
        }

        private suspend fun readInstallDiagnostics(context: Context): String = withContext(Dispatchers.IO) {
            val cacheLog = File(context.cacheDir, "logs/acc-install.log")
            if (cacheLog.isFile) {
                val content = cacheLog.readText().trim()
                if (content.isNotBlank()) {
                    return@withContext "Install log:\n${content.takeLast(MAX_LOG_CHARS)}"
                }
            }

            val shellResult = Shell.cmd(
                """
                echo "==> cache listing <=="
                ls -la ${shellQuote(context.cacheDir.absolutePath)} 2>&1
                for f in \
                  /data/adb/vr25/acc-data/logs/install.log \
                  /data/adb/vr25/acc-data/logs/install-tarball.sh.log
                do
                  if [ -f "${'$'}f" ]; then
                    echo "==> ${'$'}f <=="
                    tail -n 40 "${'$'}f"
                  fi
                done
                """.trimIndent()
            ).exec()
            val output = (shellResult.out + shellResult.err).joinToString("\n").trim()
            if (output.isBlank()) {
                ""
            } else {
                "Install log:\n${output.takeLast(MAX_LOG_CHARS)}"
            }
        }

        private const val MAX_LOG_CHARS = 4000
    }
}
