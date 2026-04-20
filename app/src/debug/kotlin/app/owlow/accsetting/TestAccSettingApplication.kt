package app.owlow.accsetting

import android.app.Application
import android.util.Log

class TestAccSettingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("TestAccSettingApplication", "Test application starting without runtime monitoring")
    }
}
