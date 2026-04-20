package app.owlow.accsetting.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AccDestinationTest {
    @Test
    fun topLevelDestinations_includeAbout() {
        assertEquals(
            listOf("overview", "configuration", "tools", "about"),
            AccDestination.topLevel.map { it.route }
        )
    }
}
