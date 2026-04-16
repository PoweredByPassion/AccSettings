package crazyboyfeng.accSettings.data

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceDataStore
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.Command
import kotlinx.coroutines.*

class ConfigDataStore(private val context: Context) : PreferenceDataStore() {
    private var supportInVoltage: Boolean = false
    private var cacheLoaded = false
    private val configCache = mutableMapOf<String, String>()

    private fun ensureCacheLoaded() {
        if (cacheLoaded) return
        runBlocking {
            val properties = Command.getCurrentConfig()
            configCache.clear()
            for (entry in properties) {
                val key = entry.key as String
                val value = entry.value as String
                configCache[key] = value
            }
            supportInVoltage = configCache[context.getString(R.string.support_in_voltage)]?.toBoolean() ?: false
            cacheLoaded = true
        }
    }
    override fun putBoolean(key: String, value: Boolean) {
        Log.v(TAG, "putBoolean: $key=$value")
        CoroutineScope(Dispatchers.Default).launch {
            when (key) {
                context.getString(R.string.support_in_voltage) -> {
                    supportInVoltage = value
                    configCache[key] = value.toString()
                }
                else -> {
                    Command.setConfig(key, value.toString())
                    configCache[key] = value.toString()
                }
            }
            onConfigChangeListener?.onConfigChanged(key)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        Log.v(TAG, "getBoolean: $key=$defValue?")
        ensureCacheLoaded()
        return when (key) {
            context.getString(R.string.support_in_voltage) -> supportInVoltage
            else -> {
                val value = configCache[key].orEmpty()
                if (value.isEmpty()) defValue else value.toBoolean()
            }
        }
    }

    override fun putInt(key: String, value: Int) {
        Log.v(TAG, "putInt: $key=$value")
        CoroutineScope(Dispatchers.Default).launch {
            Command.setConfig(key, value.toString())
            configCache[key] = value.toString()
            onConfigChangeListener?.onConfigChanged(key)
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        Log.v(TAG, "getInt: $key=$defValue?")
        ensureCacheLoaded()
        val value = configCache[key].orEmpty()
        return if (value.isEmpty()) {
            defValue
        } else {
            value.toInt()
        }
    }

    override fun putString(key: String, value: String?) {
        Log.v(TAG, "putString: $key=$value")
        CoroutineScope(Dispatchers.Default).launch {
            Command.setConfig(key, value)
            configCache[key] = value.orEmpty()
            onConfigChangeListener?.onConfigChanged(key)
        }
    }

    override fun getString(key: String, defValue: String?): String? {
        Log.v(TAG, "getString: $key=$defValue?")
        ensureCacheLoaded()
        return configCache[key].orEmpty().ifEmpty {
            defValue
        }
    }

    fun interface OnConfigChangeListener {
        fun onConfigChanged(key: String)
    }

    var onConfigChangeListener: OnConfigChangeListener? = null

    private companion object {
        const val TAG = "ConfigDataStore"
    }
}
