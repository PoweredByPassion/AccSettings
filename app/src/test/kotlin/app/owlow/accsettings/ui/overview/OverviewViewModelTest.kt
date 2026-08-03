package app.owlow.accsettings.ui.overview

import app.owlow.accsettings.MainDispatcherRule
import app.owlow.accsettings.acc.AccInstallState
import app.owlow.accsettings.acc.AccStatus
import app.owlow.accsettings.acc.BatteryInfo
import app.owlow.accsettings.acc.Command
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
class OverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_mapsAccStatusIntoOverviewSections() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.statusHeadline.contains("running", ignoreCase = true))
        assertTrue(viewModel.uiState.value.primaryActions.isNotEmpty())
    }

    @Test
    fun refresh_exposesDistinctOverviewActions() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            )
        )

        viewModel.refresh().join()

        assertEquals(
            listOf("refresh", "start", "configuration"),
            viewModel.uiState.value.primaryActions.map { it.id }
        )
    }

    @Test
    fun refresh_formatsBatteryFactsIntoUserFacingUnits() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    batteryInfo = BatteryInfo(
                        level = "83",
                        status = "charging",
                        temp = "315",
                        current = "1543000",
                        voltage = "4187",
                        power = "6459000"
                    )
                )
            )
        )

        viewModel.refresh().join()

        assertEquals(
            listOf("83%", "Charging", "31.5°C", "1.54 A", "4187 mV", "6.46 W"),
            viewModel.uiState.value.batteryFacts.map { it.value }
        )
    }

    @Test
    fun refresh_formatsSmallNegativePowerWithoutInflatingUnits() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    batteryInfo = BatteryInfo(
                        level = "83",
                        status = "discharging",
                        temp = "315",
                        current = "-65900",
                        voltage = "4197",
                        power = "-276432"
                    )
                )
            )
        )

        viewModel.refresh().join()

        assertEquals(
            listOf("83%", "Discharging", "31.5°C", "-65.9 mA", "4197 mV", "-0.28 W"),
            viewModel.uiState.value.batteryFacts.map { it.value }
        )
    }

    @Test
    fun refresh_formatsBatteryCurrentWithCorrectUnitScale() = runTest {
        val cases = listOf(
            "800" to "800 µA",
            "1200" to "1.2 mA",
            "1500000" to "1.5 A"
        )
        cases.forEach { (current, expected) ->
            val viewModel = OverviewViewModel(
                context = ApplicationProvider.getApplicationContext(),
                overviewRepository = FakeOverviewRepository(
                    status = AccStatus(
                        installState = AccInstallState.UP_TO_DATE,
                        installedVersionName = "2025.5.18-dev",
                        daemonRunning = true,
                        canManageDaemon = true,
                        showInstallAction = false,
                        showUninstallAction = true,
                        batteryInfo = BatteryInfo(
                            level = null,
                            status = null,
                            temp = null,
                            current = current,
                            voltage = null,
                            power = null
                        )
                    )
                )
            )

            viewModel.refresh().join()

            assertEquals(expected, viewModel.uiState.value.batteryFacts.single().value)
        }
    }

    @Test
    fun autoRefresh_reloadsBatteryStateUntilStopped() = runTest {
        val repository = CountingOverviewRepository()
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = repository
        )

        viewModel.startAutoRefresh(intervalMs = 3_000L)
        runCurrent()
        assertEquals(1, repository.loadCount)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(2, repository.loadCount)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(3, repository.loadCount)

        viewModel.stopAutoRefresh()
        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(3, repository.loadCount)
    }

    @Test
    fun toggleDaemon_whenAccUnavailable_appendsWarningInsteadOfCrashing() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            ).apply { failDaemonToggle = true }
        )

        viewModel.toggleDaemon(enabled = true).join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.warnings.any { it == "Failed to toggle daemon" })
    }

    @Test
    fun startService_whenAccUnavailable_appendsWarningInsteadOfCrashing() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true
                )
            ).apply { failStartService = true }
        )

        viewModel.startService().join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.warnings.any { it == "Failed to start service" })
    }

    @Test
    fun refresh_exposesLastErrorAsWarning() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = false,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    lastError = "Root permission required"
                )
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.warnings.any { it == "Root permission required" })
    }

    @Test
    fun refresh_omitsBlankLastErrorFromWarnings() = runTest {
        val viewModel = OverviewViewModel(
            context = ApplicationProvider.getApplicationContext(),
            overviewRepository = FakeOverviewRepository(
                status = AccStatus(
                    installState = AccInstallState.UP_TO_DATE,
                    installedVersionName = "2025.5.18-dev",
                    daemonRunning = true,
                    canManageDaemon = true,
                    showInstallAction = false,
                    showUninstallAction = true,
                    lastError = "   "
                )
            )
        )

        viewModel.refresh().join()

        assertTrue(viewModel.uiState.value.warnings.none { it == "   " })
    }

    private class FakeOverviewRepository(
        private val status: AccStatus?
    ) : OverviewRepository {
        var failStartService = false
        var failDaemonToggle = false

        override suspend fun loadStatus(): AccStatus? = status

        override suspend fun startService(): AccStatus? {
            if (failStartService) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = true)
        }

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus? {
            if (failDaemonToggle) {
                throw Command.NotRootException()
            }
            return status?.copy(daemonRunning = enabled)
        }
    }

    private class CountingOverviewRepository : OverviewRepository {
        var loadCount = 0

        override suspend fun loadStatus(): AccStatus {
            loadCount += 1
            return AccStatus(
                installState = AccInstallState.UP_TO_DATE,
                installedVersionName = "2025.5.18-dev",
                daemonRunning = true,
                canManageDaemon = true,
                showInstallAction = false,
                showUninstallAction = true,
                batteryInfo = BatteryInfo(
                    level = "83",
                    status = "charging",
                    temp = "315",
                    current = "1543000",
                    voltage = "4187000",
                    power = "6459000"
                )
            )
        }

        override suspend fun startService(): AccStatus = loadStatus().copy(daemonRunning = true)

        override suspend fun setDaemonRunning(enabled: Boolean): AccStatus = loadStatus().copy(daemonRunning = enabled)
    }
}
