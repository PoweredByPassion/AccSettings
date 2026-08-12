package app.owlow.accsettings

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Transparent, no-UI target for app shortcuts.
 *
 * Android launches a shortcut's `<intent>` with `startActivity`, which cannot start a
 * [BroadcastReceiver]. So shortcuts point here instead: it reads the `quickaction:` URI,
 * dispatches it via [QuickActionDispatcher], and finishes immediately (visually transparent,
 * so the user just sees the quick action run without leaving their current screen).
 */
class QuickActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriString = intent.dataString
        if (uriString != null) {
            QuickActionDispatcher.dispatch(this, uriString) {
                finish()
            }
        } else {
            finish()
        }
    }
}
