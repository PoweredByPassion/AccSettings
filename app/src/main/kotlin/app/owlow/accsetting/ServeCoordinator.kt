package app.owlow.accsetting

import app.owlow.accsetting.acc.AccInstallState
import app.owlow.accsetting.acc.AccStatus

class ServeCoordinator {
    private var lastServedKey: Pair<AccInstallState, String?>? = null

    fun shouldServe(status: AccStatus): Boolean {
        if (status.installState == AccInstallState.NOT_INSTALLED ||
            status.installState == AccInstallState.BROKEN_INSTALL
        ) {
            lastServedKey = null
            return false
        }

        if (status.daemonRunning) {
            return false
        }

        val currentKey = status.installState to status.installedVersionName
        if (lastServedKey == currentKey) {
            return false
        }

        lastServedKey = currentKey
        return true
    }
}
