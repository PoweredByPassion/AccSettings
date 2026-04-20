package app.owlow.accsetting.acc

enum class AccInstallState {
    NOT_INSTALLED,
    BROKEN_INSTALL,
    UPDATE_AVAILABLE,
    UP_TO_DATE
}

data class AccStatus(
    val installState: AccInstallState,
    val installedVersionName: String?,
    val daemonRunning: Boolean,
    val canManageDaemon: Boolean,
    val showInstallAction: Boolean,
    val showUninstallAction: Boolean
)

object AccStatusResolver {
    fun resolve(
        installedVersionCode: Int,
        installedVersionName: String?,
        bundledVersionCode: Int,
        daemonRunning: Boolean
    ): AccStatus {
        val installState = when {
            installedVersionCode <= 0 -> AccInstallState.NOT_INSTALLED
            installedVersionCode < bundledVersionCode -> AccInstallState.UPDATE_AVAILABLE
            else -> AccInstallState.UP_TO_DATE
        }
        val normalizedInstalledVersion = installedVersionName?.takeIf { it.isNotBlank() }
        val canManageDaemon = installState != AccInstallState.NOT_INSTALLED
        return AccStatus(
            installState = installState,
            installedVersionName = normalizedInstalledVersion,
            daemonRunning = canManageDaemon && daemonRunning,
            canManageDaemon = canManageDaemon,
            showInstallAction = installState != AccInstallState.UP_TO_DATE,
            showUninstallAction = installState != AccInstallState.NOT_INSTALLED
        )
    }
}
