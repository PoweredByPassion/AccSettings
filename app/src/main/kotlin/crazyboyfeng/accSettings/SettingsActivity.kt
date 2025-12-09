package crazyboyfeng.accSettings

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ContentFrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceFragmentCompat
import com.topjohnwu.superuser.Shell
import crazyboyfeng.accSettings.fragment.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        if (!Shell.rootAccess()) {
            val textView = TextView(this)
            textView.setText(R.string.need_root_permission)
            textView.gravity = Gravity.CENTER
            val contentFrameLayout = findViewById<ContentFrameLayout>(android.R.id.content)
            contentFrameLayout.addView(textView)
            return
        }
        supportFragmentManager.addOnBackStackChangedListener {
            val preferenceFragment =
                supportFragmentManager.findFragmentById(android.R.id.content) as PreferenceFragmentCompat
            supportActionBar?.subtitle = preferenceFragment.preferenceScreen.title
        }
        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }
}