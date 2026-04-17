package crazyboyfeng.accSettings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import crazyboyfeng.accSettings.R
import crazyboyfeng.accSettings.acc.AccInstallState
import crazyboyfeng.accSettings.acc.AccStateManager
import crazyboyfeng.android.preference.PreferenceFragmentCompat
import kotlinx.coroutines.launch

class MoreFragment : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_more, container, false)
        childFragmentManager.beginTransaction()
            .replace(R.id.more_container, MorePreferenceFragment())
            .commit()
        return view
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet =
            dialog?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return
        bottomSheet.setBackgroundResource(android.R.color.transparent)
        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
    }

    class MorePreferenceFragment : PreferenceFragmentCompat() {
        private lateinit var accVersionInfo: Preference
        private lateinit var installAccPreference: Preference
        private lateinit var uninstallAccPreference: Preference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.more_preferences, rootKey)
            accVersionInfo = findPreference(getString(R.string.acc_version_info)) ?: return
            installAccPreference = findPreference(getString(R.string.install_acc)) ?: return
            uninstallAccPreference = findPreference(getString(R.string.uninstall_acc)) ?: return

            installAccPreference.setOnPreferenceClickListener {
                performAccAction(Action.INSTALL)
                true
            }

            uninstallAccPreference.setOnPreferenceClickListener {
                performAccAction(Action.UNINSTALL)
                true
            }

            checkStatus()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            listView.clipToPadding = false
            val horizontalPadding =
                resources.getDimensionPixelSize(R.dimen.preference_list_compact_horizontal_padding)
            val verticalPadding =
                resources.getDimensionPixelSize(R.dimen.preference_list_compact_vertical_padding)
            listView.updatePadding(
                left = horizontalPadding,
                top = verticalPadding,
                right = horizontalPadding,
                bottom = verticalPadding * 2
            )
        }

        private enum class Action { INSTALL, UNINSTALL }

        private fun performAccAction(action: Action) {
            lifecycleScope.launch {
                try {
                    installAccPreference.isEnabled = false
                    uninstallAccPreference.isEnabled = false
                    accVersionInfo.summary = getString(R.string.initializing)
                    when (action) {
                        Action.INSTALL -> {
                            AccStateManager.ensureInstalled()
                            Toast.makeText(requireContext(), R.string.acc_install_success, Toast.LENGTH_SHORT).show()
                        }
                        Action.UNINSTALL -> {
                            AccStateManager.uninstall()
                            Toast.makeText(requireContext(), R.string.acc_uninstall_success, Toast.LENGTH_SHORT).show()
                        }
                    }
                    checkStatus()
                    refreshParent()
                } catch (e: Exception) {
                    val message = e.localizedMessage ?: getString(R.string.command_failed)
                    accVersionInfo.summary = message
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                } finally {
                    installAccPreference.isEnabled = true
                    uninstallAccPreference.isEnabled = true
                }
            }
        }

        private fun checkStatus() = lifecycleScope.launch {
            try {
                val bundledVersionName = getString(R.string.acc_version_name)
                AccStateManager.refreshNow()
                val status = AccStateManager.getCurrentStatus()
                    ?: throw IllegalStateException(getString(R.string.command_failed))

                when (status.installState) {
                    AccInstallState.NOT_INSTALLED -> {
                        accVersionInfo.summary = getString(R.string.acc_not_installed)
                        installAccPreference.title = getString(R.string.install_acc_title)
                        installAccPreference.summary = getString(R.string.acc_bundled_version, bundledVersionName)
                        installAccPreference.isEnabled = true
                        uninstallAccPreference.isVisible = false
                    }
                    AccInstallState.BROKEN_INSTALL -> {
                        accVersionInfo.summary = getString(R.string.command_failed)
                        installAccPreference.title = getString(R.string.install_acc_title)
                        installAccPreference.summary = getString(R.string.acc_bundled_version, bundledVersionName)
                        installAccPreference.isEnabled = true
                        uninstallAccPreference.isVisible = true
                    }
                    AccInstallState.UPDATE_AVAILABLE -> {
                        accVersionInfo.summary = getString(R.string.acc_installed_version, status.installedVersionName)
                        installAccPreference.title = getString(R.string.install_acc_title)
                        installAccPreference.summary = getString(R.string.acc_bundled_version, bundledVersionName)
                        installAccPreference.isEnabled = true
                        uninstallAccPreference.isVisible = true
                    }
                    AccInstallState.UP_TO_DATE -> {
                        accVersionInfo.summary = getString(R.string.acc_up_to_date_with_version, status.installedVersionName)
                        installAccPreference.title = getString(R.string.update_acc_title)
                        installAccPreference.isEnabled = false
                        installAccPreference.summary = getString(R.string.acc_bundled_version, bundledVersionName)
                        uninstallAccPreference.isVisible = true
                    }
                }
            } catch (e: Exception) {
                accVersionInfo.summary = e.localizedMessage ?: getString(R.string.command_failed)
            }
        }

        private fun refreshParent() {
            (parentFragment as? SettingsFragment)?.refreshAccState()
        }
    }
}
