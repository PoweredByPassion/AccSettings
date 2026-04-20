package app.owlow.accsetting.ui.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCleanupSmokeTest {
    @Test
    fun legacyPreferenceFragments_areRemovedFromRuntimeClasspath() {
        val legacyClasses = listOf(
            "app.owlow.accsetting.fragment.SettingsFragment",
            "app.owlow.accsetting.fragment.ConfigFragment",
            "app.owlow.accsetting.fragment.MoreFragment",
            "app.owlow.accsetting.AccSettingsApplication",
            "app.owlow.accsetting.ui.AccSettingsApp",
            "app.owlow.accsetting.ui.theme.AccSettingsTheme",
            "app.owlow.accsetting.acc.AccSettingsState",
            "app.owlow.accsetting.acc.AccSettingsSummary",
        )

        val removed = legacyClasses.all { className ->
            runCatching { Class.forName(className) }.isFailure
        }

        assertTrue("Legacy fragment classes should be removed", removed)
    }
}
