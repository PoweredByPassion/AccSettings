package crazyboyfeng.accSettings

import android.app.Application
import android.util.Log

class TestAccSettingsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("TestAccSettingsApplication", "Test application starting without runtime monitoring")
    }
}
