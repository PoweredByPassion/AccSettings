package crazyboyfeng.accSettings.fragment

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.EditTextPreferencePlus
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreference
import com.topjohnwu.superuser.Shell
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccHandler
import crazyboyfeng.accSettings.acc.AccInstallState
import crazyboyfeng.accSettings.acc.AccSettingsSummary
import crazyboyfeng.accSettings.acc.AccStatus
import crazyboyfeng.accSettings.acc.AccStateManager
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
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        reload()
        // 显示 loading 状态，避免用户误以为 ACC 未启用
        showLoadingState()
        // 使用缓存状态立即更新 UI，并开始观察状态变化
        observeAccStatus()
    }

    /**
     * 显示加载中状态
     */
    private fun showLoadingState() {
        accPreferenceCategory.summary = getString(R.string.acc_detecting)
        disableAccControls()

        // 如果缓存中没有状态，显示 loading 对话框
        if (AccStateManager.getCurrentStatus() == null && loadingDialog == null) {
            loadingDialog = AlertDialog.Builder(requireContext())
                .setMessage(R.string.acc_detecting)
                .setCancelable(false)
                .create()
            loadingDialog?.show()
        }
    }

    /**
     * 隐藏 loading 对话框
     */
    private fun hideLoadingState() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onResume() {
        super.onResume()
        // 触发一次强制刷新（可选，确保状态最新）
        lifecycleScope.launch {
            AccStateManager.refreshNow()
        }
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
        lifecycleScope.launch {
            AccStateManager.refreshNow()
        }
    }

    /**
     * 观察 ACC 状态变化并更新 UI
     */
    private fun observeAccStatus() = lifecycleScope.launch {
        // 使用 repeatOnLifecycle 确保在 RESUMED 状态下观察
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            AccStateManager.accStatus.collect { status ->
                if (status == null) {
                    // 如果缓存为空，触发一次同步获取
                    checkAcc()
                } else {
                    // 隐藏 loading 对话框
                    hideLoadingState()

                    // 使用缓存状态更新 UI
                    updateAccStatusUI(status)

                    // 如果 ACC 已安装，启动服务
                    if (status.installState != AccInstallState.NOT_INSTALLED) {
                        try {
                            AccHandler().serve()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to prepare ACC service", e)
                        }
                    }
                }
            }
        }
    }

    private fun checkAcc() = lifecycleScope.launch {
        try {
            AccStateManager.refreshNow()
        } catch (e: Exception) {
            accPreferenceCategory.summary = e.localizedMessage ?: getString(R.string.command_failed)
            disableAccControls()
        }
    }

    private fun updateAccStatusUI(status: AccStatus) {
        when (AccStateManager.toSettingsState(status).summary) {
            AccSettingsSummary.NOT_INSTALLED -> {
                accPreferenceCategory.summary = getString(R.string.acc_not_installed)
                disableAccControls()
                scheduleFollowUpRefresh()
            }
            AccSettingsSummary.BROKEN_INSTALL -> {
                accPreferenceCategory.summary = getString(R.string.command_failed)
                disableAccControls()
            }
            AccSettingsSummary.UPDATE_AVAILABLE -> {
                accPreferenceCategory.summary = getString(R.string.acc_installed_version, status.installedVersionName)
                enableAccControls(status.daemonRunning)
            }
            AccSettingsSummary.UP_TO_DATE -> {
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
                AccStateManager.refreshNow()
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
