package crazyboyfeng.accSettings.fragment

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreferencePlus
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.topjohnwu.superuser.Shell
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccHandler
import crazyboyfeng.accSettings.acc.Command
import crazyboyfeng.accSettings.data.AccDataStore
import crazyboyfeng.android.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var accPreferenceCategory: PreferenceCategory
    private lateinit var daemonPreference: Preference
    private lateinit var configPreference: Preference

    private enum class AccStatus {
        NOT_INSTALLED,
        INSTALLED,
        NEEDS_UPDATE,
        ERROR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        reload()
        checkAcc()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.clipToPadding = false
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.preference_list_horizontal_padding)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.preference_list_vertical_padding)
        listView.updatePadding(
            left = horizontalPadding,
            top = verticalPadding,
            right = horizontalPadding,
            bottom = verticalPadding * 2
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_more) {
            MoreFragment().show(childFragmentManager, "more")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun reload() {
        setPreferencesFromResource(R.xml.settings_preferences, null)
        accPreferenceCategory = findPreference(getString(R.string.acc)) ?: return
        daemonPreference = findPreference(getString(R.string.acc_daemon)) ?: return
        configPreference = findPreference(getString(R.string.configuration)) ?: return
    }

    fun refreshAccState() {
        if (!isAdded) return
        disableAccControls()
        checkAcc()
    }

    private fun checkAcc() = lifecycleScope.launch {
        // Check root permission first in background thread
        var hasRoot = withContext(Dispatchers.IO) { Shell.rootAccess() }
        if (!hasRoot) {
            hasRoot = withContext(Dispatchers.IO) {
                Shell.getShell()
                Shell.rootAccess()
            }
        }

        if (!hasRoot) {
            accPreferenceCategory.summary = getString(R.string.acc_need_root)
            disableAccControls()
            return@launch
        }

        var status: AccStatus = AccStatus.NOT_INSTALLED
        var installedVersion: String? = null
        var retryCount = 0

        while (isActive && retryCount < 5) {
            try {
                val versions = Command.getVersion()
                installedVersion = versions.second
                val installedVersionCode = versions.first
                val bundledVersionCode = resources.getInteger(R.integer.acc_version_code)

                status = when {
                    installedVersionCode == 0 -> AccStatus.NOT_INSTALLED
                    installedVersionCode < bundledVersionCode -> AccStatus.NEEDS_UPDATE
                    else -> AccStatus.INSTALLED
                }
                if (installedVersionCode > 0) {
                    try {
                        AccHandler().serve()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to prepare ACC service", e)
                    }
                }
                break
            } catch (e: Exception) {
                if (retryCount < 4) {
                    delay(1000)
                    retryCount++
                    continue
                } else {
                    status = AccStatus.ERROR
                    accPreferenceCategory.summary = e.localizedMessage ?: getString(R.string.command_failed)
                    disableAccControls()
                    return@launch
                }
            }
        }
        updateAccStatusUI(status, installedVersion)
    }

    private fun updateAccStatusUI(status: AccStatus, installedVersion: String?) {
        val bundledVersionName = getString(R.string.acc_version_name)

        when (status) {
            AccStatus.NOT_INSTALLED -> {
                accPreferenceCategory.summary = getString(R.string.acc_not_installed)
                disableAccControls()
            }
            AccStatus.NEEDS_UPDATE -> {
                accPreferenceCategory.summary = getString(R.string.acc_installed_version, installedVersion)
                enableAccControls()
            }
            AccStatus.INSTALLED -> {
                if (installedVersion != null && installedVersion != bundledVersionName) {
                    accPreferenceCategory.summary = getString(R.string.installed_possibly_incompatible, installedVersion)
                } else {
                    accPreferenceCategory.summary = getString(R.string.acc_up_to_date)
                }
                enableAccControls()
            }
            AccStatus.ERROR -> {}
        }
    }

    private fun enableAccControls() {
        preferenceManager.preferenceDataStore = AccDataStore(requireContext())
        daemonPreference.isEnabled = true
        configPreference.isEnabled = true

        val info = findPreference<PreferenceCategory>(getString(R.string.info_status))!!
        info.isVisible = true
        val infoTemp = findPreference<EditTextPreferencePlus>(getString(R.string.info_temp))!!
        infoTemp.setSummaryProvider {
            val preference = it as EditTextPreferencePlus
            preference.text
        }
        updateInfo()
    }

    private fun disableAccControls() {
        daemonPreference.isEnabled = false
        configPreference.isEnabled = false
        val info = findPreference<PreferenceCategory>(getString(R.string.info_status))
        info?.isVisible = false
    }

    private fun updateInfo() = lifecycleScope.launch {
        while (isActive) {
            try {
                val properties = Command.getInfo()
                for (property in properties) {
                    val value = property.value as String
                    if (value.isEmpty()) continue
                    val key = property.key as String
                    when (val preference = findPreference<Preference>(key)) {
                        is EditTextPreferencePlus -> preference.text = value
                        else -> preference?.summary = value
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get battery info", e)
                val message = e.localizedMessage
                if (!message.isNullOrBlank()) {
                    accPreferenceCategory.summary = message
                }
            }
            delay(1000)
        }
    }

    private companion object {
        const val TAG = "SettingsFragment"
    }
}
