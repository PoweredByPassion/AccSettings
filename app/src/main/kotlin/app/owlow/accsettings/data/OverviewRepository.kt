package app.owlow.accsettings.data

import app.owlow.accsettings.acc.AccStateManager
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.ChargingControlMode

interface OverviewRepository {
    suspend fun loadStatus(): AccStatus?
    suspend fun startService(): AccStatus?
    suspend fun setDaemonRunning(enabled: Boolean): AccStatus?
    suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus?
    suspend fun enableCharging(condition: String?): AccStatus?
    suspend fun forceFullCharge(capacity: Int): AccStatus?
    suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus?
}

object LiveOverviewRepository : OverviewRepository {
    override suspend fun loadStatus(): AccStatus? = AccStateManager.refreshStatus()

    override suspend fun startService(): AccStatus? {
        AccStateManager.setDaemonRunning(true)
        return AccStateManager.refreshStatus()
    }

    override suspend fun setDaemonRunning(enabled: Boolean): AccStatus? {
        AccStateManager.setDaemonRunning(enabled)
        return AccStateManager.refreshStatus()
    }

    override suspend fun setForceStopCharging(enabled: Boolean, condition: String?): AccStatus? {
        AccStateManager.setForceStopCharging(enabled, condition)
        return AccStateManager.refreshStatus()
    }

    override suspend fun enableCharging(condition: String?): AccStatus? {
        AccStateManager.enableCharging(condition)
        return AccStateManager.refreshStatus()
    }

    override suspend fun forceFullCharge(capacity: Int): AccStatus? {
        AccStateManager.forceFullCharge(capacity)
        return AccStateManager.refreshStatus()
    }

    override suspend fun cancelChargeAction(mode: ChargingControlMode): AccStatus? {
        AccStateManager.cancelChargeAction(mode)
        return AccStateManager.refreshStatus()
    }
}
