package crazyboyfeng.accSettings.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import crazyboyfeng.accSettings.TestAccSettingsApplication

class AccSettingsTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, TestAccSettingsApplication::class.java.name, context)
    }
}
