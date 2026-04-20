package crazyboyfeng.accSettings

import android.os.Bundle
import android.util.Log
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import crazyboyfeng.accSettings.fragment.SettingsFragment

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val fragmentContainer = findViewById<android.view.View>(R.id.fragment_container)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = systemBars.top)
            fragmentContainer.updatePadding(bottom = systemBars.bottom)
            insets
        }
        setSupportActionBar(toolbar)

        supportFragmentManager.addOnBackStackChangedListener {
            updateToolbarSubtitle()
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.title = getString(R.string.acc_settings)
        updateToolbarSubtitle()
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragmentName = pref.fragment ?: return false
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, fragmentName)
        fragment.arguments = pref.extras
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(pref.key)
            .commit()
        return true
    }

    private fun updateToolbarSubtitle() {
        val preferenceFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as? PreferenceFragmentCompat
        val subtitle = preferenceFragment?.preferenceScreen?.title?.takeIf { it.isNotBlank() }
            ?: getString(R.string.acc)
        supportActionBar?.subtitle = subtitle
    }
}
