# Changelog

## 2026-08-12

**New**

- **Charging-control quick actions**: the Overview card can now pause charging (`acc -d`), resume charging to a target (`acc -e`), or force-full charge to a capacity (`acc -f`) — three mutually-exclusive one-shot operations. Starting one auto-cancels any other active operation, and each shows a live countdown / target while in effect.
- **Live foreground notification**: while an operation is active, an ongoing notification shows the operation, a remaining-time countdown, the current battery level, and action buttons (including Cancel). It is started only while an operation runs and auto-dismisses when the operation finishes or is cancelled.
- **Quick Settings tile**: a tile reflects the active operation and cancels it on tap (or opens the app when idle).
- **Home-screen widget**: a rounded-card quick-action widget with the configured action buttons and an optional battery-status row. Default grid size is 4x2.
- **App shortcuts**: long-press the launcher icon to run quick actions directly. Shortcuts are now dynamic and follow the configured action list.
- **Customizable quick actions**: a new Quick Actions settings screen (from the Tools page) lets you add, remove, reorder, and parameterize up to five actions (pause duration/capacity, charge-to target, force-full capacity, restore), toggle the widget battery row, and edit any action's parameter. The widget, notification, and shortcuts all stay in sync.
- **Auto-read battery health**: "Estimate battery health" now reads the design capacity from sysfs automatically (no manual mAh input) and shows the result inline.
- **Loading + result feedback**: triggering a quick action from a shortcut or widget shows a brief loading card, then a success/failure result.

**Fixes**

- **Fixed "app not installed" on shortcut taps**: app shortcuts pointed at a broadcast receiver, which Android cannot start via `startActivity`. They now route through a transparent activity, so tapping a shortcut actually runs the action.
- **Fixed crash on quick-action taps**: the result toast was shown on a background thread (`Can't toast on a thread that has not called Looper.prepare()`). Toasts now post to the main looper.
- **Error toasts show the real cause**: a failed quick action now shows the actual error message (e.g. "Root permission required") instead of a generic "Error".
- **Concurrent quick actions are serialized**: a process-level lock prevents two surfaces (widget, tile, notification, app) from starting conflicting ACC commands at the same time.
- **Charging action labels are user-friendly**: removed `acc -e` / `acc -f` terminology from visible copy ("Resume charging, ignoring the configured limits", "Charge once to a set capacity").
- **Quick Action config stays consistent**: fixed a double-read race when reordering slots; slots can now be re-edited after creation.

## 2026-08-11

**New**

- **Force-stop charging now shows a live countdown**: while charging is paused, the Overview card shows the remaining time (e.g. "Charging resumes in 23m 41s") for duration-based recovery conditions instead of a static label.

**Fixes**

- **Force-stop charging state now reconciles with the real ACC state**: the card no longer stays stuck on "Charging resumes in 30 minutes" after ACC has already restored charging. On every status refresh the app derives the true device state from ACC's charging status/level — a duration condition that has elapsed, a capacity threshold that has been reached, or the battery reporting `Charging` again all clear the card automatically. This covers all three recovery condition types (duration, capacity, unconditional).
- **Reboot no longer leaves a stale force-stop card**: a force-stop started before the last boot is detected (via `SystemClock.elapsedRealtime()`) and cleared immediately, since ACC's detached timer and the sysfs charging switch both die on reboot and ACC's boot service restarts the daemon with the normal config.

## 2026-08-10

**New**

- **Charging Information section**: the home screen now reads live charging data from ACC (`acc --info`) instead of the Android system API, so it reflects the same root-side values AccSetting manages. When ACC/root is unavailable it falls back to the system battery API. Refresh interval dropped from 15s to 3s.
- **Fast-charging handshake details**: new rows show the negotiated USB protocol (`usb/type`, e.g. USB_PD), PD negotiation state, negotiated voltage/current and computed power (`voltage_max × current_max`), and CC mode — read from sysfs under `/sys/class/power_supply/` with automatic port discovery (`usb` / `main`).
- **Friendly charge-type labels**: `pc_port`/`usb`/`dc` are localized (e.g. "PC port", "USB", "Wireless") instead of shown raw.
- Section title renamed from "Battery Information" to "Charging Information" (en + zh).

**Fixes**

- **Config writes are now atomic**: the five capacity fields (sc/cc/rc/pc/cm) are merged into a single `acc --set sc=.. cc=.. rc=.. pc=.. cm=..` command instead of five separate writes, so ACC's `write-config.sh` linkage fallback can no longer rewrite intermediate states and cause frontend/backend config divergence (the "config changed on device" error).
- English-only code comments (all Chinese comments translated).

## 2026-08-04

**New**

- **Dark theme support**: the app now follows the Android system dark mode. All screens (Overview / Config / Tools / About) and the bottom navigation switch between light and dark palettes automatically; the dark palette reuses the existing Zinc-based scheme with a brighter Emerald accent for switches and success states. Startup no longer flashes white in dark mode (`values-night` window background). The terminal-style log card keeps its fixed dark look in both modes.

## 2026-08-03

**Fixes (cross-checked against official ACC command syntax + real-device testing)**

- **Fixed the root cause of config writes silently failing**: `Command.setConfig` wrongly appended a `--` prefix after `--set` (producing `--set --sc=10`, which official ACC rejects), so capacity/temperature/current edits "saved but never applied". Now emits the correct `--set sc=10` syntax, locked down with contract tests
- Fixed ACC executable cache: when the cached path goes stale after ACC uninstall/update, it is now re-validated and re-discovered instead of reusing a dead path
- **Fixed Config / Overview crashing when ACC is unavailable** (not installed, no root, command failure): errors are mapped to readable messages instead of crashing the screen
- Fixed status refresh swallowing errors: `AccStatus` gained `lastError`, and Overview now shows the real reason when a refresh fails
- Fixed battery current unit: `BATTERY_PROPERTY_CURRENT_NOW` is in µA, so low standby currents were shown as "XXX mA"; now displays µA/mA/A correctly; removed an unreachable dead branch in voltage formatting
- Fixed temperature ordering validation being too strict: official default `cooldown_temp=45 > resume_temp=40` is inverted by design, and the old check rejected valid configs; now only enforces `resume <= pause <= shutdown`
- Real capability probing: charging switch list and current/voltage control support are now read from the device (`-s s:`, `max_charging_current`, etc.) instead of hardcoded "unsupported"
- **Fixed daemon toggle appearing frozen**: a progress indicator is shown and the affected controls are disabled while the root command runs

**Cleanup**

- Removed the `capacity_sync` config item (the underlying ACC key no longer exists); reverted its UI field and serializer to the 5-element capacity format
- Cut Overview battery polling from 3s to 15s to reduce root command frequency
- Removed the full-screen loading spinner from service start and daemon toggle; failures still clear the loading state
- Fixed capacity edits discarding `ADVANCED_CUSTOM`/`MIXED_LEGACY` modes (mode is now preserved on rebuild)
- Wired the config store's capability probe to the real ACC device probe (charging switches, current/voltage control)
- Routed the boot/app-replaced workers through the serialized ACC state manager; deleted the unused `AccDataStore` and the dead `AccHandler.initial()`
- Rejected non-numeric input on numeric config fields instead of silently converting it to 0
- Removed the unused `getConfig`/`getPropertyValue` command helpers

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