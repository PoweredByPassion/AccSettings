package app.owlow.accsettings.ui.config

import app.owlow.accsettings.MainDispatcherRule
import app.owlow.accsettings.acc.CapacityConfig
import app.owlow.accsettings.acc.ConfigGroupMode
import app.owlow.accsettings.acc.DraftStatus
import app.owlow.accsettings.acc.GroupedConfigRead
import app.owlow.accsettings.acc.TemperatureConfig
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

        viewModel.onFieldChanged("charging_switch", "0").join()

        assertTrue(viewModel.uiState.value.hasPendingChanges)
        assertEquals(0, store.applyCalls)
        assertEquals(0, store.sideEffectCalls)
    }

    @Test
    fun refresh_exposesCurrentValuesAcrossEditableFields() = runTest {
        val store = FakeConfigRepository(
            initialValues = mapOf(
                "set_shutdown_capacity" to "20",
                "set_pause_capacity" to "80",
                "charging_switch" to "main",
                "current_workaround" to "true"
            )
        )
        val viewModel = ConfigViewModel(store)

        viewModel.refresh().join()

        val fields = viewModel.uiState.value.groups.flatMap { it.fields }.associateBy { it.key }
        assertEquals("20", fields.getValue("set_shutdown_capacity").value)
        assertEquals("80", fields.getValue("set_pause_capacity").value)
        assertEquals("main", fields.getValue("charging_switch").value)
        assertEquals("true", fields.getValue("current_workaround").value)
    }

    @Test
    fun editingNumericField_updatesThatFieldValue() = runTest {
        val store = FakeConfigRepository(
            initialValues = mapOf("set_shutdown_capacity" to "15")
        )
        val viewModel = ConfigViewModel(store)

        viewModel.onFieldChanged("set_shutdown_capacity", "25").join()

        val fields = viewModel.uiState.value.groups.flatMap { it.fields }.associateBy { it.key }
        assertEquals("25", fields.getValue("set_shutdown_capacity").value)
    }

    @Test
    fun applyChanges_exposesSuccessFeedbackNearActionArea() = runTest {
        val store = FakeConfigRepository(applyMessage = "Changes applied successfully")
        val viewModel = ConfigViewModel(store)

        viewModel.applyChanges().join()

        assertEquals(
            ConfigFeedback("Changes applied successfully", isError = false),
            viewModel.uiState.value.applyFeedback
        )
    }

    private class FakeConfigRepository : ConfigRepository {
        var applyCalls = 0
        var sideEffectCalls = 0
        private val values = linkedMapOf<String, String>()
        private val applyMessage: String?

        constructor(
            initialValues: Map<String, String> = emptyMap(),
            applyMessage: String? = null
        ) {
            values.putAll(initialValues)
            this.applyMessage = applyMessage
        }

        override suspend fun loadSnapshot(): ConfigDraftSnapshot = snapshotOf(values)

        override suspend fun updateField(key: String, value: String): ConfigDraftSnapshot {
            values[key] = value
            return snapshotOf(values)
        }

        override suspend fun discardDraft(): ConfigDraftSnapshot {
            values.clear()
            return snapshotOf(values)
        }

        override suspend fun applyDraft(): ConfigDraftSnapshot {
            applyCalls++
            return snapshotOf(values, applyMessage = applyMessage)
        }

        private fun snapshotOf(
            fieldValues: Map<String, String>,
            applyMessage: String? = null
        ): ConfigDraftSnapshot {
            val applied = GroupedConfigRead(
                current = Properties(),
                defaults = Properties(),
                currentCapacity = CapacityConfig(10, 70, 72, 80, false, ConfigGroupMode.NORMAL),
                currentTemperature = TemperatureConfig(42, 45, 43, 50, ConfigGroupMode.NORMAL)
            )
            val draft = GroupedConfigRead(
                current = Properties().apply {
                    fieldValues.forEach { (key, value) -> setProperty(key, value) }
                },
                defaults = Properties(),
                currentCapacity = CapacityConfig(
                    fieldValues["set_shutdown_capacity"]?.toIntOrNull() ?: 10,
                    fieldValues["set_cooldown_capacity"]?.toIntOrNull() ?: 70,
                    fieldValues["set_resume_capacity"]?.toIntOrNull() ?: 72,
                    fieldValues["set_pause_capacity"]?.toIntOrNull() ?: 80,
                    false,
                    ConfigGroupMode.NORMAL
                ),
                currentTemperature = TemperatureConfig(
                    fieldValues["set_cooldown_temp"]?.toIntOrNull() ?: 42,
                    fieldValues["set_max_temp"]?.toIntOrNull() ?: 45,
                    43,
                    fieldValues["set_shutdown_temp"]?.toIntOrNull() ?: 50,
                    ConfigGroupMode.NORMAL
                )
            )
            return ConfigDraftSnapshot(
                applied = applied,
                draft = draft,
                draftStatus = if (fieldValues.isEmpty()) DraftStatus.CLEAN else DraftStatus.MODIFIED,
                applyFeedback = applyMessage?.let { ConfigFeedback(it, isError = false) }
            )
        }
    }
}
