package app.owlow.accsettings.quickaction

/**
 * Configuration for the user-customizable quick actions shown on the widget, notification,
 * and app shortcuts. A config is an ordered list of up to [QuickActionSlot.MAX_SLOTS] slots,
 * each picking an operation type + parameter.
 */
data class QuickActionConfig(
    val slots: List<QuickActionSlot> = emptyList(),
    val showBatteryRow: Boolean = true
) {
    companion object {
        /** The default config for first launch — matches the original hardcoded action list. */
        val DEFAULT = QuickActionConfig(
            slots = listOf(
                QuickActionSlot(QuickActionSlotType.PAUSE, "30m"),
                QuickActionSlot(QuickActionSlotType.PAUSE, "1h"),
                QuickActionSlot(QuickActionSlotType.FORCE_FULL, "100"),
                QuickActionSlot(QuickActionSlotType.CHARGE_TO, "85%"),
                QuickActionSlot(QuickActionSlotType.CANCEL, null)
            ),
            showBatteryRow = true
        )
    }
}

/** The operation type a quick-action slot runs. */
enum class QuickActionSlotType {
    /** `acc -d` — pause charging until a duration/capacity condition. */
    PAUSE,
    /** `acc -e` — charge until a duration/capacity target. */
    CHARGE_TO,
    /** `acc -f` — force-full charge to a capacity. */
    FORCE_FULL,
    /** Cancel the active operation and restore normal charging. */
    CANCEL
}

/**
 * One configured quick action. [param] is the operation's argument (e.g. `"30m"`, `"85%"`,
 * `"90"` for force-full), or null for the operation's default (indefinite pause / now /
 * force-full to 100).
 */
data class QuickActionSlot(
    val type: QuickActionSlotType,
    val param: String? = null
) {
    companion object {
        const val MAX_SLOTS = 5
    }

    /**
     * Encodes this slot as a `quickaction:<type>/<param>` URI, the canonical form every surface
     * (widget, notification, shortcuts) uses to dispatch it. The param is percent-encoded so
     * `%` (e.g. in `"85%"`) survives the URI transport. Round-trips through
     * [QuickActionDispatcher.mapUriToAction].
     */
    fun toUri(): String {
        val typeSegment = when (type) {
            QuickActionSlotType.PAUSE -> "pause"
            QuickActionSlotType.CHARGE_TO -> "charge-to"
            QuickActionSlotType.FORCE_FULL -> "force-full"
            QuickActionSlotType.CANCEL -> "cancel"
        }
        return if (param != null) {
            "quickaction:$typeSegment/${android.net.Uri.encode(param)}"
        } else {
            "quickaction:$typeSegment"
        }
    }
}
