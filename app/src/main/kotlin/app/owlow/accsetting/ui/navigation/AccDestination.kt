package app.owlow.accsetting.ui.navigation

import androidx.annotation.StringRes
import app.owlow.accsetting.R

sealed class AccDestination(
    val route: String,
    @StringRes val labelRes: Int
) {
    data object Overview : AccDestination("overview", R.string.overview)
    data object Configuration : AccDestination("configuration", R.string.configuration)
    data object Tools : AccDestination("tools", R.string.tools)
    data object About : AccDestination("about", R.string.about)

    companion object {
        val topLevel: List<AccDestination>
            get() = listOf(Overview, Configuration, Tools, About)
    }
}
