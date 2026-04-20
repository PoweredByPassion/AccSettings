package crazyboyfeng.accSettings.data

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceDataStore
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccStateManager
import kotlinx.coroutines.*

class AccDataStore(private val context: Context) : PreferenceDataStore() {
    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        Log.v(TAG, "getBoolean: $key=$defValue?")
        return when (key) {
            context.getString(R.string.acc_daemon) -> {
                // 从缓存读取状态，避免阻塞 UI 线程
                val cachedStatus = AccStateManager.getCurrentStatus()
                cachedStatus?.daemonRunning ?: defValue
            }
            else -> super.getBoolean(key, defValue)
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        Log.v(TAG, "putBoolean: $key=$value")
        CoroutineScope(Dispatchers.Default).launch {
            when (key) {
                context.getString(R.string.acc_daemon) -> try {
                    AccStateManager.setDaemonRunning(value)
                } catch (_: Exception) {
                    Log.w(TAG, "Ignoring daemon toggle update because ACC is unavailable")
                }
                else -> super.putBoolean(key, value)
            }
        }
    }

    private companion object {
        const val TAG = "AccDataStore"
    }
}
