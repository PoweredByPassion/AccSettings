package app.owlow.accsettings

import app.owlow.accsettings.acc.AccInstallState
import app.owlow.accsettings.acc.AccStatus

/**
 * Half-finished serve-decision helper. Kept only as a candidate for a future "auto-start ACC on
 * boot" flow; it is not wired into any screen or worker yet. See AccPresets for the sibling
 * work-in-progress.
 */
@Deprecated("Not wired into any screen or worker yet; pending serve-on-boot feature")
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
