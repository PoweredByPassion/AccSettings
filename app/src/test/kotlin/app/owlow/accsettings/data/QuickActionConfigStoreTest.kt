package app.owlow.accsettings.data

import android.content.Context
import app.owlow.accsettings.quickaction.QuickActionConfig
import app.owlow.accsettings.quickaction.QuickActionSlot
import app.owlow.accsettings.quickaction.QuickActionSlotType
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickActionConfigStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun default_load_returns_original_five_slots() {
        val store = freshStore()
        val config = store.load()

        assertEquals(5, config.slots.size)
        assertEquals(
            QuickActionConfig.DEFAULT.slots,
            config.slots
        )
        assertTrue(config.showBatteryRow)
    }

    @Test
    fun save_and_load_round_trips_slots() {
        val store = freshStore()
        val config = QuickActionConfig(
            slots = listOf(
                QuickActionSlot(QuickActionSlotType.PAUSE, "45m"),
                QuickActionSlot(QuickActionSlotType.CHARGE_TO, "88%"),
                QuickActionSlot(QuickActionSlotType.FORCE_FULL, "90"),
                QuickActionSlot(QuickActionSlotType.CANCEL, null)
            ),
            showBatteryRow = false
        )

        store.save(config)
        val loaded = store.load()

        assertEquals(config, loaded)
    }

    @Test
    fun save_with_more_than_five_slots_clamps_to_five() {
        val store = freshStore()
        val config = QuickActionConfig(
            slots = List(7) { QuickActionSlot(QuickActionSlotType.PAUSE, "30m") }
        )

        store.save(config)
        val loaded = store.load()

        assertEquals(5, loaded.slots.size)
    }

    @Test
    fun corrupted_json_returns_default() {
        // Write a corrupted JSON string directly into a fresh prefs instance.
        val name = "quick_action_config_test_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString("quick_action_slots", "not json{{").apply()

        val loaded = QuickActionConfigStore(prefs).load()

        // Corrupted JSON degrades to the default slot list.
        assertEquals(QuickActionConfig.DEFAULT.slots, loaded.slots)
    }

    @Test
    fun empty_slots_persists_as_empty() {
        val store = freshStore()
        store.save(QuickActionConfig(slots = emptyList(), showBatteryRow = true))

        val loaded = store.load()

        assertTrue(loaded.slots.isEmpty())
        assertTrue(loaded.showBatteryRow)
    }

    @Test
    fun battery_row_toggle_persists() {
        val store = freshStore()
        store.save(QuickActionConfig(slots = emptyList(), showBatteryRow = false))

        val loaded = store.load()

        assertFalse(loaded.showBatteryRow)
    }

    @Test
    fun clear_resets_to_default() {
        val store = freshStore()
        store.save(QuickActionConfig(slots = emptyList(), showBatteryRow = false))

        store.clear()

        val loaded = store.load()
        assertEquals(QuickActionConfig.DEFAULT.slots, loaded.slots)
        assertTrue(loaded.showBatteryRow)
    }

    private fun freshStore(): QuickActionConfigStore {
        val name = "quick_action_config_test_${System.nanoTime()}"
        return QuickActionConfigStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}
