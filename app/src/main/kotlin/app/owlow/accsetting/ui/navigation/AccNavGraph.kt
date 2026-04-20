package app.owlow.accsetting.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.owlow.accsetting.ui.about.AboutRoute
import app.owlow.accsetting.ui.config.ConfigRoute
import app.owlow.accsetting.ui.overview.OverviewRoute
import app.owlow.accsetting.ui.tools.ToolsRoute

@Composable
fun AccNavGraph(
    navController: NavHostController,
    startDestination: String = AccDestination.Overview.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AccDestination.Overview.route) {
            OverviewRoute(
                onOpenConfiguration = { navController.navigate(AccDestination.Configuration.route) },
                onOpenTools = { navController.navigate(AccDestination.Tools.route) }
            )
        }
        composable(AccDestination.Configuration.route) {
            ConfigRoute()
        }
        composable(AccDestination.Tools.route) {
            ToolsRoute()
        }
        composable(AccDestination.About.route) {
            AboutRoute()
        }
    }
}
