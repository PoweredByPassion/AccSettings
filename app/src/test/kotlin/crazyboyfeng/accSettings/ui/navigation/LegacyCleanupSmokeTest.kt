package crazyboyfeng.accSettings.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCleanupSmokeTest {
    @Test
    fun legacyPreferenceFragments_areRemovedFromRuntimeClasspath() {
        val legacyClasses = listOf(
            "crazyboyfeng.accSettings.fragment.SettingsFragment",
            "crazyboyfeng.accSettings.fragment.ConfigFragment",
            "crazyboyfeng.accSettings.fragment.MoreFragment",
        )

        val removed = legacyClasses.all { className ->
            runCatching { Class.forName(className) }.isFailure
        }

        assertTrue("Legacy fragment classes should be removed", removed)
    }
}
