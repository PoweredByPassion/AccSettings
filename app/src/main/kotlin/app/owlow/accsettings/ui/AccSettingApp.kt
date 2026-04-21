package app.owlow.accsettings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.owlow.accsettings.ui.navigation.AccDestination
import app.owlow.accsettings.ui.navigation.AccNavGraph
import app.owlow.accsettings.ui.theme.*

@Composable
fun AccSettingApp(
    startDestination: String = AccDestination.Overview.route
) {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        containerColor = AccBackground,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier.padding(bottom = 0.dp) // Standard bottom nav
            ) {
                AccDestination.topLevel.forEach { destination ->
                    val isSelected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { /* No icons used yet */ },
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                style = AccTypography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccPrimary,
                            selectedTextColor = AccPrimary,
                            unselectedIconColor = Zinc400,
                            unselectedTextColor = Zinc400,
                            indicatorColor = Zinc100
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AccNavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
