package app.owlow.accsetting.acc

import java.util.Properties

class AccBridge(
    private val capabilityProbe: suspend () -> AccCapability,
    private val versionReader: suspend () -> Pair<Int, String?>,
    private val daemonReader: suspend () -> Boolean,
    private val currentConfigReader: suspend () -> Properties,
    private val defaultConfigReader: suspend () -> Properties,
    private val latestGroupedConfigReader: suspend () -> GroupedConfigRead = {
        GroupedConfigRead(current = currentConfigReader(), defaults = defaultConfigReader())
    },
    private val groupedPatchWriter: suspend (PatchGroup, GroupedConfigRead) -> GroupApplyWriteResult = { _, _ ->
        GroupApplyWriteResult.Success
    },
    private val verificationGroupedConfigReader: suspend () -> GroupedConfigRead = {
        latestGroupedConfigReader()
    },
    private val installAction: suspend () -> Unit = {},
    private val upgradeAction: suspend () -> Unit = {},
    private val repairAction: suspend () -> Unit = {},
    private val uninstallAction: suspend () -> Unit = {},
    private val daemonToggleAction: suspend (Boolean) -> Boolean = { it },
    private val reinitializeAction: suspend () -> Unit = {},
    private val lifecycleCapabilityRefresh: suspend () -> AccCapability = {
        capabilityProbe()
    },
    private val groupedConfigReader: suspend (Properties, Properties) -> GroupedConfigRead = { current, defaults ->
        GroupedConfigRead(current = current, defaults = defaults)
    },
    private val bundledVersionCodeProvider: () -> Int = { 0 },
    private val taskRunner: AccTaskRunner = AccTaskRunner()
) {
    suspend fun probeCapabilities(): AccCapability = capabilityProbe()

    suspend fun readStatus(): AccStatus {
        val (installedVersionCode, installedVersionName) = versionReader()
        val daemonRunning = daemonReader()
        return AccStatusResolver.resolve(
            installedVersionCode = installedVersionCode,
            installedVersionName = installedVersionName,
            bundledVersionCode = bundledVersionCodeProvider(),
            daemonRunning = daemonRunning
        )
    }

    suspend fun readCurrentConfig(): Properties = currentConfigReader()

    suspend fun readDefaultConfig(): Properties = defaultConfigReader()

    suspend fun readGroupedConfig(): GroupedConfigRead {
        val current = readCurrentConfig()
        val defaults = readDefaultConfig()
        return groupedConfigReader(current, defaults).resolveGroups()
    }

    suspend fun applyGroupedPatch(request: ApplyGroupedPatchRequest): ApplyGroupedPatchResult =
        taskRunner.runSerialized {
            val latest = latestGroupedConfigReader()
            if (!latest.isSameAs(request.base)) {
                return@runSerialized ApplyGroupedPatchResult.StaleBaseConfig
            }

            val validationErrors = validateRequest(request)
            if (validationErrors.isNotEmpty()) {
                return@runSerialized ApplyGroupedPatchResult.ValidationFailed(validationErrors)
            }

            val appliedGroups = mutableListOf<PatchGroup>()
            val failedGroups = linkedMapOf<PatchGroup, String>()

            for (group in request.groups.sortedBy { it.ordinal }) {
                when (val result = groupedPatchWriter(group, request.target)) {
                    GroupApplyWriteResult.Success -> appliedGroups += group
                    is GroupApplyWriteResult.Failure -> failedGroups[group] = result.message
                }
            }

            val verified = verificationGroupedConfigReader()
            return@runSerialized when {
                failedGroups.isNotEmpty() -> ApplyGroupedPatchResult.Partial(
                    appliedGroups = appliedGroups,
                    failedGroups = failedGroups,
                    verifiedConfig = verified
                )
                !verified.isSameAs(request.target) -> ApplyGroupedPatchResult.VerificationMismatch(
                    appliedGroups = appliedGroups,
                    verifiedConfig = verified
                )
                else -> ApplyGroupedPatchResult.Success(
                    appliedGroups = appliedGroups,
                    verifiedConfig = verified
                )
            }
        }

    private fun validateRequest(request: ApplyGroupedPatchRequest): List<String> {
        val errors = mutableListOf<String>()
        if (PatchGroup.CAPACITY in request.groups) {
            val capacity = request.target.currentCapacity
            if (capacity != null &&
                capacity.mode == ConfigGroupMode.NORMAL &&
                !(capacity.shutdown < capacity.cooldown &&
                    capacity.cooldown <= capacity.resume &&
                    capacity.resume < capacity.pause)
            ) {
                errors += "capacity ordering is invalid"
            }
        }
        if (PatchGroup.TEMPERATURE in request.groups) {
            val temperature = request.target.currentTemperature
            if (temperature != null &&
                temperature.mode == ConfigGroupMode.NORMAL &&
                !(temperature.cooldown < temperature.resume &&
                    temperature.resume < temperature.pause &&
                    temperature.pause < temperature.shutdown)
            ) {
                errors += "temperature ordering is invalid"
            }
        }
        return errors
    }

    suspend fun ensureInstalled(): LifecycleActionResult = taskRunner.runSerialized {
        val capability = probeCapabilities()
        val installedVersionCode = capability.staticAvailability.accVersionCode
        val decision = when {
            installedVersionCode <= 0 -> LifecycleDecision.INSTALL
            bundledVersionCodeProvider() > installedVersionCode -> LifecycleDecision.UPGRADE
            else -> LifecycleDecision.NO_OP
        }
        when (decision) {
            LifecycleDecision.INSTALL -> installAction()
            LifecycleDecision.UPGRADE -> upgradeAction()
            LifecycleDecision.NO_OP,
            LifecycleDecision.REPAIR,
            LifecycleDecision.UNINSTALL,
            LifecycleDecision.REINITIALIZE -> Unit
        }
        LifecycleActionResult(decision = decision, capability = lifecycleCapabilityRefresh())
    }

    suspend fun repair(): LifecycleActionResult = taskRunner.runSerialized {
        repairAction()
        LifecycleActionResult(
            decision = LifecycleDecision.REPAIR,
            capability = lifecycleCapabilityRefresh()
        )
    }

    suspend fun uninstall(): LifecycleActionResult = taskRunner.runSerialized {
        uninstallAction()
        LifecycleActionResult(
            decision = LifecycleDecision.UNINSTALL,
            capability = lifecycleCapabilityRefresh()
        )
    }

    suspend fun reinitialize(): LifecycleActionResult = taskRunner.runSerialized {
        reinitializeAction()
        LifecycleActionResult(
            decision = LifecycleDecision.REINITIALIZE,
            capability = lifecycleCapabilityRefresh()
        )
    }

    suspend fun setDaemonRunning(daemonRunning: Boolean): DaemonActionResult = taskRunner.runSerialized {
        val success = daemonToggleAction(daemonRunning)
        DaemonActionResult(success = success, daemonRunning = if (success) daemonRunning else daemonReader())
    }
}
