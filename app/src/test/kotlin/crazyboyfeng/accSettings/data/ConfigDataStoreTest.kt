package crazyboyfeng.accSettings.data

import crazyboyfeng.accSettings.acc.ApplyGroupedPatchRequest
import crazyboyfeng.accSettings.acc.ApplyGroupedPatchResult
import crazyboyfeng.accSettings.acc.CapacityConfig
import crazyboyfeng.accSettings.acc.ConfigGroupMode
import crazyboyfeng.accSettings.acc.GroupedConfigRead
import crazyboyfeng.accSettings.acc.PatchGroup
import crazyboyfeng.accSettings.acc.TemperatureConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class ConfigDataStoreTest {
    @Test
    fun edits_update_draft_only() {
        val store = testStore()

        store.putInt("set_pause_capacity", 85)

        assertEquals(85, store.getInt("set_pause_capacity", 0))
        assertEquals(80, store.currentDraftStateForTesting().current.currentCapacity!!.pause)
        assertTrue(store.appliedRequests.isEmpty())
    }

    @Test
    fun apply_is_explicit() {
        val store = testStore()
        store.putInt("set_pause_capacity", 85)

        store.applyDraft()

        assertEquals(1, store.appliedRequests.size)
        assertEquals(setOf(PatchGroup.CAPACITY), store.appliedRequests.single().groups)
        assertEquals(85, store.currentDraftStateForTesting().current.currentCapacity!!.pause)
    }

    @Test
    fun discard_restores_current() {
        val store = testStore()
        store.putInt("set_pause_capacity", 85)

        store.discardDraft()

        assertEquals(80, store.getInt("set_pause_capacity", 0))
        assertEquals(80, store.currentDraftStateForTesting().draft.currentCapacity!!.pause)
    }

    @Test
    fun advanced_custom_groups_are_protected_from_silent_standard_overwrite() {
        val advancedCapacity = CapacityConfig(0, 0, 0, 0, false, ConfigGroupMode.ADVANCED_CUSTOM)
        val store = testStore(
            groupedConfig = groupedConfig(capacity = advancedCapacity)
        )

        store.putInt("set_pause_capacity", 85)

        assertEquals(0, store.getInt("set_pause_capacity", 0))
        store.requestProtectedGroupRebuild(PatchGroup.CAPACITY)
        store.putInt("set_pause_capacity", 85)
        assertEquals(85, store.getInt("set_pause_capacity", 0))
    }

    private fun testStore(groupedConfig: GroupedConfigRead = groupedConfig()): TestConfigDataStore {
        val appliedRequests = mutableListOf<ApplyGroupedPatchRequest>()
        val store = ConfigDataStore(
            supportInVoltageKey = "support_in_voltage",
            currentConfigLoader = { groupedConfig },
            patchApplier = { request: ApplyGroupedPatchRequest ->
                appliedRequests += request
                ApplyGroupedPatchResult.Success(
                    appliedGroups = request.groups.toList(),
                    verifiedConfig = request.target
                )
            },
            daemonRestartAction = {},
            reinitializeAction = {}
        )
        return TestConfigDataStore(store, appliedRequests)
    }

    private fun groupedConfig(
        capacity: CapacityConfig = CapacityConfig(5, 70, 72, 80, false, ConfigGroupMode.NORMAL),
        temperature: TemperatureConfig = TemperatureConfig(42, 45, 43, 50, ConfigGroupMode.NORMAL)
    ): GroupedConfigRead {
        val current = Properties().apply {
            setProperty("capacity", capacity.serialize())
            setProperty("temperature", temperature.serialize())
        }
        return GroupedConfigRead(
            current = current,
            defaults = Properties().apply { putAll(current) },
            currentCapacity = capacity,
            defaultCapacity = capacity,
            currentTemperature = temperature,
            defaultTemperature = temperature
        )
    }

    private data class TestConfigDataStore(
        private val delegate: ConfigDataStore,
        val appliedRequests: MutableList<ApplyGroupedPatchRequest>
    ) {
        fun putInt(key: String, value: Int) = delegate.putInt(key, value)
        fun getInt(key: String, defaultValue: Int): Int = delegate.getInt(key, defaultValue)
        fun applyDraft() = delegate.applyDraft()
        fun discardDraft() = delegate.discardDraft()
        fun requestProtectedGroupRebuild(group: PatchGroup) = delegate.requestProtectedGroupRebuild(group)
        fun currentDraftStateForTesting() = delegate.currentDraftStateForTesting()
    }
}
