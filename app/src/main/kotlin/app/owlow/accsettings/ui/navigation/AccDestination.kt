package app.owlow.accsettings.ui.navigation

import androidx.annotation.StringRes
import app.owlow.accsettings.R

sealed class AccDestination(
    val route: String,
    @StringRes val labelRes: Int
) {
    data object Overview : AccDestination("overview", R.string.overview)
    data object Configuration : AccDestination("configuration", R.string.configuration)
    data object Tools : AccDestination("tools", R.string.tools)
    data object About : AccDestination("about", R.string.about)
    /** Sub-screen reached from Tools; not in the bottom nav. */
    data object QuickActions : AccDestination("quick_actions", R.string.quick_actions_config_title)

    companion object {
        val topLevel: List<AccDestination>
            get() = listOf(Overview, Configuration, Tools, About)
    }
}
