package app.owlow.accsettings

import android.app.Application
import android.util.Log
import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.quickaction.QuickActionShortcutSyncer

class AccSettingApplication : Application() {
    companion object {
        private const val TAG = "AccSettingApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting")
        AppShell.configureDefaultShell()

        // Keep the dynamic app shortcuts in sync with the persisted quick-action config
        // (e.g. after a fresh install or data reset).
        QuickActionShortcutSyncer.sync(this)

        // Start ACC status monitoring
        AccStateManager.startMonitoring(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application terminating")

        // Stop monitoring and clean up resources
        AccStateManager.cleanup()
    }
}
