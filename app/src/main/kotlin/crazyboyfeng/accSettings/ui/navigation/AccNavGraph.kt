package crazyboyfeng.accSettings.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import crazyboyfeng.accSettings.ui.config.ConfigRoute
import crazyboyfeng.accSettings.ui.overview.OverviewRoute
import crazyboyfeng.accSettings.ui.tools.ToolsRoute

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
            OverviewRoute()
        }
        composable(AccDestination.Configuration.route) {
            ConfigRoute()
        }
        composable(AccDestination.Tools.route) {
            ToolsRoute()
        }
    }
}
