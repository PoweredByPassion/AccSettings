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
import androidx.preference.SwitchPreference
import com.topjohnwu.superuser.Shell
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccHandler
import crazyboyfeng.accSettings.acc.AccInstallState
import crazyboyfeng.accSettings.acc.AccStatus
import crazyboyfeng.accSettings.acc.AccStatusResolver
import crazyboyfeng.accSettings.acc.Command
import crazyboyfeng.accSettings.data.AccDataStore
import crazyboyfeng.android.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var accPreferenceCategory: PreferenceCategory
    private lateinit var daemonPreference: SwitchPreference
    private lateinit var configPreference: Preference
    private var infoJob: Job? = null
    private var refreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        reload()
        checkAcc()
    }

    override fun onResume() {
        super.onResume()
        refreshAccState()
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
        refreshJob?.cancel()
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

        var retryCount = 0

        while (isActive && retryCount < 5) {
            try {
                val (installedVersionCode, installedVersionName) = Command.getVersion()
                val bundledVersionCode = resources.getInteger(R.integer.acc_version_code)
                val daemonRunning = Command.isDaemonRunning()

                val status = AccStatusResolver.resolve(
                    installedVersionCode = installedVersionCode,
                    installedVersionName = installedVersionName,
                    bundledVersionCode = bundledVersionCode,
                    daemonRunning = daemonRunning
                )

                if (installedVersionCode > 0) {
                    try {
                        AccHandler().serve()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to prepare ACC service", e)
                    }
                }

                updateAccStatusUI(status)
                return@launch
            } catch (e: Exception) {
                if (retryCount < 4) {
                    delay(1000)
                    retryCount++
                    continue
                } else {
                    accPreferenceCategory.summary = e.localizedMessage ?: getString(R.string.command_failed)
                    disableAccControls()
                    return@launch
                }
            }
        }
    }

    private fun updateAccStatusUI(status: AccStatus) {
        when (status.installState) {
            AccInstallState.NOT_INSTALLED -> {
                accPreferenceCategory.summary = getString(R.string.acc_not_installed)
                disableAccControls()
                scheduleFollowUpRefresh()
            }
            AccInstallState.UPDATE_AVAILABLE -> {
                accPreferenceCategory.summary = getString(R.string.acc_installed_version, status.installedVersionName)
                enableAccControls(status.daemonRunning)
            }
            AccInstallState.UP_TO_DATE -> {
                accPreferenceCategory.summary = getString(
                    R.string.acc_up_to_date_with_version,
                    status.installedVersionName
                )
                enableAccControls(status.daemonRunning)
            }
        }
    }

    private fun enableAccControls(daemonRunning: Boolean) {
        preferenceManager.preferenceDataStore = AccDataStore(requireContext())
        daemonPreference.isEnabled = true
        daemonPreference.isChecked = daemonRunning
        configPreference.isEnabled = true

        val info = findPreference<PreferenceCategory>(getString(R.string.info_status))!!
        info.isVisible = true
        val infoTemp = findPreference<EditTextPreferencePlus>(getString(R.string.info_temp))!!
        infoTemp.setSummaryProvider {
            val preference = it as EditTextPreferencePlus
            preference.text
        }
        infoJob?.cancel()
        infoJob = updateInfo()
    }

    private fun disableAccControls() {
        infoJob?.cancel()
        infoJob = null
        daemonPreference.isEnabled = false
        daemonPreference.isChecked = false
        configPreference.isEnabled = false
        val info = findPreference<PreferenceCategory>(getString(R.string.info_status))
        info?.isVisible = false
    }

    private fun scheduleFollowUpRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            delay(2500)
            if (isAdded) {
                checkAcc()
            }
        }
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
            } catch (e: Command.NotInstalledException) {
                accPreferenceCategory.summary = getString(R.string.acc_not_installed)
                disableAccControls()
                return@launch
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get battery info", e)
                val message = e.localizedMessage
                if (!message.isNullOrBlank()) {
                    accPreferenceCategory.summary = message
                }
            }
            delay(3000)
        }
    }

    private companion object {
        const val TAG = "SettingsFragment"
    }
}
