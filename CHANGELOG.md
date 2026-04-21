# Changelog

## 2026-04-21

- Upgraded the project to target SDK 36 (Android 16) and modernized with AGP 8.13, Kotlin 2.2.0, and Compose BOM 2026.03.01
- Renamed the package globally to `app.owlow.accsettings`
- Added live **Battery Information** block to the Overview page (level, temp, current, voltage, power)
- Switched Overview battery telemetry to Android system battery APIs instead of ACC runtime info
- Added **charging status** to the Overview battery block
- Refreshed Overview battery information automatically every 3 seconds while the page is visible
- Made the **GitHub project link** interactive on the About page
- Fixed **Navigation stability** ensuring the Overview tab remains reachable after internal redirects
- Implemented **Anchored Inline Feedback** for actions on Tools and Configuration pages
- Added Per-App Language support (Android 13+) via system settings
- Updated UI and lifecycle patterns with modern `enableEdgeToEdge()` and `viewModelFactory` DSL
- Raised `minSdk` to 23 to meet modern Compose library requirements

## 2026-04-20

- Fixed the Overview action wiring so refresh and navigation no longer trigger the same behavior
- Centered the Overview loading indicator
- Restored editable Configuration fields and current value display
- Added an About page with app version, package name, and project repository information
- Added ACC runtime log viewing to the Tools page
- Added missing Simplified Chinese strings for the migrated Compose UI
- Renamed the Android package/application id to `app.owlow.accsetting`
