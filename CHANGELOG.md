# Changelog

## 2026-06-02

- Fixed `cooldown_capacity=101` (ACC default disable sentinel) being classified as `MIXED_LEGACY`, which silently blocked all capacity edits (resolves #5)
- Fixed capacity ordering validation: corrected from `shutdown < cooldown <= resume < pause` to `shutdown < resume <= cooldown <= pause` to match ACC's actual trigger logic
- Added 101 as a selectable value in the cooldown_capacity picker (ACC disable sentinel)
- Fixed `shutdown_capacity=-1` (< 1) disable sentinel causing ADVANCED_CUSTOM classification and blocking edits
- Fixed `resume_temp` 'r' suffix (e.g., "45r") causing temperature group parse failure — now stripped and preserved as `resumeTempByCooldown`
- Added `capacity_sync` support: new 3-state field (auto/true/false) in the capacity configuration UI
- Fixed 6-element capacity tuple parsing — previously only accepted 5 elements, now supports both legacy 5-element and ACC's 6-element format (with capacity_sync)
- Fixed `CapacityConfig.serialize()` losing capacity_sync — now outputs 6 elements when sync differs from default
- Added `CapacitySync` enum for proper serialization/deserialization of auto/true/false values
- Updated capacity ordering error messages to reflect correct ordering
- Fixed pre-existing OverviewScreen test compilation errors

## 2026-05-22

- Added daemon toggle switch to the Overview screen and moved it to the top of the page
- Removed `.idea` from git tracking and added `.claude` to gitignore

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