package app.owlow.accsettings.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForceStopChargingStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun default_state_is_inactive() {
        val store = freshStore()
        val state = store.load()
        assertFalse(state.active)
        assertNull(state.condition)
        assertNull(state.startedAt)
    }

    @Test
    fun save_and_load_active_with_condition() {
        val store = freshStore()
        store.save(ForceStopState(active = true, condition = "1h", startedAt = 1234L))

        val loaded = store.load()
        assertTrue(loaded.active)
        assertEquals("1h", loaded.condition)
        assertEquals(1234L, loaded.startedAt)
    }

    @Test
    fun save_unconditional_active_has_null_condition() {
        val store = freshStore()
        store.save(ForceStopState(active = true, condition = null, startedAt = 1234L))

        val loaded = store.load()
        assertTrue(loaded.active)
        assertNull(loaded.condition)
        assertEquals(1234L, loaded.startedAt)
    }

    @Test
    fun clear_resets_to_inactive() {
        val store = freshStore()
        store.save(ForceStopState(active = true, condition = "50%", startedAt = 1234L))

        store.clear()

        val loaded = store.load()
        assertFalse(loaded.active)
        assertNull(loaded.condition)
        assertNull(loaded.startedAt)
    }

    private fun freshStore(): ForceStopChargingStore {
        // Fresh prefs per test: use a unique name so state doesn't leak across tests.
        val name = "force_stop_test_${System.nanoTime()}"
        return ForceStopChargingStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}
