package crazyboyfeng.accSettings.ui.navigation

import androidx.annotation.StringRes
import crazyboyfeng.accSettings.R

sealed class AccDestination(
    val route: String,
    @StringRes val labelRes: Int
) {
    data object Overview : AccDestination("overview", R.string.overview)
    data object Configuration : AccDestination("configuration", R.string.configuration)
    data object Tools : AccDestination("tools", R.string.tools)

    companion object {
        val topLevel: List<AccDestination>
            get() = listOf(Overview, Configuration, Tools)
    }
}
