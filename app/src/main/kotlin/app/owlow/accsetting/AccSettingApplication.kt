package app.owlow.accsetting

import android.app.Application
import android.util.Log
import app.owlow.accsetting.acc.AccStateManager

class AccSettingApplication : Application() {
    companion object {
        private const val TAG = "AccSettingApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application starting")
        AppShell.configureDefaultShell()

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
