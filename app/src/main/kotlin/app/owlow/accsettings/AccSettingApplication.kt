package app.owlow.accsettings

import android.app.Application
import android.util.Log
import app.owlow.accsettings.acc.AccStateManager

class AccSettingApplication : Application() {
    companion object {
        private const val TAG = "AccSettingApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting")
        AppShell.configureDefaultShell()

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
