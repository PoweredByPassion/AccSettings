package crazyboyfeng.accSettings.ui.overview

import crazyboyfeng.accSettings.MainDispatcherRule
import crazyboyfeng.accSettings.acc.AccInstallState
import crazyboyfeng.accSettings.acc.AccStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OverviewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_mapsAccStatusIntoOverviewSections() = runTest {
        val viewModel = OverviewViewModel(
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

    private class FakeOverviewRepository(
        private val status: AccStatus?
    ) : OverviewRepository {
        override suspend fun loadStatus(): AccStatus? = status
    }
}
