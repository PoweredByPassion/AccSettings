# Native Android UI Migration Design

**Goal:** Replace the current legacy settings UI with a fully new native Android interface built around clear status visibility, draft-based configuration editing, and a cleaner architecture that separates UI from ACC command execution.

**Primary Priority**
- Fully retire the current `PreferenceFragment`-driven UI instead of layering more fixes onto it.
- Make the app feel like a modern native Android system utility: calm, clear, and stable.
- Preserve explicit draft editing so configuration changes never apply silently.
- Make "where to apply changes" permanently obvious.

**Visual Direction**
- Use `Jetpack Compose` with `Material 3` as the UI foundation.
- Keep the app visually native to Android rather than imitating web dashboards.
- Favor large section titles, disciplined spacing, low visual noise, and one restrained accent color.
- Avoid dense card walls. Use grouped lists, dividers, and spacing as the default structure.
- Only use elevated surfaces for:
  - current runtime state
  - pending draft changes
  - destructive or recovery actions
- Prefer light theme as the primary design target. Dark theme may follow the same structure later without changing the information hierarchy.

**Product Model**
- The app is reorganized into three top-level destinations:
  - `Overview`
  - `Configuration`
  - `Tools`
- These destinations have distinct jobs:
  - `Overview` explains the current live ACC/device state
  - `Configuration` edits draft settings only
  - `Tools` handles install, repair, diagnostics, service controls, and app metadata
- Users should always know whether they are viewing live state, editing a draft, or performing maintenance.

**Top-Level Navigation**
- Use a single-activity Compose app shell.
- Use a bottom navigation bar with exactly three primary entries:
  - `Overview`
  - `Configuration`
  - `Tools`
- Keep navigation shallow:
  - top-level destinations for major areas
  - detail screens only when a setting group or tool needs more room
- Back behavior follows standard Android expectations:
  - back within nested detail screens returns to the previous screen
  - back from a top-level destination exits the app or returns per normal Android task behavior

**Overview Screen**
- Purpose:
  - show what is happening right now
  - show whether ACC is available and healthy
  - show whether the device is using expected control behavior
- Layout sections in order:
  1. header summary
  2. current state
  3. key actions
  4. applied configuration summary
  5. warnings and anomalies
- Header summary:
  - large title
  - one short sentence describing the live state such as running, missing, broken, or needs attention
- Current state section shows high-signal runtime facts:
  - ACC install state
  - daemon state
  - charging control state
  - battery temperature
  - battery level
- Key actions section contains only a small set of frequent actions:
  - refresh state
  - start or recover service when appropriate
  - open configuration
  - open repair flow when needed
- Applied configuration summary shows live applied values rather than draft values.
- Warnings and anomalies centralize:
  - detection failures
  - permission issues
  - command errors
  - apply verification mismatches
  - pending unapplied draft reminder if a draft exists

**Configuration Screen**
- Purpose:
  - provide a predictable settings-style editor
  - keep edits in draft form until the user explicitly applies them
- Overall structure:
  - top app bar with title and short explanatory text
  - vertically grouped settings list
  - persistent bottom draft action bar that appears only when the draft is dirty
- Initial setting groups:
  - `Charge Thresholds`
  - `Temperature Protection`
  - `Current and Voltage Behavior`
  - `Advanced Options`
  - `Compatibility and Experimental`
- Editing behavior:
  - all edits mutate draft state only
  - no root command or daemon restart is triggered by merely changing a field
  - group summaries should explain the current effective value in plain language
  - each setting should include helper text where the ACC term alone is unclear
- Item patterns:
  - switches for binary behavior
  - list rows for enumerated choices
  - stepper or bounded numeric editor for constrained numbers
  - dedicated detail screen or dialog for complex grouped values
- Unsupported or capability-gated settings:
  - remain visible when useful for comprehension
  - show disabled state with a reason
  - never pretend a feature is available when probing says otherwise

**Draft Action Bar**
- This is the primary interaction fix for the current app.
- Display rules:
  - hidden when draft equals applied config
  - visible whenever any draft change is pending
- Content:
  - short dirty-state message
  - `Discard`
  - `Apply Changes`
- Behavior:
  - `Discard` resets draft to the latest applied config snapshot
  - `Apply Changes` runs the grouped apply pipeline and shows progress inline
  - on apply success:
    - update the applied baseline
    - clear the dirty state
    - hide the action bar
  - on apply failure:
    - keep the draft intact
    - keep the action bar visible
    - show an inline error with retry available
- Leaving the configuration area with a dirty draft must trigger a confirmation sheet with:
  - keep draft and leave
  - discard changes
  - cancel

**Tools Screen**
- Purpose:
  - collect all maintenance and diagnostics flows outside normal editing
- Primary sections:
  - `Install and Repair`
  - `Service Control`
  - `Diagnostics`
  - `App and Version`
- Actions that belong here:
  - install bundled ACC
  - repair broken install
  - restart service
  - force re-detect
  - inspect diagnostics
  - view app and ACC version details
- Dangerous or device-affecting actions must:
  - live in clearly separated sections
  - use stronger explanatory copy
  - require confirmation when the action is potentially disruptive

**Architecture**
- The redesign should move the app to a four-layer structure.

**UI Layer**
- Compose screens, reusable rows, dialogs, sheets, snackbars, and app shell.
- Reads immutable screen state and emits user intents.
- Never executes ACC or root commands directly.

**State Layer**
- Owns screen state and transient UI state.
- Tracks:
  - ACC runtime status
  - applied config snapshot
  - draft config snapshot
  - dirty state
  - loading and applying state
  - user-visible error state
- Exposes state per destination instead of sharing one large mutable screen model.

**Domain Layer**
- Encodes user actions as explicit use cases:
  - refresh overview
  - load applied config
  - update draft field
  - discard draft
  - apply draft
  - start or recover service
  - inspect install state
  - repair install
- Centralizes sequencing rules and validation.

**Bridge Layer**
- The only layer that talks to ACC commands or root shell APIs.
- Owns:
  - status reads
  - config reads
  - grouped config apply
  - daemon control
  - install and repair actions
  - diagnostic export or retrieval

**State Ownership**
- `Overview` state is independent from draft editing state.
- `Configuration` owns the draft lifecycle and should not rely on polling-style UI side effects.
- `Tools` owns maintenance actions and their confirmation/progress state.
- Shared data such as current ACC capability or install status may be observed by multiple destinations, but write actions must still go through dedicated use cases.

**Configuration Data Model**
- Preserve the existing direction toward explicit draft modeling.
- Required config snapshots:
  - `applied`
  - `draft`
  - optional `lastApplyResult`
- Dirty state must be computed from normalized config comparison, not from ad hoc UI flags.
- Grouped values such as capacity or temperature ranges must be modeled and applied as groups rather than as unrelated one-field writes.

**Apply Pipeline**
- The configuration screen must use one unified apply pipeline:
  1. reload latest applied config
  2. normalize current draft
  3. validate grouped rules
  4. compute grouped diff
  5. serialize writes in safe order
  6. read back actual device config
  7. reconcile result into new applied snapshot
- Side effects such as daemon restart or service preparation must only happen inside this pipeline or explicit maintenance actions.
- Editing a field must never cause those side effects immediately.

**Error Handling**
- All major screens need first-class loading, empty, and error states.
- Required user-visible categories:
  - root unavailable
  - ACC not installed
  - broken install
  - unsupported feature
  - config validation failure
  - apply failure
  - daemon action failure
  - diagnostics read failure
- Error presentation rules:
  - screen-level problems use inline banners or dedicated status sections
  - action-level failures stay close to the triggering button or action bar
  - destructive actions should explain consequence before confirmation

**Interaction Rules**
- No hidden menu-only critical actions.
- No silent background application of draft edits.
- No periodic repeated maintenance command execution from UI recomposition or polling loops.
- Navigation labels and setting labels should prefer plain-language wording over ACC jargon where possible.

**Migration Strategy**
- This is a full UI replacement, not a visual refresh of existing fragments.
- Migration phases:
  1. introduce Compose dependencies, app theme, and navigation shell
  2. build the new `Overview`, `Configuration`, and `Tools` screens behind the existing ACC backend
  3. move draft editing and apply UX into the new configuration flow
  4. route install, repair, and diagnostics into the new tools flow
  5. remove legacy XML layouts, `PreferenceFragment` screens, and obsolete menu resources
- During migration, backend fixes already in progress for draft/apply correctness should be retained, but the legacy UI should not be treated as the target product surface.

**Testing**
- Unit tests:
  - draft dirty-state calculation
  - grouped apply sequencing
  - configuration discard and apply transitions
  - tool action state transitions
  - capability-gated field visibility and disabled reasons
- UI tests:
  - overview renders expected install/runtime states
  - configuration edit shows draft action bar
  - apply success clears dirty state
  - apply failure preserves dirty state and message
  - leaving configuration with pending draft shows confirmation
  - tools actions show confirmation and progress correctly
- Device verification:
  - app opens into overview without crash
  - configuration edits do not immediately affect device behavior
  - apply changes works only through the bottom action bar
  - repair and service actions remain available from tools
  - no repeated command spam occurs while screens stay open

**Non-Goals**
- Do not mimic iOS visual conventions in this migration.
- Do not keep the old fragment UI as a parallel permanent experience.
- Do not optimize for highly decorative dashboard visuals over clarity and stability.
- Do not allow direct UI-triggered root side effects outside explicit actions or the apply pipeline.
