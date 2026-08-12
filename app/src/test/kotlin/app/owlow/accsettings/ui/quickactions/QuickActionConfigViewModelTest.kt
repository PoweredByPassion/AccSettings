package app.owlow.accsettings.ui.quickactions

import android.content.Context
import app.owlow.accsettings.MainDispatcherRule
import app.owlow.accsettings.data.QuickActionConfigStore
import app.owlow.accsettings.quickaction.QuickActionConfig
import app.owlow.accsettings.quickaction.QuickActionSlot
import app.owlow.accsettings.quickaction.QuickActionSlotType
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class QuickActionConfigViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun addSlot_appendsAndOpensParamPicker() {
        val vm = freshViewModel()
        val initialSize = vm.uiState.value.slots.size

        vm.addSlot(QuickActionSlotType.PAUSE)

        assertEquals(initialSize + 1, vm.uiState.value.slots.size)
        assertEquals(initialSize, vm.uiState.value.editingSlotIndex)
        assertEquals(QuickActionSlotType.PAUSE, vm.uiState.value.slots.last().type)
    }

    @Test
    fun addCancel_slot_addsWithoutParamPicker() {
        val vm = freshViewModel()
        vm.addSlot(QuickActionSlotType.CANCEL)

        assertEquals(QuickActionSlotType.CANCEL, vm.uiState.value.slots.last().type)
        assertEquals(null, vm.uiState.value.editingSlotIndex)
    }

    @Test
    fun removeSlot_removesAtIndex() {
        val vm = freshViewModel()
        val before = vm.uiState.value.slots.size

        vm.removeSlot(0)

        assertEquals(before - 1, vm.uiState.value.slots.size)
    }

    @Test
    fun moveSlotUp_swapsWithPrevious() {
        val vm = freshViewModel()
        val original = vm.uiState.value.slots

        vm.moveSlotUp(1)

        // The labels/params swap; derived index/canMove fields recompute.
        assertEquals(original[1].label, vm.uiState.value.slots[0].label)
        assertEquals(original[0].label, vm.uiState.value.slots[1].label)
    }

    @Test
    fun moveSlotUp_atFirst_noop() {
        val vm = freshViewModel()
        val before = vm.uiState.value.slots.map { it.label }

        vm.moveSlotUp(0)

        assertEquals(before, vm.uiState.value.slots.map { it.label })
    }

    @Test
    fun moveSlotDown_atLast_noop() {
        val vm = freshViewModel()
        val before = vm.uiState.value.slots.map { it.label }

        vm.moveSlotDown(vm.uiState.value.slots.lastIndex)

        assertEquals(before, vm.uiState.value.slots.map { it.label })
    }

    @Test
    fun setSlotParam_updatesParam() {
        val (vm, store) = freshViewModelWithStore()
        vm.addSlot(QuickActionSlotType.PAUSE)
        val index = vm.uiState.value.slots.lastIndex

        vm.setSlotParam(index, "45m")

        assertEquals("45m", vm.uiState.value.slots[index].param)
        // Persisted too.
        assertEquals("45m", store.load().slots[index].param)
    }

    @Test
    fun toggleBatteryRow_flipsAndPersists() {
        val (vm, store) = freshViewModelWithStore()
        val initial = vm.uiState.value.showBatteryRow

        vm.toggleBatteryRow()

        assertEquals(!initial, vm.uiState.value.showBatteryRow)
        assertEquals(!initial, store.load().showBatteryRow)
    }

    @Test
    fun canAdd_becomesFalseAtFiveSlots() {
        val vm = freshViewModel()
        // Fresh store starts with 2 slots; adding 3 more reaches the 5 cap.
        assertEquals(2, vm.uiState.value.slots.size)
        assertTrue(vm.uiState.value.canAdd)

        vm.addSlot(QuickActionSlotType.PAUSE)
        vm.addSlot(QuickActionSlotType.FORCE_FULL)
        vm.addSlot(QuickActionSlotType.CANCEL)
        assertEquals(5, vm.uiState.value.slots.size)
        assertFalse(vm.uiState.value.canAdd)

        vm.addSlot(QuickActionSlotType.PAUSE)
        // Add is a no-op at the cap.
        assertEquals(5, vm.uiState.value.slots.size)
    }

    private fun freshViewModel(): QuickActionConfigViewModel = freshViewModelWithStore().first

    /** Builds a VM + its backing store so tests can assert persistence on the same prefs. */
    private fun freshViewModelWithStore(): Pair<QuickActionConfigViewModel, QuickActionConfigStore> {
        val store = freshStore()
        // Reset to a known small state so add/remove/reorder/set-param behave predictably.
        store.save(
            QuickActionConfig(
                slots = listOf(
                    QuickActionSlot(QuickActionSlotType.PAUSE, "30m"),
                    QuickActionSlot(QuickActionSlotType.CHARGE_TO, "85%")
                ),
                showBatteryRow = true
            )
        )
        return QuickActionConfigViewModel(context, store) to store
    }

    private fun freshStore(): QuickActionConfigStore {
        val name = "qa_config_vm_test_${System.nanoTime()}"
        return QuickActionConfigStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}
