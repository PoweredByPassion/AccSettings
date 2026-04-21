package app.owlow.accsettings.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.owlow.accsettings.ui.about.AboutRoute
import app.owlow.accsettings.ui.config.ConfigRoute
import app.owlow.accsettings.ui.overview.OverviewRoute
import app.owlow.accsettings.ui.tools.ToolsRoute

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
                onOpenConfiguration = {
                    navController.navigate(AccDestination.Configuration.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenTools = {
                    navController.navigate(AccDestination.Tools.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
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
