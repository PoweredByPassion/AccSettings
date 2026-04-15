package crazyboyfeng.accSettings

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.topjohnwu.superuser.Shell
import crazyboyfeng.accSettings.fragment.SettingsFragment

class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    companion object {
        private const val TAG = "SettingsActivity"
        init {
            // Set static configurations for libsu
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(120))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

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
