package crazyboyfeng.accSettings.ui.config

import crazyboyfeng.accSettings.MainDispatcherRule
import crazyboyfeng.accSettings.acc.DraftStatus
import crazyboyfeng.accSettings.acc.GroupedConfigRead
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Properties

class ConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun editingField_marksDraftDirty_withoutApplyingSideEffects() = runTest {
        val store = FakeConfigRepository()
        val viewModel = ConfigViewModel(store)

        viewModel.onChargingSwitchChanged("0").join()

        assertTrue(viewModel.uiState.value.hasPendingChanges)
        assertEquals(0, store.applyCalls)
        assertEquals(0, store.sideEffectCalls)
    }

    private class FakeConfigRepository : ConfigRepository {
        var applyCalls = 0
        var sideEffectCalls = 0
        private var currentValue = ""

        override suspend fun loadSnapshot(): ConfigDraftSnapshot = snapshotOf(currentValue)

        override suspend fun updateString(key: String, value: String): ConfigDraftSnapshot {
            currentValue = value
            return snapshotOf(currentValue)
        }

        override suspend fun discardDraft(): ConfigDraftSnapshot {
            currentValue = ""
            return snapshotOf(currentValue)
        }

        override suspend fun applyDraft(): ConfigDraftSnapshot {
            applyCalls++
            return snapshotOf(currentValue)
        }

        private fun snapshotOf(chargingSwitch: String): ConfigDraftSnapshot {
            val applied = GroupedConfigRead(
                current = Properties(),
                defaults = Properties()
            )
            val draft = GroupedConfigRead(
                current = Properties().apply {
                    if (chargingSwitch.isNotEmpty()) {
                        setProperty("set_charging_switch", chargingSwitch)
                    }
                },
                defaults = Properties()
            )
            return ConfigDraftSnapshot(
                applied = applied,
                draft = draft,
                draftStatus = if (chargingSwitch.isEmpty()) DraftStatus.CLEAN else DraftStatus.MODIFIED
            )
        }
    }
}
