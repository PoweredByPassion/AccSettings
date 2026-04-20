package app.owlow.accsetting.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import app.owlow.accsetting.TestAccSettingApplication

class AccSettingTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, TestAccSettingApplication::class.java.name, context)
    }
}
