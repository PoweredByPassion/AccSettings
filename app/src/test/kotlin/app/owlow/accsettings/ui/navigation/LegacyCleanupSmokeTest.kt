package app.owlow.accsettings.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCleanupSmokeTest {
    @Test
    fun legacyPreferenceFragments_areRemovedFromRuntimeClasspath() {
        val legacyClasses = listOf(
            "app.owlow.accsettings.fragment.SettingsFragment",
            "app.owlow.accsettings.fragment.ConfigFragment",
            "app.owlow.accsettings.fragment.MoreFragment",
            "app.owlow.accsettings.AccSettingsApplication",
            "app.owlow.accsettings.ui.AccSettingsApp",
            "app.owlow.accsettings.ui.theme.AccSettingsTheme",
            "app.owlow.accsettings.acc.AccSettingsState",
            "app.owlow.accsettings.acc.AccSettingsSummary",
        )

        val removed = legacyClasses.all { className ->
            runCatching { Class.forName(className) }.isFailure
        }

        assertTrue("Legacy fragment classes should be removed", removed)
    }
}
