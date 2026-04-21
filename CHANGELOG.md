# Changelog

## 2026-04-21

- Upgraded the project to target SDK 36 (Android 16) and modernized with AGP 8.13, Kotlin 2.2.0, and Compose BOM 2026.03.01
- Renamed the package globally to `app.owlow.accsettings`
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
