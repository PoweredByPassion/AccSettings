package app.owlow.accsetting.ui.overview

import app.owlow.accsetting.MainDispatcherRule
import app.owlow.accsetting.acc.AccInstallState
import app.owlow.accsetting.acc.AccStatus
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    private class FakeOverviewRepository(
        private val status: AccStatus?
    ) : OverviewRepository {
        override suspend fun loadStatus(): AccStatus? = status

        override suspend fun startService(): AccStatus? = status?.copy(daemonRunning = true)
    }
}
