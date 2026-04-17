package crazyboyfeng.accSettings

import android.app.Application
import android.util.Log
import crazyboyfeng.accSettings.acc.AccStateManager

class AccSettingsApplication : Application() {
    companion object {
        private const val TAG = "AccSettingsApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting")

        // 启动 ACC 状态监控
        AccStateManager.startMonitoring(applicationContext)
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application terminating")

        // 停止监控并清理资源
        AccStateManager.cleanup()
    }
}
