package app.owlow.accsettings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.topjohnwu.superuser.Shell
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.Command
import app.owlow.accsettings.quickaction.QuickActionShortcutSyncer
import kotlinx.coroutines.runBlocking

class WorkerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent!!.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Re-sync dynamic shortcuts after an app update (package identity may be re-registered).
                QuickActionShortcutSyncer.sync(context)
                run(context, InitialWorker::class.java)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> run(
                context,
                ServeWorker::class.java
            )
        }
    }

    private fun run(context: Context, worker: Class<out Worker>) {
        val request = OneTimeWorkRequest.Builder(worker).build()
        WorkManager.getInstance(context).enqueue(request)
    }

    class InitialWorker(private val context: Context, workerParams: WorkerParameters) :
        Worker(context, workerParams) {
        override fun doWork(): Result = runBlocking {
            Shell.rootAccess()
            try {
                // Route through AccStateManager.ensureInstalled() so lifecycle decisions and the
                // capability refresh run inside the shared AccBridge task runner, keeping this
                // background worker serialized with any UI-triggered ACC operations.
                AccStateManager.ensureInstalled()
                Result.success()
            } catch (e: Command.AccException) {
                Result.failure()
            }
        }
    }

    class ServeWorker(context: Context, workerParams: WorkerParameters) :
        Worker(context, workerParams) {
        override fun doWork(): Result = runBlocking {
            Shell.rootAccess()
            try {
                // Route through AccStateManager.ensureInstalled() so the lifecycle decision and the
                // capability refresh run inside the shared AccBridge task runner, keeping this
                // background worker serialized with any UI-triggered ACC operations. For an already
                // installed ACC this resolves to NO_OP; a fresh install implicitly serves the daemon
                // (AccHandler.install calls serve() internally).
                AccStateManager.ensureInstalled()
                Result.success()
            } catch (e: Command.AccException) {
                Result.failure()
            }
        }
    }
}