package app.owlow.accsettings.quickaction

/**
 * A quick action that any surface (QS tile, shortcut, widget, notification, or the app UI) can
 * hand to [ChargingControlCoordinator.execute] to run against ACC.
 *
 * [Pause] and [ChargeTo] carry a nullable condition/target. Quick-action surfaces (tiles,
 * shortcuts, widget, notification) ALWAYS pass a concrete value (e.g. "30m", "85%") — a
 * picker-requiring surface opens the app's Overview dialog instead of sending an unchosen
 * condition. The in-app Overview dialog is the only path that passes `null`, which means
 * "pause indefinitely / resume now" (unconditional `acc -d` / `acc -e`).
 */
sealed interface QuickAction {
    /** Pause charging for [condition] (e.g. "30m", "1h", "60%"), or null = indefinitely. */
    data class Pause(val condition: String?) : QuickAction
    /** Charge to a target [target] (e.g. "85%", "30m"), or null = resume immediately. */
    data class ChargeTo(val target: String?) : QuickAction
    /** One-shot force-full charge to [capacity]%. */
    data class ForceFull(val capacity: Int) : QuickAction
    /** Cancel the active operation and restore normal charging. */
    data object Cancel : QuickAction
    /** Start the ACC daemon. */
    data object StartDaemon : QuickAction
    /** Stop the ACC daemon. */
    data object StopDaemon : QuickAction
}

/** How a surface reports the result of a quick action (toast for widgets/shortcuts, etc.). */
interface FeedbackSink {
    fun show(message: String)
}
