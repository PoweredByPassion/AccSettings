package app.owlow.accsettings.acc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class AccDraftStateTest {
    @Test
    fun current_config_copies_into_clean_draft() {
        val current = groupedConfig(
            currentCapacity = CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL),
            currentTemperature = TemperatureConfig(39, 45, 42, 50, ConfigGroupMode.NORMAL)
        )

        val state = AccDraftState.from(current = current, defaults = groupedConfig())

        assertEquals(current.currentCapacity, state.draft.currentCapacity)
        assertEquals(current.currentTemperature, state.draft.currentTemperature)
        assertEquals(DraftStatus.CLEAN, state.status)
    }

    @Test
    fun draft_state_transitions_to_modified_and_advanced_modified() {
        val initial = AccDraftState.from(current = groupedConfig(), defaults = groupedConfig())
        val modified = initial.updateCapacity(
            CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL)
        )
        val advanced = modified.updateCapacity(
            CapacityConfig(5, 101, 72, 80, false, ConfigGroupMode.MIXED_LEGACY)
        )

        assertEquals(DraftStatus.MODIFIED, modified.status)
        assertEquals(DraftStatus.ADVANCED_MODIFIED, advanced.status)
    }

    @Test
    fun protected_group_overwrite_requires_explicit_rebuild() {
        val advanced = AccDraftState.from(current = groupedConfig(), defaults = groupedConfig())
            .updateCapacity(
                CapacityConfig(5, 101, 72, 80, false, ConfigGroupMode.MIXED_LEGACY)
            )

        assertFalse(
            advanced.canOverwrite(
                PatchGroup.CAPACITY,
                allowProtectedGroupRebuild = false
            )
        )
        assertTrue(
            advanced.canOverwrite(
                PatchGroup.CAPACITY,
                allowProtectedGroupRebuild = true
            )
        )
    }

    @Test
    fun stale_base_config_detection_uses_current_snapshot_inputs() {
        val current = groupedConfig()
        val state = AccDraftState.from(current = current, defaults = groupedConfig())
        val changedCurrent = groupedConfig(
            currentCapacity = CapacityConfig(10, 70, 72, 80, true, ConfigGroupMode.NORMAL)
        )

        assertFalse(state.isStaleAgainst(current))
        assertTrue(state.isStaleAgainst(changedCurrent))
    }

    private fun groupedConfig(
        currentCapacity: CapacityConfig? = null,
        currentTemperature: TemperatureConfig? = null
    ): GroupedConfigRead = GroupedConfigRead(
        current = Properties(),
        defaults = Properties(),
        currentCapacity = currentCapacity,
        currentTemperature = currentTemperature
    )
}
