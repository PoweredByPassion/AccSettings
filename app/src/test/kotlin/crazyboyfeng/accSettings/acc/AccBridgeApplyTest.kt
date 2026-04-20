package crazyboyfeng.accSettings.acc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class AccBridgeApplyTest {
    @Test
    fun stale_base_config_rejects_apply() = runBlocking {
        val base = groupedConfig(capacity = CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL))
        val latest = groupedConfig(capacity = CapacityConfig(10, 70, 72, 80, true, ConfigGroupMode.NORMAL))
        val request = ApplyGroupedPatchRequest(
            base = base,
            target = groupedConfig(capacity = CapacityConfig(5, 70, 72, 85, true, ConfigGroupMode.NORMAL)),
            groups = setOf(PatchGroup.CAPACITY)
        )
        val bridge = AccBridge(
            capabilityProbe = { capability() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { Properties() },
            defaultConfigReader = { Properties() },
            latestGroupedConfigReader = { latest }
        )

        assertEquals(
            ApplyGroupedPatchResult.StaleBaseConfig,
            bridge.applyGroupedPatch(request)
        )
    }

    @Test
    fun grouped_validation_failure_returns_error() = runBlocking {
        val invalidTarget = groupedConfig(
            capacity = CapacityConfig(90, 70, 72, 80, true, ConfigGroupMode.NORMAL)
        )
        val request = ApplyGroupedPatchRequest(
            base = groupedConfig(),
            target = invalidTarget,
            groups = setOf(PatchGroup.CAPACITY)
        )
        val bridge = AccBridge(
            capabilityProbe = { capability() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { Properties() },
            defaultConfigReader = { Properties() },
            latestGroupedConfigReader = { groupedConfig() }
        )

        assertEquals(
            ApplyGroupedPatchResult.ValidationFailed(listOf("capacity ordering is invalid")),
            bridge.applyGroupedPatch(request)
        )
    }

    @Test
    fun full_apply_success_returns_verified_result() = runBlocking {
        val writes = mutableListOf<PatchGroup>()
        val target = groupedConfig(
            capacity = CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL),
            temperature = TemperatureConfig(39, 45, 42, 50, ConfigGroupMode.NORMAL)
        )
        val request = ApplyGroupedPatchRequest(
            base = groupedConfig(),
            target = target,
            groups = setOf(PatchGroup.CAPACITY, PatchGroup.TEMPERATURE)
        )
        val bridge = AccBridge(
            capabilityProbe = { capability() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { Properties() },
            defaultConfigReader = { Properties() },
            latestGroupedConfigReader = { groupedConfig() },
            groupedPatchWriter = { group: PatchGroup, _: GroupedConfigRead ->
                writes += group
                GroupApplyWriteResult.Success
            },
            verificationGroupedConfigReader = { target }
        )

        assertEquals(
            ApplyGroupedPatchResult.Success(
                appliedGroups = listOf(PatchGroup.CAPACITY, PatchGroup.TEMPERATURE),
                verifiedConfig = target
            ),
            bridge.applyGroupedPatch(request)
        )
        assertEquals(listOf(PatchGroup.CAPACITY, PatchGroup.TEMPERATURE), writes)
    }

    @Test
    fun partial_apply_result_reports_failed_groups() = runBlocking {
        val target = groupedConfig(
            capacity = CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL),
            temperature = TemperatureConfig(39, 45, 42, 50, ConfigGroupMode.NORMAL)
        )
        val request = ApplyGroupedPatchRequest(
            base = groupedConfig(),
            target = target,
            groups = setOf(PatchGroup.CAPACITY, PatchGroup.TEMPERATURE)
        )
        val bridge = AccBridge(
            capabilityProbe = { capability() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { Properties() },
            defaultConfigReader = { Properties() },
            latestGroupedConfigReader = { groupedConfig() },
            groupedPatchWriter = { group: PatchGroup, _: GroupedConfigRead ->
                if (group == PatchGroup.CAPACITY) GroupApplyWriteResult.Success
                else GroupApplyWriteResult.Failure("temperature write failed")
            },
            verificationGroupedConfigReader = { groupedConfig(capacity = target.currentCapacity) }
        )

        assertEquals(
            ApplyGroupedPatchResult.Partial(
                appliedGroups = listOf(PatchGroup.CAPACITY),
                failedGroups = mapOf(PatchGroup.TEMPERATURE to "temperature write failed"),
                verifiedConfig = groupedConfig(capacity = target.currentCapacity)
            ),
            bridge.applyGroupedPatch(request)
        )
    }

    @Test
    fun verification_mismatch_returns_result() = runBlocking {
        val target = groupedConfig(
            capacity = CapacityConfig(5, 70, 72, 80, true, ConfigGroupMode.NORMAL)
        )
        val verified = groupedConfig(
            capacity = CapacityConfig(5, 70, 72, 79, true, ConfigGroupMode.NORMAL)
        )
        val request = ApplyGroupedPatchRequest(
            base = groupedConfig(),
            target = target,
            groups = setOf(PatchGroup.CAPACITY)
        )
        val bridge = AccBridge(
            capabilityProbe = { capability() },
            versionReader = { Pair(0, null) },
            daemonReader = { false },
            currentConfigReader = { Properties() },
            defaultConfigReader = { Properties() },
            latestGroupedConfigReader = { groupedConfig() },
            groupedPatchWriter = { _: PatchGroup, _: GroupedConfigRead -> GroupApplyWriteResult.Success },
            verificationGroupedConfigReader = { verified }
        )

        val result = bridge.applyGroupedPatch(request)

        assertEquals(
            ApplyGroupedPatchResult.VerificationMismatch(
                appliedGroups = listOf(PatchGroup.CAPACITY),
                verifiedConfig = verified
            ),
            result
        )
        assertTrue(result is ApplyGroupedPatchResult.VerificationMismatch)
    }

    private fun capability(): AccCapability = AccCapability.from(
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
            supportedChargingSwitches = listOf("battery_charging_enabled"),
            preferredChargingSwitch = "battery_charging_enabled",
            supportsCurrentControl = false,
            supportsVoltageControl = false,
            supportedCapacityModes = setOf(CapacityMode.PERCENT),
            supportedTemperatureModes = setOf(TemperatureMode.CELSIUS)
        )
    )

    private fun groupedConfig(
        capacity: CapacityConfig? = null,
        temperature: TemperatureConfig? = null
    ): GroupedConfigRead = GroupedConfigRead(
        current = Properties(),
        defaults = Properties(),
        currentCapacity = capacity,
        currentTemperature = temperature
    )
}
