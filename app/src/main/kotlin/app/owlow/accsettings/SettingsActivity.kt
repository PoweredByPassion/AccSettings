package app.owlow.accsettings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.owlow.accsettings.ui.AccSettingApp
import app.owlow.accsettings.ui.navigation.AccDestination
import app.owlow.accsettings.ui.theme.AccSettingTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDestination = intent.getStringExtra(EXTRA_START_DESTINATION)
            ?: AccDestination.Overview.route
        setContent {
            AccSettingTheme {
                AccSettingApp(startDestination = startDestination)
            }
        }
    }

    companion object {
        private const val EXTRA_START_DESTINATION = "start_destination"

        fun createIntent(
            context: Context,
            startDestination: String = AccDestination.Overview.route
        ): Intent {
            return Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_START_DESTINATION, startDestination)
            }
        }
    }
}
