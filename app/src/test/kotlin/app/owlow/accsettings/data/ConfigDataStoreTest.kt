package app.owlow.accsettings.data

import app.owlow.accsettings.acc.ApplyGroupedPatchRequest
import app.owlow.accsettings.acc.ApplyGroupedPatchResult
import app.owlow.accsettings.acc.CapacityConfig
import app.owlow.accsettings.acc.ConfigGroupMode
import app.owlow.accsettings.acc.GroupedConfigRead
import app.owlow.accsettings.acc.PatchGroup
import app.owlow.accsettings.acc.TemperatureConfig
import org.junit.Assert.assertFalse
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

    @Test
    fun preloaded_config_avoids_blocking_loader_during_reads() {
        var loaderCalls = 0
        val groupedConfig = groupedConfig()
        val store = ConfigDataStore(
            supportInVoltageKey = "support_in_voltage",
            currentConfigLoader = {
                loaderCalls++
                groupedConfig
            },
            patchApplier = { request ->
                ApplyGroupedPatchResult.Success(
                    appliedGroups = request.groups.toList(),
                    verifiedConfig = request.target
                )
            },
            daemonRestartAction = {},
            reinitializeAction = {},
            initialGroupedConfig = groupedConfig
        )

        val pauseCapacity = store.getInt("set_pause_capacity", 0)

        assertEquals(80, pauseCapacity)
        assertEquals(0, loaderCalls)
    }

    @Test
    fun string_edits_do_not_trigger_side_effects_before_apply() {
        val effects = mutableListOf<String>()
        val store = ConfigDataStore(
            supportInVoltageKey = "support_in_voltage",
            currentConfigLoader = { groupedConfig() },
            patchApplier = { request ->
                ApplyGroupedPatchResult.Success(
                    appliedGroups = request.groups.toList(),
                    verifiedConfig = request.target
                )
            },
            daemonRestartAction = { effects += "restart" },
            reinitializeAction = { effects += "reinitialize" }
        )

        store.putString("set_charging_switch", "1234")
        store.putString("set_current_workaround", "true")

        assertTrue(effects.isEmpty())
    }

    @Test
    fun pending_changes_follow_draft_difference() {
        val store = ConfigDataStore(
            supportInVoltageKey = "support_in_voltage",
            currentConfigLoader = { groupedConfig() },
            patchApplier = { request ->
                ApplyGroupedPatchResult.Success(
                    appliedGroups = request.groups.toList(),
                    verifiedConfig = request.target
                )
            },
            daemonRestartAction = {},
            reinitializeAction = {}
        )

        assertFalse(store.hasPendingChanges())
        store.putString("set_charging_switch", "1234")
        assertTrue(store.hasPendingChanges())
        store.discardDraft()
        assertFalse(store.hasPendingChanges())
    }

    @Test
    fun split_threshold_config_populates_editable_fields() {
        val store = testStore(
            groupedConfig = splitGroupedConfig()
        )

        assertEquals(5, store.getInt("set_shutdown_capacity", 0))
        assertEquals(72, store.getInt("set_resume_capacity", 0))
        assertEquals(40, store.getInt("set_resume_temp", 0))
        assertEquals(55, store.getInt("set_shutdown_temp", 0))
    }

    @Test
    fun editing_one_split_capacity_value_preserves_other_live_values() {
        val store = testStore(
            groupedConfig = splitGroupedConfig()
        )

        store.putInt("set_pause_capacity", 85)

        val draftCapacity = store.currentDraftStateForTesting().draft.currentCapacity
        assertEquals(
            CapacityConfig(5, 70, 72, 85, false, ConfigGroupMode.NORMAL),
            draftCapacity
        )
    }

    @Test
    fun new_core_fields_update_draft_and_group_into_expected_apply_batches() {
        val store = testStore(groupedConfig = splitGroupedConfig())

        store.putBoolean("allow_idle_above_pcap", false)
        store.putString("cooldown_current", "500")
        store.putString("max_charging_current", "1200")
        store.putString("apply_on_boot", "battery/input_suspend::0")

        val draft = store.currentDraftStateForTesting().draft.current
        assertEquals("false", draft.getProperty("allow_idle_above_pcap"))
        assertEquals("500", draft.getProperty("cooldown_current"))
        assertEquals("1200", draft.getProperty("max_charging_current"))
        assertEquals("battery/input_suspend::0", draft.getProperty("apply_on_boot"))

        store.applyDraft()

        assertEquals(
            setOf(PatchGroup.COOLDOWN, PatchGroup.CURRENT_VOLTAGE, PatchGroup.RUNTIME_HOOKS),
            store.appliedRequests.single().groups
        )
    }

    @Test
    fun apply_triggers_each_required_side_effect_once() {
        val effects = mutableListOf<String>()
        val store = ConfigDataStore(
            supportInVoltageKey = "support_in_voltage",
            currentConfigLoader = { groupedConfig() },
            patchApplier = { request ->
                ApplyGroupedPatchResult.Success(
                    appliedGroups = request.groups.toList(),
                    verifiedConfig = request.target
                )
            },
            daemonRestartAction = { effects += "restart" },
            reinitializeAction = { effects += "reinitialize" }
        )

        store.putString("set_charging_switch", "1234")
        store.putString("set_current_workaround", "true")
        store.applyDraft()

        assertEquals(listOf("restart", "reinitialize"), effects)
    }

    @Test
    fun failed_apply_does_not_trigger_side_effects() {
        val effects = mutableListOf<String>()
        val store = ConfigDataStore(
            supportInVoltageKey = "support_in_voltage",
            currentConfigLoader = { groupedConfig() },
            patchApplier = {
                ApplyGroupedPatchResult.ValidationFailed(listOf("boom"))
            },
            daemonRestartAction = { effects += "restart" },
            reinitializeAction = { effects += "reinitialize" }
        )

        store.putString("set_charging_switch", "1234")
        val result = store.applyDraft()

        assertTrue(result is ApplyGroupedPatchResult.ValidationFailed)
        assertFalse("No side effects expected after failed apply", effects.isNotEmpty())
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

    private fun splitGroupedConfig(): GroupedConfigRead {
        val current = Properties().apply {
            setProperty("shutdown_capacity", "5")
            setProperty("cooldown_capacity", "70")
            setProperty("resume_capacity", "72")
            setProperty("pause_capacity", "80")
            setProperty("capacity_mask", "false")
            setProperty("cooldown_temp", "45")
            setProperty("max_temp", "50")
            setProperty("resume_temp", "40")
            setProperty("shutdown_temp", "55")
        }
        val defaults = Properties().apply { putAll(current) }
        return GroupedConfigRead(
            current = current,
            defaults = defaults
        )
    }

    private data class TestConfigDataStore(
        private val delegate: ConfigDataStore,
        val appliedRequests: MutableList<ApplyGroupedPatchRequest>
    ) {
        fun putInt(key: String, value: Int) = delegate.putInt(key, value)
        fun putBoolean(key: String, value: Boolean) = delegate.putBoolean(key, value)
        fun putString(key: String, value: String) = delegate.putString(key, value)
        fun getInt(key: String, defaultValue: Int): Int = delegate.getInt(key, defaultValue)
        fun applyDraft() = delegate.applyDraft()
        fun discardDraft() = delegate.discardDraft()
        fun requestProtectedGroupRebuild(group: PatchGroup) = delegate.requestProtectedGroupRebuild(group)
        fun currentDraftStateForTesting() = delegate.currentDraftStateForTesting()
    }
}
