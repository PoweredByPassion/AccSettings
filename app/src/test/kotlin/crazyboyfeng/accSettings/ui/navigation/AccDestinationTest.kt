package crazyboyfeng.accSettings.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AccDestinationTest {
    @Test
    fun topLevelDestinations_areOverviewConfigurationAndTools() {
        assertEquals(
            listOf("overview", "configuration", "tools"),
            AccDestination.topLevel.map { it.route }
        )
    }
}
