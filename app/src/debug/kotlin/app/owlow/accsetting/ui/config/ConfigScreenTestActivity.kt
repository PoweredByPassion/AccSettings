package app.owlow.accsetting.ui.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.owlow.accsetting.ui.theme.AccSettingTheme

class ConfigScreenTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasPendingChanges = intent.getBooleanExtra(EXTRA_HAS_PENDING_CHANGES, true)
        setContent {
            AccSettingTheme {
                ConfigScreen(
                    state = ConfigUiState(hasPendingChanges = hasPendingChanges)
                )
            }
        }
    }

    companion object {
        private const val EXTRA_HAS_PENDING_CHANGES = "has_pending_changes"

        fun createIntent(
            context: Context,
            hasPendingChanges: Boolean = true
        ): Intent {
            return Intent(context, ConfigScreenTestActivity::class.java).apply {
                putExtra(EXTRA_HAS_PENDING_CHANGES, hasPendingChanges)
            }
        }
    }
}
