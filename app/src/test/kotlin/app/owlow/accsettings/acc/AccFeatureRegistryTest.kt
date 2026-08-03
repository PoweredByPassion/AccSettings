package app.owlow.accsettings.acc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccFeatureRegistryTest {
    @Test
    fun feature_metadata_includes_initial_standard_groups() {
        val keys = AccFeatureRegistry.features
            .filter { it.exposeLevel == ExposeLevel.STANDARD }
            .map { it.key }
            .toSet()

        assertEquals(
            setOf(
                "capacity",
                "temperature",
                "cooldown",
                "charging_control",
                "runtime_behavior",
                "current_voltage",
                "stats_reset"
            ),
            keys
        )
    }

    @Test
    fun capability_gating_hides_unsupported_features() {
        val capability = AccCapability.from(
            AccProbeFacts(
                hasRoot = true,
                availableEntrypoints = listOf("/dev/acca"),
                selectedEntrypoint = "/dev/acca",
                accVersionName = "2025.5.18-dev",
                accVersionCode = 202505180,
                daemonRunning = true,
                canReadInfo = true,
                canReadCurrentConfig = true,
                canReadDefaultConfig = true,
                canReadLogs = true,
                canExportDiagnostics = true,
                supportedChargingSwitches = emptyList(),
                preferredChargingSwitch = null,
                supportsCurrentControl = false,
                supportsVoltageControl = false,
                supportedCapacityModes = setOf(CapacityMode.PERCENT),
                supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
            )
        )

        assertFalse(AccFeatureRegistry.isAvailable("charging_control", capability))
        assertFalse(AccFeatureRegistry.isAvailable("current_voltage", capability))
        assertTrue(AccFeatureRegistry.isAvailable("capacity", capability))
    }

    @Test
    fun grouped_serializer_lookup_returns_registered_serializer() {
        val serializer = AccFeatureRegistry.serializerFor("capacity")

        assertNotNull(serializer)
        assertEquals(
            "(5 70 72 80 true)",
            serializer!!.serialize(
                CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL)
            )
        )
    }

    @Test
    fun advanced_only_feature_is_registered() {
        val feature = AccFeatureRegistry.require("raw_patch")

        assertEquals(ExposeLevel.ADVANCED, feature.exposeLevel)
        assertEquals(FeatureGroup.RUNTIME_BEHAVIOR, feature.group)
    }
}
